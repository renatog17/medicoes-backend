package com.rohub.medicoes.medicoes_api.config;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class TimeConfigTest {
    @Test
    void forneceClockRealNoFusoDeBrasilia() {
        try (var context = new AnnotationConfigApplicationContext(TimeConfig.class)) {
            var clock = context.getBean(Clock.class);
            assertEquals(ZoneId.of("America/Sao_Paulo"), clock.getZone());
            var antes = Instant.now();
            var agora = clock.instant();
            var depois = Instant.now();
            assertFalse(agora.isBefore(antes));
            assertFalse(agora.isAfter(depois));
        }
    }

    @Test
    void respeitaViradaDeDiaEmBrasilia() {
        var fuso = new TimeConfig().clock().getZone();
        var antes = Clock.fixed(Instant.parse("2026-02-01T02:59:59Z"), fuso);
        var depois = Clock.fixed(Instant.parse("2026-02-01T03:00:00Z"), fuso);
        assertEquals(LocalDateTime.of(2026, 1, 31, 23, 59, 59), LocalDateTime.now(antes));
        assertEquals(LocalDateTime.of(2026, 2, 1, 0, 0), LocalDateTime.now(depois));
    }
}
