package com.desitech.vyaparsathi.delivery.service;

import com.desitech.vyaparsathi.delivery.dto.DeliveryDTO;
import com.desitech.vyaparsathi.delivery.dto.DeliveryPersonDTO;
import com.desitech.vyaparsathi.delivery.dto.DeliveryStatusHistoryDTO;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import com.desitech.vyaparsathi.delivery.entity.DeliveryPerson;
import com.desitech.vyaparsathi.delivery.entity.DeliveryStatusHistory;
import com.desitech.vyaparsathi.delivery.enums.DeliveryStatus;
import com.desitech.vyaparsathi.delivery.mapper.DeliveryMapper;
import com.desitech.vyaparsathi.delivery.mapper.DeliveryPersonMapper;
import com.desitech.vyaparsathi.delivery.mapper.DeliveryStatusHistoryMapper;
import com.desitech.vyaparsathi.delivery.repository.DeliveryPersonRepository;
import com.desitech.vyaparsathi.delivery.repository.DeliveryRepository;
import com.desitech.vyaparsathi.delivery.repository.DeliveryStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DeliveryService {
    private final DeliveryRepository deliveryRepo;
    private final DeliveryPersonRepository personRepo;
    private final DeliveryStatusHistoryRepository statusHistoryRepo;
    private final DeliveryMapper deliveryMapper;
    private final DeliveryPersonMapper deliveryPersonMapper;
    private final DeliveryStatusHistoryMapper deliveryStatusHistoryMapper;

    public DeliveryService(
            DeliveryRepository deliveryRepo,
            DeliveryPersonRepository personRepo,
            DeliveryStatusHistoryRepository statusHistoryRepo,
            DeliveryMapper deliveryMapper,
            DeliveryPersonMapper deliveryPersonMapper,
            DeliveryStatusHistoryMapper deliveryStatusHistoryMapper
    ) {
        this.deliveryRepo = deliveryRepo;
        this.personRepo = personRepo;
        this.statusHistoryRepo = statusHistoryRepo;
        this.deliveryMapper = deliveryMapper;
        this.deliveryPersonMapper = deliveryPersonMapper;
        this.deliveryStatusHistoryMapper = deliveryStatusHistoryMapper;
    }

    public DeliveryDTO createDelivery(DeliveryDTO deliveryDTO) {
        Delivery delivery = deliveryMapper.toEntity(deliveryDTO);
        delivery.setCreatedAt(LocalDateTime.now());
        delivery.setUpdatedAt(LocalDateTime.now());
        Delivery saved = deliveryRepo.save(delivery);
        addStatusHistory(saved, saved.getDeliveryStatus(), "system");
        return deliveryMapper.toDto(saved);
    }

    public Optional<DeliveryDTO> getDelivery(Long id) {
        return deliveryRepo.findById(id).map(deliveryMapper::toDto);
    }

    public List<DeliveryDTO> getAllDeliveries() {
        return deliveryRepo.findAll().stream()
                .map(deliveryMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<DeliveryDTO> getDeliveriesBySaleId(Long saleId) {
        return deliveryRepo.findBySaleId(saleId).stream()
                .map(deliveryMapper::toDto)
                .collect(Collectors.toList());
    }

    /** Update only address, charge, paidBy, notes */
    @Transactional
    public DeliveryDTO updateDeliveryDetails(Long id, DeliveryDTO updatedDTO) {
        return deliveryRepo.findById(id)
                .map(existing -> {
                    if(updatedDTO.getDeliveryAddress() != null)
                        existing.setDeliveryAddress(updatedDTO.getDeliveryAddress());
                    if(updatedDTO.getDeliveryCharge() != null)
                        existing.setDeliveryCharge(updatedDTO.getDeliveryCharge());
                    if(updatedDTO.getDeliveryPaidBy() != null)
                        existing.setDeliveryPaidBy(updatedDTO.getDeliveryPaidBy());
                    if(updatedDTO.getDeliveryNotes() != null)
                        existing.setDeliveryNotes(updatedDTO.getDeliveryNotes());
                    existing.setUpdatedAt(LocalDateTime.now());
                    Delivery saved = deliveryRepo.save(existing);
                    return deliveryMapper.toDto(saved);
                })
                .orElseThrow(() -> new RuntimeException("Delivery not found"));
    }

    /** Assign or change delivery person */
    @Transactional
    public DeliveryDTO assignDeliveryPerson(Long deliveryId, DeliveryPersonDTO personDto) {
        Delivery delivery = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));
        DeliveryPerson person;
        if (personDto.getId() != null) {
            person = personRepo.findById(personDto.getId())
                    .orElseThrow(() -> new RuntimeException("Delivery person not found"));
        } else {
            // Create ad-hoc person if not exists
            person = personRepo.save(deliveryPersonMapper.toEntity(personDto));
        }
        delivery.setDeliveryPerson(person);
        delivery.setUpdatedAt(LocalDateTime.now());
        Delivery saved = deliveryRepo.save(delivery);
        return deliveryMapper.toDto(saved);
    }

    /** Update only status */
    @Transactional
    public DeliveryDTO updateStatus(Long deliveryId, DeliveryStatus newStatus, String changedBy) {
        Delivery delivery = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));
        delivery.setDeliveryStatus(newStatus);
        if (newStatus == DeliveryStatus.DELIVERED) {
            delivery.setDeliveredAt(LocalDateTime.now());
        }
        delivery.setUpdatedAt(LocalDateTime.now());
        Delivery saved = deliveryRepo.save(delivery);
        addStatusHistory(saved, newStatus, changedBy);
        return deliveryMapper.toDto(saved);
    }

    public void addStatusHistory(Delivery delivery, DeliveryStatus status, String changedBy) {
        DeliveryStatusHistory history = new DeliveryStatusHistory();
        history.setDelivery(delivery);
        history.setStatus(status);
        history.setChangedAt(LocalDateTime.now());
        history.setChangedBy(changedBy);
        statusHistoryRepo.save(history);
    }

    public List<DeliveryStatusHistoryDTO> getStatusHistory(Long deliveryId) {
        return statusHistoryRepo.findByDelivery_Id(deliveryId).stream()
                .map(deliveryStatusHistoryMapper::toDto)
                .collect(Collectors.toList());
    }

    public void deleteDelivery(Long id) {
        deliveryRepo.deleteById(id);
    }

    // --- Delivery Person CRUD ---

    public DeliveryPersonDTO createPerson(DeliveryPersonDTO dpDTO) {
        DeliveryPerson entity = deliveryPersonMapper.toEntity(dpDTO);
        return deliveryPersonMapper.toDto(personRepo.save(entity));
    }

    public List<DeliveryPersonDTO> listPersons() {
        return personRepo.findAll().stream()
                .map(deliveryPersonMapper::toDto)
                .collect(Collectors.toList());
    }

    public Optional<DeliveryPersonDTO> getPerson(Long id) {
        return personRepo.findById(id).map(deliveryPersonMapper::toDto);
    }

    public void deletePerson(Long id) {
        personRepo.deleteById(id);
    }
}