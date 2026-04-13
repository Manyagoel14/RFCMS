package com.example.RFCMS.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;

@Service
public class WagonSeedService {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    public WagonSeedService(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Inserts wagons from classpath JSON (skips existing wagonid).
     */
    public Map<String, Object> importFromClasspath(String classpathLocation) {
        int inserted = 0;
        int skipped = 0;
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            if (!root.isArray()) {
                return Map.of("error", "JSON must be an array of wagon objects", "inserted", 0, "skipped", 0);
            }
            for (JsonNode n : root) {
                Document d = Document.parse(n.toString());
                Object wid = d.get("wagonid");
                if (wid == null) {
                    skipped++;
                    continue;
                }
                boolean exists = mongoTemplate.exists(
                        new Query(Criteria.where("wagonid").is(wid.toString())),
                        "wagons"
                );
                if (exists) {
                    skipped++;
                    continue;
                }
                mongoTemplate.insert(d, "wagons");
                inserted++;
            }
        } catch (Exception e) {
            return Map.of("error", e.getMessage(), "inserted", inserted, "skipped", skipped);
        }
        return Map.of("inserted", inserted, "skipped", skipped, "resource", classpathLocation);
    }
}
