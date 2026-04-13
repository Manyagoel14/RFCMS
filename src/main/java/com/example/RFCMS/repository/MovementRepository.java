package com.example.RFCMS.repository;

import com.example.RFCMS.models.Movement;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.List;

public interface MovementRepository extends MongoRepository<Movement, String> {
    List<Movement> findByCnOrderByPlannedArrivalAsc(String cn);
    Optional<Movement> findByCnAndStation(String cn, String station);
}