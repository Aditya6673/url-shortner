package com.urlshortner.repository;

import com.urlshortner.document.ClickEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository extends MongoRepository<ClickEvent, String> {
    List<ClickEvent> findByShortCode(String shortCode);
    long countByShortCode(String shortCode);
    List<ClickEvent> findByShortCodeAndClickedAtBetween(String shortCode, LocalDateTime start, LocalDateTime end);
    List<ClickEvent> findTop20ByShortCodeOrderByClickedAtDesc(String shortCode);
}
