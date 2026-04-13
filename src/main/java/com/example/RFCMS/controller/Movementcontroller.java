package com.example.RFCMS.controller;

import com.example.RFCMS.models.Livepositiondto;
import com.example.RFCMS.models.Movement;
import com.example.RFCMS.service.Movementservice;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/movement")
@CrossOrigin(origins = "*")
public class Movementcontroller {

    private final Movementservice movementService;

    public Movementcontroller(Movementservice movementService) {
        this.movementService = movementService;
    }

    @PostMapping("/arrive")
    public ResponseEntity<Movement> recordArrival(@RequestBody ArrivalRequest req) {
        return ResponseEntity.ok(movementService.recordArrival(req.cn, req.station, req.actualArrival));
    }

    @PostMapping("/depart")
    public ResponseEntity<Movement> recordDeparture(@RequestBody DepartureRequest req) {
        return ResponseEntity.ok(movementService.recordDeparture(req.cn, req.station, req.actualDeparture, req.speedKmh));
    }

    @GetMapping("/track/{cn}")
    public ResponseEntity<Livepositiondto> trackConsignment(@PathVariable String cn) {
        return ResponseEntity.ok(movementService.getLivePosition(cn));
    }

    @GetMapping("/position/{cn}")
    public ResponseEntity<Map<String, Object>> position(@PathVariable String cn) {
        return ResponseEntity.ok(movementService.getCurrentPositionSimple(cn));
    }

    @PostMapping("/schedule")
    public ResponseEntity<List<Movement>> createSchedule(@RequestBody ScheduleRequest req) {
        return ResponseEntity.ok(movementService.createSchedule(
                req.cn, req.orderedStations, req.departureTime, req.avgSpeedKmh, req.avgDistKmPerHop
        ));
    }

    public static class ArrivalRequest {
        public String cn;
        public String station;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        public LocalDateTime actualArrival;
    }

    public static class DepartureRequest {
        public String cn;
        public String station;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        public LocalDateTime actualDeparture;
        public Double speedKmh;
    }

    public static class ScheduleRequest {
        public String cn;
        public List<String> orderedStations;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        public LocalDateTime departureTime;
        public double avgSpeedKmh = 80;
        public double avgDistKmPerHop = 200;
    }
}
