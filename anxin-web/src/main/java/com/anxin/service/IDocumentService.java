package com.anxin.service;

import com.anxin.dto.UploadConfirmDTO;
import com.anxin.dto.UploadCredentialDTO;
import com.anxin.entity.Document;
import com.anxin.result.PageResult;
import com.anxin.vo.DocumentDetailVO;
import com.anxin.vo.DocumentListVO;
import com.anxin.vo.DocumentUploadVO;
import com.anxin.vo.UploadCredentialVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 文档业务接口。
 */
public interface IDocumentService extends IService<Document> {

    /**
     * 签发 OSS 表单直传凭证：小程序拿到后把文件直接传给 OSS，字节不经过服务端。
     * 对象名由服务端生成并写死在 policy 里，客户端改不了路径也超不了大小。
     */
    UploadCredentialVO requestUploadCredential(UploadCredentialDTO dto);

    /**
     * 直传完成后登记：校验 key 归属 → 按对象真实大小与魔数复核类型 → 落库并创建分析任务。
     * 校验不过会删掉刚传上来的对象，不给脏文件留在那里。
     */
    DocumentUploadVO confirmUpload(UploadConfirmDTO dto);


    PageResult<DocumentListVO> getListDocuments(Integer pageSize, String statusGroup, String cursor);

    DocumentDetailVO detail(Long documentId);

    void deleteDocument(Long documentId);

    DocumentUploadVO reanalyze(Long documentId);
}
