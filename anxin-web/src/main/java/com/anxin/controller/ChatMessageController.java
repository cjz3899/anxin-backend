package com.anxin.controller;

import com.anxin.dto.ChatMessageDTO;
import com.anxin.result.Result;
import com.anxin.service.IChatService;
import com.anxin.vo.ChatMessageVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 问答消息：提问、历史消息、关闭会话
 */
@RestController
@RequestMapping("/api/chat-sessions/{sessionId}")
public class ChatMessageController {

    @Resource
    private IChatService chatService;

    /**
     * 基于当前会话所属文档提问
     */
    @PostMapping("/messages")
    public Result<ChatMessageVO> sendMessage(@PathVariable @Positive Long sessionId,
                                             @Valid @RequestBody ChatMessageDTO dto) {
        return Result.success(chatService.sendMessage(sessionId, dto));
    }

    /**
     * 历史消息（正序，含回答引用的条款）
     */
    @GetMapping("/messages")
    public Result<List<ChatMessageVO>> listMessages(@PathVariable @Positive Long sessionId) {
        return Result.success(chatService.listMessages(sessionId));
    }

    /**
     * 关闭会话：保留历史记录，仅置状态为已关闭
     */
    @PatchMapping
    public Result<Void> closeSession(@PathVariable @Positive Long sessionId) {
        chatService.closeSession(sessionId);
        return Result.success();
    }
}
