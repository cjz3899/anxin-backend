package com.anxin.service;

import com.anxin.dto.CreateSessionDTO;
import com.anxin.vo.ChatSessionVO;
import jakarta.validation.Valid;

public interface IChatService {
    ChatSessionVO createSession(Long documentId, @Valid CreateSessionDTO createSessionDTO);
}
