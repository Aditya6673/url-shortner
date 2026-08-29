package com.urlshortner.exception;

public class DuplicateAliasException extends RuntimeException {
    public DuplicateAliasException(String alias) {
        super("Custom alias already in use: " + alias);
    }
}
