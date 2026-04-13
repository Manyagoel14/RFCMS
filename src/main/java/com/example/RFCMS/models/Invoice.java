package com.example.RFCMS.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "invoices")
public class Invoice {

    @Id
    private String id;

    private String consignmentId;
    private String source;
    private String destination;
    private String cargoDescription;

    private double freightCharge;
    private int demurrageCharge;
    private double totalAmount;

    private String generatedAt;

    // GETTERS & SETTERS

    public String getConsignmentId() { return consignmentId; }
    public void setConsignmentId(String consignmentId) { this.consignmentId = consignmentId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getCargoDescription() { return cargoDescription; }
    public void setCargoDescription(String cargoDescription) { this.cargoDescription = cargoDescription; }

    public double getFreightCharge() { return freightCharge; }
    public void setFreightCharge(double freightCharge) { this.freightCharge = freightCharge; }

    public int getDemurrageCharge() { return demurrageCharge; }
    public void setDemurrageCharge(int demurrageCharge) { this.demurrageCharge = demurrageCharge; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
}