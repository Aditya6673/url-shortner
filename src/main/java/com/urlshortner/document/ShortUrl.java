package com.urlshortner.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "short_urls")
public class ShortUrl {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String shortCode;
    
    private String originalUrl;
    
    private String customAlias;
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime expiresAt;
    
    @Builder.Default
    private long clickCount = 0;
    
    @Builder.Default
    private boolean active = true;
}
