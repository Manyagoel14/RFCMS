package com.example.RFCMS.controller;

import com.example.RFCMS.service.WagonStatusEtaUpdaterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/wagon-status")
public class WagonStatusEtaUpdaterController {

    private final WagonStatusEtaUpdaterService updaterService;

    public WagonStatusEtaUpdaterController(WagonStatusEtaUpdaterService updaterService) {
        this.updaterService = updaterService;
    }

    /**
     * Recompute ETA for all wagon_status records using:
     * - departureTime + speed (from wagon_eta docs)
     * - shortest distance from `graph` (Dijkstra)
     *
     * Reads/writes `wagon_status`:
     * - eta
     * - distance_km
     * - eta_calculated_at
     */
    @PostMapping("/recompute-eta")
    public ResponseEntity<WagonStatusEtaUpdaterService.Result> recompute(
            @RequestParam(defaultValue = "false") boolean overwriteExisting
    ) {
        return ResponseEntity.ok(updaterService.recomputeEtaForAll(overwriteExisting));
    }
}

