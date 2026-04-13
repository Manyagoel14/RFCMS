package com.example.RFCMS.service;

import com.example.RFCMS.models.Consignment;
import com.example.RFCMS.models.Movement;
import com.example.RFCMS.repository.ConsignmentRepository;
import com.example.RFCMS.repository.MovementRepository;
import com.example.RFCMS.repository.WagonRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrackingService {

    @Autowired
    private ConsignmentRepository consignmentRepo;

    @Autowired
    private MovementRepository movementRepo;

    @Autowired
    private WagonStatusLookupService wagonStatusLookupService;

    @Autowired
    private WagonRepository wagonRepository;

    // 🔹 Get full route using CN
    public List<Movement> getRouteByCN(String cn) {
        List<Movement> route = movementRepo.findByCnOrderByPlannedArrivalAsc(cn);
        if (!route.isEmpty()) return route;

        // If allocated and wagon_status exists, expose current leg in route API
        var consignmentOpt = consignmentRepo.findByConsignmentNumber(cn);
        if (consignmentOpt.isPresent()) {
            Consignment c = consignmentOpt.get();
            if (c.getWagonId() != null && !c.getWagonId().isBlank()) {
                String businessWagonId = resolveToBusinessWagonId(c.getWagonId());
                WagonStatusLookupService.WagonStatus ws = wagonStatusLookupService.getCurrentLegByWagonId(businessWagonId);
                if (ws != null && ws.source() != null && ws.destination() != null) {
                    Movement src = new Movement();
                    src.setCn(cn);
                    src.setStation(ws.source());
                    src.setPlannedDeparture(ws.departureTime());
                    src.setStatus("IN_PROCESS");

                    Movement dst = new Movement();
                    dst.setCn(cn);
                    dst.setStation(ws.destination());
                    dst.setPlannedArrival(null);
                    dst.setStatus("PENDING");

                    return List.of(src, dst);
                }
            }
        }

        // If not allocated/scheduled yet, return a placeholder at source station
        return consignmentOpt
                // treat null allocationStatus as still pending (older data)
                .filter(c -> c.getAllocationStatus() == null || "PENDING".equalsIgnoreCase(c.getAllocationStatus()))
                .map(c -> {
                    Movement m = new Movement();
                    m.setCn(cn);
                    m.setStation(c.getSource());
                    m.setPlannedDeparture(c.getBookingTimestamp());
                    m.setStatus("IN_PROCESS");
                    return List.of(m);
                })
                .orElseGet(ArrayList::new);
    }

    public Map<String, Object> getActiveWagonStatByCN(String cn) {
        Map<String, Object> response = new HashMap<>();
        response.put("cn", cn);

        Consignment c = consignmentRepo.findByConsignmentNumber(cn).orElse(null);
        if (c == null) {
            response.put("found", false);
            response.put("reason", "CN_NOT_FOUND");
            return response;
        }

        String wagonId = resolveToBusinessWagonId(c.getWagonId());
        WagonStatusLookupService.WagonStatus ws = wagonStatusLookupService.getCurrentLegByWagonId(wagonId);
        if (ws == null) {
            response.put("found", false);
            response.put("wagonId", wagonId);
            response.put("reason", "NO_WAGON_STATUS");
            return response;
        }

        response.put("found", true);
        response.put("wagonId", ws.wagonId());
        response.put("wagonstatId", ws.wagonstatId());
        response.put("source", ws.source());
        response.put("destination", ws.destination());
        response.put("actualDepartureTime", ws.departureTime());
        response.put("speedKmph", ws.speedKmph());
        return response;
    }

    private String resolveToBusinessWagonId(String wagonIdOrMongoId) {
        if (wagonIdOrMongoId == null || wagonIdOrMongoId.isBlank()) return wagonIdOrMongoId;
        if (wagonIdOrMongoId.toUpperCase().startsWith("W")) return wagonIdOrMongoId;
        var w = wagonRepository.findById(wagonIdOrMongoId).orElse(null);
        if (w == null) return wagonIdOrMongoId;
        return (w.getWagonId() != null && !w.getWagonId().isBlank()) ? w.getWagonId() : wagonIdOrMongoId;
    }

    // 🔹 Calculate ETA
    public LocalDateTime calculateETA(String cn) {
        List<Movement> route = getRouteByCN(cn);
        if (route.isEmpty()) return null;
        if (route.size() == 1 && "IN_PROCESS".equalsIgnoreCase(route.get(0).getStatus())) return null;

        for (Movement m : route) {
            if (m.getActualArrival() == null) {
                return m.getPlannedArrival(); // next expected
            }
        }
        return route.get(route.size() - 1).getActualArrival();
    }

    // 🔹 Current Position Logic
    public Map<String, Object> getCurrentPosition(String cn) {
        List<Movement> route = getRouteByCN(cn);

        Map<String, Object> response = new HashMap<>();

        if (route.isEmpty()) {
            // Unknown CN or no data yet: keep consistent "in process" response
            response.put("status", "IN_PROCESS");
            response.put("lastStation", null);
            response.put("progress", 0.0);
            return response;
        }

        // Not allocated yet → show at source station (map/API: single placeholder movement)
        if (route.size() == 1 && "IN_PROCESS".equalsIgnoreCase(route.get(0).getStatus())) {
            var c = consignmentRepo.findByConsignmentNumber(cn).orElse(null);
            boolean pending = c != null && (c.getAllocationStatus() == null
                    || "PENDING".equalsIgnoreCase(c.getAllocationStatus()));
            response.put("status", pending ? "AWAITING_WAGON_ASSIGNMENT" : "IN_PROCESS");
            response.put("lastStation", route.get(0).getStation());
            response.put("progress", 0.0);
            return response;
        }

        // Allocated/scheduled but not departed anywhere yet → still at source
        boolean anyDeparted = route.stream().anyMatch(m -> m.getActualDeparture() != null);
        if (!anyDeparted) {
            String src = route.get(0).getStation();
            response.put("status", "IN_PROCESS");
            response.put("lastStation", src);
            response.put("progress", 0.0);
            return response;
        }

        for (int i = 0; i < route.size(); i++) {
            Movement curr = route.get(i);

            if (curr.getActualDeparture() != null &&
                (i + 1 < route.size() && route.get(i + 1).getActualArrival() == null)) {

                Movement next = route.get(i + 1);

                // progress calculation
                long total = Duration.between(
                        curr.getPlannedDeparture(),
                        next.getPlannedArrival()).toMinutes();

                long done = Duration.between(
                        curr.getActualDeparture(),
                        LocalDateTime.now(ZoneOffset.UTC)).toMinutes();

                double progress = (double) done / total;

                response.put("status", "IN_TRANSIT");
                response.put("lastStation", curr.getStation());
                response.put("nextStation", next.getStation());
                response.put("progress", Math.min(progress, 1.0));

                return response;
            }
        }

        // If we couldn't find an in-transit leg but we do have departures,
        // treat as arrived only if the final station has actualArrival.
        Movement last = route.get(route.size() - 1);
        if (last.getActualArrival() != null) {
            response.put("status", "ARRIVED");
            response.put("lastStation", last.getStation());
            response.put("nextStation", last.getStation());
            response.put("progress", 1.0);
            return response;
        }

        // fallback: still processing
        response.put("status", "IN_PROCESS");
        return response;
    }

    // 🔹 Delay Calculation
    public Map<String, Long> getDelay(String cn) {
        List<Movement> route = getRouteByCN(cn);

        if (route.size() == 1 && "IN_PROCESS".equalsIgnoreCase(route.get(0).getStatus())) {
            Map<String, Long> delay = new HashMap<>();
            delay.put("departureDelay", 0L);
            delay.put("arrivalDelay", 0L);
            return delay;
        }

        long depDelay = 0;
        long arrDelay = 0;

        for (Movement m : route) {
            if (m.getActualDeparture() != null) {
                depDelay += Duration.between(
                        m.getPlannedDeparture(),
                        m.getActualDeparture()).toMinutes();
            }

            if (m.getActualArrival() != null) {
                arrDelay += Duration.between(
                        m.getPlannedArrival(),
                        m.getActualArrival()).toMinutes();
            }
        }

        Map<String, Long> delay = new HashMap<>();
        delay.put("departureDelay", depDelay);
        delay.put("arrivalDelay", arrDelay);

        return delay;
    }

    // 🔹 Update Arrival
    public void updateArrival(String movementId) {
        Movement m = movementRepo.findById(movementId).orElseThrow();
        m.setActualArrival(LocalDateTime.now(ZoneOffset.UTC));
        m.setStatus("ARRIVED");
        movementRepo.save(m);
    }

    // 🔹 Update Departure
    public void updateDeparture(String movementId) {
        Movement m = movementRepo.findById(movementId).orElseThrow();
        m.setActualDeparture(LocalDateTime.now(ZoneOffset.UTC));
        m.setStatus("DEPARTED");
        movementRepo.save(m);
    }
}