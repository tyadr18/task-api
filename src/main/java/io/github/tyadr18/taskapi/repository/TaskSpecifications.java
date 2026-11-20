package io.github.tyadr18.taskapi.repository;

import io.github.tyadr18.taskapi.domain.Task;
import io.github.tyadr18.taskapi.domain.TaskPriority;
import io.github.tyadr18.taskapi.domain.TaskStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

/** 一覧検索の絞り込み条件。指定されなかった条件は無視する。 */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> matches(@Nullable TaskStatus status, @Nullable TaskPriority priority) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
