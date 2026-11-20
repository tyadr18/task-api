package io.github.tyadr18.taskapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.tyadr18.taskapi.domain.Task;
import io.github.tyadr18.taskapi.domain.TaskPriority;
import io.github.tyadr18.taskapi.domain.TaskStatus;
import io.github.tyadr18.taskapi.dto.PageResponse;
import io.github.tyadr18.taskapi.dto.TaskCreateRequest;
import io.github.tyadr18.taskapi.dto.TaskResponse;
import io.github.tyadr18.taskapi.dto.TaskUpdateRequest;
import io.github.tyadr18.taskapi.exception.InvalidSortException;
import io.github.tyadr18.taskapi.exception.TaskNotFoundException;
import io.github.tyadr18.taskapi.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("status と priority を省略すると TODO / MEDIUM になり、タイトルの前後の空白は除去される")
        void appliesDefaults() {
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

            TaskResponse response = taskService.create(new TaskCreateRequest("  資料を作る  ", null, null, null, null));

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.title()).isEqualTo("資料を作る");
            assertThat(response.status()).isEqualTo(TaskStatus.TODO);
            assertThat(response.priority()).isEqualTo(TaskPriority.MEDIUM);
        }

        @Test
        @DisplayName("指定した値はそのまま保存される")
        void keepsGivenValues() {
            ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
            when(taskRepository.save(captor.capture())).thenAnswer(invocation -> withId(invocation.getArgument(0), 2L));
            LocalDate due = LocalDate.of(2026, 12, 31);

            taskService.create(new TaskCreateRequest("請求書を送る", "月末締め分", TaskStatus.DOING, TaskPriority.HIGH, due));

            Task saved = captor.getValue();
            assertThat(saved.getTitle()).isEqualTo("請求書を送る");
            assertThat(saved.getDescription()).isEqualTo("月末締め分");
            assertThat(saved.getStatus()).isEqualTo(TaskStatus.DOING);
            assertThat(saved.getPriority()).isEqualTo(TaskPriority.HIGH);
            assertThat(saved.getDueDate()).isEqualTo(due);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("存在するIDならタスクを返す")
        void returnsTask() {
            when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask(1L)));

            TaskResponse response = taskService.findById(1L);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.title()).isEqualTo("サンプル");
        }

        @Test
        @DisplayName("存在しないIDなら TaskNotFoundException")
        void throwsWhenMissing() {
            when(taskRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.findById(99L))
                    .isInstanceOf(TaskNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("リポジトリの結果をページ情報ごと詰め替えて返す")
        void mapsPage() {
            Pageable pageable = PageRequest.of(0, 2, Sort.by("id"));
            List<Task> tasks = List.of(sampleTask(1L), sampleTask(2L));
            when(taskRepository.findAll(ArgumentMatchers.<Specification<Task>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(tasks, pageable, 5));

            PageResponse<TaskResponse> result = taskService.search(TaskStatus.TODO, null, pageable);

            assertThat(result.content()).extracting(TaskResponse::id).containsExactly(1L, 2L);
            assertThat(result.page()).isZero();
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(5);
            assertThat(result.totalPages()).isEqualTo(3);
            assertThat(result.first()).isTrue();
            assertThat(result.last()).isFalse();
        }

        @Test
        @DisplayName("許可していない項目でのソートは InvalidSortException で、DBには問い合わせない")
        void rejectsUnknownSortProperty() {
            Pageable pageable = PageRequest.of(0, 20, Sort.by("unknownField"));

            assertThatThrownBy(() -> taskService.search(null, null, pageable))
                    .isInstanceOf(InvalidSortException.class)
                    .hasMessageContaining("unknownField");
            verifyNoInteractions(taskRepository);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("全項目を置き換え、flush して返す")
        void replacesAllFields() {
            Task task = sampleTask(1L);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(taskRepository.saveAndFlush(task)).thenReturn(task);
            LocalDate due = LocalDate.of(2027, 1, 15);

            TaskResponse response = taskService.update(1L,
                    new TaskUpdateRequest(" 更新後 ", null, TaskStatus.DONE, TaskPriority.LOW, due));

            assertThat(response.title()).isEqualTo("更新後");
            assertThat(response.description()).isNull();
            assertThat(response.status()).isEqualTo(TaskStatus.DONE);
            assertThat(response.priority()).isEqualTo(TaskPriority.LOW);
            assertThat(response.dueDate()).isEqualTo(due);
            verify(taskRepository).saveAndFlush(task);
        }

        @Test
        @DisplayName("存在しないIDなら TaskNotFoundException で、保存しない")
        void throwsWhenMissing() {
            when(taskRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.update(99L,
                    new TaskUpdateRequest("x", null, TaskStatus.TODO, TaskPriority.LOW, null)))
                    .isInstanceOf(TaskNotFoundException.class);
            verify(taskRepository, never()).saveAndFlush(any(Task.class));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("存在するタスクを削除する")
        void deletesTask() {
            Task task = sampleTask(1L);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

            taskService.delete(1L);

            verify(taskRepository).delete(task);
        }

        @Test
        @DisplayName("存在しないIDなら TaskNotFoundException で、削除しない")
        void throwsWhenMissing() {
            when(taskRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.delete(99L)).isInstanceOf(TaskNotFoundException.class);
            // JpaSpecificationExecutor にも delete(Specification) があるため、型を明示して曖昧さを避ける
            verify(taskRepository, never()).delete(any(Task.class));
        }
    }

    private static Task sampleTask(long id) {
        return withId(new Task("サンプル", "説明", TaskStatus.TODO, TaskPriority.MEDIUM, null), id);
    }

    private static Task withId(Task task, long id) {
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }
}
