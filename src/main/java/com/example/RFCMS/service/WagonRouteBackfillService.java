package com.example.RFCMS.service;

import org.bson.Document;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class WagonRouteBackfillService {

    private final MongoTemplate mongoTemplate;
    private final RailwayDijkstra railwayDijkstra;

    public WagonRouteBackfillService(MongoTemplate mongoTemplate, RailwayDijkstra railwayDijkstra) {
        this.mongoTemplate = mongoTemplate;
        this.railwayDijkstra = railwayDijkstra;
    }

    public Result backfillRoutes(boolean overwriteExisting) {
        List<Document> wagons = mongoTemplate.findAll(Document.class, "wagons");

        int total = wagons.size();
        int updated = 0;
        int skipped = 0;

        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "wagons");

        for (Document w : wagons) {
            String id = asString(w.get("_id"));
            if (id == null || id.isBlank()) {
                skipped++;
                continue;
            }

            boolean hasRoute = w.containsKey("route") && w.get("route") != null;
            if (hasRoute && !overwriteExisting) {
                skipped++;
                continue;
            }

            // Try common field names. If your wagon docs don't have source/destination,
            // set them first or adjust these keys.
            String source = firstString(w, "source", "currentStation", "from");
            String destination = firstString(w, "destination", "to");

            if (isBlank(source) || isBlank(destination)) {
                skipped++;
                continue;
            }

            List<String> route = railwayDijkstra.shortestPath(source, destination);
            if (route == null) route = new ArrayList<>();
            route = route.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList();

            Query q = new Query(Criteria.where("_id").is(w.get("_id")));
            Update u = new Update().set("route", route);
            bulk.updateOne(q, u);
            updated++;
        }

        if (updated > 0) {
            bulk.execute();
        }

        return new Result(total, updated, skipped);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstString(Document d, String... keys) {
        for (String k : keys) {
            Object v = d.get(k);
            String s = asString(v);
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    private static String asString(Object v) {
        if (v == null) return null;
        return v.toString();
    }

    public record Result(int totalWagons, int updated, int skipped) {}
}

