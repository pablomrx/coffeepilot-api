package com.coffeepilot.api.employee.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.coffeepilot.api.user.Role;

import tools.jackson.databind.json.JsonMapper;

class EmployeeResponseTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializaSemCamposSensiveisEComDataIso() throws Exception {
        var resp = new EmployeeResponse(1L, "Ana", "ana@x.com", Role.valueOf("ATTENDANT"),
                true, OffsetDateTime.parse("2026-10-28T10:15:30-03:00"), null);

        String json = mapper.writeValueAsString(resp);

        assertThat(json).contains("\"createdAt\":\"2026-10-28T10:15:30-03:00\"");
        assertThat(json).doesNotContain("password");
    }
}
