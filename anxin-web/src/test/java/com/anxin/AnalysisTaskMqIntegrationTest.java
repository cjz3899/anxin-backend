package com.anxin;

import com.anxin.entity.AnalysisTask;
import com.anxin.entity.User;
import com.anxin.enums.TaskStatus;
import com.anxin.mapper.AnalysisTaskMapper;
import com.anxin.mapper.UserMapper;
import com.anxin.service.IDocumentService;
import com.anxin.threadlocal.BaseContext;
import com.anxin.vo.DocumentUploadVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 上传 → RocketMQ 投递 → 消费状态机 集成测试（依赖本地 MySQL/Redis/RocketMQ 与 dev 配置）
 */
@SpringBootTest
@Slf4j
class AnalysisTaskMqIntegrationTest {

    private static final String TEST_OPENID = "test-openid-001";

    @Resource
    private IDocumentService documentService;

    @Resource
    private AnalysisTaskMapper analysisTaskMapper;

    @Resource
    private UserMapper userMapper;

    @Test
    void uploadDispatchAndConsume() throws InterruptedException {
        Long userId = ensureTestUser();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", minimalPdf());

        BaseContext.setCurrentId(userId);
        DocumentUploadVO vo;
        try {
            vo = documentService.upload(file);
        } finally {
            BaseContext.remove();
        }

        assertNotNull(vo.getDocumentId(), "documentId 未回填");
        assertNotNull(vo.getTaskId(), "taskId 未回填");
        assertEquals("PENDING", vo.getStatus());
        log.info("上传成功 documentId : {}, taskId : {}", vo.getDocumentId(), vo.getTaskId());
        Long taskId = Long.valueOf(vo.getTaskId());
        AnalysisTask task = waitUntilFinished(taskId, Duration.ofSeconds(60));
        log.info("任务终态 status : {}, retryCount : {}, errorMessage : {}", task.getStatus(), task.getRetryCount(), task.getErrorMessage());
        assertEquals(TaskStatus.SUCCESS.getCode(), task.getStatus().intValue(),
                "任务未在超时时间内到达 SUCCESS，请确认 RocketMQ broker 正在运行且消费日志无异常");
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
        assertTrue(task != null, "任务记录不存在 taskId : " + taskId);
        return task;
    }

    /**
     * 最小合法 PDF（仅 %PDF 魔数 + 空内容）：上传闸门只做 Tika 魔数检测，mock 阶段消费端不解析内容
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
