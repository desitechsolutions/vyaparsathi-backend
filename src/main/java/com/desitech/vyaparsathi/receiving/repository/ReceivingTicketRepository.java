package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;

import java.util.List;

public interface ReceivingTicketRepository extends BaseRepository<ReceivingTicket, Long> {

    List<ReceivingTicket> findByReceiving_Id(Long id);

}
