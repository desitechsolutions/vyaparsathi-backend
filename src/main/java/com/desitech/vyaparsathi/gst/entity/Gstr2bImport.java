package com.desitech.vyaparsathi.gst.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "gstr2b_imports")
@Getter
@Setter
@NoArgsConstructor
public class Gstr2bImport extends ShopAwareEntity {

    @Column(name = "return_period", nullable = false, length = 7)
    private String returnPeriod; // "MM-YYYY"

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "total_invoices", nullable = false)
    private int totalInvoices;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "mismatched_count", nullable = false)
    private int mismatchedCount;

    @Column(name = "missing_in_books_count", nullable = false)
    private int missingInBooksCount;

    @Column(name = "missing_in_portal_count", nullable = false)
    private int missingInPortalCount;

    @OneToMany(mappedBy = "gstr2bImport", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Gstr2bEntry> entries = new ArrayList<>();
}
