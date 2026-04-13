package com.example.RFCMS.service;

import com.example.RFCMS.models.Wagon;
import com.example.RFCMS.repository.WagonRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
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
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class WagonStatusAdvanceService {

    private final MongoTemplate mongoTemplate;
    private final WagonRepository wagonRepository;
    private final WagonStatusEtaUpdaterService etaUpdaterService;

    public WagonStatusAdvanceService(MongoTemplate mongoTemplate,
                                    WagonRepository wagonRepository,
                                    WagonStatusEtaUpdaterService etaUpdaterService) {
        this.mongoTemplate = mongoTemplate;
        this.wagonRepository = wagonRepository;
        this.etaUpdaterService = etaUpdaterService;
    }

    /**
     * If latest wagon_status leg for wagonid has actual_arrival_time set,
     * create the next leg record (station1 -> next station) and return true if created.
     */
    public boolean advanceOne(String wagonid) {
        if (wagonid == null || wagonid.isBlank()) return false;

        Document latest = latestArrivedStatusForWagon(wagonid);
        if (latest == null) return false;

        return advanceFromStatus(latest) != null;
    }

    /**
     * Creates next wagon_status leg from the exact wagonstat_ID record.
     * Returns the new leg's wagonstat_ID if created; otherwise null.
     */
    public String advanceFromWagonStatId(String wagonstatId) {
        if (wagonstatId == null || wagonstatId.isBlank()) return null;
        Query q = new Query(Criteria.where("wagonstat_ID").is(wagonstatId));
        Document st = mongoTemplate.findOne(q, Document.class, "wagon_status");
        if (st == null) return null;
        return advanceFromStatus(st);
    }

    private String advanceFromStatus(Document latest) {
        LocalDateTime actualArrival = asLocalDateTime(latest.get("actual_arrival_time"));
        if (actualArrival == null) return null; // not arrived yet

        // prevent duplicates: if already advanced from this arrival, don't create again
        if (Boolean.TRUE.equals(latest.getBoolean("next_leg_created"))) return null;

        String wagonid = asString(latest.get("wagonid"));
        if (wagonid == null || wagonid.isBlank()) return null;

        String station1 = asString(latest.get("destination"));
        if (station1 == null || station1.isBlank()) return null;

        Wagon wagon = wagonRepository.findByWagonId(wagonid);
        if (wagon == null || wagon.getRoute() == null || wagon.getRoute().isEmpty()) return null;

        String next = nextStationInWagonRoute(wagon.getRoute(), station1);
        if (next == null) return null;

        LocalDateTime dep = actualArrival.plusMinutes(30);

        String newWagonstatId = "WS-" + UUID.randomUUID();
        Document nextRec = new Document();
        nextRec.put("wagonstat_ID", newWagonstatId);
        nextRec.put("wagonid", wagonid);
        nextRec.put("source", station1);
        nextRec.put("destination", next);
        nextRec.put("departure_time", dep);
        nextRec.put("eta", null); // will be computed below
        nextRec.put("actual_arrival_time", null);
        nextRec.put("actual_departure_time", null);
        nextRec.put("speed_kmph", 60); // default; can be updated anytime
        nextRec.put("distance_km", null);
        nextRec.put("eta_calculated_at", null);

        // status_at_next_station: compare last leg actual_arrival_time vs eta
        nextRec.put("status_at_next_station", statusOnTimeOrDelay(
                asLocalDateTime(latest.get("eta")),
                actualArrival
        ));

        mongoTemplate.insert(nextRec, "wagon_status");

        // mark latest as advanced
        Query markQ = new Query(Criteria.where("_id").is(latest.get("_id")));
        mongoTemplate.updateFirst(markQ,
                new Update().set("next_leg_created", true),
                "wagon_status");

        // compute eta + distance for the new record (writes eta/distance_km)
        etaUpdaterService.recomputeEtaForWagonStatId(newWagonstatId, false);

        return newWagonstatId;
    }

    public int advanceAllArrived() {
        List<Document> wagons = mongoTemplate.findAll(Document.class, "wagon_status");
        int created = 0;
        for (Document d : wagons) {
            String w = asString(d.get("wagonid"));
            if (w == null) continue;
            if (advanceOne(w)) created++;
        }
        return created;
    }

    private Document latestArrivedStatusForWagon(String wagonid) {
        // Only consider records that have actually arrived and haven't already been advanced
        Criteria c = Criteria.where("wagonid").is(wagonid)
                .and("actual_arrival_time").ne(null)
                .and("next_leg_created").ne(true);
        Query q = new Query(c).with(Sort.by(Sort.Direction.DESC, "actual_arrival_time"));
        return mongoTemplate.findOne(q, Document.class, "wagon_status");
    }

    private String nextStationInWagonRoute(List<String> route, String currentDest) {
        for (int i = 0; i < route.size(); i++) {
            if (route.get(i) != null && route.get(i).equalsIgnoreCase(currentDest)) {
                if (i + 1 < route.size()) return route.get(i + 1);
                return null;
            }
        }
        return null;
    }

    private String statusOnTimeOrDelay(LocalDateTime eta, LocalDateTime actualArrival) {
        if (eta == null || actualArrival == null) return "ON_TIME";
        return actualArrival.isAfter(eta) ? "DELAY" : "ON_TIME";
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
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
}

