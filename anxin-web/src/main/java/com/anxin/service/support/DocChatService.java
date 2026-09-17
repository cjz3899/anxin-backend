package com.anxin.service.support;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.anxin.ai.prompt.DocChatPromptTemplate;
import com.anxin.dto.ChatMessageDTO;
import com.anxin.dto.CreateSessionDTO;
import com.anxin.entity.ChatMessage;
import com.anxin.entity.ChatSession;
import com.anxin.entity.Document;
import com.anxin.entity.DocumentSection;
import com.anxin.enums.ResultCode;
import com.anxin.exception.ServiceException;
import com.anxin.mapper.ChatMessageMapper;
import com.anxin.mapper.ChatSessionMapper;
import com.anxin.mapper.DocumentMapper;
import com.anxin.mapper.DocumentSectionMapper;
import com.anxin.result.PageResult;
import com.anxin.service.IChatService;
import com.anxin.threadlocal.BaseContext;
import com.anxin.vo.ChatMessageVO;
import com.anxin.vo.ChatReferenceVO;
import com.anxin.vo.ChatSessionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 文档问答服务（轻量 RAG）：
 * 上下文取当前文档已解析的全部条款（超预算按顺序截断），引用由模型返回的 sectionNo 映射回 sectionId
 *
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
     * 拼入 prompt 的历史消息条数，控制上下文长度
     */
    private static final int MAX_HISTORY = 6;

    /**
     * 条款上下文的字符预算，超出则按条款顺序截断
     */
    private static final int CONTEXT_CHAR_BUDGET = 24000;

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

    private final ChatClient chatClient;

    public DocChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

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
    public ChatMessageVO sendMessage(Long sessionId, ChatMessageDTO dto) {
        ChatSession session = getOwnedSession(sessionId);
        if (session.getStatus() == null || session.getStatus() != SESSION_OPEN) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), "会话已关闭，无法继续提问");
        }

        // 先落用户消息：即便随后调用模型失败，提问记录也不会丢
        LocalDateTime now = LocalDateTime.now();
        chatMessageMapper.insert(ChatMessage.builder()
                .sessionId(sessionId)
                .role("USER")
                .content(dto.getContent())
                .createdTime(now)
                .updatedTime(now)
                .build());

        List<DocumentSection> sections = loadSections(session.getDocumentId());
        if (sections.isEmpty()) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), "该文档暂无可引用的条款内容");
        }

        String answer = chatClient.prompt()
                .system(DocChatPromptTemplate.buildSystemPrompt())
                .user(DocChatPromptTemplate.buildUserPrompt(
                        buildContext(sections), buildHistory(sessionId), dto.getContent()))
                .call()
                .content();

        ChatAnswer parsed = parseAnswer(answer, indexBySectionNo(sections));
        ChatMessage assistantMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .role("ASSISTANT")
                .content(parsed.content())
                .referenceSections(JSONUtil.toJsonStr(parsed.referenceIds()))
                .tokenUsage(parsed.content().length())
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();
        chatMessageMapper.insert(assistantMessage);

        return ChatMessageVO.builder()
                .messageId(String.valueOf(assistantMessage.getId()))
                .role("ASSISTANT")
                .content(parsed.content())
                .references(parsed.references())
                .createdTime(assistantMessage.getCreatedTime())
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

    /**
     * 文档条款上下文：按 sort 顺序拼接，超出字符预算即截断（轻量 RAG 的取舍：不做检索）
     */
    private String buildContext(List<DocumentSection> sections) {
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (DocumentSection section : sections) {
            String block = formatBlock(section);
            if (used > 0 && used + block.length() > CONTEXT_CHAR_BUDGET) {
                log.warn("文档条款超出上下文预算，已截断，纳入字符数 : {}", used);
                break;
            }
            sb.append(block);
            used += block.length();
        }
        return sb.toString();
    }

    private String formatBlock(DocumentSection section) {
        return "【" + (section.getSectionNo() == null ? "" : section.getSectionNo()) + "】"
                + (section.getTitle() == null ? "" : section.getTitle()) + "\n"
                + section.getContent() + "\n\n";
    }

    /**
     * 历史对话：取最近 MAX_HISTORY 条后恢复正序
     */
    private String buildHistory(Long sessionId) {
        List<ChatMessage> history = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + MAX_HISTORY));
        Collections.reverse(history);
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : history) {
            sb.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 解析模型回答：期望 {"content": "...", "references": ["第15条"]}；
     * 模型未按 JSON 输出时退化为纯文本回答、引用置空，不让格式问题打断对话
     */
    private ChatAnswer parseAnswer(String answer, Map<String, DocumentSection> sectionByNo) {
        String content = answer == null ? "" : answer;
        List<Long> referenceIds = new ArrayList<>();
        List<ChatReferenceVO> references = new ArrayList<>();
        try {
            JSONObject json = JSONUtil.parseObj(extractJson(answer));
            content = json.getStr("content", content);
            JSONArray refs = json.getJSONArray("references");
            if (refs != null) {
                for (Object sectionNo : refs) {
                    DocumentSection section = sectionByNo.get(String.valueOf(sectionNo));
                    if (section != null && !referenceIds.contains(section.getId())) {
                        referenceIds.add(section.getId());
                        references.add(toReference(section));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("问答回答非 JSON 格式，按纯文本处理");
        }
        return new ChatAnswer(content, referenceIds, references);
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

    private Map<String, DocumentSection> indexBySectionNo(List<DocumentSection> sections) {
        Map<String, DocumentSection> sectionByNo = new HashMap<>();
        for (DocumentSection section : sections) {
            if (section.getSectionNo() != null) {
                sectionByNo.putIfAbsent(section.getSectionNo(), section);
            }
        }
        return sectionByNo;
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
     * 剥离 ```json 围栏并截取首尾大括号之间内容
     */
    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }
        String cleaned = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        return (start >= 0 && end > start) ? cleaned.substring(start, end + 1) : cleaned;
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

    /**
     * 解析后的回答：正文 + 引用条款
     */
    private record ChatAnswer(String content, List<Long> referenceIds, List<ChatReferenceVO> references) {
    }
}
