package com.example.RFCMS.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.RFCMS.models.Invoice;

public interface InvoiceRepository extends MongoRepository<Invoice, String> {
}