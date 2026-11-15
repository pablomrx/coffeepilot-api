package com.coffeepilot.api.establishment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "establishments")
public class Establishment {

    public static final String DEFAULT_TIME_ZONE = "America/Sao_Paulo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone = DEFAULT_TIME_ZONE;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Establishment() {
    }

    public Establishment(String name) {
        this.name = name;
    }

    public Establishment(String name, String timeZone) {
        this.name = name;
        this.timeZone = validateTimeZone(timeZone);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public ZoneId getZoneId() {
        return ZoneId.of(timeZone);
    }

    public void changeTimeZone(String timeZone) {
        this.timeZone = validateTimeZone(timeZone);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    private static String validateTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            throw new IllegalArgumentException(
                    "O fuso horário é obrigatório."
            );
        }

        String normalized = timeZone.trim();

        if (!ZoneId.getAvailableZoneIds().contains(normalized)) {
            throw new IllegalArgumentException(
                    "Fuso horário inválido."
            );
        }

        return normalized;
    }
}
