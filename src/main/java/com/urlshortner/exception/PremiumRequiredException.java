package com.urlshortner.exception;

public class PremiumRequiredException extends RuntimeException {
    public PremiumRequiredException(String feature) {
        super("Premium subscription required for: " + feature);
    }
}
