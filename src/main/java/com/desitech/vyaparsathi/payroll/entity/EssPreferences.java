package com.desitech.vyaparsathi.payroll.entity;

import lombok.*;
import jakarta.persistence.*;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;

@Entity
@Table(name = "ess_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EssPreferences extends ShopAwareEntity {
    @OneToOne
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Column(nullable = false)
    private boolean payslipDispatchEmail = true;

    @Column(nullable = false)
    private boolean payslipDispatchWhatsapp = false;

    @Column(nullable = false)
    private boolean payslipDispatchSms = false;

    @Column(nullable = false)
    private boolean autoTaxCalculation = true;

    @Column(nullable = false)
    private boolean notificationOptIn = true;

    // Convenience setters for notification preferences
    public void setEmailNotifications(boolean value) {
        this.payslipDispatchEmail = value;
    }

    public void setSmsNotifications(boolean value) {
        this.payslipDispatchSms = value;
    }

    public void setWhatsappNotifications(boolean value) {
        this.payslipDispatchWhatsapp = value;
    }
}
