package com.urlshortner.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The redirect target is a trust boundary: whatever passes here is handed to a
 * browser as a Location header. Guards the scheme allowlist AND that a host is present.
 */
class CreateUrlRequestValidationTest {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    private boolean valid(String url) {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setUrl(url);
        return VALIDATOR.validate(request).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com",
            "http://example.com/a?b=1#c",
            "https://sub.example.co.uk:8443/path/to/thing",
            "https://192.168.0.1:3000/x",
    })
    @DisplayName("Accepts http(s) URLs that have a host")
    void accepts(String url) {
        assertTrue(valid(url), url + " should be accepted");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://",                 // scheme but no host
            "http://",
            "http:// not a url",        // host position is whitespace
            "https://example.com/a b",  // unencoded space
            "example.com",              // no scheme
            "ftp://example.com",        // wrong scheme
            "javascript:alert(1)",      // XSS via Location
            "file:///etc/passwd",
            "//example.com",            // protocol-relative
            "",                         // @NotBlank
            "   ",
    })
    @DisplayName("Rejects anything that is not an http(s) URL with a host")
    void rejects(String url) {
        assertFalse(valid(url), url + " should be rejected");
    }
}
