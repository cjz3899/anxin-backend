package com.anxin.controller;

import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import com.anxin.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/document")
public class DocumentController {
    @PostMapping("/upload")
    public Result<Void> upload(@RequestParam("file") MultipartFile file) {
        throw new ServiceException(ResultCode.SYSTEM_ERROR);
        //TODO: 上传文件
    }
}
