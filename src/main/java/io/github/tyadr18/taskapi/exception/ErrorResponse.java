package io.github.tyadr18.taskapi.exception;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * エラー時に返すJSON。どのエラーでも同じ形にそろえる。
 *
 * @param errors 入力チェックで引っかかった項目の一覧（それ以外のエラーでは空配列）
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> errors) {

    public record FieldViolation(String field, String message) {
    }

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return of(status, message, path, List.of());
    }

    public static ErrorResponse of(HttpStatus status, String message, String path, List<FieldViolation> errors) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, errors);
    }
}
