package com.example.RFCMS.service;

import com.example.RFCMS.models.Consignment;
import com.example.RFCMS.models.Wagon;
import com.example.RFCMS.repository.WagonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class WagonService {

    private final WagonRepository wagonRepository;

    public WagonService(WagonRepository wagonRepository) {
        this.wagonRepository = wagonRepository;
    }

    public Wagon addWagon(Wagon w) {
        if (w.getCapacity() <= 0) {
            w.setCapacity(1000);
        }
        if (w.getRemainingCapacity() <= 0) {
            w.setRemainingCapacity(w.getCapacity());
        }
        if (w.getStatus() == null || w.getStatus().isBlank()) {
            w.setStatus("AVAILABLE");
        }
        return wagonRepository.save(w);
    }

    @Transactional
    public Wagon allocateForConsignment(Consignment c) {
        float weightKg = c.getWeight();
        List<Wagon> available = wagonRepository.findByStatusIgnoreCaseAndRemainingCapacityGreaterThanEqual(
                "available", weightKg);

        // Choose the best matching wagon by:
        // - route contains source and destination in correct order
        // - wagon currentStation is at/before source on that route (if route exists)
        // - earliest departure (from wagon_eta) and minimal segment length (route index distance)
        Wagon best = available.stream()
                .filter(w -> w.getRemainingCapacity() >= c.getWeight())
                .filter(w -> isRouteCompatible(w, c.getSource(), c.getDestination()))
                .min(Comparator
                        .comparing((Wagon w) -> bestDepartureTime(w).orElse(LocalDateTime.MAX))
                        .thenComparingInt(w -> segmentLength(w, c.getSource(), c.getDestination()))
                )
                .orElse(null);

        if (best != null) {
            best.setRemainingCapacity(best.getRemainingCapacity() - c.getWeight());
            if (best.getCurrentStation() == null || best.getCurrentStation().isBlank()) {
                best.setCurrentStation(c.getSource());
            }
            if (best.getRemainingCapacity() <= 0) {
                best.setRemainingCapacity(0);
                best.setStatus("FULL");
                best.setReadyToDispatch(true);
            } else {
                float used = best.getCapacity() - best.getRemainingCapacity();
                float dispatchThreshold = Math.max(1.0f, (float) (best.getCapacity() * 0.8));
                best.setReadyToDispatch(used >= dispatchThreshold);
            }
            return wagonRepository.save(best);
        }
        // As requested: DO NOT create new wagons during allocation.
        return null;
    }

    public Optional<LocalDateTime> bestDepartureTime(Wagon w) {
        // Use estimated departure time from wagon record (your schema: departure_time)
        return Optional.ofNullable(w.getDepartureTime());
    }

    private boolean isRouteCompatible(Wagon w, String src, String dst) {
        if (src == null || dst == null) return false;
        List<String> route = w.getRoute();
        if (route == null || route.isEmpty()) {
            // fallback to direct source/destination match if route missing
            if (w.getSource() == null || w.getDestination() == null) return false;
            return w.getSource().equalsIgnoreCase(src) && w.getDestination().equalsIgnoreCase(dst);
        }

        int sIdx = indexOfIgnoreCase(route, src);
        int dIdx = indexOfIgnoreCase(route, dst);
        if (sIdx < 0 || dIdx < 0 || sIdx >= dIdx) return false;

        // If wagon has a known currentStation, ensure it hasn't passed the pickup station
        if (w.getCurrentStation() != null && !w.getCurrentStation().isBlank()) {
            int cIdx = indexOfIgnoreCase(route, w.getCurrentStation());
            if (cIdx >= 0 && cIdx > sIdx) return false;
        }
        return true;
    }

    private int segmentLength(Wagon w, String src, String dst) {
        List<String> route = w.getRoute();
        if (route == null || route.isEmpty()) return Integer.MAX_VALUE;
        int sIdx = indexOfIgnoreCase(route, src);
        int dIdx = indexOfIgnoreCase(route, dst);
        if (sIdx < 0 || dIdx < 0 || sIdx >= dIdx) return Integer.MAX_VALUE;
        return dIdx - sIdx;
    }

    private int indexOfIgnoreCase(List<String> route, String station) {
        for (int i = 0; i < route.size(); i++) {
            if (route.get(i) != null && route.get(i).equalsIgnoreCase(station)) return i;
        }
        return -1;
    }
}
