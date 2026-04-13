package com.example.RFCMS.service;


import org.springframework.stereotype.Service;
import com.example.RFCMS.models.Consignment;


@Service
public class FreightService {
    
    public double calculateFreight(Consignment c) {

        double baseRate = 10;
        double prioritySurcharge = 5;
        double totalCost = 0;
        double weightRate = 0;
        
        double weight = c.getWeight();

        /* ADD SOURCE TO DESTINATION CHARGE BASED ON THE GRAPH DB*/ 
        
        if ("HIGH".equals(c.getPriority())) {
            totalCost += prioritySurcharge;
        }

        if (weight <= 50) {
            weightRate = 4;
        } else if (weight <= 200) {
            weightRate = 5;
        } 
        else if (weight <= 500) {
            weightRate = 6;
        } 
        else if (weight <= 1000) {
            weightRate = 8;
        } 
        else {
            weightRate = 10;
        }

        totalCost += baseRate*c.getDistance() + weightRate;

        return totalCost;
    }

}
