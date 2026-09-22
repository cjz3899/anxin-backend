package com.anxin.service;

import com.anxin.dto.ChatMessageDTO;
import com.anxin.dto.CreateSessionDTO;
import com.anxin.result.PageResult;
import com.anxin.vo.ChatMessageVO;
import com.anxin.vo.ChatSessionVO;

import java.util.List;

/**
 * 文档问答服务（轻量 RAG：上下文取当前文档全部条款）
 */
public interface IChatService {

    /**
     * 为指定文档创建问答会话（文档需分析完成）
     */
    ChatSessionVO createSession(Long documentId, CreateSessionDTO dto);

    /**
     * 会话列表分页查询，page 从 1 开始
     */
    PageResult<ChatSessionVO> listSessions(Long documentId, int page, int size);

    /**
     * 提问：落库用户消息与一条 PENDING 的占位回答后立即返回，
     * 模型调用在后台完成，前端轮询 listMessages 看 status 拿答案
     */
    ChatMessageVO sendMessage(Long sessionId, ChatMessageDTO dto);

    /**
     * 查询会话的历史消息（正序），含回答状态与引用的条款
     */
    List<ChatMessageVO> listMessages(Long sessionId);

    /**
     * 关闭会话（保留历史，仅置状态为已关闭）
     */
    void closeSession(Long sessionId);
}
