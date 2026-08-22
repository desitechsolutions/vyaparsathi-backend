package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.exception.BankingIntegrationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@Service
public class PayrollRetryService {

    private static final Logger logger = LoggerFactory.getLogger(PayrollRetryService.class);

    @Retryable(
        retryFor = { BankingIntegrationException.class, Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        recover = "fallback"
    )
    public <T> T executeWithRetry(String operationName, Supplier<T> operation) {
        try {
            logger.info("Executing operation: {}", operationName);
            T result = operation.get();
            logger.info("Operation {} completed successfully", operationName);
            return result;
        } catch (Exception e) {
            logger.warn("Operation {} failed: {}", operationName, e.getMessage());
            throw new BankingIntegrationException("Retry operation failed: " + operationName, e, true);
        }
    }

    public <T> T fallback(String operationName, Supplier<T> operation, Exception e) {
        logger.error("Operation {} exhausted all retries. Error: {}", operationName, e.getMessage());
        throw new BankingIntegrationException("Operation " + operationName + " failed after retries", e, false);
    }

    // Disbursal retry with exponential backoff
    @Retryable(
        retryFor = { BankingIntegrationException.class },
        maxAttempts = 5,
        backoff = @Backoff(delay = 5000, multiplier = 1.5),
        recover = "fallbackDisbursal"
    )
    public void disburseWithRetry(Long payrollRunId, Runnable disbursalOperation) {
        try {
            logger.info("Attempting disbursal for payroll run: {}", payrollRunId);
            disbursalOperation.run();
            logger.info("Disbursal for payroll run {} completed successfully", payrollRunId);
        } catch (Exception e) {
            logger.warn("Disbursal for payroll run {} failed: {}", payrollRunId, e.getMessage());
            throw new BankingIntegrationException("Disbursal failed for run " + payrollRunId, e, true);
        }
    }

    public void fallbackDisbursal(Long payrollRunId, Runnable disbursalOperation, Exception e) {
        logger.error("Disbursal for payroll run {} exhausted all retries. Error: {}", payrollRunId, e.getMessage());
        // Store failure for manual reconciliation
        storeFailedDisbursal(payrollRunId, e.getMessage());
        throw new BankingIntegrationException("Disbursal failed after retries for run " + payrollRunId, e, false);
    }

    private void storeFailedDisbursal(Long payrollRunId, String errorMessage) {
        // TODO: Store failed disbursal in database for manual reconciliation
        logger.error("Failed disbursal stored for manual review. Run ID: {}, Error: {}", payrollRunId, errorMessage);
    }

    // Batch export retry
    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public String exportBatchFileWithRetry(String exportType, Long payrollRunId, Supplier<String> exportOperation) {
        try {
            logger.info("Exporting {} batch for payroll run: {}", exportType, payrollRunId);
            String result = exportOperation.get();
            logger.info("Export {} for payroll run {} completed successfully", exportType, payrollRunId);
            return result;
        } catch (Exception e) {
            logger.warn("Export {} for payroll run {} failed: {}", exportType, payrollRunId, e.getMessage());
            throw new BankingIntegrationException("Export " + exportType + " failed", e, true);
        }
    }
}
