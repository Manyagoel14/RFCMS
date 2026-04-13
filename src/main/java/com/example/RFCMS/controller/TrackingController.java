package com.example.RFCMS.controller;

import com.example.RFCMS.models.Movement;
import com.example.RFCMS.service.TrackingService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tracking")
public class TrackingController {

    @Autowired
    private TrackingService trackingService;

    // 🔹 Full route (for map)
    @GetMapping("/route/{cn}")
    public List<Movement> getRoute(@PathVariable String cn) {
        return trackingService.getRouteByCN(cn);
    }

    // 🔹 Current position
    @GetMapping("/position/{cn}")
    public Map<String, Object> getPosition(@PathVariable String cn) {
        return trackingService.getCurrentPosition(cn);
    }

    // 🔹 Active wagonstat by CN
    @GetMapping("/active-wagonstat/{cn}")
    public Map<String, Object> getActiveWagonstat(@PathVariable String cn) {
        return trackingService.getActiveWagonStatByCN(cn);
    }

    // 🔹 ETA
    @GetMapping("/eta/{cn}")
    public LocalDateTime getETA(@PathVariable String cn) {
        return trackingService.calculateETA(cn);
    }

    // 🔹 Delay
    @GetMapping("/delay/{cn}")
    public Map<String, Long> getDelay(@PathVariable String cn) {
        return trackingService.getDelay(cn);
    }

    // 🔹 Station manager updates
    @PostMapping("/arrival/{id}")
    public String markArrival(@PathVariable String id) {
        trackingService.updateArrival(id);
        return "Arrival updated";
    }

    @PostMapping("/departure/{id}")
    public String markDeparture(@PathVariable String id) {
        trackingService.updateDeparture(id);
        return "Departure updated";
    }
}