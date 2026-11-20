package io.github.tyadr18.taskapi.exception;

import java.util.Collection;

/** 並べ替えに使えない項目が指定されたときの例外。 */
public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String property, Collection<String> allowed) {
        super("sort に指定できない項目です: " + property + "（指定できる項目: " + String.join(", ", allowed) + "）");
    }
}
