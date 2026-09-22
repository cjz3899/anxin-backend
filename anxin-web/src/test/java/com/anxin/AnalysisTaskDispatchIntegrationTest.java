package com.anxin;

import com.anxin.ai.analysis.RiskAnalyzer;
import com.anxin.ai.model.RiskAnalysisResult;
import com.anxin.ai.model.RiskDetailInfo;
import com.anxin.ai.model.RiskLevel;
import com.anxin.entity.AnalysisTask;
import com.anxin.entity.Document;
import com.anxin.entity.DocumentSection;
import com.anxin.entity.RiskDetail;
import com.anxin.entity.RiskResult;
import com.anxin.entity.User;
import com.anxin.enums.TaskStatus;
import com.anxin.mapper.AnalysisTaskMapper;
import com.anxin.mapper.DocumentMapper;
import com.anxin.mapper.DocumentSectionMapper;
import com.anxin.mapper.RiskDetailMapper;
import com.anxin.mapper.RiskResultMapper;
import com.anxin.mapper.UserMapper;
import com.anxin.service.IDocumentService;
import com.anxin.support.DirectUploadFixture;
import com.anxin.task.AnalysisTaskConsumer;
import com.anxin.task.AnalysisTaskMessage;
import com.anxin.vo.DocumentUploadVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 上传 → 事务提交后线程池派发 → 消费状态机 集成测试（依赖本地 MySQL/Redis 与 dev 配置）
 */
@SpringBootTest(properties = "anxin.task.compensation-enabled=false")
@Slf4j
class AnalysisTaskDispatchIntegrationTest {

    private static final String TEST_OPENID = "test-openid-001";

    @Resource
    private IDocumentService documentService;

    @Resource
    private AnalysisTaskMapper analysisTaskMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private DocumentSectionMapper documentSectionMapper;

    @Resource
    private RiskResultMapper riskResultMapper;

    @Resource
    private RiskDetailMapper riskDetailMapper;

    @Resource
    private AnalysisTaskConsumer analysisTaskConsumer;

    @MockitoBean
    private RiskAnalyzer riskAnalyzer;

    @Test
    void uploadDispatchAndConsume() throws Exception {
        when(riskAnalyzer.analyze(any())).thenReturn(mockAnalysisResult());

        Long userId = ensureTestUser();
        DocumentUploadVO vo = DirectUploadFixture.upload(
                documentService, userId, "contract.pdf", contractPdf());

        assertNotNull(vo.getDocumentId(), "documentId 未回填");
        assertNotNull(vo.getTaskId(), "taskId 未回填");
        assertEquals("PENDING", vo.getStatus());
        log.info("上传成功 documentId : {}, taskId : {}", vo.getDocumentId(), vo.getTaskId());
        Long taskId = Long.valueOf(vo.getTaskId());
        Long documentId = Long.valueOf(vo.getDocumentId());
        AnalysisTask task = waitUntilFinished(taskId, Duration.ofSeconds(60));
        log.info("任务终态 status : {}, retryCount : {}, errorMessage : {}", task.getStatus(), task.getRetryCount(), task.getErrorMessage());
        assertEquals(TaskStatus.SUCCESS.getCode(), task.getStatus().intValue(),
                "任务未在超时时间内到达 SUCCESS，请确认分析线程池正常且消费日志无异常");
        assertEquals(0, task.getRetryCount(), "成功任务不应产生重试");

        Document document = documentMapper.selectById(documentId);
        assertNotNull(document, "document 记录不存在");
        assertEquals(TaskStatus.SUCCESS.getCode(), document.getStatus().intValue(), "document.status 未回写 SUCCESS");

        assertSectionsAndRisks(documentId, taskId, "第一条链路验收", 1);
    }

    /**
     * 故障演练：LLM 分析持续失败（等价于 dev.yml base-url 指向不存在的地址），
     * 手动驱动重投直到终态，验证 retry_count 0→1→2→3、终态 FAILED、document 同步置 FAILED、
     * 先删后插保证 document_section 不残留重复数据
     */
    @Test
    void retryExhaustionMarksTaskAndDocumentFailed() throws Exception {
        when(riskAnalyzer.analyze(any())).thenThrow(new RuntimeException("Connection refused: api.deepseek.ai"));

        Long userId = ensureTestUser();
        DocumentUploadVO vo = DirectUploadFixture.upload(
                documentService, userId, "contract.pdf", contractPdf());
        Long taskId = Long.valueOf(vo.getTaskId());
        Long documentId = Long.valueOf(vo.getDocumentId());

        Document document = documentMapper.selectById(documentId);
        AnalysisTaskMessage message = AnalysisTaskMessage.builder()
                .taskId(taskId)
                .documentId(documentId)
                .fileUrl(document.getFileUrl())
                .fileType(document.getFileType())
                .build();

        AnalysisTask task = driveRetriesUntilFinished(message);
        log.info("故障演练终态 status : {}, retryCount : {}, errorMessage : {}", task.getStatus(), task.getRetryCount(), task.getErrorMessage());
        assertEquals(TaskStatus.FAILED.getCode(), task.getStatus().intValue(), "重试耗尽后任务应为 FAILED");
        assertEquals(3, task.getRetryCount().intValue(), "重试次数应为 3");
        assertTrue(task.getErrorMessage() != null && !task.getErrorMessage().isBlank(), "终态 FAILED 应有 error_message");

        Document failedDocument = documentMapper.selectById(documentId);
        assertEquals(TaskStatus.FAILED.getCode(), failedDocument.getStatus().intValue(), "document.status 未同步置 FAILED");

        //条款已落库，但模型调用失败，结果表必须是空的——不能留下半截风险报告
        assertSectionsAndRisks(documentId, taskId, "故障演练验收", 0);
    }

