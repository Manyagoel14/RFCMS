package com.example.RFCMS.models;



import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "wagons")
public class Wagon {

    @Id
    private String id;

    // business id (as in DB: wagonid)
    @Field("wagonid")
    private String wagonId;

    @Field("capacity")
    private float capacity;

    // DB: remaining_capacity
    @Field("remaining_capacity")
    private float remainingCapacity;

    private String currentStation;
    private String status;

    // planning / routing
    private String source;
    private String destination;
    private List<String> route;

    // DB: departure_time (estimated)
    @Field("departure_time")
    private LocalDateTime departureTime;

    // DB: eta
    private LocalDateTime eta;

    // DB: ready_to_dispatch
    @Field("ready_to_dispatch")
    private boolean readyToDispatch;

    // tracking support
    private double currentLatitude;
    private double currentLongitude;
}
