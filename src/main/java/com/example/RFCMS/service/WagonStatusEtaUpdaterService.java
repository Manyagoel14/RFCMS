package com.example.RFCMS.service;

import org.bson.Document;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class WagonStatusEtaUpdaterService {

    private final MongoTemplate mongoTemplate;

    public WagonStatusEtaUpdaterService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Result recomputeEtaForAll(boolean overwriteExisting) {
        Query target = overwriteExisting
                ? new Query()
                : new Query(new Criteria().orOperator(
                Criteria.where("eta").is(null),
                Criteria.where("eta").exists(false)
        ));
        List<Document> statuses = mongoTemplate.find(target, Document.class, "wagon_status");

        int total = statuses.size();
        int updated = 0;
        int skipped = 0;
        int skippedMissingFields = 0;
        int skippedNoDistance = 0;

        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "wagon_status");

        for (Document st : statuses) {
            Object id = st.get("_id");
            if (id == null) { skipped++; continue; }

            boolean hasEta = st.get("eta") != null;
            if (hasEta && !overwriteExisting) { skipped++; continue; }

            String src = asString(st.get("source"));
            String dst = asString(st.get("destination"));
            // Prefer station-manager actual departure; else estimated departure_time
            Instant dep = asInstant(st.get("actual_departure_time"));
            if (dep == null) dep = asInstant(st.get("actual_departure")); // backward compat
            if (dep == null) dep = asInstant(st.get("departure_time"));

            Double speedKmh = firstDouble(st, "speed_kmph", "speed_kmh", "speedKmh", "speed");

            if (src == null || dst == null || dep == null || speedKmh == null || speedKmh <= 0) {
                skipped++;
                skippedMissingFields++;
                continue;
            }

            // As requested: use ONLY the direct edge distance from `graph` for the same src/dest.
            Integer distKm = directEdgeDistanceKm(src, dst);
            if (distKm == null) {
                skipped++;
                skippedNoDistance++;
                continue;
            }

            long minutes = Math.max(1, Math.round((distKm / speedKmh) * 60.0));
            Instant eta = dep.plusSeconds(minutes * 60L);

            Query q = new Query(Criteria.where("_id").is(id));
            Update u = new Update()
                    .set("eta", Date.from(eta))
                    .set("distance_km", distKm)
                    .set("eta_calculated_at", Date.from(Instant.now()));

            bulk.updateOne(q, u);
            updated++;
        }

        if (updated > 0) bulk.execute();

        return new Result(total, updated, skipped, skippedMissingFields, skippedNoDistance);
    }

    public boolean recomputeEtaForWagonStatId(String wagonstatId, boolean overwriteExisting) {
        if (wagonstatId == null || wagonstatId.isBlank()) return false;
        Query q = new Query(Criteria.where("wagonstat_ID").is(wagonstatId));
        Document st = mongoTemplate.findOne(q, Document.class, "wagon_status");
        if (st == null) return false;

        boolean hasEta = st.get("eta") != null;
        if (hasEta && !overwriteExisting) return false;

        String src = asString(st.get("source"));
        String dst = asString(st.get("destination"));
        Instant dep = asInstant(st.get("actual_departure_time"));
        if (dep == null) dep = asInstant(st.get("actual_departure")); // backward compat
        if (dep == null) dep = asInstant(st.get("departure_time"));

        Double speedKmh = firstDouble(st, "speed_kmph", "speed_kmh", "speedKmh", "speed");

        if (src == null || dst == null || dep == null || speedKmh == null || speedKmh <= 0) return false;

        Integer distKm = directEdgeDistanceKm(src, dst);
        if (distKm == null) return false;

        long minutes = Math.max(1, Math.round((distKm / speedKmh) * 60.0));
        Instant eta = dep.plusSeconds(minutes * 60L);

        Query uq = new Query(Criteria.where("_id").is(st.get("_id")));
        Update u = new Update()
                .set("eta", Date.from(eta))
                .set("distance_km", distKm)
                .set("eta_calculated_at", Date.from(Instant.now()));
        mongoTemplate.updateFirst(uq, u, "wagon_status");
        return true;
    }

    private Integer directEdgeDistanceKm(String src, String dst) {
        String s = normalize(src);
        String d = normalize(dst);
        if (s == null || d == null) return null;

        // 1) Strict/trimmed/case-insensitive match on common field names.
        Document edge = findEdgeByFields("source", "destination", s, d);
        if (edge == null) edge = findEdgeByFields("src", "dest", s, d);

        // 2) Reverse direction fallback (graph may be stored opposite direction).
        if (edge == null) edge = findEdgeByFields("source", "destination", d, s);
        if (edge == null) edge = findEdgeByFields("src", "dest", d, s);

        if (edge == null) return null;
        return extractDistance(edge);
    }

    private Document findEdgeByFields(String srcField, String dstField, String src, String dst) {
        Pattern srcPattern = Pattern.compile("^\\s*" + Pattern.quote(src) + "\\s*$", Pattern.CASE_INSENSITIVE);
        Pattern dstPattern = Pattern.compile("^\\s*" + Pattern.quote(dst) + "\\s*$", Pattern.CASE_INSENSITIVE);
        Query q = new Query(Criteria.where(srcField).regex(srcPattern).and(dstField).regex(dstPattern));
        return mongoTemplate.findOne(q, Document.class, "graph");
    }

    private Integer extractDistance(Document edge) {
        Object d = edge.get("distance");
        if (d == null) d = edge.get("distance_km");
        if (d instanceof Number n) return n.intValue();
        try { return d == null ? null : Integer.parseInt(d.toString().trim()); } catch (Exception e) { return null; }
    }

    private String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.replaceAll("\\s+", " ").toLowerCase();
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
        // spring-data typically gives LocalDateTime already for mapped docs; fallback:
        try { return LocalDateTime.parse(v.toString()); } catch (Exception e) { return null; }
    }

    private static Instant asInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        if (v instanceof Date d) return d.toInstant();
        if (v instanceof LocalDateTime t) return t.atZone(ZoneOffset.UTC).toInstant();
        try { return OffsetDateTime.parse(v.toString()).toInstant(); } catch (Exception ignored) {}
        try { return ZonedDateTime.parse(v.toString()).toInstant(); } catch (Exception ignored) {}
        try {
            LocalDateTime ldt = LocalDateTime.parse(v.toString());
            return ldt.atZone(ZoneOffset.UTC).toInstant();
        } catch (Exception ignored) {}
        return null;
    }

    private static Double firstDouble(Document d, String... keys) {
        for (String k : keys) {
            Object v = d.get(k);
            Double n = asDouble(v);
            if (n != null) return n;
        }
        return null;
    }

    private static Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Double dd) return dd;
        if (v instanceof Number nn) return nn.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    public record Result(int total, int updated, int skipped, int skippedMissingFields, int skippedNoDistance) {}
}

