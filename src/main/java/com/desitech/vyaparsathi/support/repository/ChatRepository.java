package com.desitech.vyaparsathi.support.repository;

import com.desitech.vyaparsathi.support.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<ChatMessageEntity, Long> {

    // This will be restricted by the @Filter (Perfect for Shop Owners)
    List<ChatMessageEntity> findAllByOrderByCreatedAtAsc();

    /**
     * NATIVE QUERY: Bypasses Hibernate @Filter.
     * Use this for Super Admin view to see messages for ANY shop.
     */
    @Query(value = "SELECT * FROM support_chats WHERE shop_id = :shopId ORDER BY created_at ASC",
            nativeQuery = true)
    List<ChatMessageEntity> findByShopIdNative(@Param("shopId") Long shopId);

    @Query(value = "SELECT * FROM support_chats m " +
            "WHERE m.id IN (SELECT MAX(id) FROM support_chats GROUP BY shop_id) " +
            "ORDER BY m.created_at DESC", nativeQuery = true)
    List<ChatMessageEntity> findLatestMessagesPerShop();

    List<ChatMessageEntity> findByShopIdAndIsFromAdminFalseAndIsReadByAdminFalse(Long shopId);
    boolean existsByShopIdAndIsFromAdminFalseAndIsReadByAdminFalse(Long shopId);

    @Modifying
    @Query(value = "UPDATE support_chats SET is_read_by_admin = true WHERE shop_id = :shopId AND is_from_admin = false", nativeQuery = true)
    void markAllAsReadNative(@Param("shopId") Long shopId);
}