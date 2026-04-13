package com.example.RFCMS.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.RFCMS.models.Consignment;

import java.util.Optional;
import java.util.List;

public interface ConsignmentRepository extends MongoRepository<Consignment, String> {
    Optional<Consignment> findByConsignmentNumber(String consignmentNumber);
    List<Consignment> findByAllocationStatus(String allocationStatus);
    List<Consignment> findByWagonId(String wagonId);
}