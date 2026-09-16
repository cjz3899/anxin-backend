package com.anxin.service.support;

import com.anxin.dto.CreateSessionDTO;
import com.anxin.service.IChatService;
import com.anxin.vo.ChatSessionVO;

/**
 * 文档问答服务：把当前文档已解析的条款拼入上下文，引用由 LLM 返回的 sectionNo 映射回 sectionId
 */
public class DocChatService implements IChatService {

    @Override
    public ChatSessionVO createSession(Long documentId, CreateSessionDTO createSessionDTO) {
        return null;
    }
}
