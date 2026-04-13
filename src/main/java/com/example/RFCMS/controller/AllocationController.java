package com.example.RFCMS.controller;

import com.example.RFCMS.service.AllocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/allocation")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    /**
     * Trigger end-of-day allocation run manually (useful for Postman testing).
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run() {
        int allocated = allocationService.runEndOfDayAllocation();
        return ResponseEntity.ok(Map.of("allocatedCount", allocated));
    }
}

