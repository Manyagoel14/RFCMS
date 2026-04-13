package com.example.RFCMS.service;

import com.example.RFCMS.models.Consignment;
import com.example.RFCMS.repository.ConsignmentRepository;
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
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WagonStatusManagerService {

    private final MongoTemplate mongoTemplate;
    private final WagonStatusAdvanceService advanceService;
    private final WagonStatusEtaUpdaterService etaUpdaterService;
    private final Movementservice movementService;
    private final ConsignmentRepository consignmentRepository;

    public WagonStatusManagerService(MongoTemplate mongoTemplate,
                                     WagonStatusAdvanceService advanceService,
                                     WagonStatusEtaUpdaterService etaUpdaterService,
                                     Movementservice movementService,
                                     ConsignmentRepository consignmentRepository) {
        this.mongoTemplate = mongoTemplate;
        this.advanceService = advanceService;
        this.etaUpdaterService = etaUpdaterService;
        this.movementService = movementService;
        this.consignmentRepository = consignmentRepository;
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
        String destination = wagonStatusDoc.getString("destination");
        if (wagonId == null || wagonId.isBlank() || destination == null || destination.isBlank()) return;

        // Reflect live wagon location in wagons collection when leg arrival is confirmed.
        Update wagonUpdate = new Update().set("currentStation", destination);
        mongoTemplate.updateFirst(new Query(Criteria.where("wagonid").is(wagonId)), wagonUpdate, "wagons");
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(wagonId)), wagonUpdate, "wagons");

        List<Consignment> consignments = consignmentRepository.findByWagonId(wagonId);
        for (Consignment c : consignments) {
            String cn = c.getConsignmentNumber();
            if (cn == null || cn.isBlank()) continue;
            try {
                movementService.recordArrival(cn, destination, when);
            } catch (Exception ignored) {
                // Some consignments may not have this station in movement schedule.
            }

            if (c.getDestination() != null && c.getDestination().equalsIgnoreCase(destination)) {
                c.setActualArrivalTimestamp(when);
                c.setStatus("ARRIVED");
                consignmentRepository.save(c);
            }
        }
    }

    private void syncConsignmentMovementsOnDeparture(Document wagonStatusDoc, LocalDateTime when) {
        String wagonId = wagonStatusDoc.getString("wagonid");
        String source = wagonStatusDoc.getString("source");
        Double speed = asDouble(wagonStatusDoc.get("speed_kmph"));
        if (wagonId == null || wagonId.isBlank() || source == null || source.isBlank()) return;

        List<Consignment> consignments = consignmentRepository.findByWagonId(wagonId);
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

