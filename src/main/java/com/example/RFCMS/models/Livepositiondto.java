package com.example.RFCMS.models;

import java.time.LocalDateTime;
import java.util.List;

public class Livepositiondto {
    private String consignmentNumber;
    private List<String> route;
    private String lastStation;
    private String nextStation;
    private double progressBetweenStations;
    private LocalDateTime etaNextStation;
    private Long arrivalDelayMinutes;
    private Long departureDelayMinutes;
    private List<MovementDTO> movements;

    public static class MovementDTO {
        private String station;
        private int order;
        private LocalDateTime plannedArrival;
        private LocalDateTime actualArrival;
        private LocalDateTime plannedDeparture;
        private LocalDateTime actualDeparture;
        private String status;

        public String getStation() { return station; }
        public void setStation(String station) { this.station = station; }
        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }
        public LocalDateTime getPlannedArrival() { return plannedArrival; }
        public void setPlannedArrival(LocalDateTime plannedArrival) { this.plannedArrival = plannedArrival; }
        public LocalDateTime getActualArrival() { return actualArrival; }
        public void setActualArrival(LocalDateTime actualArrival) { this.actualArrival = actualArrival; }
        public LocalDateTime getPlannedDeparture() { return plannedDeparture; }
        public void setPlannedDeparture(LocalDateTime plannedDeparture) { this.plannedDeparture = plannedDeparture; }
        public LocalDateTime getActualDeparture() { return actualDeparture; }
        public void setActualDeparture(LocalDateTime actualDeparture) { this.actualDeparture = actualDeparture; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public String getConsignmentNumber() { return consignmentNumber; }
    public void setConsignmentNumber(String consignmentNumber) { this.consignmentNumber = consignmentNumber; }
    public List<String> getRoute() { return route; }
    public void setRoute(List<String> route) { this.route = route; }
    public String getLastStation() { return lastStation; }
    public void setLastStation(String lastStation) { this.lastStation = lastStation; }
    public String getNextStation() { return nextStation; }
    public void setNextStation(String nextStation) { this.nextStation = nextStation; }
    public double getProgressBetweenStations() { return progressBetweenStations; }
    public void setProgressBetweenStations(double progressBetweenStations) { this.progressBetweenStations = progressBetweenStations; }
    public LocalDateTime getEtaNextStation() { return etaNextStation; }
    public void setEtaNextStation(LocalDateTime etaNextStation) { this.etaNextStation = etaNextStation; }
    public Long getArrivalDelayMinutes() { return arrivalDelayMinutes; }
    public void setArrivalDelayMinutes(Long arrivalDelayMinutes) { this.arrivalDelayMinutes = arrivalDelayMinutes; }
    public Long getDepartureDelayMinutes() { return departureDelayMinutes; }
    public void setDepartureDelayMinutes(Long departureDelayMinutes) { this.departureDelayMinutes = departureDelayMinutes; }
    public List<MovementDTO> getMovements() { return movements; }
    public void setMovements(List<MovementDTO> movements) { this.movements = movements; }
}
