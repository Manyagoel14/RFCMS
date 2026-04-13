package com.example.RFCMS.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@Configuration
public class MongoIndexInitializer {

    @Bean
    public ApplicationRunner ensureMongoIndexes(MongoTemplate mongoTemplate) {
        return args -> {
            ensureConsignmentIndexes(mongoTemplate);
            ensureWagonStatusIndexes(mongoTemplate);
            ensureMovementIndexes(mongoTemplate);
            ensureGraphIndexes(mongoTemplate);
            ensureWagonIndexes(mongoTemplate);
        };
    }

    private void ensureConsignmentIndexes(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps("consignments");
        // Not unique: legacy rows may have consignmentNumber null/missing; uniqueness is enforced in booking code.
        ops.ensureIndex(new Index().on("consignmentNumber", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("allocationStatus", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("wagonId", Sort.Direction.ASC));
    }

    private void ensureWagonStatusIndexes(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps("wagon_status");
        ops.ensureIndex(new Index().on("wagonid", Sort.Direction.ASC));
        // Sparse: many legacy docs omit wagonstat_ID until backfill; only non-null values must be unique.
        ops.ensureIndex(new Index().on("wagonstat_ID", Sort.Direction.ASC).unique().sparse());
        ops.ensureIndex(new Index().on("source", Sort.Direction.ASC).on("destination", Sort.Direction.ASC));
    }

    private void ensureMovementIndexes(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps("movement");
        ops.ensureIndex(new Index().on("cn", Sort.Direction.ASC).on("plannedArrival", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("cn", Sort.Direction.ASC).on("station", Sort.Direction.ASC));
    }

    private void ensureGraphIndexes(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps("graph");
        ops.ensureIndex(new Index().on("source", Sort.Direction.ASC).on("destination", Sort.Direction.ASC));
        ops.ensureIndex(new Index().on("src", Sort.Direction.ASC).on("dest", Sort.Direction.ASC));
    }

    private void ensureWagonIndexes(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps("wagons");
        ops.ensureIndex(new Index().on("wagonid", Sort.Direction.ASC).unique().sparse());
        ops.ensureIndex(new Index().on("status", Sort.Direction.ASC));
    }
}
