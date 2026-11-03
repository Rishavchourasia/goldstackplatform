package com.goldstack.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Paging {

    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static Pageable newestFirst(int page, int size, String timeField) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE),
                Sort.by(Sort.Direction.DESC, timeField));
    }
}
