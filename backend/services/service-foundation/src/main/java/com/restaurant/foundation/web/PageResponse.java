package com.restaurant.foundation.web;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(List.copyOf(content), page, size, totalElements, totalPages);
    }
}
