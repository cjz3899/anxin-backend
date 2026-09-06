package com.anxin.service;

import com.anxin.vo.DocumentUploadVO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.anxin.entity.Document;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档业务接口。
 */
public interface IDocumentService extends IService<Document> {

    /**
     * 上传文件（PDF/Word/图片）并创建分析任务：
     * 校验大小/真实类型/内容安全 → 存 OSS → 落库 document + analysis_task(PENDING) → 投递异步任务。
     */
    DocumentUploadVO upload(MultipartFile file);
}
