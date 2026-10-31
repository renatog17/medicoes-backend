package com.rohub.medicoes.medicoes_api;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Propriedades;
import com.rohub.medicoes.medicoes_api.dto.MedicaoRequest;
import com.rohub.medicoes.medicoes_api.exception.ConflitoException;
import com.rohub.medicoes.medicoes_api.repository.*;
import com.rohub.medicoes.medicoes_api.service.FaturaEmissaoService;
import com.rohub.medicoes.medicoes_api.service.MedicaoService;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MedicaoIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void banco(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MedicaoService medicoes;
    @Autowired MedicaoRepository medicaoRepository;
    @Autowired InstalacaoRepository instalacoes;
    @Autowired FaturaRepository faturas;
    @Autowired PlatformTransactionManager manager;

    @Test
    void referenciaEMensalNoMesmoMesComConcorrenciaEFaturamento() throws Exception {
        Long endereco = jdbc.queryForObject("INSERT INTO enderecos DEFAULT VALUES RETURNING id", Long.class);
        Long cliente = jdbc.queryForObject("INSERT INTO clientes (nome, cin, endereco_cobranca_id) VALUES ('Ana','52998224725',?) RETURNING id", Long.class, endereco);
        Long imovel = jdbc.queryForObject("INSERT INTO imoveis (endereco_id) VALUES (?) RETURNING id", Long.class, endereco);
        Long medidor = jdbc.queryForObject("INSERT INTO medidores (numero_serie) VALUES ('MED-MENSAL') RETURNING id", Long.class);
        Long instalacao = jdbc.queryForObject("INSERT INTO instalacoes (cliente_id, imovel_id, medidor_id, data_inicio) VALUES (?,?,?,'2026-10-01T03:00:00Z') RETURNING id", Long.class, cliente, imovel, medidor);
        var referencia = medicoes.criar(new MedicaoRequest(instalacao,
                Instant.parse("2026-10-01T03:00:00Z"), new BigDecimal("100")));
        var clock = Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        var emissao = new FaturaEmissaoService(faturas, instalacoes, medicaoRepository,
                new Propriedades(true, new BigDecimal("3.25")), clock);
        var transactions = new TransactionTemplate(manager);
        assertEquals("REFERENCIA_INICIAL", transactions.execute(
                status -> emissao.emitir(instalacao, YearMonth.of(2026, 10))).resultado());
        assertTrue(faturas.findByInstalacaoIdAndCompetencia(instalacao, "2026-10").isEmpty());

        var prontas = new CountDownLatch(2);
        var iniciar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(tentarMensal(instalacao, "2026-10-17T12:00:00Z", prontas, iniciar));
            var b = executor.submit(tentarMensal(instalacao, "2026-10-17T12:30:00Z", prontas, iniciar));
            try {
                assertTrue(prontas.await(10, TimeUnit.SECONDS));
            } finally {
                iniciar.countDown();
            }
            assertNotEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM medicoes WHERE instalacao_id=?", Long.class, instalacao));
        var erro = assertThrows(ConflitoException.class, () -> medicoes.criar(new MedicaoRequest(instalacao,
                Instant.parse("2026-10-18T12:00:00Z"), new BigDecimal("115"))));
        assertEquals("Ja existe uma medicao para esta instalacao neste mes", erro.getMessage());
        assertEquals("EMITIDA", transactions.execute(
                status -> emissao.emitir(instalacao, YearMonth.of(2026, 10))).resultado());
        var fatura = faturas.findByInstalacaoIdAndCompetencia(instalacao, "2026-10").orElseThrow();
        assertEquals(referencia.id(), fatura.getMedicaoAnteriorId());
        assertEquals(new BigDecimal("15.000000"), fatura.getConsumo());
        assertEquals(new BigDecimal("48.75"), fatura.getTotal());
    }

    private Callable<Boolean> tentarMensal(Long instalacao, String data, CountDownLatch prontas, CountDownLatch iniciar) {
        return () -> {
            prontas.countDown();
            if (!iniciar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Inicio das tentativas expirou");
            try {
                medicoes.criar(new MedicaoRequest(instalacao, Instant.parse(data), new BigDecimal("115")));
                return true;
            } catch (ConflitoException ex) {
                return false;
            }
        };
    }
}
