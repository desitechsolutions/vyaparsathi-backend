package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.sales.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends BaseRepository<Customer, Long> {
    List<Customer> findByNameContainingIgnoreCase(String name);

    @Query("SELECT c FROM Customer c WHERE c.name LIKE %:q%")
    List<Customer> searchByCustomerPartial(@Param("q") String q);

    @Query("SELECT c FROM Customer c WHERE c.shop.id = :shopId")
    List<Customer> findAllByShopId(@Param("shopId") Long shopId);

}
