package com.sunnyblog.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public record PageResult<T>(List<T> records, long total, long page, long size, long pages) {

    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), 0, page, size, 0);
    }
}
