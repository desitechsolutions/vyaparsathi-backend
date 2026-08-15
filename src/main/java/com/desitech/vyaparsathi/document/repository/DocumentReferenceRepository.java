package com.desitech.vyaparsathi.document.repository;

import com.desitech.vyaparsathi.document.entity.DocumentReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentReferenceRepository extends JpaRepository<DocumentReference, Long> {

    /** All docs downstream of (sourceType, sourceId). */
    List<DocumentReference> findBySourceTypeAndSourceId(String sourceType, Long sourceId);

    /** All docs upstream of (targetType, targetId). */
    List<DocumentReference> findByTargetTypeAndTargetId(String targetType, Long targetId);
}
