package com.desitech.vyaparsathi.gst.dto;

import java.util.List;

/** API response: reconciliation header KPIs + full entry list. */
public class Gstr2bReconciliationSummaryDto {

    private Long importId;
    private String returnPeriod;
    private String fileName;
    private String importedAt;
    private int totalInvoices;
    private int matchedCount;
    private int mismatchedCount;
    private int missingInBooksCount;
    private int missingInPortalCount;
    private List<Gstr2bEntryDto> entries;

    public Long getImportId()             { return importId; }
    public void setImportId(Long v)         { this.importId = v; }

    public String getReturnPeriod()       { return returnPeriod; }
    public void setReturnPeriod(String v)   { this.returnPeriod = v; }

    public String getFileName()           { return fileName; }
    public void setFileName(String v)       { this.fileName = v; }

    public String getImportedAt()         { return importedAt; }
    public void setImportedAt(String v)     { this.importedAt = v; }

    public int getTotalInvoices()         { return totalInvoices; }
    public void setTotalInvoices(int v)     { this.totalInvoices = v; }

    public int getMatchedCount()          { return matchedCount; }
    public void setMatchedCount(int v)      { this.matchedCount = v; }

    public int getMismatchedCount()       { return mismatchedCount; }
    public void setMismatchedCount(int v)   { this.mismatchedCount = v; }

    public int getMissingInBooksCount()   { return missingInBooksCount; }
    public void setMissingInBooksCount(int v) { this.missingInBooksCount = v; }

    public int getMissingInPortalCount()  { return missingInPortalCount; }
    public void setMissingInPortalCount(int v) { this.missingInPortalCount = v; }

    public List<Gstr2bEntryDto> getEntries() { return entries; }
    public void setEntries(List<Gstr2bEntryDto> v) { this.entries = v; }
}
