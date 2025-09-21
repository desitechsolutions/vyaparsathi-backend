package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceivingTicketRepository extends JpaRepository<ReceivingTicket, Long> {

    List<ReceivingTicket> findByReceiving_Id(Long id);

}
