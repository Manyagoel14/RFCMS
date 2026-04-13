package com.example.RFCMS.service;

import com.example.RFCMS.models.Consignment;
import com.example.RFCMS.models.Wagon;
import com.example.RFCMS.repository.ConsignmentRepository;
import com.example.RFCMS.repository.WagonRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WagonStatusManagerService {

    private final MongoTemplate mongoTemplate;
    private final WagonStatusAdvanceService advanceService;
    private final WagonStatusEtaUpdaterService etaUpdaterService;
    private final Movementservice movementService;
    private final ConsignmentRepository consignmentRepository;
    private final WagonRepository wagonRepository;

    public WagonStatusManagerService(MongoTemplate mongoTemplate,
                                     WagonStatusAdvanceService advanceService,
                                     WagonStatusEtaUpdaterService etaUpdaterService,
                                     Movementservice movementService,
                                     ConsignmentRepository consignmentRepository,
                                     WagonRepository wagonRepository) {
        this.mongoTemplate = mongoTemplate;
        this.advanceService = advanceService;
        this.etaUpdaterService = etaUpdaterService;
        this.movementService = movementService;
        this.consignmentRepository = consignmentRepository;
        this.wagonRepository = wagonRepository;
    }

    public String ensureWagonStatId(Document doc) {
        String existing = doc.getString("wagonstat_ID");
        if (existing != null && !existing.isBlank()) return existing;
        String id = "WS-" + UUID.randomUUID();
        Query q = new Query(Criteria.where("_id").is(doc.get("_id")));
        mongoTemplate.updateFirst(q, new Update().set("wagonstat_ID", id), "wagon_status");
        return id;
    }

    public Map<String, Object> markArrived(String wagonstatId) {
        Document st = findByWagonStatId(wagonstatId);
        if (st == null) throw new RuntimeException("wagonstat_ID not found: " + wagonstatId);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime eta = asLocalDateTime(st.get("eta"));

        String status = computeOnTimeOrDelay(eta, now);
        Long delayMin = eta == null ? null : Duration.between(eta, now).toMinutes();

        Query q = new Query(Criteria.where("_id").is(st.get("_id")));
        Update u = new Update()
                .set("actual_arrival_time", now)
                .set("status_at_next_station", status);
        if (delayMin != null) u.set("arrival_delay_minutes", delayMin);

        mongoTemplate.updateFirst(q, u, "wagon_status");
        syncConsignmentMovementsOnArrival(st, now);

        // Auto-create next leg right after arrival is recorded.
        String nextWagonstatId = advanceService.advanceFromWagonStatId(wagonstatId);

        // Also compute ETA for any eta=null record(s) involved (current + next leg).
        etaUpdaterService.recomputeEtaForWagonStatId(wagonstatId, false);
        if (nextWagonstatId != null) {
            etaUpdaterService.recomputeEtaForWagonStatId(nextWagonstatId, false);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("wagonstat_ID", wagonstatId);
        response.put("actual_arrival_time", now);
        response.put("status_at_next_station", status);
        response.put("next_wagonstat_ID", nextWagonstatId);
        return response;
    }

    public Map<String, Object> markDeparted(String wagonstatId) {
        Document st = findByWagonStatId(wagonstatId);
        if (st == null) throw new RuntimeException("wagonstat_ID not found: " + wagonstatId);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Query q = new Query(Criteria.where("_id").is(st.get("_id")));
        mongoTemplate.updateFirst(q, new Update().set("actual_departure_time", now), "wagon_status");
        syncConsignmentMovementsOnDeparture(st, now);
        return Map.of(
                "wagonstat_ID", wagonstatId,
                "actual_departure_time", now
        );
    }

    public int backfillWagonStatIds() {
        var all = mongoTemplate.findAll(Document.class, "wagon_status");
        int updated = 0;
        for (Document d : all) {
            String id = d.getString("wagonstat_ID");
            if (id == null || id.isBlank()) {
                ensureWagonStatId(d);
                updated++;
            }
        }
        return updated;
    }

    private Document findByWagonStatId(String wagonstatId) {
        Query q = new Query(Criteria.where("wagonstat_ID").is(wagonstatId));
        return mongoTemplate.findOne(q, Document.class, "wagon_status");
    }

    private static String computeOnTimeOrDelay(LocalDateTime eta, LocalDateTime actualArrival) {
        if (eta == null || actualArrival == null) return "ON_TIME";
        return actualArrival.isAfter(eta) ? "DELAY" : "ON_TIME";
    }

    private static LocalDateTime asLocalDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime t) return t;
        if (v instanceof Date d) {
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(d.getTime()), ZoneOffset.UTC).toLocalDateTime();
        }
        String s = v.toString();
        try { return OffsetDateTime.parse(s).toLocalDateTime(); } catch (Exception ignored) {}
        try { return ZonedDateTime.parse(s).toLocalDateTime(); } catch (Exception ignored) {}
        try { return LocalDateTime.parse(s); } catch (Exception ignored) {}
        return null;
    }

    private void syncConsignmentMovementsOnArrival(Document wagonStatusDoc, LocalDateTime when) {
        String wagonId = wagonStatusDoc.getString("wagonid");
        // Station the wagon has reached on this leg (wagon's "current" station after arrival).
        String arrivalStation = wagonStatusDoc.getString("destination");
        if (wagonId == null || wagonId.isBlank() || arrivalStation == null || arrivalStation.isBlank()) return;

        // Reflect live wagon location in wagons collection when leg arrival is confirmed.
        Update wagonUpdate = new Update().set("currentStation", arrivalStation);
        mongoTemplate.updateFirst(new Query(Criteria.where("wagonid").is(wagonId)), wagonUpdate, "wagons");
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(wagonId)), wagonUpdate, "wagons");

        List<Consignment> consignments = listConsignmentsOnWagon(wagonId);
        for (Consignment c : consignments) {
            String cn = c.getConsignmentNumber();
            if (cn == null || cn.isBlank()) continue;
            try {
                movementService.recordArrival(cn, arrivalStation, when);
            } catch (Exception ignored) {
                // Some consignments may not have this station in movement schedule.
            }

            if (consignmentDestinationMatchesStation(c.getDestination(), arrivalStation)) {
                completeConsignmentArrivalAtStation(c, wagonId, when);
            }
        }
    }

    /**
     * All consignments tied to this wagon (business wagonid and/or Mongo wagon _id on the consignment).
     */
    private List<Consignment> listConsignmentsOnWagon(String businessWagonId) {
        if (businessWagonId == null || businessWagonId.isBlank()) return List.of();
        List<Consignment> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Consignment c : consignmentRepository.findByWagonId(businessWagonId)) {
            if (seen.add(consignmentKey(c))) {
                out.add(c);
            }
        }
        Wagon w = wagonRepository.findByWagonId(businessWagonId);
        if (w != null && w.getId() != null && !w.getId().isBlank()) {
            for (Consignment c : consignmentRepository.findByWagonId(w.getId())) {
                if (seen.add(consignmentKey(c))) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    private static String consignmentKey(Consignment c) {
        if (c.getId() != null && !c.getId().isBlank()) return c.getId();
        return "cn:" + (c.getConsignmentNumber() == null ? "" : c.getConsignmentNumber());
    }

    private static boolean consignmentDestinationMatchesStation(String consignmentDestination, String arrivalStation) {
        if (consignmentDestination == null || arrivalStation == null) return false;
        return consignmentDestination.trim().equalsIgnoreCase(arrivalStation.trim());
    }

    /** Final delivery: unload from wagon, restore capacity, mark consignment ARRIVED / DELIVERED. */
    private void completeConsignmentArrivalAtStation(Consignment c, String businessWagonId, LocalDateTime when) {
        releaseWagonCapacity(businessWagonId, c.getWeight());
        c.setActualArrivalTimestamp(when);
        c.setStatus("ARRIVED");
        c.setWagonId(null);
        c.setAllocationStatus("DELIVERED");
        consignmentRepository.save(c);
    }

    /** Returns freed weight to the wagon and refreshes status / ready-to-dispatch flags (mirrors allocation rules). */
    private void releaseWagonCapacity(String businessWagonId, float weightKg) {
        if (businessWagonId == null || businessWagonId.isBlank()) return;
        Wagon w = wagonRepository.findByWagonId(businessWagonId);
        if (w == null) return;
        if (weightKg > 0) {
            float cap = w.getCapacity();
            float newRem = w.getRemainingCapacity() + weightKg;
            if (cap > 0) {
                newRem = Math.min(newRem, cap);
            }
            w.setRemainingCapacity(newRem);
        }
        if (w.getStatus() != null && w.getStatus().equalsIgnoreCase("FULL") && w.getRemainingCapacity() > 0) {
            w.setStatus("AVAILABLE");
        }
        float used = w.getCapacity() - w.getRemainingCapacity();
        float dispatchThreshold = Math.max(1.0f, (float) (w.getCapacity() * 0.8));
        w.setReadyToDispatch(w.getCapacity() > 0 && used >= dispatchThreshold);
        wagonRepository.save(w);
    }

    private void syncConsignmentMovementsOnDeparture(Document wagonStatusDoc, LocalDateTime when) {
        String wagonId = wagonStatusDoc.getString("wagonid");
        String source = wagonStatusDoc.getString("source");
        Double speed = asDouble(wagonStatusDoc.get("speed_kmph"));
        if (wagonId == null || wagonId.isBlank() || source == null || source.isBlank()) return;

        List<Consignment> consignments = listConsignmentsOnWagon(wagonId);
        for (Consignment c : consignments) {
            String cn = c.getConsignmentNumber();
            if (cn == null || cn.isBlank()) continue;
            try {
                movementService.recordDeparture(cn, source, when, speed);
            } catch (Exception ignored) {
                // Some consignments may not have this station in movement schedule.
            }
        }
    }

    private static Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception ignored) {
            return null;
        }
    }
}

