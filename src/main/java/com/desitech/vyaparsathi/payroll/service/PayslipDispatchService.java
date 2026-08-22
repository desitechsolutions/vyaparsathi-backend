package com.desitech.vyaparsathi.payroll.service;

import com.desitech.vyaparsathi.payroll.entity.*;
import com.desitech.vyaparsathi.payroll.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class PayslipDispatchService {

    @Autowired private PayslipDispatchLogRepository dispatchLogRepository;
    @Autowired private PayrollSlipRepository payrollSlipRepository;
    @Autowired private EssPreferencesRepository essPreferencesRepository;

    @Async
    @Transactional
    public void dispatchPayslipsForRun(Long runId) {
        List<PayrollSlip> slips = payrollSlipRepository.findByPayrollRunId(runId, Pageable.unpaged()).getContent();

        for (PayrollSlip slip : slips) {
            dispatchPayslipToEmployee(slip);
        }
    }

    @Transactional
    public void dispatchPayslipToEmployee(PayrollSlip slip) {
        Employee employee = slip.getEmployee();
        EssPreferences prefs = essPreferencesRepository.findByEmployeeId(employee.getId())
                .orElse(new EssPreferences());

        // Email dispatch (default)
        if (prefs.isPayslipDispatchEmail() && employee.getEmail() != null) {
            dispatchViaEmail(slip, employee);
        }

        // WhatsApp dispatch
        if (prefs.isPayslipDispatchWhatsapp() && employee.getPhone() != null) {
            dispatchViaWhatsapp(slip, employee);
        }

        // SMS dispatch
        if (prefs.isPayslipDispatchSms() && employee.getPhone() != null) {
            dispatchViaSms(slip, employee);
        }
    }

    private void dispatchViaEmail(PayrollSlip slip, Employee employee) {
        PayslipDispatchLog log = new PayslipDispatchLog();
        log.setPayrollSlip(slip);
        log.setEmployee(employee);
        log.setDispatchMethod("EMAIL");
        log.setRecipientAddress(employee.getEmail());
        log.setSentAt(LocalDate.now());
        
        // Mock email sending
        String messageId = "MSG_" + System.currentTimeMillis();
        log.setProviderReference(messageId);
        log.setDeliveryStatus("DELIVERED");
        
        dispatchLogRepository.save(log);
    }

    private void dispatchViaWhatsapp(PayrollSlip slip, Employee employee) {
        PayslipDispatchLog log = new PayslipDispatchLog();
        log.setPayrollSlip(slip);
        log.setEmployee(employee);
        log.setDispatchMethod("WHATSAPP");
        log.setRecipientAddress(employee.getPhone());
        log.setSentAt(LocalDate.now());
        
        // Mock WhatsApp message
        String messageId = "WA_" + System.currentTimeMillis();
        log.setProviderReference(messageId);
        log.setDeliveryStatus("DELIVERED");
        
        dispatchLogRepository.save(log);
    }

    private void dispatchViaSms(PayrollSlip slip, Employee employee) {
        PayslipDispatchLog log = new PayslipDispatchLog();
        log.setPayrollSlip(slip);
        log.setEmployee(employee);
        log.setDispatchMethod("SMS");
        log.setRecipientAddress(employee.getPhone());
        log.setSentAt(LocalDate.now());
        
        // Mock SMS
        String messageId = "SMS_" + System.currentTimeMillis();
        log.setProviderReference(messageId);
        log.setDeliveryStatus("DELIVERED");
        
        dispatchLogRepository.save(log);
    }

    public List<PayslipDispatchLog> getDispatchLogsForSlip(Long slipId) {
        return dispatchLogRepository.findByPayrollSlipId(slipId);
    }

    public Map<String, Long> getDispatchStats(Long runId) {
        Map<String, Long> stats = new HashMap<>();
        List<PayrollSlip> slips = payrollSlipRepository.findByPayrollRunId(runId, Pageable.unpaged()).getContent();
        
        long totalDispatched = slips.size();
        long emailSuccess = slips.stream()
                .flatMap(s -> dispatchLogRepository.findByPayrollSlipId(s.getId()).stream())
                .filter(d -> "EMAIL".equals(d.getDispatchMethod()) && "DELIVERED".equals(d.getDeliveryStatus()))
                .count();
        long whatsappSuccess = slips.stream()
                .flatMap(s -> dispatchLogRepository.findByPayrollSlipId(s.getId()).stream())
                .filter(d -> "WHATSAPP".equals(d.getDispatchMethod()) && "DELIVERED".equals(d.getDeliveryStatus()))
                .count();

        stats.put("total", totalDispatched);
        stats.put("emailSuccess", emailSuccess);
        stats.put("whatsappSuccess", whatsappSuccess);
        stats.put("failedCount", Math.max(0, totalDispatched - emailSuccess - whatsappSuccess));

        return stats;
    }
}
