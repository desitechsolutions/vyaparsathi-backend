package com.desitech.vyaparsathi.subscriptions.razorpay.repository;

import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpayCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RazorpayCustomerRepository extends JpaRepository<RazorpayCustomer, Long> {

    Optional<RazorpayCustomer> findByShopId(Long shopId);
}
