package io.github.tyadr18.taskapi.dto;

import io.github.tyadr18.taskapi.domain.Task;
import io.github.tyadr18.taskapi.domain.TaskPriority;
import io.github.tyadr18.taskapi.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * タスク更新リクエスト（PUTによる全置換）。
 *
 * <p>省略した項目を「変更なし」と扱うと意図しない上書きが起きやすいため、
 * status と priority は必須にしている。description と dueDate は null を送ると空になる。
 */
public record TaskUpdateRequest(
        @NotBlank(message = "タイトルは必須です")
        @Size(max = Task.TITLE_MAX_LENGTH, message = "タイトルは{max}文字以内で入力してください")
        String title,

        @Size(max = Task.DESCRIPTION_MAX_LENGTH, message = "説明は{max}文字以内で入力してください")
        String description,

        @NotNull(message = "ステータスは必須です")
        TaskStatus status,

        @NotNull(message = "優先度は必須です")
        TaskPriority priority,

        LocalDate dueDate) {
}
