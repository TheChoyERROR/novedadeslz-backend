package com.novedadeslz.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private PageableResponse pageable;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private int size;
    private int number;
    private SortResponse sort;
    private int numberOfElements;
    private boolean first;
    private boolean empty;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .pageable(PageableResponse.from(page.getPageable()))
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .size(page.getSize())
                .number(page.getNumber())
                .sort(SortResponse.from(page.getSort()))
                .numberOfElements(page.getNumberOfElements())
                .first(page.isFirst())
                .empty(page.isEmpty())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageableResponse {
        private int pageNumber;
        private int pageSize;
        private long offset;
        private boolean paged;
        private boolean unpaged;
        private SortResponse sort;

        public static PageableResponse from(Pageable pageable) {
            return PageableResponse.builder()
                    .pageNumber(pageable.getPageNumber())
                    .pageSize(pageable.getPageSize())
                    .offset(pageable.getOffset())
                    .paged(pageable.isPaged())
                    .unpaged(pageable.isUnpaged())
                    .sort(SortResponse.from(pageable.getSort()))
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortResponse {
        private boolean sorted;
        private boolean empty;
        private boolean unsorted;

        public static SortResponse from(Sort sort) {
            return SortResponse.builder()
                    .sorted(sort.isSorted())
                    .empty(sort.isEmpty())
                    .unsorted(sort.isUnsorted())
                    .build();
        }
    }
}
