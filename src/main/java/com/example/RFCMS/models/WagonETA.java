package com.example.RFCMS.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "wagon_eta")
public class WagonETA {

    @Id
    private String id;   // eta_id

    private String wagonId;  // reference to Wagon

    private String source;
    private String destination;

    private LocalDateTime departureTime;
    private LocalDateTime eta;

    private ETAStatus status;  // enum (ON_TIME / DELAY)

    private String statusAtNextStation;
}