package com.anxin.controller;

import com.anxin.dto.CreateSessionDTO;
import com.anxin.result.Result;
import com.anxin.service.IChatService;
import com.anxin.vo.ChatSessionVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档问答：会话与消息接口
 */
@RestController("/api")
public class ChatController {
    @Resource
    private IChatService chatService;

    @PostMapping("/document/{documentId}/chat/session")
    public Result<ChatSessionVO> createSession(@PathVariable Long documentId, @Valid @RequestBody CreateSessionDTO createSessionDTO) {
        return Result.success(chatService.createSession(documentId, createSessionDTO));
    }
}
