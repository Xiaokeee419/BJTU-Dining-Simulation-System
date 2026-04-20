package com.bjtu.dining.common.response;

import java.util.List;

public class PageResponse<T> {
    private final List<T> list;
    private final long total;
    private final long page;
    private final long pageSize;

    private PageResponse(List<T> list, long total, long page, long pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public static <T> PageResponse<T> of(List<T> list, long total, long page, long pageSize) {
        return new PageResponse<>(list, total, page, pageSize);
    }

    public List<T> getList() {
        return list;
    }

    public long getTotal() {
        return total;
    }

    public long getPage() {
        return page;
    }

    public long getPageSize() {
        return pageSize;
    }
}
