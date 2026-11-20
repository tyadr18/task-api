package io.github.tyadr18.taskapi.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * ページネーション付きの一覧レスポンス。
 *
 * <p>Spring Data の {@code Page} をそのままJSONにすると内部構造が露出し、
 * バージョンによって形が変わるため、必要な項目だけを詰め替えて返す。
 *
 * @param page 0始まりのページ番号
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
