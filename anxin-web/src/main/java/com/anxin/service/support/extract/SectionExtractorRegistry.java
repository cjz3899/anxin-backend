package com.anxin.service.support.extract;

import com.anxin.exception.NonRetryableTaskException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 条款提取策略注册表：启动时按 supportedTypes 建立「文件类型 → 策略」索引，运行期按类型取用
 */
@Slf4j
@Component
public class SectionExtractorRegistry {

    private final Map<String, SectionExtractor> extractorByType = new HashMap<>();

    public SectionExtractorRegistry(List<SectionExtractor> extractors) {
        for (SectionExtractor extractor : extractors) {
            for (String type : extractor.supportedTypes()) {
                SectionExtractor previous = extractorByType.put(type, extractor);
                if (previous != null) {
                    //同类型注册了多个策略属配置错误，启动即失败好过运行期随机选中
                    throw new IllegalStateException("文件类型存在重复的条款提取策略 : " + type);
                }
            }
        }
        log.info("条款提取策略注册完成，已支持类型 : {}", extractorByType.keySet());
    }

    public SectionExtractor get(String fileType) {
        SectionExtractor extractor = fileType == null ? null : extractorByType.get(fileType);
        if (extractor == null) {
            //上传侧已有白名单校验，走到这里说明类型不合法，重试无意义
            throw new NonRetryableTaskException("不支持的文件类型 : " + fileType);
        }
        return extractor;
    }
}
