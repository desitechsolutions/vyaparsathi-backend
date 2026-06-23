package com.desitech.vyaparsathi.notification.repository;

import com.desitech.vyaparsathi.notification.entity.NewsletterSubscriber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, Long> {

    Optional<NewsletterSubscriber> findByEmail(String email);

    Optional<NewsletterSubscriber> findByUnsubscribeToken(String token);

    Page<NewsletterSubscriber> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    Page<NewsletterSubscriber> findByActive(boolean active, Pageable pageable);

    Page<NewsletterSubscriber> findBySource(String source, Pageable pageable);

    @Query("SELECT n FROM NewsletterSubscriber n WHERE " +
           "(:email IS NULL OR LOWER(n.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
           "(:active IS NULL OR n.active = :active) AND " +
           "(:source IS NULL OR n.source = :source)")
    Page<NewsletterSubscriber> findWithFilters(
            @Param("email") String email,
            @Param("active") Boolean active,
            @Param("source") String source,
            Pageable pageable);

    long countByActive(boolean active);

    @Query("SELECT COUNT(n) FROM NewsletterSubscriber n WHERE n.active = true AND n.subscribedAt >= :date")
    long countActiveSubscribersNewerThan(@Param("date") LocalDateTime date);

    List<NewsletterSubscriber> findAllByActiveTrue();

    @Query("SELECT n.source, COUNT(n) FROM NewsletterSubscriber n GROUP BY n.source")
    List<Object[]> countSubscribersBySource();

    @Query("SELECT DATE_FORMAT(n.subscribedAt, '%Y-%m'), COUNT(n) FROM NewsletterSubscriber n GROUP BY DATE_FORMAT(n.subscribedAt, '%Y-%m') ORDER BY DATE_FORMAT(n.subscribedAt, '%Y-%m') DESC")
    List<Object[]> countSubscribersByMonth();
}
