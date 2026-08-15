package com.desitech.vyaparsathi.shop.repository;

import com.desitech.vyaparsathi.shop.entity.ShopBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ShopBankAccount}. Every read is shop-scoped by
 * the {@code ShopFilterAspect}, so callers don't need to pass shopId
 * explicitly for the normal listing methods.
 */
@Repository
public interface ShopBankAccountRepository extends JpaRepository<ShopBankAccount, Long> {

    /** All active accounts for the current shop, defaults first, ordered by label. */
    List<ShopBankAccount> findByIsActiveTrueOrderByIsDefaultDescLabelAsc();

    /** Default account for the current shop + currency. */
    Optional<ShopBankAccount> findFirstByIsActiveTrueAndIsDefaultTrueAndCurrencyCode(String currencyCode);

    /** Any active account with a given purpose (for routing salary vouchers etc.). */
    List<ShopBankAccount> findByIsActiveTrueAndPurpose(String purpose);

    /** Clear the default flag for a given currency, before setting a new default.
     *  Runs a bulk update so we never have two rows with is_default=1 in the
     *  same (shop, currency) partition. */
    @Modifying
    @Query("UPDATE ShopBankAccount b SET b.isDefault = false " +
           "WHERE b.currencyCode = :currencyCode AND b.isDefault = true")
    int clearDefaultForCurrency(@Param("currencyCode") String currencyCode);
}
