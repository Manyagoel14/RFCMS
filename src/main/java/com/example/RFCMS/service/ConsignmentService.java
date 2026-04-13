package com.example.RFCMS.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;

import com.example.RFCMS.models.Consignment;
import com.example.RFCMS.models.Invoice;
import com.example.RFCMS.repository.ConsignmentRepository;
import com.example.RFCMS.repository.InvoiceRepository;

@Service
public class ConsignmentService {

    @Autowired
    private FreightService freightService;

    @Autowired
    private ConsignmentRepository consignmentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    // ---------------- BOOK ----------------
    public Consignment bookConsignment(Consignment c){

        double freightCharge = freightService.calculateFreight(c);
        c.setFreightCharge(freightCharge);
        c.setStatus("BOOKED");

        c.setBookingTimestamp(LocalDateTime.now(ZoneOffset.UTC));

        c.setActualArrivalTimestamp(null);
        c.setPickupTimestamp(null);

        if (c.getConsignmentNumber() == null || c.getConsignmentNumber().isBlank()) {
            c.setConsignmentNumber(generateConsignmentNumber());
        }

        // Allocation happens in end-of-day batch job
        c.setAllocationStatus("PENDING");
        c.setWagonId(null);
        return consignmentRepository.save(c);
    }

    private String generateConsignmentNumber() {
        // deterministic, readable, and sufficiently unique for this app
        return "CN" + System.currentTimeMillis();
    }

    public String generateInvoiceOnPickup(String consignmentId) {

    Optional<Consignment> optional = consignmentRepository.findById(consignmentId);

    if (optional.isEmpty()) {
        return "Consignment not found!";
    }

    Consignment consignment = optional.get();

    if (!"ARRIVED".equals(consignment.getStatus())) {
        return "Consignment has not arrived yet!";
    }

    if (consignment.getActualArrivalTimestamp() == null) {
        return "Arrival timestamp missing!";
    }

    LocalDateTime pickupTime = LocalDateTime.now(ZoneOffset.UTC);
    consignment.setPickupTimestamp(pickupTime);

    LocalDateTime arrivalTime = consignment.getActualArrivalTimestamp();

    long hoursStored = java.time.Duration.between(arrivalTime, pickupTime).toHours();

    int demurrage = calculateDemurrage(hoursStored);
    consignment.setDemurrageCharge(demurrage);

    double freight = consignment.getFreightCharge();
    double total = freight + demurrage;
    consignment.setFinalAmount(total);

    consignmentRepository.save(consignment);

    Invoice invoice = new Invoice();
    invoice.setConsignmentId(consignmentId);
    invoice.setSource(consignment.getSource());
    invoice.setDestination(consignment.getDestination());
    invoice.setCargoDescription(consignment.getCargoDescription());
    invoice.setFreightCharge(freight);
    invoice.setDemurrageCharge(demurrage);
    invoice.setTotalAmount(total);
    invoice.setGeneratedAt(pickupTime.toString());

    invoiceRepository.save(invoice);

    return formatInvoice(invoice);
}
    // ---------------- DEMURRAGE ----------------
    private int calculateDemurrage(long hoursStored) {

        if (hoursStored <= 24) return 0;

        int days = (int) (hoursStored / 24);  // floor division
        return days * 100;
    }

    private String formatInvoice(Invoice invoice) {

        return "\n===== FINAL INVOICE =====\n" +
                "Consignment ID: " + invoice.getConsignmentId() + "\n" +
                "Route: " + invoice.getSource() + " → " + invoice.getDestination() + "\n" +
                "Cargo: " + invoice.getCargoDescription() + "\n\n" +

                "Freight Charge: ₹" + invoice.getFreightCharge() + "\n" +
                "Demurrage Charge: ₹" + invoice.getDemurrageCharge() + "\n" +
                "----------------------------\n" +
                "TOTAL AMOUNT: ₹" + invoice.getTotalAmount() + "\n\n" +

                "Generated At: " + invoice.getGeneratedAt() + "\n" +
                "Status: ARRIVED\n" +
                "=========================\n";
    }
}