package com.anxin.document;

import com.anxin.dto.UploadConfirmDTO;
import com.anxin.dto.UploadCredentialDTO;
import com.anxin.entity.User;
import com.anxin.mapper.UserMapper;
import com.anxin.service.IDocumentService;
import com.anxin.support.DirectUploadFixture;
import com.anxin.threadlocal.BaseContext;
import com.anxin.vo.DocumentUploadVO;
import com.anxin.vo.UploadCredentialVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OSS 表单直传凭证测试：真实签发 → 真实 POST 到 OSS → 真实登记建任务。
 * PostPolicy 的签名和表单字段错一个字符就会被 OSS 拒绝，只有真传一次才算验证过
 */
@SpringBootTest(properties = "anxin.task.compensation-enabled=false")
@Slf4j
class OssDirectUploadTest {

    private static final String TEST_OPENID = "test-openid-direct-upload";

    private static final byte[] PDF = ("%PDF-1.4\n"
            + "1 0 obj<</Type/Catalog>>endobj\n"
            + "trailer<</Root 1 0 R>>\n"
            + "%%EOF\n").getBytes(StandardCharsets.UTF_8);

    @Resource
    private IDocumentService documentService;

    @Resource
    private UserMapper userMapper;

    @Test
    void credentialUploadsToOssAndConfirms() throws Exception {
        Long userId = ensureTestUser();
        BaseContext.setCurrentId(userId);
        Long documentId = null;
        try {
            UploadCredentialDTO credentialRequest = new UploadCredentialDTO();
            credentialRequest.setFileName("direct-upload.pdf");
            UploadCredentialVO credential = documentService.requestUploadCredential(credentialRequest);

            assertNotNull(credential.getHost());
            assertNotNull(credential.getPolicy());
            assertNotNull(credential.getSignature());
            assertTrue(credential.getKey().startsWith("documents/" + userId + "/"),
                    "对象名应带上当前用户目录 : " + credential.getKey());

            assertEquals(200, DirectUploadFixture.postToOss(credential, "direct-upload.pdf", PDF),
                    "OSS 拒绝了直传，检查 PostPolicy 签名与表单字段");

            UploadConfirmDTO confirm = new UploadConfirmDTO();
            confirm.setObjectKey(credential.getKey());
            confirm.setFileName("direct-upload.pdf");
            DocumentUploadVO vo = documentService.confirmUpload(confirm);

            assertNotNull(vo.getDocumentId(), "确认后未返回 documentId");
            documentId = Long.valueOf(vo.getDocumentId());
            assertEquals("PENDING", vo.getStatus());
            log.info("直传登记成功 documentId : {}，taskId : {}", vo.getDocumentId(), vo.getTaskId());
        } finally {
            //顺手清掉测试数据，对象存储里的文件由 deleteDocument 一起删
            if (documentId != null) {
                documentService.deleteDocument(documentId);
            }
            BaseContext.remove();
        }
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
