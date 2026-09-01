package com.urlshortner.exception;

public class AccountRequiredException extends RuntimeException {
    public AccountRequiredException(String feature) {
        super("An account is required for: " + feature);
    }
}
