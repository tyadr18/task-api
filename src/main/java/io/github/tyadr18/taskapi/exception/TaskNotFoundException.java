package io.github.tyadr18.taskapi.exception;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(long id) {
        super("ID " + id + " のタスクが見つかりません");
    }
}
