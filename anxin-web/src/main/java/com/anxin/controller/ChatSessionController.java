package com.anxin.controller;

import com.anxin.dto.CreateSessionDTO;
import com.anxin.result.PageResult;
import com.anxin.result.Result;
import com.anxin.service.IChatService;
import com.anxin.vo.ChatSessionVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

/**
 * 问答会话：创建与列表
 */
@RestController
@RequestMapping("/api/documents/{documentId}/chat-sessions")
public class ChatSessionController {

    @Resource
    private IChatService chatService;

    /**
     * 创建会话（文档需分析完成）
     */
    @PostMapping
    public Result<ChatSessionVO> createSession(@PathVariable @Positive Long documentId,
                                               @Valid @RequestBody CreateSessionDTO dto) {
        return Result.success(chatService.createSession(documentId, dto));
    }

    /**
     * 会话列表（页码分页，page 从 1 开始）
     */
    @GetMapping
    public Result<PageResult<ChatSessionVO>> listSessions(@PathVariable @Positive Long documentId,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return Result.success(chatService.listSessions(documentId, page, size));
    }
}
