package com.yiguan.his.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 分页结果，前端统一按 {list,total,page,size} 消费。
 */
public record PageResult<T>(List<T> list, long total, int page, int size) {

    public static <S, T> PageResult<T> of(Page<S> source, java.util.function.Function<S, T> mapper) {
        return new PageResult<>(
                source.getContent().stream().map(mapper).toList(),
                source.getTotalElements(),
                source.getNumber() + 1,
                source.getSize());
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }
}
