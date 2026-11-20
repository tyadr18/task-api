package io.github.tyadr18.taskapi.service;

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
import io.github.tyadr18.taskapi.repository.TaskSpecifications;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {

    /**
     * sort パラメータに指定できる項目。
     *
     * <p>status は文字列で保存しているため辞書順（DOING → DONE → TODO）になり、
     * 並べ替えの意味が薄いので対象外にしている（status は絞り込みで使う想定）。
     */
    static final List<String> SORTABLE_PROPERTIES =
            List.of("id", "title", "priority", "dueDate", "createdAt", "updatedAt");

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public TaskResponse create(TaskCreateRequest request) {
        Task task = new Task(
                request.title().strip(),
                request.description(),
                Objects.requireNonNullElse(request.status(), TaskStatus.TODO),
                Objects.requireNonNullElse(request.priority(), TaskPriority.MEDIUM),
                request.dueDate());
        return TaskResponse.from(taskRepository.save(task));
    }

    public TaskResponse findById(long id) {
        return TaskResponse.from(getTask(id));
    }

    public PageResponse<TaskResponse> search(
            @Nullable TaskStatus status, @Nullable TaskPriority priority, Pageable pageable) {
        validateSort(pageable.getSort());
        return PageResponse.from(
                taskRepository.findAll(TaskSpecifications.matches(status, priority), pageable)
                        .map(TaskResponse::from));
    }

    @Transactional
    public TaskResponse update(long id, TaskUpdateRequest request) {
        Task task = getTask(id);
        task.update(
                request.title().strip(),
                request.description(),
                request.status(),
                request.priority(),
                request.dueDate());
        // flush しておかないと @UpdateTimestamp がコミット時まで反映されず、
        // レスポンスの updatedAt が更新前の値のままになる。
        return TaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public void delete(long id) {
        taskRepository.delete(getTask(id));
    }

    private Task getTask(long id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    /** 未知の項目でソートすると500になるため、許可した項目以外は400で弾く。 */
    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), SORTABLE_PROPERTIES);
            }
        }
    }
}
