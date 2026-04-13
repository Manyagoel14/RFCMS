package com.example.RFCMS.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "consignments")
public class Consignment {

    @Id
    private String id;

    // Human-friendly identifier used by tracking/movements (e.g. CN1712670000000)
    private String consignmentNumber;

    private String cargoDescription;
    private String cargoType;
    private int weight;
    private String source;
    private String destination;
    private String priority;

    private double FreightCharge;
    private String status;

    // allocation
    private String wagonId;
    private String allocationStatus; // PENDING / ASSIGNED

    // ✅ Proper timestamps
    private LocalDateTime bookingTimestamp;
    private LocalDateTime actualArrivalTimestamp; // initially null
    private LocalDateTime pickupTimestamp;        // initially null

    private int delay;
    private int demurrageCharge;
    private double finalAmount;

    private int distance;

    // ---------------- GETTERS & SETTERS ----------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConsignmentNumber() { return consignmentNumber; }
    public void setConsignmentNumber(String consignmentNumber) { this.consignmentNumber = consignmentNumber; }

    public String getCargoDescription() { return cargoDescription; }
    public void setCargoDescription(String cargoDescription) { this.cargoDescription = cargoDescription; }

    public String getCargoType() { return cargoType; }
    public void setCargoType(String cargoType) { this.cargoType = cargoType; }

    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public double getFreightCharge() { return FreightCharge; }
    public void setFreightCharge(double freightCharge) { FreightCharge = freightCharge; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getWagonId() { return wagonId; }
    public void setWagonId(String wagonId) { this.wagonId = wagonId; }

    public String getAllocationStatus() { return allocationStatus; }
    public void setAllocationStatus(String allocationStatus) { this.allocationStatus = allocationStatus; }

    public LocalDateTime getBookingTimestamp() { return bookingTimestamp; }
    public void setBookingTimestamp(LocalDateTime bookingTimestamp) { this.bookingTimestamp = bookingTimestamp; }

    public LocalDateTime getActualArrivalTimestamp() { return actualArrivalTimestamp; }
    public void setActualArrivalTimestamp(LocalDateTime actualArrivalTimestamp) { this.actualArrivalTimestamp = actualArrivalTimestamp; }

    public LocalDateTime getPickupTimestamp() { return pickupTimestamp; }
    public void setPickupTimestamp(LocalDateTime pickupTimestamp) { this.pickupTimestamp = pickupTimestamp; }

    public int getDelay() { return delay; }
    public void setDelay(int delay) { this.delay = delay; }

    public int getDemurrageCharge() { return demurrageCharge; }
    public void setDemurrageCharge(int demurrageCharge) { this.demurrageCharge = demurrageCharge; }

    public double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(double finalAmount) { this.finalAmount = finalAmount; }

    public int getDistance() { return distance; }
    public void setDistance(int distance) { this.distance = distance; }
}