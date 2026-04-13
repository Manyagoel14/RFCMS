package com.example.RFCMS.controller;

import com.example.RFCMS.service.WagonRouteBackfillService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/wagons")
public class WagonRouteBackfillController {

    private final WagonRouteBackfillService backfillService;

    public WagonRouteBackfillController(WagonRouteBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    /**
     * Computes and stores `route` for each wagon using RailwayDijkstra + `graph` collection.
     *
     * Uses wagon fields:
     * - source: `source` (fallback: `currentStation`)
     * - destination: `destination`
     */
    @PostMapping("/backfill-route")
    public ResponseEntity<WagonRouteBackfillService.Result> backfill(
            @RequestParam(defaultValue = "false") boolean overwriteExisting
    ) {
        return ResponseEntity.ok(backfillService.backfillRoutes(overwriteExisting));
    }
}

