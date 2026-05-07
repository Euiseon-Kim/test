package com.example.ozr.controller;

import com.example.ozr.dto.AuditLogDto;
import com.example.ozr.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<Page<AuditLogDto>> listAuditLogs(
            @RequestParam(required = false) String requesterId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable) {

        if (requesterId != null && !requesterId.isBlank()) {
            return ResponseEntity.ok(auditService.findByRequesterId(requesterId, pageable));
        }
        return ResponseEntity.ok(auditService.findAll(pageable));
    }
}
