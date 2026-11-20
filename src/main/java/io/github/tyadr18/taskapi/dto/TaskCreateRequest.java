package io.github.tyadr18.taskapi.dto;

import io.github.tyadr18.taskapi.domain.Task;
import io.github.tyadr18.taskapi.domain.TaskPriority;
import io.github.tyadr18.taskapi.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * タスク作成リクエスト。
 *
 * <p>status を省略すると TODO、priority を省略すると MEDIUM になる。
 */
public record TaskCreateRequest(
        @NotBlank(message = "タイトルは必須です")
        @Size(max = Task.TITLE_MAX_LENGTH, message = "タイトルは{max}文字以内で入力してください")
        String title,

        @Size(max = Task.DESCRIPTION_MAX_LENGTH, message = "説明は{max}文字以内で入力してください")
        String description,

        TaskStatus status,

        TaskPriority priority,

        LocalDate dueDate) {
}
