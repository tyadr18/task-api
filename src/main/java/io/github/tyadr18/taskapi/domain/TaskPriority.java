package io.github.tyadr18.taskapi.domain;

/**
 * タスクの優先度。
 *
 * <p>DBには名前ではなく {@link #level()} の数値で保存する。
 * 文字列のまま保存すると「HIGH → LOW → MEDIUM」の辞書順で並んでしまい、
 * 優先度でのソートが意味をなさなくなるため。
 */
public enum TaskPriority {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int level;

    TaskPriority(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public static TaskPriority fromLevel(int level) {
        for (TaskPriority priority : values()) {
            if (priority.level == level) {
                return priority;
            }
        }
        throw new IllegalArgumentException("Unknown priority level: " + level);
    }
}
