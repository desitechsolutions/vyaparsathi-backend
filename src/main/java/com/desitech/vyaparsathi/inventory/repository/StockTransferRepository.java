package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.inventory.entity.StockTransfer;
import com.desitech.vyaparsathi.inventory.enums.StockTransferStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockTransferRepository extends BaseRepository<StockTransfer, Long> {

    Optional<StockTransfer> findByTransferNumber(String transferNumber);

    /** All transfers where this shop is the sender. */
    List<StockTransfer> findByFromShopIdOrderByTransferDateDesc(Long fromShopId);

    /** All transfers where this shop is the receiver. */
    List<StockTransfer> findByToShopIdOrderByTransferDateDesc(Long toShopId);

    /** All transfers involving a specific shop (either sender or receiver). */
    @Query("SELECT st FROM StockTransfer st WHERE st.fromShop.id = :shopId OR st.toShop.id = :shopId ORDER BY st.transferDate DESC")
    List<StockTransfer> findAllByShopId(@Param("shopId") Long shopId);

    /** All transfers in a given status. */
    List<StockTransfer> findByStatus(StockTransferStatus status);

    /** Count pending transfers (used for dashboard badges). */
    long countByStatus(StockTransferStatus status);
}
