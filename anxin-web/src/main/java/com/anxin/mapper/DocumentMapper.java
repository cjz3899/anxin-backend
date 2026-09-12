package com.anxin.mapper;

import com.anxin.entity.Document;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DocumentMapper extends BaseMapper<Document> {
    /**
     * statusGroup：ALL-全部，PROCESSING-分析中(status IN 0,1)，SUCCESS-已完成，FAILED-失败；null 等同 ALL
     */
    long countByUser(@Param("userId") Long userId, @Param("statusGroup") String statusGroup);

    /**
     * 游标分页：cursor 为上一页最后一条的 id，null 表示第一页；limit 传 pageSize+1 用于探测是否还有下一页
     */
    List<Document> selectPageByUser(@Param("userId") Long userId,
                                    @Param("statusGroup") String statusGroup,
                                    @Param("cursor") Long cursor,
                                    @Param("pageSize") int pageSize);
}
