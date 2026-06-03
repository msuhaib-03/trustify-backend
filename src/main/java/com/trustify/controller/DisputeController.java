package com.trustify.controller;

import com.trustify.model.Dispute;
import com.trustify.repository.DisputeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/disputes")
@RequiredArgsConstructor
public class DisputeController {
    private final DisputeRepository disputeRepository;

    // ── GET /disputes — list all disputes (user-facing; admin uses /admin/disputes) ──
    @GetMapping
    public ResponseEntity<?> getAllDisputes(
            @RequestParam(required = false) String status) {
        List<Dispute> disputes = (status != null && !status.isBlank())
                ? disputeRepository.findByStatus(status)
                : disputeRepository.findAll();
        return ResponseEntity.ok(disputes);
    }

    // ── GET /disputes/stats ──────────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        long open     = disputeRepository.findByStatus("OPEN").size();
        long resolved = disputeRepository.findByStatus("RESOLVED").size();
        return ResponseEntity.ok(Map.of(
                "openDisputes",     open,
                "resolvedDisputes", resolved,
                "totalDisputes",    open + resolved
        ));
    }

    // ── GET /disputes/{id} ───────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        return disputeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── GET /disputes/transaction/{transactionId} ────────────────────────────────
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<?> getByTransaction(@PathVariable String transactionId) {
        return disputeRepository.findByTransactionId(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
