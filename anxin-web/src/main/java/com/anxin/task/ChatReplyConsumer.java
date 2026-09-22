package com.anxin.task;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.anxin.ai.prompt.DocChatPromptTemplate;
import com.anxin.entity.ChatMessage;
import com.anxin.entity.ChatSession;
import com.anxin.entity.DocumentSection;
import com.anxin.enums.TaskStatus;
import com.anxin.mapper.ChatMessageMapper;
import com.anxin.mapper.ChatSessionMapper;
import com.anxin.mapper.DocumentSectionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 回答生成消费者：抢占 chat_message 上那条 PENDING 的 ASSISTANT 占位消息，调模型，写回终态。
 * 运行在线程池里，拿不到请求上下文，所以一切数据都按 ID 从库里读，不做归属校验
 * （归属已在提交提问的那个请求里校验过了）
 */
@Slf4j
@Service
public class ChatReplyConsumer {

    /**
     * 拼入 prompt 的历史消息条数，控制上下文长度
     */
    private static final int MAX_HISTORY = 6;

    /**
     * 条款上下文的字符预算，超出则按条款顺序截断
     */
    private static final int CONTEXT_CHAR_BUDGET = 24000;

    private static final int ERROR_MESSAGE_MAX = 500;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private DocumentSectionMapper documentSectionMapper;

    private final ChatClient chatClient;

    public ChatReplyConsumer(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public void generate(Long messageId) {
        //条件更新抢占：抢占失败说明已被其它线程/实例领走，或该行已是终态
        int rows = chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getId, messageId)
                .eq(ChatMessage::getStatus, TaskStatus.PENDING.getCode())
                .set(ChatMessage::getStatus, TaskStatus.PROCESSING.getCode())
                .set(ChatMessage::getUpdatedTime, LocalDateTime.now()));
        if (rows == 0) {
            log.debug("回答已被生成或消息已终态，跳过 messageId : {}", messageId);
            return;
        }

        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            return;
        }
        ChatSession session = chatSessionMapper.selectById(message.getSessionId());
        if (session == null) {
            markFailed(messageId, "会话不存在或已删除");
            return;
        }

        try {
            List<DocumentSection> sections = loadSections(session.getDocumentId());
            if (sections.isEmpty()) {
                markFailed(messageId, "该文档暂无可引用的条款内容");
                return;
            }
            String question = lastQuestion(message.getSessionId(), messageId);
            String answer = chatClient.prompt()
                    .system(DocChatPromptTemplate.buildSystemPrompt())
                    .user(DocChatPromptTemplate.buildUserPrompt(
                            buildContext(sections), buildHistory(message.getSessionId()), question))
                    .call()
                    .content();

            ChatReply parsed = parseReply(answer, indexBySectionNo(sections));
            chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                    .eq(ChatMessage::getId, messageId)
                    .eq(ChatMessage::getStatus, TaskStatus.PROCESSING.getCode())
                    .set(ChatMessage::getContent, parsed.content())
                    .set(ChatMessage::getReferenceSections, JSONUtil.toJsonStr(parsed.referenceIds()))
                    .set(ChatMessage::getTokenUsage, parsed.content().length())
                    .set(ChatMessage::getStatus, TaskStatus.SUCCESS.getCode())
                    .set(ChatMessage::getUpdatedTime, LocalDateTime.now()));
            log.info("回答生成完成 messageId : {}，字符数 : {}", messageId, parsed.content().length());
        } catch (Exception e) {
            log.error("回答生成失败 messageId : {}", messageId, e);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            markFailed(messageId, truncate(reason, ERROR_MESSAGE_MAX));
        }
    }

    /**
     * 本次提问内容：占位回答之前最近的一条用户消息
     */
    private String lastQuestion(Long sessionId, Long messageId) {
        ChatMessage question = chatMessageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getRole, "USER")
                .lt(ChatMessage::getId, messageId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
        return question == null ? "" : question.getContent();
    }

    /**
     * 历史对话：取最近 MAX_HISTORY 条后恢复正序。
     * 排除空 content，否则会把自己那条占位回答拼进上下文
     */
    private String buildHistory(Long sessionId) {
        List<ChatMessage> history = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .ne(ChatMessage::getContent, "")
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
     * 解析模型回答：期望 {"content": "...", "references": ["第15条"]}；
     * 模型未按 JSON 输出时退化为纯文本回答、引用置空，不让格式问题打断对话
     */
    private ChatReply parseReply(String answer, Map<String, DocumentSection> sectionByNo) {
        String content = answer == null ? "" : answer;
        List<Long> referenceIds = new ArrayList<>();
        try {
            JSONObject json = JSONUtil.parseObj(extractJson(answer));
            content = json.getStr("content", content);
            JSONArray refs = json.getJSONArray("references");
            if (refs != null) {
                for (Object sectionNo : refs) {
                    DocumentSection section = sectionByNo.get(String.valueOf(sectionNo));
                    if (section != null && !referenceIds.contains(section.getId())) {
                        referenceIds.add(section.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("问答回答非 JSON 格式，按纯文本处理");
        }
        return new ChatReply(content, referenceIds);
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

    private void markFailed(Long messageId, String errorMessage) {
        chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getId, messageId)
                .in(ChatMessage::getStatus, TaskStatus.PENDING.getCode(), TaskStatus.PROCESSING.getCode())
                .set(ChatMessage::getStatus, TaskStatus.FAILED.getCode())
                .set(ChatMessage::getErrorMessage, errorMessage)
                .set(ChatMessage::getUpdatedTime, LocalDateTime.now()));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /**
     * 解析后的回答：正文 + 引用条款 ID
     */
    private record ChatReply(String content, List<Long> referenceIds) {
    }
}
