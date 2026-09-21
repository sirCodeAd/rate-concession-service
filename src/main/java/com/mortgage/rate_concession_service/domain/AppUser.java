package com.mortgage.rate_concession_service.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A simulated authenticated principal, seeded via Flyway migrations (see V2__seed_data.sql). */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private String id;

    private String name;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    protected AppUser() {
        // JPA
    }

    public AppUser(String id, String name, UserRole role) {
        this.id = id;
        this.name = name;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UserRole getRole() {
        return role;
    }
}
