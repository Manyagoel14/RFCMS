package com.example.RFCMS.repository;

import com.example.RFCMS.models.Wagon;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface WagonRepository extends MongoRepository<Wagon, String> {
    List<Wagon> findByStatusIgnoreCase(String status);

    /** Pre-filter allocation candidates: capacity must fit remaining load. */
    List<Wagon> findByStatusIgnoreCaseAndRemainingCapacityGreaterThanEqual(String status, float minRemaining);

    Wagon findByWagonId(String wagonId);
}

