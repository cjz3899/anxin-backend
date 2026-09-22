package com.anxin.support;

import com.anxin.dto.UploadConfirmDTO;
import com.anxin.dto.UploadCredentialDTO;
import com.anxin.service.IDocumentService;
import com.anxin.threadlocal.BaseContext;
import com.anxin.vo.DocumentUploadVO;
import com.anxin.vo.UploadCredentialVO;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * 集成测试夹具：按「签发凭证 → 直传 OSS → 登记」三步把一份文件变成一条待分析任务。
 * 上传只剩这一条入口，测试也走它，顺带把 PostPolicy 签名和类型复核一起覆盖掉
 */
public final class DirectUploadFixture {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private DirectUploadFixture() {
    }

    /**
     * @param userId 以哪个用户的登录态提交，直传对象名会带上它的目录
     */
    public static DocumentUploadVO upload(IDocumentService documentService, Long userId,
                                          String fileName, byte[] bytes) throws Exception {
        BaseContext.setCurrentId(userId);
        try {
            UploadCredentialDTO credentialRequest = new UploadCredentialDTO();
            credentialRequest.setFileName(fileName);
            UploadCredentialVO credential = documentService.requestUploadCredential(credentialRequest);

            int status = postToOss(credential, fileName, bytes);
            if (status != 200) {
                throw new IllegalStateException("OSS 拒绝了直传，http 状态码 : " + status);
            }

            UploadConfirmDTO confirm = new UploadConfirmDTO();
            confirm.setObjectKey(credential.getKey());
            confirm.setFileName(fileName);
            return documentService.confirmUpload(confirm);
        } finally {
            BaseContext.remove();
        }
    }

    /**
     * 按 OSS PostObject 协议拼 multipart/form-data：文件字段必须放在最后
     */
    public static int postToOss(UploadCredentialVO credential, String fileName, byte[] bytes) throws Exception {
        String boundary = "anxinTestBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(body, false, StandardCharsets.UTF_8);
        appendField(writer, boundary, "key", credential.getKey());
        appendField(writer, boundary, "policy", credential.getPolicy());
        appendField(writer, boundary, "OSSAccessKeyId", credential.getAccessKeyId());
        appendField(writer, boundary, "signature", credential.getSignature());
        appendField(writer, boundary, "success_action_status", "200");
        writer.print("--" + boundary + "\r\n");
        writer.print("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n");
        writer.flush();
        body.write(bytes);
        writer.print("\r\n--" + boundary + "--\r\n");
        writer.flush();

        HttpRequest request = HttpRequest.newBuilder(URI.create(credential.getHost()))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private static void appendField(PrintWriter writer, String boundary, String name, String value) {
        writer.print("--" + boundary + "\r\n");
        writer.print("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writer.print(value + "\r\n");
    }
}
