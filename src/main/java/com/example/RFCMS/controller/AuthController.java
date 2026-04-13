package com.example.RFCMS.controller;

import com.example.RFCMS.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    // ---------------- SIGNUP ----------------
    @PostMapping("/signup")
    public String signup(@RequestBody Map<String, String> request) {

        String username = request.get("username");
        String password = request.get("password");

        return authService.signup(username, password);
    }

    // ---------------- LOGIN ----------------
    @PostMapping("/login")
    public String login(@RequestBody Map<String, String> request) {

        String username = request.get("username");
        String password = request.get("password");
        String role = request.get("role"); // USER / ADMIN / STATION_MANAGER

        return authService.login(username, password, role);
    }
}