    /**
     * 手动重复调用消费入口驱动重投，不等 30 秒的补偿扫描周期，
     * 每轮失败 retry_count +1，最多 6 轮兜底，等待任务到达终态
     */
    private AnalysisTask driveRetriesUntilFinished(AnalysisTaskMessage message) throws InterruptedException {
        for (int i = 0; i < 6; i++) {
            analysisTaskConsumer.process(message);
            AnalysisTask task = analysisTaskMapper.selectById(message.getTaskId());
            if (task != null && isFinished(task)) {
                return task;
            }
            Thread.sleep(200);
        }
        AnalysisTask task = analysisTaskMapper.selectById(message.getTaskId());
        assertNotNull(task, "任务记录不存在 taskId : " + message.getTaskId());
        return task;
    }

    private boolean isFinished(AnalysisTask task) {
        return task.getStatus() != TaskStatus.PENDING.getCode()
                && task.getStatus() != TaskStatus.PROCESSING.getCode();
    }

    /**
     * 验收断言：document_section 与解析章节一一对应，risk_result 条数按场景给定
     * （成功链路 1 条并校验汇总与明细，失败链路 0 条），无重复残留
     */
    private void assertSectionsAndRisks(Long documentId, Long taskId, String scene, int expectedRiskResults) {
        List<DocumentSection> sections = documentSectionMapper.selectList(new LambdaQueryWrapper<DocumentSection>()
                .eq(DocumentSection::getDocumentId, documentId)
                .orderByAsc(DocumentSection::getSort));
        assertEquals(3, sections.size(), scene + "：document_section 应为 3 条且无重复残留");
        Map<String, Long> sectionIdByNo = sections.stream()
                .collect(Collectors.toMap(DocumentSection::getSectionNo, DocumentSection::getId, (a, b) -> a));
        assertEquals("1", sections.get(0).getSectionNo(), scene + "：章节编号不符");
        assertEquals("3", sections.get(2).getSectionNo(), scene + "：章节编号不符");
        assertNotEquals(sections.get(0).getId(), sections.get(1).getId(), scene + "：存在重复章节记录");

        List<RiskResult> results = riskResultMapper.selectList(new LambdaQueryWrapper<RiskResult>()
                .eq(RiskResult::getTaskId, taskId));
        assertEquals(expectedRiskResults, results.size(),
                scene + "：risk_result 应为 " + expectedRiskResults + " 条");
        if (results.isEmpty()) {
            return;
        }
        RiskResult riskResult = results.get(0);
        assertEquals("发现2处风险条款", riskResult.getRiskSummary());
        assertEquals(1, riskResult.getHighCount().intValue());
        assertEquals(1, riskResult.getMediumCount().intValue());
        assertEquals(0, riskResult.getLowCount().intValue());

        List<RiskDetail> details = riskDetailMapper.selectList(new LambdaQueryWrapper<RiskDetail>()
                .eq(RiskDetail::getRiskResultId, riskResult.getId())
                .orderByAsc(RiskDetail::getId));
        assertEquals(2, details.size(), scene + "：risk_detail 应为 2 条");
        assertEquals(sectionIdByNo.get("1"), details.get(0).getSectionId(), scene + "：risk_detail.section_id 与章节对不上");
        assertEquals(sectionIdByNo.get("2"), details.get(1).getSectionId(), scene + "：risk_detail.section_id 与章节对不上");
        assertEquals("HIGH", details.get(0).getRiskLevel());
        assertEquals("MEDIUM", details.get(1).getRiskLevel());
    }

    /**
     * 轮询等待任务离开 PENDING/PROCESSING（消费链路经本机线程池异步完成）
     */
    private AnalysisTask waitUntilFinished(Long taskId, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            AnalysisTask task = analysisTaskMapper.selectById(taskId);
            if (task != null && isFinished(task)) {
                return task;
            }
            Thread.sleep(500);
        }
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        assertTrue(task != null, "任务记录不存在 taskId : " + taskId);
        return task;
    }

    /**
     * pdfbox 程序化生成 3 条款小 PDF（编号 1./2./3. 供 SectionSplitter 按条款切分）
     */
    private byte[] contractPdf() {
        String[] lines = {
                "1. Party A shall not terminate this contract within thirty days without cause.",
                "2. Party A shall bear all liabilities regardless of fault.",
                "3. Any dispute shall be finally resolved by arbitration."
        };
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(18f);
                cs.newLineAtOffset(50, 750);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("测试 PDF 生成失败", e);
        }
    }

    private RiskAnalysisResult mockAnalysisResult() {
        return RiskAnalysisResult.builder()
                .riskSummary("发现2处风险条款")
                .risks(List.of(
                        RiskDetailInfo.builder()
                                .sectionNo("1")
                                .riskType("解除权限制")
                                .riskLevel(RiskLevel.HIGH)
                                .title("限制单方解除权")
                                .originalText("Party A shall not terminate this contract within thirty days")
                                .reason("排除对方主要权利")
                                .impact("守约方难以及时止损")
                                .suggestion("约定合理的解除条件")
                                .build(),
                        RiskDetailInfo.builder()
                                .sectionNo("2")
                                .riskType("责任加重")
                                .riskLevel(RiskLevel.MEDIUM)
                                .title("无过错责任")
                                .build()))
                .build();
    }

    private Long ensureTestUser() {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, TEST_OPENID));
        if (user == null) {
            user = new User();
            user.setOpenid(TEST_OPENID);
            userMapper.insert(user);
        }
        return user.getId();
    }
}
