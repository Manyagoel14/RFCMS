package com.example.RFCMS.controller;

import com.example.RFCMS.service.WagonSeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/seed")
public class WagonSeedController {

    private final WagonSeedService wagonSeedService;

    public WagonSeedController(WagonSeedService wagonSeedService) {
        this.wagonSeedService = wagonSeedService;
    }

    /**
     * Loads sample wagons from classpath (does not overwrite existing wagonid).
     * Default: data/wagons-seed.json
     */
    @PostMapping("/wagons")
    public ResponseEntity<Map<String, Object>> seedWagons(
            @RequestParam(defaultValue = "data/wagons-seed.json") String resource
    ) {
        return ResponseEntity.ok(wagonSeedService.importFromClasspath(resource));
    }
}
