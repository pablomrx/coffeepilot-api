package com.coffeepilot.api.establishment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZoneId;

class EstablishmentTest {

    @Test
    void construtorComApenasONomeUsaOFusoPadrao() {
        Establishment establishment = new Establishment("Café Central");

        assertThat(establishment.getName()).isEqualTo("Café Central");
        assertThat(establishment.getTimeZone())
                .isEqualTo(Establishment.DEFAULT_TIME_ZONE)
                .isEqualTo("America/Sao_Paulo");
        assertThat(establishment.isActive()).isTrue();
    }

    @Test
    void construtorComFusoValidoGuardaOFuso() {
        Establishment establishment =
                new Establishment("Bar do Zé", "America/Manaus");

        assertThat(establishment.getTimeZone()).isEqualTo("America/Manaus");
    }

    @Test
    void fusoComEspacosNasPontasEAparado() {
        Establishment establishment =
                new Establishment("Bar", "  America/Sao_Paulo  ");

        assertThat(establishment.getTimeZone()).isEqualTo("America/Sao_Paulo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Europe/Lisbon", "America/Noronha"})
    void aceitaIdentificadoresDeRegiaoValidos(String zone) {
        Establishment establishment = new Establishment("Bar", zone);

        assertThat(establishment.getTimeZone()).isEqualTo(zone);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void fusoNuloOuEmBrancoEhRejeitadoNoConstrutor(String zone) {
        assertThatThrownBy(() -> new Establishment("Bar", zone))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O fuso horário é obrigatório.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Foo/Bar", "+03:00", "-03:00", "Brasil", "america/sao_paulo"})
    void fusoInvalidoEhRejeitadoNoConstrutor(String zone) {
        assertThatThrownBy(() -> new Establishment("Bar", zone))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Fuso horário inválido.");
    }

    @Test
    void changeTimeZoneAlteraOFusoQuandoValido() {
        Establishment establishment = new Establishment("Bar");

        establishment.changeTimeZone("America/Fortaleza");

        assertThat(establishment.getTimeZone()).isEqualTo("America/Fortaleza");
    }

    @Test
    void changeTimeZoneInvalidoLancaExcecaoEMantemOFusoAnterior() {
        Establishment establishment = new Establishment("Bar");

        assertThatThrownBy(() -> establishment.changeTimeZone("Foo/Bar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Fuso horário inválido.");
        assertThatThrownBy(() -> establishment.changeTimeZone(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O fuso horário é obrigatório.");

        assertThat(establishment.getTimeZone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void getZoneIdConverteOTextoParaZoneId() {
        Establishment establishment =
                new Establishment("Bar", "America/Manaus");

        assertThat(establishment.getZoneId()).isEqualTo(ZoneId.of("America/Manaus"));
    }

    @Test
    void setActiveAlternaOStatus() {
        Establishment establishment = new Establishment("Bar");

        establishment.setActive(false);
        assertThat(establishment.isActive()).isFalse();

        establishment.setActive(true);
        assertThat(establishment.isActive()).isTrue();
    }
}
