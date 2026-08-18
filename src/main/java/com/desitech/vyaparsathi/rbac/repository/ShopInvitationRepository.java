package com.desitech.vyaparsathi.rbac.repository;

import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import com.desitech.vyaparsathi.rbac.entity.ShopInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Shop invitations are shop-scoped but we bypass the tenant filter so
 * the invitee (who is not yet in the shop) can accept via the token
 * lookup without a shopId in their context.
 */
@SkipShopFilter
@Repository
public interface ShopInvitationRepository extends JpaRepository<ShopInvitation, Long> {

    Optional<ShopInvitation> findByTokenHash(String tokenHash);

    List<ShopInvitation> findByShopIdOrderByCreatedAtDesc(Long shopId);

    List<ShopInvitation> findByShopIdAndStatus(Long shopId, ShopInvitation.Status status);

    boolean existsByShopIdAndEmailAndStatus(Long shopId, String email, ShopInvitation.Status status);
}
