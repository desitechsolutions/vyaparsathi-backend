package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReceivingTicketRepository extends BaseRepository<ReceivingTicket, Long> {

    List<ReceivingTicket> findByReceiving_Id(Long id);

    /**
     * Loads all OPEN / IN_PROGRESS tickets raised before {@code cutoff}, eagerly
     * fetching the parent {@code Receiving} and its {@code Shop} in a single query.
     * Used by the escalation scheduler to avoid {@code LazyInitializationException}
     * when the loaded entity is handed off to an {@code @Async} notification thread.
     */
    @Query("""
            SELECT t FROM ReceivingTicket t
            JOIN FETCH t.receiving r
            JOIN FETCH r.shop
            WHERE t.status IN ('OPEN', 'IN_PROGRESS')
              AND t.raisedAt < :cutoff
            """)
    List<ReceivingTicket> findOpenTicketsOlderThan(@Param("cutoff") LocalDateTime cutoff);

}
