package com.example.RFCMS.service;

import com.example.RFCMS.models.Consignment;
import com.example.RFCMS.repository.ConsignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AllocationService {

    private final ConsignmentRepository consignmentRepository;
    private final WagonService wagonService;
    private final Movementservice movementService;
    private final RailwayDijkstra railwayDijkstra;

    public AllocationService(ConsignmentRepository consignmentRepository,
                             WagonService wagonService,
                             Movementservice movementService,
                             RailwayDijkstra railwayDijkstra) {
        this.consignmentRepository = consignmentRepository;
        this.wagonService = wagonService;
        this.movementService = movementService;
        this.railwayDijkstra = railwayDijkstra;
    }

    @Transactional
    public int runEndOfDayAllocation() {
        List<Consignment> pending = consignmentRepository.findByAllocationStatus("PENDING");
        pending.sort(Comparator
                .comparingInt(this::priorityRank).reversed()
                .thenComparing(c -> c.getBookingTimestamp() == null ? LocalDateTime.MIN : c.getBookingTimestamp())
        );
        int allocated = 0;
        Map<String, List<String>> pathCache = new HashMap<>();

        for (Consignment c : pending) {
            // allocate wagon
            var wagon = wagonService.allocateForConsignment(c);
            if (wagon == null) {
                // No suitable existing wagon available; keep it pending.
                continue;
            }
            // store business wagon id (matches wagon_status.wagonid)
            c.setWagonId(wagon.getWagonId() != null ? wagon.getWagonId() : wagon.getId());
            c.setAllocationStatus("ASSIGNED");
            consignmentRepository.save(c);

            // create route + movement schedule for tracking
            String pathKey = cacheKey(c.getSource(), c.getDestination());
            List<String> route = pathCache.computeIfAbsent(pathKey, k ->
                    railwayDijkstra.shortestPath(c.getSource(), c.getDestination()));
            LocalDateTime dep = wagonService.bestDepartureTime(wagon).orElse(LocalDateTime.now(ZoneOffset.UTC));
            movementService.createSchedule(c.getConsignmentNumber(), route, dep, 80, 200);

            allocated++;
        }

        return allocated;
    }

    private int priorityRank(Consignment c) {
        if (c == null || c.getPriority() == null) return 0;
        return switch (c.getPriority().toUpperCase()) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static String cacheKey(String a, String b) {
        if (a == null || b == null) return "\0";
        return a.trim().toLowerCase() + "\0" + b.trim().toLowerCase();
    }
}

