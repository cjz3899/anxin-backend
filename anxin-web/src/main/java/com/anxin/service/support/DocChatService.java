package com.anxin.service.support;

import cn.hutool.json.JSONUtil;
import com.anxin.dto.ChatMessageDTO;
import com.anxin.dto.CreateSessionDTO;
import com.anxin.entity.ChatMessage;
import com.anxin.entity.ChatSession;
import com.anxin.entity.Document;
import com.anxin.entity.DocumentSection;
import com.anxin.enums.ResultCode;
import com.anxin.enums.TaskStatus;
import com.anxin.exception.ServiceException;
import com.anxin.mapper.ChatMessageMapper;
import com.anxin.mapper.ChatSessionMapper;
import com.anxin.mapper.DocumentMapper;
import com.anxin.mapper.DocumentSectionMapper;
import com.anxin.result.PageResult;
import com.anxin.service.IChatService;
import com.anxin.task.ChatReplyDispatcher;
import com.anxin.threadlocal.BaseContext;
import com.anxin.util.SnowUtil;
import com.anxin.vo.ChatMessageVO;
import com.anxin.vo.ChatReferenceVO;
import com.anxin.vo.ChatSessionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 文档问答服务（轻量 RAG）：
 * 提问只负责落库与派发，模型调用与回写由 ChatReplyConsumer 在后台完成，前端轮询消息列表拿结果。
 * 这样提问接口不再被几十秒的模型响应占住，也就绕开了云托管网关的超时上限
 */
@Slf4j
@Service
public class DocChatService implements IChatService {

    /**
     * chat_session.status：1-正常，0-已关闭
     */
    private static final int SESSION_OPEN = 1;
    private static final int SESSION_CLOSED = 0;

    /**
     * 会话列表每页条数上限，防止前端传入过大的 size
     */
    private static final int MAX_PAGE_SIZE = 50;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private DocumentSectionMapper documentSectionMapper;

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private ChatReplyDispatcher chatReplyDispatcher;

    @Override
    public ChatSessionVO createSession(Long documentId, CreateSessionDTO dto) {
        getOwnedSuccessDocument(documentId);
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = ChatSession.builder()
                .userId(BaseContext.getCurrentId())
                .documentId(documentId)
                .title(dto.getTitle())
                .status(SESSION_OPEN)
                .createdTime(now)
                .updatedTime(now)
                .build();
        chatSessionMapper.insert(session);
        return toSessionVO(session);
    }

