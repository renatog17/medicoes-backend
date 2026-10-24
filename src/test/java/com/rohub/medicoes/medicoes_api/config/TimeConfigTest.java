package com.rohub.medicoes.medicoes_api.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TimeConfigTest {
    @Test
    void usaBahiaPorPadrao() {
        try (var context = new AnnotationConfigApplicationContext(TimeConfig.class)) {
            assertEquals(ZoneId.of("America/Bahia"), context.getBean(Clock.class).getZone());
        }
    }

    @Test
    void permiteConfigurarFuso() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("app.time.zone", "UTC")));
            context.register(TimeConfig.class);
            context.refresh();
            assertEquals(ZoneId.of("UTC"), context.getBean(Clock.class).getZone());
        }
    }

    @Test
    void clockFixoDeterminaDataEHoraLocal() {
        var clock = Clock.fixed(Instant.parse("2026-02-01T02:30:00Z"), ZoneId.of("America/Bahia"));
        assertEquals(LocalDate.of(2026, 1, 31), LocalDate.now(clock));
        assertEquals(LocalDateTime.of(2026, 1, 31, 23, 30), LocalDateTime.now(clock));
    }
}
