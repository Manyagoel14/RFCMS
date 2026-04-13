package com.example.RFCMS.service;

import com.example.RFCMS.models.Livepositiondto;
import com.example.RFCMS.models.Movement;
import com.example.RFCMS.repository.MovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class Movementservice {
    private final MovementRepository movementRepo;

    public Movementservice(MovementRepository movementRepo) {
        this.movementRepo = movementRepo;
    }

    @Transactional
    public Movement recordArrival(String cn, String station, LocalDateTime actualArrival) {
        Movement m = movementRepo.findByCnAndStation(cn, station)
                .orElseThrow(() -> new RuntimeException("No movement record for cn=" + cn + " station=" + station));
        m.setActualArrival(actualArrival);
        m.setStatus("ARRIVED");
        return movementRepo.save(m);
    }

    @Transactional
    public Movement recordDeparture(String cn, String station, LocalDateTime actualDeparture, Double speedKmh) {
        Movement m = movementRepo.findByCnAndStation(cn, station)
                .orElseThrow(() -> new RuntimeException("No movement record for cn=" + cn + " station=" + station));
        m.setActualDeparture(actualDeparture);
        m.setStatus("DEPARTED");
        if (speedKmh != null) m.setSpeed(speedKmh);
        return movementRepo.save(m);
    }

    @Transactional
    public List<Movement> createSchedule(String cn, List<String> orderedStations, LocalDateTime departureTime,
                                         double avgSpeedKmh, double avgDistKmPerHop) {
        if (orderedStations == null || orderedStations.isEmpty()) throw new RuntimeException("orderedStations is required");
        long hopMinutes = (long) ((avgDistKmPerHop / Math.max(avgSpeedKmh, 1.0)) * 60);
        LocalDateTime cursor = departureTime;

        List<Movement> saved = new ArrayList<>();
        for (int i = 0; i < orderedStations.size(); i++) {
            Movement mv = new Movement();
            mv.setCn(cn);
            mv.setStation(orderedStations.get(i));
            mv.setStatus("PENDING");
            if (i == 0) {
                mv.setPlannedArrival(null);
                mv.setPlannedDeparture(cursor);
            } else {
                LocalDateTime arr = cursor.plusMinutes(hopMinutes);
                mv.setPlannedArrival(arr);
                mv.setPlannedDeparture(i < orderedStations.size() - 1 ? arr.plusMinutes(30) : arr);
                cursor = mv.getPlannedDeparture();
            }
            saved.add(movementRepo.save(mv));
        }
        return saved;
    }

    public Livepositiondto getLivePosition(String cn) {
        List<Movement> route = movementRepo.findByCnOrderByPlannedArrivalAsc(cn);
        if (route.isEmpty()) throw new RuntimeException("No movement data found for cn=" + cn);

        Movement lastDeparted = null;
        for (Movement m : route) if (m.getActualDeparture() != null) lastDeparted = m;

        Movement next = null;
        if (lastDeparted != null) {
            boolean after = false;
            for (Movement m : route) {
                if (after && m.getActualArrival() == null) { next = m; break; }
                if (m == lastDeparted) after = true;
            }
        } else {
            next = route.get(0);
        }

        String lastStation = lastDeparted != null ? lastDeparted.getStation() : route.get(0).getStation();
        String nextStation = next != null ? next.getStation() : route.get(route.size() - 1).getStation();

        double progress = 0.0;
        LocalDateTime eta = next != null ? next.getPlannedArrival() : null;
        Long depDelay = null;
        Long arrDelay = null;

        if (lastDeparted != null && next != null
                && lastDeparted.getActualDeparture() != null
                && lastDeparted.getPlannedDeparture() != null
                && next.getPlannedArrival() != null) {
            long total = Duration.between(lastDeparted.getPlannedDeparture(), next.getPlannedArrival()).toMinutes();
            long done = Duration.between(lastDeparted.getActualDeparture(), LocalDateTime.now(ZoneOffset.UTC)).toMinutes();
            if (total > 0) progress = Math.min(1.0, Math.max(0.0, (double) done / total));
            depDelay = Duration.between(lastDeparted.getPlannedDeparture(), lastDeparted.getActualDeparture()).toMinutes();
            if (next.getActualArrival() != null) {
                arrDelay = Duration.between(next.getPlannedArrival(), next.getActualArrival()).toMinutes();
            }
        }

        Livepositiondto dto = new Livepositiondto();
        dto.setConsignmentNumber(cn);
        dto.setRoute(route.stream().map(Movement::getStation).collect(Collectors.toList()));
        dto.setLastStation(lastStation);
        dto.setNextStation(nextStation);
        dto.setProgressBetweenStations(progress);
        dto.setEtaNextStation(eta);
        dto.setDepartureDelayMinutes(depDelay);
        dto.setArrivalDelayMinutes(arrDelay);

        List<Livepositiondto.MovementDTO> moves = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) {
            Movement mv = route.get(i);
            Livepositiondto.MovementDTO md = new Livepositiondto.MovementDTO();
            md.setStation(mv.getStation());
            md.setOrder(i);
            md.setPlannedArrival(mv.getPlannedArrival());
            md.setActualArrival(mv.getActualArrival());
            md.setPlannedDeparture(mv.getPlannedDeparture());
            md.setActualDeparture(mv.getActualDeparture());
            md.setStatus(mv.getStatus());
            moves.add(md);
        }
        dto.setMovements(moves);
        return dto;
    }

    public Map<String, Object> getCurrentPositionSimple(String cn) {
        List<Movement> route = movementRepo.findByCnOrderByPlannedArrivalAsc(cn);
        Map<String, Object> response = new HashMap<>();
        if (route.isEmpty()) { response.put("status", "NO_ROUTE"); return response; }

        for (int i = 0; i < route.size(); i++) {
            Movement curr = route.get(i);
            if (curr.getActualDeparture() != null && (i + 1 < route.size() && route.get(i + 1).getActualArrival() == null)) {
                Movement next = route.get(i + 1);
                long total = (curr.getPlannedDeparture() != null && next.getPlannedArrival() != null)
                        ? Duration.between(curr.getPlannedDeparture(), next.getPlannedArrival()).toMinutes() : 0;
                long done = Duration.between(curr.getActualDeparture(), LocalDateTime.now(ZoneOffset.UTC)).toMinutes();
                double progress = total > 0 ? (double) done / total : 0.0;
                response.put("lastStation", curr.getStation());
                response.put("nextStation", next.getStation());
                response.put("progress", Math.min(progress, 1.0));
                return response;
            }
        }

        response.put("status", "ARRIVED");
        return response;
    }
}
