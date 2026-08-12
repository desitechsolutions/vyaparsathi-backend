package com.desitech.vyaparsathi.salesorder.repository;

import com.desitech.vyaparsathi.salesorder.entity.SalesOrder;
import com.desitech.vyaparsathi.salesorder.enums.SalesOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    Page<SalesOrder> findAllByShopId(Long shopId, Pageable pageable);

    Page<SalesOrder> findByShopIdAndStatus(Long shopId, SalesOrderStatus status, Pageable pageable);

    Page<SalesOrder> findByShopIdAndCustomer_Id(Long shopId, Long customerId, Pageable pageable);
}
