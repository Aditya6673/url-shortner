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
@Document(collection = "click_events")
public class ClickEvent {
    @Id
    private String id;
    
    @Indexed
    private String shortCode;
    
    private LocalDateTime clickedAt;
    private String ipAddress;
    private String userAgent;
    private String referer;
    private String browser;
    private String os;
}
