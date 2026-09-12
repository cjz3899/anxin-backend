package com.anxin.result;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 游标分页结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /**
     * 当前页数据
     */
    private List<T> records;

    /**
     * 下一页游标（本页最后一条记录的 id）；
     * null 表示没有更多数据，前端据此停止加载
     */
    private String nextCursor;

    /**
     * 该筛选条件下的总条数（不含游标条件），供 Tab/总数展示；不需要可传 null
     */
    private Long total;

    public static <T> PageResult<T> of(List<T> records, String nextCursor, Long total) {
        return new PageResult<>(records, nextCursor, total);
    }
}
