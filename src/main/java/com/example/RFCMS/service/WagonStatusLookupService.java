package com.example.RFCMS.service;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

@Service
public class WagonStatusLookupService {

    private final MongoTemplate mongoTemplate;

    public WagonStatusLookupService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public WagonStatus getByWagonId(String wagonId) {
        return getCurrentLegByWagonId(wagonId);
    }

    /**
     * Returns the current active leg for a wagon (latest record where actual_arrival_time is null),
     * falling back to the latest record by departure_time if none are active.
     */
    public WagonStatus getCurrentLegByWagonId(String wagonId) {
        if (wagonId == null || wagonId.isBlank()) return null;

        Document d = mongoTemplate.findOne(
                new Query(Criteria.where("wagonid").is(wagonId).and("actual_arrival_time").is(null))
                        .with(Sort.by(Sort.Direction.DESC, "departure_time")),
                Document.class,
                "wagon_status"
        );
        if (d == null) {
            d = mongoTemplate.findOne(
                    new Query(Criteria.where("wagonid").is(wagonId))
                            .with(Sort.by(Sort.Direction.DESC, "departure_time")),
                    Document.class,
                    "wagon_status"
            );
        }
        if (d == null) return null;

        return fromDoc(d);
    }

    public WagonStatus getByWagonStatId(String wagonstatId) {
        if (wagonstatId == null || wagonstatId.isBlank()) return null;
        Query q = new Query(Criteria.where("wagonstat_ID").is(wagonstatId));
        Document d = mongoTemplate.findOne(q, Document.class, "wagon_status");
        if (d == null) return null;
        return fromDoc(d);
    }

    private WagonStatus fromDoc(Document d) {
        String wagonId = asString(d.get("wagonid"));
        String wagonstatId = asString(d.get("wagonstat_ID"));
        Double speed = asDouble(d.get("speed_kmph"));

        // Station manager sets this; animation should start only after this exists.
        LocalDateTime dep = asLocalDateTime(d.get("actual_departure_time"));
        if (dep == null) dep = asLocalDateTime(d.get("actual_departure")); // backward compat if you used this key earlier

        List<String> route = new ArrayList<>();
        Object r = d.get("route");
        if (r instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) route.add(o.toString());
            }
        }

        String currentStation = asString(d.get("current_station"));
        String nextStation = asString(d.get("next_station"));

        return new WagonStatus(
                wagonId,
                wagonstatId,
                asString(d.get("source")),
                asString(d.get("destination")),
                dep,
                speed,
                route,
                currentStation,
                nextStation
        );
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private static Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    private static LocalDateTime asLocalDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime t) return t;
        if (v instanceof Date d) {
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(d.getTime()), ZoneOffset.UTC).toLocalDateTime();
        }
        try { return LocalDateTime.parse(v.toString()); } catch (Exception e) { return null; }
    }

    public record WagonStatus(
            String wagonId,
            String wagonstatId,
            String source,
            String destination,
            LocalDateTime departureTime,
            Double speedKmph,
            List<String> route,
            String currentStation,
            String nextStation
    ) {}
}

