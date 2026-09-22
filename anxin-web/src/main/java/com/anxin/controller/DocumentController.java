package com.anxin.controller;

import com.anxin.dto.UploadConfirmDTO;
import com.anxin.dto.UploadCredentialDTO;
import com.anxin.result.PageResult;
import com.anxin.result.Result;
import com.anxin.service.IAnalysisQueryService;
import com.anxin.service.IDocumentService;
import com.anxin.vo.DocumentDetailVO;
import com.anxin.vo.DocumentListVO;
import com.anxin.vo.DocumentUploadVO;
import com.anxin.vo.RiskDetailVO;
import com.anxin.vo.UploadCredentialVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    @Resource
    private IDocumentService documentService;

    @Resource
    private IAnalysisQueryService analysisQueryService;

    /**
     * 申请 OSS 表单直传凭证：小程序用 wx.uploadFile 把文件直接传给 OSS，字节不经过服务端
     */
    @PostMapping("/upload-credential")
    public Result<UploadCredentialVO> uploadCredential(@Valid @RequestBody UploadCredentialDTO dto) {
        return Result.success(documentService.requestUploadCredential(dto));
    }

    /**
     * 直传成功后登记文件并创建分析任务
     */
    @PostMapping("/upload-confirm")
    public Result<DocumentUploadVO> uploadConfirm(@Valid @RequestBody UploadConfirmDTO dto) {
        return Result.success("文件上传成功，分析任务已创建", documentService.confirmUpload(dto));
    }

    /**
     * 查询当前用户的文件和历史分析记录
     */
    @GetMapping("/list")
    public Result<PageResult<DocumentListVO>> list(@RequestParam(defaultValue = "5") Integer pageSize,
                                                   @RequestParam(defaultValue = "ALL") String statusGroup,
                                                   @RequestParam(required = false) String cursor) {
        return Result.success(documentService.getListDocuments(pageSize, statusGroup, cursor));
    }

    @GetMapping("/{documentId}")
    public Result<DocumentDetailVO> detail(@PathVariable Long documentId) {
        return Result.success(documentService.detail(documentId));
    }

    @DeleteMapping("/{documentId}")
    public Result<Void> delete(@PathVariable Long documentId) {
        documentService.deleteDocument(documentId);
        return Result.success();
    }

    @PostMapping("/{documentId}/reanalyze")
    public Result<DocumentUploadVO> reanalyze(@PathVariable Long documentId) {
        return Result.success("重新分析任务已创建", documentService.reanalyze(documentId));
    }

    @GetMapping("/{documentId}/risks/{riskId}")
    public Result<RiskDetailVO> riskDetail(@PathVariable String documentId, @PathVariable String riskId) {
        return Result.success(analysisQueryService.getRiskDetail(documentId, riskId));
    }
}
