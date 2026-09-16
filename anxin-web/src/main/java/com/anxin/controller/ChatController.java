package com.anxin.controller;

import com.anxin.dto.CreateSessionDTO;
import com.anxin.result.Result;
import com.anxin.service.IChatService;
import com.anxin.vo.ChatSessionVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 文档问答：会话与消息接口
 */
@RestController
@RequestMapping("/api")
public class ChatController {
    @Resource
    private IChatService chatService;

    @PostMapping("/document/{documentId}/chat/session")
    public Result<ChatSessionVO> createSession(@PathVariable Long documentId, @Valid @RequestBody CreateSessionDTO createSessionDTO) {
        return Result.success(chatService.createSession(documentId, createSessionDTO));
    }
}
