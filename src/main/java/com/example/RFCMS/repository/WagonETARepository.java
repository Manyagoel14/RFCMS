package com.example.RFCMS.repository;

import com.example.RFCMS.models.WagonETA;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface WagonETARepository extends MongoRepository<WagonETA, String> {
    Optional<WagonETA> findTopByWagonIdOrderByDepartureTimeDesc(String wagonId);
}

