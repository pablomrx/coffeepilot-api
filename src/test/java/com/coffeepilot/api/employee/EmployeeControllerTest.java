package com.coffeepilot.api.employee;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.coffeepilot.api.common.exception.EmailAlreadyUsedException;
import com.coffeepilot.api.employee.dto.EmployeeResponse;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.user.Role;

@SpringBootTest
@AutoConfigureMockMvc
class EmployeeControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EmployeeService employeeService;

    // ---------- helpers ----------

    private RequestPostProcessor owner() {
        var principal = new AuthenticatedUser(
                1L, "Owner", "owner@x.com", Role.OWNER, 10L, "Cafeteria Teste");
        var token = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }

    private String body(String name, String email, String password, String role) {
        return """
                {"name":"%s","email":"%s","password":"%s","role":"%s"}"""
                .formatted(name, email, password, role);
    }

    private String validBody() {
        return body("Ana", "ana@x.com", "senha1234", "ATTENDANT");
    }

    // ---------- autenticação ----------

    @Test
    void semAutenticacaoRetorna401() throws Exception {
        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Autenticação necessária."));

        verify(employeeService, never()).create(any(), any());
    }

    // ---------- caminho feliz ----------

    @Test
    void requisicaoValidaRetorna201SemSenha() throws Exception {
        when(employeeService.create(any(), any())).thenReturn(new EmployeeResponse(
                5L, "Ana", "ana@x.com", Role.ATTENDANT, true,
                OffsetDateTime.parse("2026-10-28T10:15:30-03:00"), null));

        mockMvc.perform(post("/employees").with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.email").value("ana@x.com"))
                .andExpect(jsonPath("$.role").value("ATTENDANT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // ---------- validação (400) ----------

    @Test
    void nomeEmBrancoRetorna400() throws Exception {
        mockMvc.perform(post("/employees").with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ", "ana@x.com", "senha1234", "ATTENDANT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$..field").value(hasItem("name")));

        verify(employeeService, never()).create(any(), any());
    }

    @Test
    void emailInvalidoRetorna400() throws Exception {
        mockMvc.perform(post("/employees").with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ana", "a@b", "senha1234", "ATTENDANT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$..field").value(hasItem("email")));
    }

    @Test
    void senhaCurtaRetorna400() throws Exception {
        mockMvc.perform(post("/employees").with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ana", "ana@x.com", "1234567", "ATTENDANT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$..field").value(hasItem("password")));
    }

    @Test
    void senhaComMaisDe72BytesRetornaErroNoCampoPassword() throws Exception {
        // 40 caracteres, 80 bytes em UTF-8: passa no @Size, falha no @AssertTrue
        String senha = "ç".repeat(40);

        mockMvc.perform(post("/employees").with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ana", "ana@x.com", senha, "ATTENDANT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$..field").value(hasItem("password")));
    }

    @Test
    void cargoInexistenteRetorna400() throws Exception {
        mockMvc.perform(post("/employees").with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ana", "ana@x.com", "senha1234", "CHEFE")))
                .andExpect(status().isBadRequest());

        verify(employeeService, never()).create(any(), any());
    }

    // ---------- tradução de erros do service ----------

    @Test
    void accessDeniedDoServiceRetorna403() throws Exception {
        when(employeeService.create(any(), any()))
                .thenThrow(new AccessDeniedException("sem permissão"));

        mockMvc.perform(post("/employees").with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void emailDuplicadoDoServiceRetorna409() throws Exception {
        when(employeeService.create(any(), any()))
                .thenThrow(new EmailAlreadyUsedException());

        mockMvc.perform(post("/employees").with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$..field").value(hasItem("email")));
    }
}