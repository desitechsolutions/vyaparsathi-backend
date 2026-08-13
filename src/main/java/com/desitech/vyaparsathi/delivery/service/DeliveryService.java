package com.desitech.vyaparsathi.delivery.service;

import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.delivery.dto.DeliveryDTO;
import com.desitech.vyaparsathi.delivery.dto.DeliveryMetricsDto;
import com.desitech.vyaparsathi.delivery.dto.DeliveryPersonDTO;
import com.desitech.vyaparsathi.delivery.dto.DeliveryStatusHistoryDTO;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.delivery.entity.DeliveryPerson;
import com.desitech.vyaparsathi.delivery.entity.DeliveryStatusHistory;
import com.desitech.vyaparsathi.delivery.enums.DeliveryHistoryEventType;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import com.desitech.vyaparsathi.delivery.mapper.DeliveryMapper;
import com.desitech.vyaparsathi.delivery.mapper.DeliveryPersonMapper;
import com.desitech.vyaparsathi.delivery.mapper.DeliveryStatusHistoryMapper;
import com.desitech.vyaparsathi.delivery.repository.DeliveryPersonRepository;
import com.desitech.vyaparsathi.delivery.repository.DeliveryRepository;
import com.desitech.vyaparsathi.delivery.repository.DeliveryStatusHistoryRepository;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.notification.service.NotificationService;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeliveryService {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryRepository deliveryRepo;
    private final DeliveryPersonRepository personRepo;
    private final DeliveryStatusHistoryRepository statusHistoryRepo;
    private final DeliveryMapper deliveryMapper;
    private final DeliveryPersonMapper deliveryPersonMapper;
    private final DeliveryStatusHistoryMapper deliveryStatusHistoryMapper;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;

    @Autowired
    public DeliveryService(
            DeliveryRepository deliveryRepo,
            DeliveryPersonRepository personRepo,
            DeliveryStatusHistoryRepository statusHistoryRepo,
            DeliveryMapper deliveryMapper,
            DeliveryPersonMapper deliveryPersonMapper,
            DeliveryStatusHistoryMapper deliveryStatusHistoryMapper,
            PaymentService paymentService,
            PaymentRepository paymentRepository,
            NotificationService notificationService
    ) {
        this.deliveryRepo = deliveryRepo;
        this.personRepo = personRepo;
        this.statusHistoryRepo = statusHistoryRepo;
        this.deliveryMapper = deliveryMapper;
        this.deliveryPersonMapper = deliveryPersonMapper;
        this.deliveryStatusHistoryMapper = deliveryStatusHistoryMapper;
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
    }

    // ── Reads ─────────────────────────────────────────────────────────

    public Optional<DeliveryDTO> getDelivery(Long id) {
        return deliveryRepo.findById(id).map(deliveryMapper::toDto);
    }

    public Page<DeliveryDTO> search(
            DeliveryStatus status,
            Long personId,
            Long saleId,
            LocalDateTime from,
            LocalDateTime to,
            String q,
            Pageable pageable
    ) {
        return deliveryRepo.search(status, personId, saleId, from, to,
                (q != null && q.isBlank()) ? null : q, pageable)
                .map(deliveryMapper::toDto);
    }

    public List<DeliveryDTO> getDeliveriesBySaleId(Long saleId) {
        return deliveryRepo.findBySaleIdOrderByCreatedAtDesc(saleId).stream()
                .map(deliveryMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<DeliveryStatusHistoryDTO> getStatusHistory(Long deliveryId) {
        return statusHistoryRepo.findByDelivery_IdOrderByChangedAtAsc(deliveryId).stream()
                .map(deliveryStatusHistoryMapper::toDto)
                .collect(Collectors.toList());
    }

    // ── Writes ────────────────────────────────────────────────────────

    @Transactional
    public DeliveryDTO createDelivery(DeliveryDTO deliveryDTO) {
        Delivery delivery = deliveryMapper.toEntity(deliveryDTO);
        LocalDateTime now = LocalDateTime.now();
        delivery.setCreatedAt(now);
        delivery.setUpdatedAt(now);
        if (delivery.getDeliveryStatus() == null) {
            delivery.setDeliveryStatus(DeliveryStatus.PENDING);
        }
        // Immutable address at dispatch time: if the caller didn't supply a
        // snapshot, freeze whatever address they provided now — subsequent
        // customer-record edits won't drift the challan.
        if (delivery.getDeliveryAddressSnapshot() == null || delivery.getDeliveryAddressSnapshot().isBlank()) {
            delivery.setDeliveryAddressSnapshot(delivery.getDeliveryAddress());
        }
        Delivery saved = deliveryRepo.save(delivery);
        recordHistory(saved, saved.getDeliveryStatus(),
                DeliveryHistoryEventType.STATUS_CHANGE, "Delivery created", currentUsername());
        notifyDeliveryEvent(saved, "Delivery created",
                "Delivery for " + saved.getCustomerName() + " has been created.", "INFO");
        return deliveryMapper.toDto(saved);
    }

    @Transactional
    public DeliveryDTO updateDeliveryDetails(Long id, DeliveryDTO updated) {
        Delivery existing = requireDelivery(id);

        if (updated.getDeliveryAddress() != null) existing.setDeliveryAddress(updated.getDeliveryAddress());
        if (updated.getDeliveryCharge() != null) existing.setDeliveryCharge(updated.getDeliveryCharge());
        if (updated.getDeliveryPaidBy() != null) existing.setDeliveryPaidBy(updated.getDeliveryPaidBy());
        if (updated.getDeliveryNotes() != null) existing.setDeliveryNotes(updated.getDeliveryNotes());
        if (updated.getEstimatedDeliveryDate() != null) existing.setEstimatedDeliveryDate(updated.getEstimatedDeliveryDate());
        if (updated.getTrackingNumber() != null) existing.setTrackingNumber(updated.getTrackingNumber());
        if (updated.getEwayBillNo() != null) existing.setEwayBillNo(updated.getEwayBillNo());
        if (updated.getCourierPartner() != null) existing.setCourierPartner(updated.getCourierPartner());
        if (updated.getCodAmount() != null) existing.setCodAmount(updated.getCodAmount());

        existing.setUpdatedAt(LocalDateTime.now());
        return deliveryMapper.toDto(deliveryRepo.save(existing));
    }

    @Transactional
    public DeliveryDTO assignDeliveryPerson(Long deliveryId, DeliveryPersonDTO personDto) {
        Delivery delivery = requireDelivery(deliveryId);
        if (DeliveryTransitions.isTerminal(delivery.getDeliveryStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot (re)assign a delivery already in state " + delivery.getDeliveryStatus());
        }
        String previousName = delivery.getDeliveryPerson() != null
                ? delivery.getDeliveryPerson().getName() : null;

        DeliveryPerson person;
        if (personDto.getId() != null) {
            person = personRepo.findById(personDto.getId())
                    .orElseThrow(() -> new EntityNotFoundAppException("DeliveryPerson", personDto.getId()));
        } else {
            person = personRepo.save(deliveryPersonMapper.toEntity(personDto));
        }
        delivery.setDeliveryPerson(person);
        delivery.setUpdatedAt(LocalDateTime.now());
        Delivery saved = deliveryRepo.save(delivery);

        String note = previousName == null
                ? "Assigned to " + person.getName()
                : "Reassigned: " + previousName + " → " + person.getName();
        recordHistory(saved, saved.getDeliveryStatus(),
                DeliveryHistoryEventType.ASSIGNMENT, note, currentUsername());
        notifyDeliveryEvent(saved, "Delivery assigned",
                note + " (order #" + saved.getInvoiceNumber() + ")", "INFO");
        return deliveryMapper.toDto(saved);
    }

    @Transactional
    public DeliveryDTO updateStatus(Long deliveryId, DeliveryStatus newStatus, String noteFromCaller) {
        Delivery delivery = requireDelivery(deliveryId);
        DeliveryStatus current = delivery.getDeliveryStatus();

        // Idempotency: setting the same status is a no-op — no history spam,
        // no double-firing of side effects like COD payment recording.
        if (DeliveryTransitions.isNoOp(current, newStatus)) {
            return deliveryMapper.toDto(delivery);
        }
        if (!DeliveryTransitions.isAllowed(current, newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Illegal transition: " + current + " → " + newStatus +
                            ". Allowed next: " + DeliveryTransitions.nextAllowed(current));
        }

        delivery.setDeliveryStatus(newStatus);
        LocalDateTime now = LocalDateTime.now();
        if (newStatus == DeliveryStatus.DELIVERED) {
            delivery.setDeliveredAt(now);
        }
        delivery.setUpdatedAt(now);
        Delivery saved = deliveryRepo.save(delivery);

        recordHistory(saved, newStatus, DeliveryHistoryEventType.STATUS_CHANGE,
                noteFromCaller, currentUsername());

        // On successful delivery, record a COD Payment if one is due and hasn't
        // already been recorded. Failure here is logged but not rolled back —
        // the state transition itself is the source of truth.
        if (newStatus == DeliveryStatus.DELIVERED) {
            tryRecordCodPayment(saved);
        }
        notifyStatusChange(saved, current, newStatus, noteFromCaller);
        return deliveryMapper.toDto(saved);
    }

    /**
     * Log a failed delivery attempt. Increments attemptCount, records lastAttemptAt,
     * writes an ATTEMPT audit row. Does not change status — the caller decides
     * whether to also transition (typically back to OUT_FOR_DELIVERY for retry or
     * to CANCELLED after too many failures).
     */
    @Transactional
    public DeliveryDTO recordFailedAttempt(Long deliveryId, String failureReason) {
        Delivery delivery = requireDelivery(deliveryId);
        if (DeliveryTransitions.isTerminal(delivery.getDeliveryStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot record an attempt on a terminal-state delivery");
        }
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        LocalDateTime now = LocalDateTime.now();
        delivery.setLastAttemptAt(now);
        if (failureReason != null && !failureReason.isBlank()) {
            delivery.setFailureReason(failureReason);
        }
        delivery.setUpdatedAt(now);
        Delivery saved = deliveryRepo.save(delivery);
        recordHistory(saved, saved.getDeliveryStatus(), DeliveryHistoryEventType.ATTEMPT,
                failureReason == null ? "Failed attempt" : "Failed attempt: " + failureReason,
                currentUsername());
        notifyDeliveryEvent(saved, "Delivery attempt failed",
                "Attempt #" + saved.getAttemptCount() + " failed for order #"
                        + saved.getInvoiceNumber()
                        + (failureReason != null ? " — " + failureReason : ""),
                "HIGH");
        return deliveryMapper.toDto(saved);
    }

    /**
     * Capture proof-of-delivery: recipient name, signature URL, photo URL,
     * OTP, and mark {@code podCollectedAt}. Called from the drawer when the
     * agent hands over the parcel. Independent of the status update — status
     * transitions and POD capture are decoupled so the UI can flow them in
     * either order.
     */
    @Transactional
    public DeliveryDTO capturePod(Long deliveryId, DeliveryDTO podFields) {
        Delivery delivery = requireDelivery(deliveryId);
        if (podFields.getRecipientName() != null) delivery.setRecipientName(podFields.getRecipientName());
        if (podFields.getPodSignatureUrl() != null) delivery.setPodSignatureUrl(podFields.getPodSignatureUrl());
        if (podFields.getPodPhotoUrl() != null) delivery.setPodPhotoUrl(podFields.getPodPhotoUrl());
        if (podFields.getPodOtp() != null) delivery.setPodOtp(podFields.getPodOtp());
        delivery.setPodCollectedAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(podFields.getCodCollected())) {
            delivery.setCodCollected(true);
            delivery.setCodCollectedAt(LocalDateTime.now());
            // Auto-stamp codAmount if the user marked collected without setting one.
            // Metrics + the auto-payment record both gate on codAmount > 0, so
            // leaving it null silently zeros out the drawer reconciliation.
            if (delivery.getCodAmount() == null) {
                BigDecimal computed = computeExpectedCodAmount(delivery);
                if (computed.signum() > 0) {
                    delivery.setCodAmount(computed);
                    logger.info("Auto-stamped codAmount={} on delivery {} (source: sale balance + customer-paid delivery charge)",
                            computed, delivery.getId());
                }
            }
        }
        delivery.setUpdatedAt(LocalDateTime.now());
        return deliveryMapper.toDto(deliveryRepo.save(delivery));
    }

    /**
     * Best-effort inference of what money should be collected from the customer
     * at delivery time. Composed of:
     *   • unpaid sale balance = sale.grandTotal - sum(existing SALE Payments),
     *     clamped to 0. Represents credit sales / partial-prepay balance.
     *   • plus the delivery charge itself when the customer pays for delivery
     *     ({@code deliveryPaidBy = CUSTOMER}).
     * Returns {@link BigDecimal#ZERO} when the delivery has no linked sale or
     * the sale is fully prepaid and delivery is shop-paid.
     */
    private BigDecimal computeExpectedCodAmount(Delivery delivery) {
        BigDecimal total = BigDecimal.ZERO;
        if (delivery.getSale() != null && delivery.getSale().getId() != null) {
            BigDecimal grand = delivery.getSale().getGrandTotal();
            if (grand != null && grand.signum() > 0) {
                BigDecimal paid = paymentRepository.sumPaymentsBySource(
                        PaymentSourceType.SALE, delivery.getSale().getId());
                BigDecimal unpaid = grand.subtract(paid == null ? BigDecimal.ZERO : paid);
                if (unpaid.signum() > 0) total = total.add(unpaid);
            }
        }
        if (delivery.getDeliveryPaidBy() == com.desitech.vyaparsathi.delivery.enums.DeliveryPaidBy.CUSTOMER
                && delivery.getDeliveryCharge() != null && delivery.getDeliveryCharge() > 0) {
            total = total.add(BigDecimal.valueOf(delivery.getDeliveryCharge()));
        }
        return total;
    }

    @Transactional
    public void deleteDelivery(Long id) {
        Delivery d = requireDelivery(id);
        if (d.getDeliveryStatus() == DeliveryStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete a delivered delivery — cancel instead.");
        }
        deliveryRepo.deleteById(id);
    }

    // ── Delivery-person CRUD ──────────────────────────────────────────

    @Transactional
    public DeliveryPersonDTO createPerson(DeliveryPersonDTO dpDTO) {
        DeliveryPerson entity = deliveryPersonMapper.toEntity(dpDTO);
        if (dpDTO.getActive() == null) entity.setActive(true);
        return deliveryPersonMapper.toDto(personRepo.save(entity));
    }

    @Transactional
    public DeliveryPersonDTO updatePerson(Long id, DeliveryPersonDTO dpDTO) {
        DeliveryPerson existing = personRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("DeliveryPerson", id));
        if (dpDTO.getName() != null) existing.setName(dpDTO.getName());
        if (dpDTO.getPhone() != null) existing.setPhone(dpDTO.getPhone());
        if (dpDTO.getNotes() != null) existing.setNotes(dpDTO.getNotes());
        if (dpDTO.getVehicleNumber() != null) existing.setVehicleNumber(dpDTO.getVehicleNumber());
        if (dpDTO.getLicenseNumber() != null) existing.setLicenseNumber(dpDTO.getLicenseNumber());
        if (dpDTO.getEmployeeId() != null) existing.setEmployeeId(dpDTO.getEmployeeId());
        if (dpDTO.getActive() != null) existing.setActive(dpDTO.getActive());
        return deliveryPersonMapper.toDto(personRepo.save(existing));
    }

    public List<DeliveryPersonDTO> listPersons() {
        return personRepo.findAll().stream()
                .map(deliveryPersonMapper::toDto)
                .collect(Collectors.toList());
    }

    public Optional<DeliveryPersonDTO> getPerson(Long id) {
        return personRepo.findById(id).map(deliveryPersonMapper::toDto);
    }

    @Transactional
    public void deletePerson(Long id) {
        personRepo.deleteById(id);
    }

    // ── Metrics ───────────────────────────────────────────────────────

    public DeliveryMetricsDto getMetrics(LocalDate from, LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate t = to   != null ? to   : LocalDate.now();
        LocalDateTime start = f.atStartOfDay();
        LocalDateTime end   = t.atTime(23, 59, 59, 999_999_999);

        List<Delivery> deliveries = deliveryRepo.findForMetrics(start, end);

        long total = 0;
        long delivered = 0, cancelled = 0, inProgress = 0;
        long onTime = 0, deliveredWithEta = 0;
        double totalLeadHours = 0.0;
        long deliveredWithLead = 0;
        BigDecimal codTotal = BigDecimal.ZERO;
        long codCount = 0;

        Map<Long, PersonAccum> perPerson = new HashMap<>();

        // A delivery is "in the creation window" if it was created inside [start, end].
        // A delivery is "in the COD window" if its codCollectedAt falls inside [start, end].
        // Lifecycle metrics (delivered/cancelled/onTime/leadTime/perPerson) count only
        // deliveries created in the window — the "cohort" view. COD metrics count only
        // collections in the window — the "cash received" view. This split fixes the
        // stale bug where COD collected on an older delivery showed as 0 because the
        // row was excluded by the creation-based query.
        for (Delivery d : deliveries) {
            LocalDateTime createdAt = d.getCreatedAt();
            boolean createdInWindow = createdAt != null && !createdAt.isBefore(start) && !createdAt.isAfter(end);
            LocalDateTime codAt = d.getCodCollectedAt();
            boolean codInWindow = codAt != null && !codAt.isBefore(start) && !codAt.isAfter(end);

            DeliveryStatus s = d.getDeliveryStatus();
            boolean isDelivered = s == DeliveryStatus.DELIVERED;
            boolean isCancelled = s == DeliveryStatus.CANCELLED;

            if (createdInWindow) {
                total++;
                if (isDelivered) delivered++;
                else if (isCancelled) cancelled++;
                else inProgress++;
            }

            // On-time: DELIVERED and (deliveredAt as LocalDate) <= estimatedDeliveryDate
            if (createdInWindow && isDelivered && d.getEstimatedDeliveryDate() != null && d.getDeliveredAt() != null) {
                deliveredWithEta++;
                if (!d.getDeliveredAt().toLocalDate().isAfter(d.getEstimatedDeliveryDate())) {
                    onTime++;
                }
            }
            // Lead time: hours from createdAt → deliveredAt
            double leadHrs = 0.0;
            boolean hasLead = false;
            if (createdInWindow && isDelivered && d.getCreatedAt() != null && d.getDeliveredAt() != null) {
                leadHrs = Duration.between(d.getCreatedAt(), d.getDeliveredAt()).toMinutes() / 60.0;
                totalLeadHours += leadHrs;
                deliveredWithLead++;
                hasLead = true;
            }
            // COD collected sums — count when the COLLECTION happened in-window,
            // regardless of when the delivery was originally created. If a legacy
            // row was marked collected without an explicit amount (pre-2026-08-13
            // capturePod flow), compute the expected amount on-the-fly so the
            // metric still reflects the money that changed hands.
            if (codInWindow) {
                BigDecimal amount = d.getCodAmount();
                if (amount == null) {
                    amount = computeExpectedCodAmount(d);
                }
                if (amount != null && amount.signum() > 0) {
                    codTotal = codTotal.add(amount);
                    codCount++;
                }
            }
            // Per-person roll-up — cohort view, gated on createdInWindow so a person
            // isn't credited for closing a delivery that belongs to another period.
            if (createdInWindow && d.getDeliveryPerson() != null) {
                Long pid = d.getDeliveryPerson().getId();
                PersonAccum acc = perPerson.computeIfAbsent(pid,
                        k -> new PersonAccum(pid, d.getDeliveryPerson().getName()));
                if (isDelivered) acc.delivered++;
                else if (!isCancelled) acc.inProgress++;
                if (isDelivered && d.getEstimatedDeliveryDate() != null && d.getDeliveredAt() != null) {
                    acc.deliveredWithEta++;
                    if (!d.getDeliveredAt().toLocalDate().isAfter(d.getEstimatedDeliveryDate())) {
                        acc.onTime++;
                    }
                }
                if (hasLead) {
                    acc.totalLeadHours += leadHrs;
                    acc.deliveredWithLead++;
                }
            }
        }

        double onTimePct = deliveredWithEta == 0 ? 0.0 :
                Math.round((100.0 * onTime / deliveredWithEta) * 10) / 10.0;
        double avgLead = deliveredWithLead == 0 ? 0.0 :
                Math.round((totalLeadHours / deliveredWithLead) * 10) / 10.0;

        List<DeliveryMetricsDto.PerPersonMetrics> perPersonList = perPerson.values().stream()
                .map(p -> {
                    double ppOnTimePct = p.deliveredWithEta == 0 ? 0.0 :
                            Math.round((100.0 * p.onTime / p.deliveredWithEta) * 10) / 10.0;
                    double ppAvg = p.deliveredWithLead == 0 ? 0.0 :
                            Math.round((p.totalLeadHours / p.deliveredWithLead) * 10) / 10.0;
                    return new DeliveryMetricsDto.PerPersonMetrics(
                            p.personId, p.personName, p.delivered, p.inProgress, ppOnTimePct, ppAvg);
                })
                .sorted(Comparator.comparingLong(DeliveryMetricsDto.PerPersonMetrics::getDelivered).reversed())
                .collect(Collectors.toList());

        return new DeliveryMetricsDto(f, t, total, delivered, cancelled, inProgress,
                onTimePct, avgLead, codTotal, codCount, perPersonList);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Delivery requireDelivery(Long id) {
        return deliveryRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Delivery", id));
    }

    private void recordHistory(Delivery delivery, DeliveryStatus status,
                               DeliveryHistoryEventType type, String note, String changedBy) {
        DeliveryStatusHistory h = new DeliveryStatusHistory();
        h.setDelivery(delivery);
        h.setStatus(status);
        h.setEventType(type);
        h.setNote(note);
        h.setChangedAt(LocalDateTime.now());
        h.setChangedBy(changedBy);
        statusHistoryRepo.save(h);
    }

    /**
     * Best-effort auto-record of a Cash-on-Delivery Payment against the linked Sale
     * when the delivery is marked DELIVERED. Runs only if:
     *   • the delivery has a COD amount > 0 and is marked codCollected, AND
     *   • no existing SALE Payment already covers this sale (idempotent replay-safe).
     */
    private void tryRecordCodPayment(Delivery delivery) {
        try {
            if (!delivery.isCodCollected()) return;
            if (delivery.getCodAmount() == null || delivery.getCodAmount().signum() <= 0) return;
            if (delivery.getSale() == null || delivery.getSale().getId() == null) return;

            Long saleId = delivery.getSale().getId();
            BigDecimal existingPaid = paymentRepository.sumPaymentsBySource(PaymentSourceType.SALE, saleId);
            if (existingPaid != null && existingPaid.compareTo(delivery.getCodAmount()) >= 0) {
                // Sale is already fully covered — don't double-record.
                logger.info("Skipping COD payment for delivery {} — sale {} already paid ≥ COD amount",
                        delivery.getId(), saleId);
                return;
            }

            PaymentDto dto = new PaymentDto();
            dto.setSourceType(PaymentSourceType.SALE);
            dto.setSourceId(saleId);
            dto.setAmount(delivery.getCodAmount());
            dto.setPaymentDate(LocalDateTime.now());
            dto.setPaymentMethod(PaymentMethod.CASH);
            dto.setStatus(PaymentStatus.PAID);
            dto.setNotes("Auto-recorded on delivery #" + delivery.getId());
            if (delivery.getInvoiceNumber() != null) dto.setInvoiceNumber(delivery.getInvoiceNumber());
            paymentService.createPayment(dto);
            delivery.setCodCollectedAt(LocalDateTime.now());
            deliveryRepo.save(delivery);
            logger.info("Recorded COD payment of {} for delivery {} / sale {}",
                    delivery.getCodAmount(), delivery.getId(), saleId);
        } catch (Exception e) {
            // Never rollback the status transition on payment failure — surface it in logs.
            logger.error("Failed to auto-record COD payment for delivery {}: {}",
                    delivery.getId(), e.getMessage(), e);
        }
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().isBlank()) {
            return "system";
        }
        return auth.getName();
    }

    // ── Bulk operations ───────────────────────────────────────────────

    /**
     * Assign N deliveries to one person in a single transaction. Terminal-state
     * rows are skipped (never break the whole batch). Returns the count that
     * actually changed hands.
     */
    @Transactional
    public int bulkAssign(List<Long> deliveryIds, Long personId) {
        if (deliveryIds == null || deliveryIds.isEmpty() || personId == null) return 0;
        DeliveryPerson person = personRepo.findById(personId)
                .orElseThrow(() -> new EntityNotFoundAppException("DeliveryPerson", personId));
        int changed = 0;
        LocalDateTime now = LocalDateTime.now();
        String who = currentUsername();
        for (Long id : deliveryIds) {
            Delivery d = deliveryRepo.findById(id).orElse(null);
            if (d == null) continue;
            if (DeliveryTransitions.isTerminal(d.getDeliveryStatus())) continue;
            String prev = d.getDeliveryPerson() != null ? d.getDeliveryPerson().getName() : null;
            d.setDeliveryPerson(person);
            d.setUpdatedAt(now);
            deliveryRepo.save(d);
            String note = prev == null ? "Assigned to " + person.getName()
                    : "Reassigned: " + prev + " → " + person.getName();
            recordHistory(d, d.getDeliveryStatus(), DeliveryHistoryEventType.ASSIGNMENT, note, who);
            changed++;
        }
        if (changed > 0) {
            notificationService.sendNotification(
                    "DELIVERY", "Deliveries assigned",
                    changed + " deliveries assigned to " + person.getName(),
                    ownerRecipient(), null, "INFO");
        }
        return changed;
    }

    // ── POD file uploads ──────────────────────────────────────────────

    /**
     * Persist a public-facing URL onto the delivery's POD signature slot.
     * The caller (controller) is responsible for uploading via the shared
     * {@code FileStorageService} and passing the returned path in.
     */
    @Transactional
    public DeliveryDTO setPodSignatureUrl(Long deliveryId, String url) {
        Delivery d = requireDelivery(deliveryId);
        d.setPodSignatureUrl(url);
        d.setUpdatedAt(LocalDateTime.now());
        return deliveryMapper.toDto(deliveryRepo.save(d));
    }

    @Transactional
    public DeliveryDTO setPodPhotoUrl(Long deliveryId, String url) {
        Delivery d = requireDelivery(deliveryId);
        d.setPodPhotoUrl(url);
        d.setUpdatedAt(LocalDateTime.now());
        return deliveryMapper.toDto(deliveryRepo.save(d));
    }

    // ── Notification helpers ──────────────────────────────────────────

    /**
     * Notify only on interesting transitions — created, out-for-delivery,
     * delivered, cancelled. Intermediate hops (PACKED, IN_TRANSIT) are
     * usually noise for shop owners.
     */
    private void notifyStatusChange(Delivery d, DeliveryStatus from, DeliveryStatus to, String note) {
        String title;
        String priority;
        switch (to) {
            case OUT_FOR_DELIVERY -> { title = "Out for delivery"; priority = "INFO"; }
            case DELIVERED        -> { title = "Delivery completed"; priority = "INFO"; }
            case CANCELLED        -> { title = "Delivery cancelled"; priority = "HIGH"; }
            default -> { return; }  // skip PACKED / IN_TRANSIT — too chatty
        }
        StringBuilder msg = new StringBuilder(title)
                .append(" for order #").append(d.getInvoiceNumber())
                .append(" (").append(d.getCustomerName()).append(")");
        if (note != null && !note.isBlank()) msg.append(" — ").append(note);
        notifyDeliveryEvent(d, title, msg.toString(), priority);
    }

    private void notifyDeliveryEvent(Delivery d, String title, String message, String priority) {
        try {
            notificationService.sendNotification(
                    "DELIVERY", title, message,
                    ownerRecipient(),
                    "/deliveries",  // deep-link into the delivery page
                    priority);
        } catch (Exception e) {
            // Notification is a side effect — never let it fail the primary op.
            logger.warn("Failed to send delivery notification for delivery {}: {}",
                    d.getId(), e.getMessage());
        }
    }

    /**
     * Recipient for delivery-lifecycle notifications. Falls back to the current
     * authenticated user; a real deployment would resolve the shop owner from
     * TenantContext.
     */
    private static String ownerRecipient() {
        return currentUsername();
    }

    private static final class PersonAccum {
        final Long personId;
        final String personName;
        long delivered;
        long inProgress;
        long onTime;
        long deliveredWithEta;
        double totalLeadHours;
        long deliveredWithLead;

        PersonAccum(Long personId, String personName) {
            this.personId = personId;
            this.personName = personName;
        }
    }
}