    @Override
    public PageResult<ChatSessionVO> listSessions(Long documentId, int page, int size) {
        getOwnedSuccessDocument(documentId);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (currentPage - 1) * pageSize;

        Long total = chatSessionMapper.selectCount(sessionQuery(documentId));
        List<ChatSessionVO> records = total == null || total == 0
                ? List.of()
                : chatSessionMapper.selectList(sessionQuery(documentId)
                        .orderByDesc(ChatSession::getId)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream().map(this::toSessionVO).toList();
        // 页码分页下无需游标，nextCursor 恒为 null，前端按自己请求的 page 翻页
        return PageResult.of(records, null, total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO sendMessage(Long sessionId, ChatMessageDTO dto) {
        ChatSession session = getOwnedSession(sessionId);
        if (session.getStatus() == null || session.getStatus() != SESSION_OPEN) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), "会话已关闭，无法继续提问");
        }
        //条款都取不到就别占坑，否则用户只会看到一条永远转圈的空回答
        if (loadSections(session.getDocumentId()).isEmpty()) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), "该文档暂无可引用的条款内容");
        }

        LocalDateTime now = LocalDateTime.now();
        //消息 ID 由应用侧生成：占位回答的 ID 要立刻返回给前端轮询，不能等自增主键
        long assistantMessageId = SnowUtil.nextId();
        chatMessageMapper.insert(ChatMessage.builder()
                .id(SnowUtil.nextId())
                .sessionId(sessionId)
                .role("USER")
                .content(dto.getContent())
                .status(TaskStatus.SUCCESS.getCode())
                .createdTime(now)
                .updatedTime(now)
                .build());
        chatMessageMapper.insert(ChatMessage.builder()
                .id(assistantMessageId)
                .sessionId(sessionId)
                .role("ASSISTANT")
                .content("")
                .status(TaskStatus.PENDING.getCode())
                .createdTime(now)
                .updatedTime(now)
                .build());

        //事务提交后才派发，否则后台线程读不到这条占位消息
        chatReplyDispatcher.dispatch(assistantMessageId);

        return ChatMessageVO.builder()
                .messageId(String.valueOf(assistantMessageId))
                .role("ASSISTANT")
                .content("")
                .status(TaskStatus.PENDING.name())
                .references(List.of())
                .createdTime(now)
                .build();
    }

    @Override
    public List<ChatMessageVO> listMessages(Long sessionId) {
        ChatSession session = getOwnedSession(sessionId);
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId));
        Map<Long, DocumentSection> sectionById = new HashMap<>();
        for (DocumentSection section : loadSections(session.getDocumentId())) {
            sectionById.put(section.getId(), section);
        }
        return messages.stream().map(message -> ChatMessageVO.builder()
                .messageId(String.valueOf(message.getId()))
                .role(message.getRole())
                .content(message.getContent())
                .status(TaskStatus.fromCode(message.getStatus()).name())
                .errorMessage(message.getErrorMessage())
                .references(parseReferences(message.getReferenceSections(), sectionById))
                .createdTime(message.getCreatedTime())
                .build()).toList();
    }

    @Override
    public void closeSession(Long sessionId) {
        getOwnedSession(sessionId);
        chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .set(ChatSession::getStatus, SESSION_CLOSED)
                .set(ChatSession::getUpdatedTime, LocalDateTime.now()));
    }

    private List<ChatReferenceVO> parseReferences(String referenceSections, Map<Long, DocumentSection> sectionById) {
        List<ChatReferenceVO> references = new ArrayList<>();
        if (referenceSections == null || referenceSections.isBlank()) {
            return references;
        }
        try {
            for (Object id : JSONUtil.parseArray(referenceSections)) {
                DocumentSection section = sectionById.get(Long.valueOf(String.valueOf(id)));
                if (section != null) {
                    references.add(toReference(section));
                }
            }
        } catch (Exception e) {
            log.warn("reference_sections 解析失败 : {}", referenceSections, e);
        }
        return references;
    }

    private List<DocumentSection> loadSections(Long documentId) {
        return documentSectionMapper.selectList(new LambdaQueryWrapper<DocumentSection>()
                .eq(DocumentSection::getDocumentId, documentId)
                .orderByAsc(DocumentSection::getSort));
    }

    private LambdaQueryWrapper<ChatSession> sessionQuery(Long documentId) {
        return new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getDocumentId, documentId)
                .eq(ChatSession::getUserId, BaseContext.getCurrentId());
    }

    private ChatSessionVO toSessionVO(ChatSession session) {
        return ChatSessionVO.builder()
                .id(String.valueOf(session.getId()))
                .documentId(String.valueOf(session.getDocumentId()))
                .title(session.getTitle())
                .status(session.getStatus() != null && session.getStatus() == SESSION_OPEN ? "OPEN" : "CLOSED")
                .createdTime(session.getCreatedTime())
                .build();
    }

    private ChatReferenceVO toReference(DocumentSection section) {
        return ChatReferenceVO.builder()
                .sectionId(String.valueOf(section.getId()))
                .sectionNo(section.getSectionNo())
                .title(section.getTitle())
                .content(section.getContent())
                .build();
    }

    /**
     * 归属校验：会话不存在或非当前用户所有，统一按不存在处理，避免暴露他人资源
     */
    private ChatSession getOwnedSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(BaseContext.getCurrentId())) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), "会话不存在");
        }
        return session;
    }

    private void getOwnedSuccessDocument(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null || !document.getUserId().equals(BaseContext.getCurrentId())) {
            throw new ServiceException(ResultCode.DOCUMENT_NOT_EXIST);
        }
        if (document.getStatus() == null || document.getStatus() != 2) {
            throw new ServiceException(ResultCode.ANALYSIS_NOT_COMPLETED);
        }
    }
}
