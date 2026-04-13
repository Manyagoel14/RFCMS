package com.example.RFCMS.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.example.RFCMS.models.Wagon;
import com.example.RFCMS.service.WagonService;

@RestController
@RequestMapping("/api/wagons")
public class WagonController {
    
    @Autowired
    private WagonService wagonService;

    @PostMapping("/addWagon")
    public Wagon addWagon(@RequestBody Wagon w) {   
        return wagonService.addWagon(w);
    }

}
