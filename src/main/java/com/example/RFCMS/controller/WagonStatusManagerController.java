package com.example.RFCMS.controller;

import com.example.RFCMS.service.WagonStatusManagerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/station/wagon-status")
public class WagonStatusManagerController {

    private final WagonStatusManagerService service;

    public WagonStatusManagerController(WagonStatusManagerService service) {
        this.service = service;
    }

    @PostMapping("/arrive")
    public ResponseEntity<Map<String, Object>> arrive(@RequestParam String wagonstatId) {
        return ResponseEntity.ok(service.markArrived(wagonstatId));
    }

    @PostMapping("/depart")
    public ResponseEntity<Map<String, Object>> depart(@RequestParam String wagonstatId) {
        return ResponseEntity.ok(service.markDeparted(wagonstatId));
    }

    @PostMapping("/backfill-ids")
    public ResponseEntity<Map<String, Object>> backfillIds() {
        int updated = service.backfillWagonStatIds();
        return ResponseEntity.ok(Map.of("updatedCount", updated));
    }
}

