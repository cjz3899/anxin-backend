package com.anxin.controller;

import com.anxin.result.PageResult;
import com.anxin.result.Result;
import com.anxin.service.IDocumentService;
import com.anxin.vo.DocumentListVO;
import com.anxin.vo.DocumentUploadVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    @Resource
    private IDocumentService documentService;

    /**
     * 上传 PDF/Word/图片并创建分析任务，multipart 字段名 file，需登录态 token
     */
    @PostMapping("/upload")
    public Result<DocumentUploadVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.success("文件上传成功，分析任务已创建", documentService.upload(file));
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
}
