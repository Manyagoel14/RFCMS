package com.example.RFCMS.controller;

import com.example.RFCMS.service.WagonStatusAdvanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/wagon-status")
public class WagonStatusAdvanceController {

    private final WagonStatusAdvanceService advanceService;

    public WagonStatusAdvanceController(WagonStatusAdvanceService advanceService) {
        this.advanceService = advanceService;
    }

    @PostMapping("/advance")
    public ResponseEntity<Map<String, Object>> advanceOne(@RequestParam String wagonid) {
        boolean created = advanceService.advanceOne(wagonid);
        return ResponseEntity.ok(Map.of("wagonid", wagonid, "createdNextLeg", created));
    }

    @PostMapping("/advance-all")
    public ResponseEntity<Map<String, Object>> advanceAll() {
        int created = advanceService.advanceAllArrived();
        return ResponseEntity.ok(Map.of("createdNextLegCount", created));
    }
}

