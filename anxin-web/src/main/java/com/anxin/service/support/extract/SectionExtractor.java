package com.anxin.service.support.extract;

import com.anxin.parser.model.ParsedSection;

import java.util.List;
import java.util.Set;

/**
 * 条款提取策略：把「原始文件字节」转换为统一的下游输入（条款列表）。
 * 各实现只负责"文本从哪来、怎么切"，拆节落库/LLM 分析/风险落库等下游链路对类型无感知。
 */
public interface SectionExtractor {

    /**
     * 本策略支持的文件类型（取值同 document.file_type：PDF/DOC/DOCX/IMAGE）
     */
    Set<String> supportedTypes();

    /**
     * 提取条款列表；业务性失败抛 NonRetryableTaskException（不重试），技术性失败抛其它异常（触发重试）
     */
    List<ParsedSection> extract(Long documentId, String fileType, byte[] bytes);
}
