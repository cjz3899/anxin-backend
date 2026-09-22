package com.anxin.chat;

import com.anxin.dto.ChatMessageDTO;
import com.anxin.dto.CreateSessionDTO;
import com.anxin.entity.ChatMessage;
import com.anxin.entity.Document;
import com.anxin.entity.DocumentSection;
import com.anxin.entity.User;
import com.anxin.enums.TaskStatus;
import com.anxin.mapper.ChatMessageMapper;
import com.anxin.mapper.DocumentMapper;
import com.anxin.mapper.DocumentSectionMapper;
import com.anxin.mapper.UserMapper;
import com.anxin.service.IChatService;
import com.anxin.threadlocal.BaseContext;
import com.anxin.util.SnowUtil;
import com.anxin.vo.ChatMessageVO;
import com.anxin.vo.ChatSessionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 文档问答异步链路集成测试：依赖本地 MySQL/Redis 与 dev 配置里的真实模型
 */
@SpringBootTest(properties = "anxin.task.compensation-enabled=false")
@Slf4j
class ChatReplyAsyncIntegrationTest {

    private static final String TEST_OPENID = "test-openid-chat";

    @Resource
    private IChatService chatService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private DocumentSectionMapper documentSectionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Test
    void questionReturnsPlaceholderThenAnswerArrivesAsync() throws InterruptedException {
        Long userId = ensureTestUser();
        Long documentId = seedAnalysedDocument(userId);

        String sessionId;
        ChatMessageVO pending;
        BaseContext.setCurrentId(userId);
        try {
            CreateSessionDTO create = new CreateSessionDTO();
            create.setTitle("异步问答冒烟");
            ChatSessionVO session = chatService.createSession(documentId, create);
            sessionId = session.getId();

            ChatMessageDTO ask = new ChatMessageDTO();
            ask.setContent("这份合同的违约责任有什么风险？请引用具体条款。");
            pending = chatService.sendMessage(Long.valueOf(sessionId), ask);
        } finally {
            BaseContext.remove();
        }

        //提问接口不该等模型，只该立刻给一条可轮询的占位回答
        assertEquals("PENDING", pending.getStatus(), "提问接口不应同步返回结果");
        assertNotNull(pending.getMessageId());
        log.info("提问已受理 messageId : {}", pending.getMessageId());

        ChatMessage answered = waitAnswered(Long.valueOf(pending.getMessageId()), Duration.ofSeconds(120));
        assertEquals(TaskStatus.SUCCESS.getCode(), answered.getStatus().intValue(),
                "回答未在超时时间内生成，errorMessage : " + answered.getErrorMessage());
        assertFalse(answered.getContent().isBlank(), "回答内容为空");
        log.info("异步回答生成完成，字符数 : {}，引用 : {}",
                answered.getContent().length(), answered.getReferenceSections());

        //会话列表里的用户消息应已是终态，且不会把空占位当成回答
        BaseContext.setCurrentId(userId);
        try {
            assertEquals(2, chatService.listMessages(Long.valueOf(sessionId)).size(),
                    "一轮问答应有用户消息 + 回答两条记录");
        } finally {
            BaseContext.remove();
        }
    }

    /**
     * 轮询占位回答直到离开 PENDING/PROCESSING（生成由 chat-reply 线程池异步完成）
     */
    private ChatMessage waitAnswered(Long messageId, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ChatMessage message = chatMessageMapper.selectById(messageId);
            if (message != null
                    && message.getStatus() != TaskStatus.PENDING.getCode()
                    && message.getStatus() != TaskStatus.PROCESSING.getCode()) {
                return message;
            }
            Thread.sleep(500);
        }
        ChatMessage message = chatMessageMapper.selectById(messageId);
        assertNotNull(message, "回答记录不存在 messageId : " + messageId);
        return message;
    }

    /**
     * 直接造一份「已分析完成 + 有条款」的文档，把测试焦点留给问答链路，不重复跑一遍解析
     */
    private Long seedAnalysedDocument(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        long documentId = SnowUtil.nextId();
        documentMapper.insert(Document.builder()
                .id(documentId)
                .userId(userId)
                .fileName("chat-smoke.docx")
                .fileType("DOCX")
                .fileSize(1024L)
                .fileUrl("https://chat-smoke.invalid/doc.docx")
                .status(TaskStatus.SUCCESS.getCode())
                .createdTime(now)
                .updatedTime(now)
                .build());
        insertSection(documentId, "第一条", "违约责任",
                "甲方逾期付款的，应按未付款项每日万分之五向乙方支付违约金。", 1);
        insertSection(documentId, "第二条", "免责条款",
                "因不可抗力导致合同无法履行的，双方互不承担违约责任。", 2);
        return documentId;
    }

    private void insertSection(Long documentId, String sectionNo, String title, String content, int sort) {
        LocalDateTime now = LocalDateTime.now();
        documentSectionMapper.insert(DocumentSection.builder()
                .id(SnowUtil.nextId())
                .documentId(documentId)
                .sectionNo(sectionNo)
                .title(title)
                .content(content)
                .sort(sort)
                .createdTime(now)
                .updatedTime(now)
                .build());
    }

    private Long ensureTestUser() {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, TEST_OPENID));
        if (user == null) {
            user = new User();
            user.setOpenid(TEST_OPENID);
            userMapper.insert(user);
        }
        return user.getId();
    }
}
