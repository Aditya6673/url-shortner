package com.urlshortner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUrlRequest {

    @NotBlank(message = "URL cannot be blank")
    @URL(message = "Invalid URL format")
    private String url;

    @Size(max = 20, message = "Custom alias cannot exceed 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9-]*$", message = "Custom alias can only contain alphanumeric characters and hyphens")
    private String customAlias;

    private LocalDateTime expiresAt;
}
