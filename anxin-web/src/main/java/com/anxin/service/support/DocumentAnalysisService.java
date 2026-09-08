package com.anxin.service.support;

import com.anxin.entity.Document;
import com.anxin.entity.DocumentSection;
import com.anxin.entity.RiskDetail;
import com.anxin.entity.RiskResult;
import com.anxin.enums.TaskStatus;
import com.anxin.mapper.DocumentMapper;
import com.anxin.mapper.DocumentSectionMapper;
import com.anxin.mapper.RiskDetailMapper;
import com.anxin.mapper.RiskResultMapper;
import com.anxin.rocketmq.message.AnalysisTaskMessage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档风险分析服务
 */
@Slf4j
@Service
public class DocumentAnalysisService {

    @Resource
    private OssStorageService ossStorageService;

    @Resource
    private WxSecurityService wxSecurityService;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private RiskResultMapper riskResultMapper;

    @Resource
    private RiskDetailMapper riskDetailMapper;

    @Resource
    private DocumentSectionMapper documentSectionMapper;

    public void analysis(AnalysisTaskMessage analysisTaskMessage) {
        byte[] bytes = ossStorageService.download(analysisTaskMessage.getFileUrl());
        //微信异步审核提交
        wxSecurityService.checkMediaAsync(bytes, analysisTaskMessage.getFileType(), analysisTaskMessage.getDocumentId());
        // TODO 引入OCR解析图片
        if ("IMAGE".equals(analysisTaskMessage.getFileType())) {
            markDocumentSuccess(analysisTaskMessage.getDocumentId());
            return;
        }

        //重试幂等，清掉上次执行可能残留的半截数据
        clearExisting(analysisTaskMessage);

        // TODO 继续

    }

    private void clearExisting(AnalysisTaskMessage analysisTaskMessage) {
        List<RiskResult> oldResults = riskResultMapper.selectList(new LambdaQueryWrapper<RiskResult>()
                .eq(RiskResult::getTaskId, analysisTaskMessage.getTaskId()));
        for (RiskResult old : oldResults) {
            riskDetailMapper.delete(new LambdaQueryWrapper<RiskDetail>()
                    .eq(RiskDetail::getRiskResultId, old.getId()));
        }
        riskResultMapper.delete(new LambdaQueryWrapper<RiskResult>()
                .eq(RiskResult::getTaskId, analysisTaskMessage.getTaskId()));
        documentSectionMapper.delete(new LambdaQueryWrapper<DocumentSection>()
                .eq(DocumentSection::getDocumentId, analysisTaskMessage.getDocumentId()));
    }

    private void markDocumentSuccess(Long documentId) {
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, documentId)
                .set(Document::getStatus, TaskStatus.SUCCESS.getCode())
                .set(Document::getUpdatedTime, LocalDateTime.now()));
    }
}
