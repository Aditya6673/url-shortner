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
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    @Builder.Default
    private Plan plan = Plan.FREE;

    private LocalDateTime planExpiresAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isPremium() {
        return plan == Plan.PREMIUM
            && (planExpiresAt == null || planExpiresAt.isAfter(LocalDateTime.now()));
    }
}
