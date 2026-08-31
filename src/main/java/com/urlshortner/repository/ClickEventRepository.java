package com.urlshortner.repository;

import com.urlshortner.document.ClickEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository extends MongoRepository<ClickEvent, String> {
    List<ClickEvent> findByShortCode(String shortCode);
    List<ClickEvent> findTop20ByShortCodeOrderByClickedAtDesc(String shortCode);

    // Account-wide queries (for premium dashboard)
    List<ClickEvent> findByShortCodeIn(List<String> shortCodes);
    List<ClickEvent> findByShortCodeInAndClickedAtAfter(List<String> shortCodes, LocalDateTime after);
    List<ClickEvent> findTop20ByShortCodeInOrderByClickedAtDesc(List<String> shortCodes);
}
