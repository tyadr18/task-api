package io.github.tyadr18.taskapi.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link TaskPriority} とDBの数値カラムを相互変換する。 */
@Converter
public class TaskPriorityConverter implements AttributeConverter<TaskPriority, Integer> {

    @Override
    public Integer convertToDatabaseColumn(TaskPriority priority) {
        return priority == null ? null : priority.level();
    }

    @Override
    public TaskPriority convertToEntityAttribute(Integer level) {
        return level == null ? null : TaskPriority.fromLevel(level);
    }
}
