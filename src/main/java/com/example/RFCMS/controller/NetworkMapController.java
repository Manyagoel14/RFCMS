package com.example.RFCMS.controller;

import com.example.RFCMS.service.NetworkMapService;
import com.example.RFCMS.service.WagonStatusLookupService;
import com.example.RFCMS.service.TrackingService;
import com.example.RFCMS.repository.ConsignmentRepository;
import com.example.RFCMS.repository.MovementRepository;
import com.example.RFCMS.repository.WagonRepository;
import com.example.RFCMS.models.Wagon;
import com.example.RFCMS.models.Movement;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/map")
public class NetworkMapController {

    private final NetworkMapService networkMapService;
    private final TrackingService trackingService;
    private final ConsignmentRepository consignmentRepository;
    private final MovementRepository movementRepository;
    private final WagonStatusLookupService wagonStatusLookupService;
    private final WagonRepository wagonRepository;

    public NetworkMapController(NetworkMapService networkMapService,
                                TrackingService trackingService,
                                ConsignmentRepository consignmentRepository,
                                MovementRepository movementRepository,
                                WagonStatusLookupService wagonStatusLookupService,
                                WagonRepository wagonRepository) {
        this.networkMapService = networkMapService;
        this.trackingService = trackingService;
        this.consignmentRepository = consignmentRepository;
        this.movementRepository = movementRepository;
        this.wagonStatusLookupService = wagonStatusLookupService;
        this.wagonRepository = wagonRepository;
    }

    @GetMapping(value = "/network", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> network() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(networkMapService.buildNetworkHtml());
    }

    @GetMapping(value = "/track/{cn}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> track(@PathVariable String cn) {
        Map<String, Object> pos = trackingService.getCurrentPosition(cn);
        String status = pos.get("status") != null ? pos.get("status").toString() : "UNKNOWN";

        String wagonBusinessId = consignmentRepository.findByConsignmentNumber(cn)
                .map(c -> c.getWagonId())
                .orElse(null);

        // If consignment stored Mongo _id of wagon, resolve to business wagonid (e.g., W001)
        wagonBusinessId = resolveToBusinessWagonId(wagonBusinessId);

        WagonStatusLookupService.WagonStatus ws = wagonStatusLookupService.getCurrentLegByWagonId(wagonBusinessId);
        // For animation: prefer wagon_status segment (current->next OR source->destination),
        // else wagon_status.route (adjacent list), else fall back to movement schedule.
        List<String> routeStations = new ArrayList<>();
        if (ws != null && ws.currentStation() != null && ws.nextStation() != null
                && !ws.currentStation().isBlank() && !ws.nextStation().isBlank()) {
            routeStations.add(ws.currentStation());
            routeStations.add(ws.nextStation());
        } else if (ws != null && ws.source() != null && ws.destination() != null
                && !ws.source().isBlank() && !ws.destination().isBlank()) {
            // Your wagon_status schema (screenshot) uses source/destination as the active segment.
            routeStations.add(ws.source());
            routeStations.add(ws.destination());
        } else if (ws != null && ws.route() != null && ws.route().size() >= 2) {
            // Assume wagon_status.route is adjacent stations sequence (what you described)
            routeStations = ws.route();
        } else {
            List<Movement> movements = movementRepository.findByCnOrderByPlannedArrivalAsc(cn);
            if (movements != null && !movements.isEmpty()) {
                for (Movement m : movements) routeStations.add(m.getStation());
            } else {
                String src = consignmentRepository.findByConsignmentNumber(cn).map(c -> c.getSource()).orElse(null);
                if (src != null) routeStations.add(src);
            }
        }

        // If we still don't have a station, return 404
        if (routeStations.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    "<html><body><h3>No route found for CN " + cn + "</h3>" +
                            "<p>Make sure the consignment exists and stations are present in <code>lat_long</code>.</p>" +
                            "</body></html>"
            );
        }

        Double speed = ws != null ? ws.speedKmph() : null;
        // Move only after station manager sets actual_departure_time (so departureTime is non-null).
        String depIso = (ws != null && ws.departureTime() != null)
                ? ws.departureTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(networkMapService.buildTrackingHtmlDynamic(
                        cn,
                        status + (ws != null && ws.wagonstatId() != null ? (" (ws:" + ws.wagonstatId() + ")") : ""),
                        routeStations,
                        wagonBusinessId,
                        speed,
                        depIso
                ));
    }

    /**
     * Track directly by wagonstat_ID (recommended after auto-advance on arrival).
     */
    @GetMapping(value = "/track-ws/{wagonstatId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> trackByWagonStat(@PathVariable String wagonstatId) {
        WagonStatusLookupService.WagonStatus ws = wagonStatusLookupService.getByWagonStatId(wagonstatId);
        if (ws == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .cacheControl(CacheControl.noStore())
                    .body("<html><body><h3>wagonstat_ID not found: " + wagonstatId + "</h3></body></html>");
        }

        List<String> routeStations = new ArrayList<>();
        if (ws.currentStation() != null && ws.nextStation() != null
                && !ws.currentStation().isBlank() && !ws.nextStation().isBlank()) {
            routeStations.add(ws.currentStation());
            routeStations.add(ws.nextStation());
        } else if (ws.source() != null && ws.destination() != null
                && !ws.source().isBlank() && !ws.destination().isBlank()) {
            routeStations.add(ws.source());
            routeStations.add(ws.destination());
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .cacheControl(CacheControl.noStore())
                    .body("<html><body><h3>No source/destination on wagonstat_ID: " + wagonstatId + "</h3></body></html>");
        }

        String depIso = (ws.departureTime() != null)
                ? ws.departureTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(networkMapService.buildTrackingHtmlDynamic(
                        "wagonstat:" + wagonstatId,
                        "WAGON_STATUS",
                        routeStations,
                        ws.wagonId(),
                        ws.speedKmph(),
                        depIso
                ));
    }

    private String resolveToBusinessWagonId(String wagonIdMaybe) {
        if (wagonIdMaybe == null || wagonIdMaybe.isBlank()) return wagonIdMaybe;
        // Heuristic: Mongo ObjectId is 24 hex chars; business id looks like W001.
        boolean looksLikeObjectId = wagonIdMaybe.matches("^[a-fA-F0-9]{24}$");
        if (!looksLikeObjectId) return wagonIdMaybe;

        return wagonRepository.findById(wagonIdMaybe)
                .map(Wagon::getWagonId)
                .filter(id -> id != null && !id.isBlank())
                .orElse(wagonIdMaybe);
    }
}

