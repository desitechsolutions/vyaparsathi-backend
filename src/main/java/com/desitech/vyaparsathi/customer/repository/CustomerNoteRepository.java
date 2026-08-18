package com.desitech.vyaparsathi.customer.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.customer.entity.CustomerNote;

import java.util.List;

public interface CustomerNoteRepository extends BaseRepository<CustomerNote, Long> {
    /**
     * Pinned notes first, then most-recent. Matches the natural read
     * order for the FE Notes tab so no client-side sort is needed.
     */
    List<CustomerNote> findByCustomerIdOrderByPinnedDescCreatedAtDesc(Long customerId);
}
