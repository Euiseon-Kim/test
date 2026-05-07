package com.example.ozr.service;

import com.example.ozr.dto.AuditLogDto;
import com.example.ozr.model.AuditLog;
import com.example.ozr.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditLog save(AuditLog auditLog) {
        return auditLogRepository.save(auditLog);
    }

    public Page<AuditLogDto> findAll(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(AuditLogDto::from);
    }

    public Page<AuditLogDto> findByRequesterId(String requesterId, Pageable pageable) {
        return auditLogRepository.findByRequesterIdOrderByTimestampDesc(requesterId, pageable)
                .map(AuditLogDto::from);
    }
}
