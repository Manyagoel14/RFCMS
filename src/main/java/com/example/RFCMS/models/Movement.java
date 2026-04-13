package com.example.RFCMS.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "movement")
public class Movement {

    @Id
    private String id;

    private String cn;
    private String station;

    private LocalDateTime plannedArrival;
    private LocalDateTime actualArrival;

    private LocalDateTime plannedDeparture;
    private LocalDateTime actualDeparture;

    private double speed;
    private String status; // ARRIVED / DEPARTED / IN_TRANSIT

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCn() {
        return cn;
    }

    public void setCn(String cn) {
        this.cn = cn;
    }

    public String getStation() {
        return station;
    }

    public void setStation(String station) {
        this.station = station;
    }

    public LocalDateTime getPlannedArrival() {
        return plannedArrival;
    }

    public void setPlannedArrival(LocalDateTime plannedArrival) {
        this.plannedArrival = plannedArrival;
    }

    public LocalDateTime getActualArrival() {
        return actualArrival;
    }

    public void setActualArrival(LocalDateTime actualArrival) {
        this.actualArrival = actualArrival;
    }

    public LocalDateTime getPlannedDeparture() {
        return plannedDeparture;
    }

    public void setPlannedDeparture(LocalDateTime plannedDeparture) {
        this.plannedDeparture = plannedDeparture;
    }

    public LocalDateTime getActualDeparture() {
        return actualDeparture;
    }

    public void setActualDeparture(LocalDateTime actualDeparture) {
        this.actualDeparture = actualDeparture;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}