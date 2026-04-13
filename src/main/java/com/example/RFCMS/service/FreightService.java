package com.example.RFCMS.service;


import org.springframework.stereotype.Service;
import com.example.RFCMS.models.Consignment;


@Service
public class FreightService {

    /** Freight = weight (kg) × route distance (km) from graph. */
    public double calculateFreight(Consignment c) {
        double weight = Math.max(0, c.getWeight());
        double distance = Math.max(0, c.getDistance());
        return weight * distance;
    }

}
