package com.example.RFCMS.service;

import com.example.RFCMS.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private MongoTemplate mongoTemplate;

    // ---------------- SIGNUP ----------------
    public String signup(String username, String password) {

        // Check if user already exists
        Query query = new Query(Criteria.where("username").is(username));
        User existingUser = mongoTemplate.findOne(query, User.class);

        if (existingUser != null) {
            return "Username already exists!";
        }

        // Only USER role allowed
        User user = new User(username, password, "USER");

        mongoTemplate.save(user);

        return "Signup successful!";
    }

    // ---------------- LOGIN ----------------
    public String login(String username, String password, String role) {

        Query query = new Query(
                Criteria.where("username").is(username)
                        .and("password").is(password)
                        .and("role").is(role)
        );

        User user = mongoTemplate.findOne(query, User.class);

        if (user == null) {
            return "Invalid credentials or role!";
        }

        return "Login successful as " + role;
    }
}