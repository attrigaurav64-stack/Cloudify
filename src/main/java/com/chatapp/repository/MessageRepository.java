package com.chatapp.repository;

import com.chatapp.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Integer> {

    /**
     * Retrieve the latest 50 messages ordered by id descending.
     */
    List<Message> findTop50ByOrderByIdDesc();

    /**
     * Incrementally fetch new messages sent after a specific message ID.
     */
    List<Message> findByIdGreaterThanOrderByIdAsc(Integer afterId);

    /**
     * Retrieve the most recent distinct senders.
     */
    @Query(value = "SELECT DISTINCT sender FROM messages ORDER BY id DESC LIMIT 15", nativeQuery = true)
    List<String> findRecentSenders();

    /**
     * Keyword search across message content or sender.
     */
    @Query("SELECT m FROM Message m WHERE LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(m.sender) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY m.id DESC")
    List<Message> searchMessages(@Param("query") String query);
}
