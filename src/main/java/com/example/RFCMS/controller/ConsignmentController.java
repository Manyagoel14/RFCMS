package com.example.RFCMS.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.example.RFCMS.models.Consignment;
import com.example.RFCMS.service.ConsignmentService;

@RestController
@RequestMapping("/consignment")
public class ConsignmentController {

    @Autowired
    private ConsignmentService consignmentService;

    @PostMapping("/book")
    public Consignment bookConsignment(@RequestBody Consignment c) {
        return consignmentService.bookConsignment(c);
    }

    @PostMapping("/pickup/{id}")
    public String generateInvoice(@PathVariable String id) {
        return consignmentService.generateInvoiceOnPickup(id);
    }
}