package com.desitech.vyaparsathi.document.service;

import com.desitech.vyaparsathi.document.dto.DocumentReferenceDto;
import com.desitech.vyaparsathi.document.entity.DocumentReference;
import com.desitech.vyaparsathi.document.repository.DocumentReferenceRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists + walks the typed doc-reference graph. Callers create edges via
 * {@link #link} at the moment a downstream document is generated (e.g. GRN
 * creation registers a PARENT edge to its PO). The renderer's "Reference
 * Documents" block asks {@link #upstreamOf} and {@link #downstreamOf}.
 */
@Service
public class DocumentReferenceService {

    private final DocumentReferenceRepository repository;

    public DocumentReferenceService(DocumentReferenceRepository repository) {
        this.repository = repository;
    }

    public DocumentReference link(String sourceType, Long sourceId, String sourceNumber,
                                  String targetType, Long targetId, String targetNumber,
                                  String linkKind) {
        DocumentReference ref = new DocumentReference();
        ref.setSourceType(sourceType);
        ref.setSourceId(sourceId);
        ref.setSourceNumber(sourceNumber);
        ref.setTargetType(targetType);
        ref.setTargetId(targetId);
        ref.setTargetNumber(targetNumber);
        ref.setLinkKind(linkKind);
        return repository.save(ref);
    }

    public List<DocumentReferenceDto> upstreamOf(String targetType, Long targetId) {
        List<DocumentReferenceDto> out = new ArrayList<>();
        for (DocumentReference r : repository.findByTargetTypeAndTargetId(targetType, targetId)) {
            out.add(new DocumentReferenceDto(r.getSourceType(), r.getSourceId(), r.getSourceNumber(), null));
        }
        return out;
    }

    public List<DocumentReferenceDto> downstreamOf(String sourceType, Long sourceId) {
        List<DocumentReferenceDto> out = new ArrayList<>();
        for (DocumentReference r : repository.findBySourceTypeAndSourceId(sourceType, sourceId)) {
            out.add(new DocumentReferenceDto(r.getTargetType(), r.getTargetId(), r.getTargetNumber(), null));
        }
        return out;
    }
}
