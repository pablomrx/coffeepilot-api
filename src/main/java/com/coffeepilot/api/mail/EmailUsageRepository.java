package com.coffeepilot.api.mail;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public class EmailUsageRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public boolean tryIncrement(LocalDate day, int limit) {
        int affected = entityManager.createNativeQuery("""
                        INSERT INTO email_usage (day, reserved)
                        VALUES (:day, 1)
                        ON CONFLICT (day) DO UPDATE
                           SET reserved = email_usage.reserved + 1
                         WHERE email_usage.reserved < :limit
                        """)
                .setParameter("day", day)
                .setParameter("limit", limit)
                .executeUpdate();

        return affected == 1;
    }

    @Transactional(readOnly = true)
    public int reservedOn(LocalDate day) {
        List<?> rows = entityManager
                .createNativeQuery(
                        "SELECT reserved FROM email_usage WHERE day = :day")
                .setParameter("day", day)
                .getResultList();

        return rows.isEmpty() ? 0 : ((Number) rows.get(0)).intValue();
    }
}
