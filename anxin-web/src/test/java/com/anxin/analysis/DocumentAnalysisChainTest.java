package com.anxin.analysis;

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
import com.anxin.threadlocal.BaseContext;
import com.anxin.vo.DocumentUploadVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * DocumentAnalysisService 全链路集成测试（stub 掉 LLM，无需 api-key）：
 * 依赖本地 MySQL/Redis/RocketMQ，覆盖 解析→拆节落库→AI结果落库→状态流转 与 空解析终止 两条路径
 */
@SpringBootTest
class DocumentAnalysisChainTest {

    private static final String TEST_OPENID = "test-openid-001";

    @Resource
    private IDocumentService documentService;

    @Resource
    private AnalysisTaskMapper analysisTaskMapper;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private DocumentSectionMapper documentSectionMapper;

    @Resource
    private RiskResultMapper riskResultMapper;

    @Resource
    private RiskDetailMapper riskDetailMapper;

    @Resource
    private UserMapper userMapper;

    @MockitoBean
    private RiskAnalyzer riskAnalyzer;

    @Test
    void docxFullChainPersistsSectionsAndRisks() throws InterruptedException {
        when(riskAnalyzer.analyze(anyList())).thenReturn(stubResult());

        Long userId = ensureTestUser();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-contract.docx", "application/octet-stream", minimalDocx());

        BaseContext.setCurrentId(userId);
        DocumentUploadVO vo;
        try {
            vo = documentService.upload(file);
        } finally {
            BaseContext.remove();
        }
        Long taskId = Long.valueOf(vo.getTaskId());
        Long documentId = Long.valueOf(vo.getDocumentId());

        AnalysisTask task = waitUntilFinished(taskId, Duration.ofSeconds(240));
        System.out.println("docx 全链路任务终态 status : " + task.getStatus()
                + ", errorMessage : " + task.getErrorMessage());
        assertEquals(TaskStatus.SUCCESS.getCode(), task.getStatus().intValue(),
                "任务未在超时时间内到达 SUCCESS，请确认 RocketMQ broker 正在运行且消费日志无异常");

        Document document = documentMapper.selectById(documentId);
        assertEquals(TaskStatus.SUCCESS.getCode(), document.getStatus().intValue(), "document.status 未同步置 SUCCESS");

        List<DocumentSection> sections = documentSectionMapper.selectList(
                new LambdaQueryWrapper<DocumentSection>().eq(DocumentSection::getDocumentId, documentId));
        assertEquals(2, sections.size(), "document_section 拆节数量不符");

        RiskResult riskResult = riskResultMapper.selectOne(
                new LambdaQueryWrapper<RiskResult>().eq(RiskResult::getTaskId, taskId));
        assertNotNull(riskResult, "risk_result 未落库");
        assertEquals(1, riskResult.getHighCount().intValue());
        assertEquals(0, riskResult.getMediumCount().intValue());
        assertEquals(0, riskResult.getLowCount().intValue());
        assertEquals("测试整体摘要", riskResult.getRiskSummary());

        Long firstSectionId = sections.stream()
                .filter(s -> "第一条".equals(s.getSectionNo()))
                .findFirst().orElseThrow().getId();
        List<RiskDetail> details = riskDetailMapper.selectList(
                new LambdaQueryWrapper<RiskDetail>().eq(RiskDetail::getRiskResultId, riskResult.getId()));
        assertEquals(1, details.size(), "risk_detail 数量不符");
        assertEquals(firstSectionId, details.get(0).getSectionId(), "sectionNo→section_id 映射错误");
        assertEquals("HIGH", details.get(0).getRiskLevel());
    }

    @Test
    void emptyParseFailsImmediatelyWithoutRetry() throws InterruptedException {
        Long userId = ensureTestUser();
        MockMultipartFile file = new MockMultipartFile(
                "file", "scan.pdf", "application/pdf", minimalPdf());

        BaseContext.setCurrentId(userId);
        DocumentUploadVO vo;
        try {
            vo = documentService.upload(file);
        } finally {
            BaseContext.remove();
        }
        Long taskId = Long.valueOf(vo.getTaskId());
        Long documentId = Long.valueOf(vo.getDocumentId());

        AnalysisTask task = waitUntilFinished(taskId, Duration.ofSeconds(240));
        System.out.println("空解析任务终态 status : " + task.getStatus()
                + ", retryCount : " + task.getRetryCount()
                + ", errorMessage : " + task.getErrorMessage());
        assertEquals(TaskStatus.FAILED.getCode(), task.getStatus().intValue(), "空解析应直接 FAILED");
        assertEquals(0, task.getRetryCount().intValue(), "非重试失败不应累加 retry_count");
        assertNotNull(task.getErrorMessage(), "error_message 应有提示");
        assertTrue(task.getErrorMessage().contains("重新上传"), "error_message 应提示用户重新上传 : " + task.getErrorMessage());
        assertEquals(TaskStatus.FAILED.getCode(), documentMapper.selectById(documentId).getStatus().intValue(),
                "document.status 未同步置 FAILED");
        assertEquals(0L, documentSectionMapper.selectCount(
                        new LambdaQueryWrapper<DocumentSection>().eq(DocumentSection::getDocumentId, documentId)).longValue(),
                "空解析不应残留 document_section 数据");
    }

    private RiskAnalysisResult stubResult() {
        RiskDetailInfo risk = RiskDetailInfo.builder()
                .sectionNo("第一条")
                .riskType("违约责任")
                .riskLevel(RiskLevel.HIGH)
                .title("测试高风险条款")
                .originalText("甲方应按约定时间支付全部款项。")
                .reason("测试原因")
                .impact("测试影响")
                .suggestion("测试建议")
                .build();
        return RiskAnalysisResult.builder()
                .riskSummary("测试整体摘要")
                .risks(List.of(risk))
                .build();
    }

    /**
     * 轮询等待任务离开 PENDING/PROCESSING（消费链路经真实 RocketMQ 异步完成）
     */
    private AnalysisTask waitUntilFinished(Long taskId, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            AnalysisTask task = analysisTaskMapper.selectById(taskId);
            if (task != null
                    && task.getStatus() != TaskStatus.PENDING.getCode()
                    && task.getStatus() != TaskStatus.PROCESSING.getCode()) {
                return task;
            }
            Thread.sleep(500);
        }
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        assertNotNull(task, "任务记录不存在 taskId : " + taskId);
        return task;
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

    /**
     * 最小合法 PDF（仅 %PDF 魔数 + 无正文）：解析不出条款，触发空解析终止分支
     */
    private byte[] minimalPdf() {
        String pdf = "%PDF-1.4\n"
                + "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\n"
                + "trailer<</Size 4/Root 1 0 R>>\n"
                + "%%EOF\n";
        return pdf.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 内存构造最小 docx：两个条款 + 一段无编号段落，验证 Tika 抽取与条款切分
     */
    private byte[] minimalDocx() {
        String contentTypes = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """;
        String rels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """;
        String document = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>第一条 甲方应按约定时间支付全部款项。</w:t></w:r></w:p>
                    <w:p><w:r><w:t>第二条 乙方有权单方面解除本协议且不承担赔偿责任。</w:t></w:r></w:p>
                    <w:p><w:r><w:t>本协议未尽事宜由双方友好协商解决。</w:t></w:r></w:p>
                  </w:body>
                </w:document>
                """;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(contentTypes.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write(rels.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(document.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("构造测试 docx 失败", e);
        }
    }
}
