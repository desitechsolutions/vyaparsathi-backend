package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.customer.dto.CustomerNoteDto;
import com.desitech.vyaparsathi.customer.entity.CustomerNote;
import com.desitech.vyaparsathi.customer.mapper.CustomerNoteMapper;
import com.desitech.vyaparsathi.customer.repository.CustomerNoteRepository;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User-written notes on a customer. Distinct from
 * {@link CustomerAuditService} — audit is for system-generated events,
 * this is for human intent. Author identity is captured from the
 * SecurityContext at create time and denormalised onto the row so
 * the note stays readable even if the author's account is later
 * deactivated.
 */
@Service
public class CustomerNoteService {

    private final CustomerNoteRepository repo;
    private final CustomerRepository customerRepo;
    private final CustomerNoteMapper mapper;
    private final UserRepository userRepo;

    public CustomerNoteService(CustomerNoteRepository repo,
                               CustomerRepository customerRepo,
                               CustomerNoteMapper mapper,
                               UserRepository userRepo) {
        this.repo = repo;
        this.customerRepo = customerRepo;
        this.mapper = mapper;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public List<CustomerNoteDto> listByCustomer(Long customerId) {
        ensureCustomerExists(customerId);
        return mapper.toDtoList(repo.findByCustomerIdOrderByPinnedDescCreatedAtDesc(customerId));
    }

    @Transactional
    public CustomerNoteDto create(Long customerId, CustomerNoteDto dto) {
        ensureCustomerExists(customerId);
        CustomerNote entity = mapper.toEntity(dto);
        entity.setId(null);
        entity.setCustomerId(customerId);
        // Stamp author from the security context — the note is a human
        // artefact, so knowing who wrote it is a first-class field.
        User author = currentUserOrNull();
        if (author != null) {
            entity.setAuthorUserId(author.getId());
            String displayName = buildDisplayName(author);
            entity.setAuthorName(displayName != null ? displayName : author.getUsername());
        }
        return mapper.toDto(repo.save(entity));
    }

    @Transactional
    public CustomerNoteDto update(Long noteId, CustomerNoteDto dto) {
        CustomerNote existing = repo.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException("Note not found: " + noteId));
        // Only the author (or an ADMIN/OWNER — enforced via permission
        // on the endpoint) may edit. Author-vs-caller check keeps
        // audit trail honest.
        User caller = currentUserOrNull();
        if (caller != null && existing.getAuthorUserId() != null
                && !existing.getAuthorUserId().equals(caller.getId())
                && !isPrivilegedRole(caller)) {
            throw new ApplicationException("Only the author can edit their own note.");
        }
        mapper.updateEntityFromDto(dto, existing);
        return mapper.toDto(repo.save(existing));
    }

    @Transactional
    public void delete(Long noteId) {
        CustomerNote existing = repo.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException("Note not found: " + noteId));
        User caller = currentUserOrNull();
        if (caller != null && existing.getAuthorUserId() != null
                && !existing.getAuthorUserId().equals(caller.getId())
                && !isPrivilegedRole(caller)) {
            throw new ApplicationException("Only the author can delete their own note.");
        }
        repo.delete(existing);
    }

    @Transactional
    public CustomerNoteDto setPinned(Long noteId, boolean pinned) {
        CustomerNote existing = repo.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException("Note not found: " + noteId));
        existing.setPinned(pinned);
        return mapper.toDto(repo.save(existing));
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private void ensureCustomerExists(Long customerId) {
        if (customerId == null) {
            throw new ApplicationException("customerId is required");
        }
        if (!customerRepo.existsById(customerId)) {
            throw new EntityNotFoundException("Customer not found: " + customerId);
        }
    }

    private User currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepo.findByUsername(auth.getName()).orElse(null);
    }

    private String buildDisplayName(User user) {
        String fn = user.getFirstName();
        String ln = user.getLastName();
        if (fn == null && ln == null) return null;
        if (fn == null) return ln;
        if (ln == null) return fn;
        return fn + " " + ln;
    }

    /** OWNER + ADMIN can edit or delete anyone's note. */
    private boolean isPrivilegedRole(User user) {
        if (user == null || user.getRole() == null) return false;
        return user.getRole() == com.desitech.vyaparsathi.auth.model.Role.OWNER
                || user.getRole() == com.desitech.vyaparsathi.auth.model.Role.ADMIN
                || user.getRole() == com.desitech.vyaparsathi.auth.model.Role.SUPER_ADMIN;
    }
}
