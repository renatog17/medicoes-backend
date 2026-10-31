package com.rohub.medicoes.medicoes_api;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Propriedades;
import com.rohub.medicoes.medicoes_api.domain.Fatura;
import com.rohub.medicoes.medicoes_api.repository.*;
import com.rohub.medicoes.medicoes_api.service.FaturaEmissaoService;
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
class FaturamentoIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void banco(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired FaturaRepository faturas;
    @Autowired MedicaoRepository medicoes;
    @Autowired InstalacaoRepository instalacoes;
    @Autowired PlatformTransactionManager manager;

    @Test void migracaoConcorrenciaETarifaHistorica() throws Exception {
        // Dados isolados no PostgreSQL descartavel do Testcontainers.
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'faturas' AND column_name = 'cliente_id'", Integer.class));
        Long endereco = jdbc.queryForObject("INSERT INTO enderecos DEFAULT VALUES RETURNING id", Long.class);
        Long cliente = jdbc.queryForObject("INSERT INTO clientes (nome, cin, endereco_cobranca_id) VALUES ('Ana','12345678909',?) RETURNING id", Long.class, endereco);
        Long imovel = jdbc.queryForObject("INSERT INTO imoveis (endereco_id) VALUES (?) RETURNING id", Long.class, endereco);
        Long medidor = jdbc.queryForObject("INSERT INTO medidores (numero_serie) VALUES ('FAT-TESTE') RETURNING id", Long.class);
        Long instalacao = jdbc.queryForObject("INSERT INTO instalacoes (cliente_id, imovel_id, medidor_id, data_inicio) VALUES (?,?,?,'2026-01-01T03:00:00Z') RETURNING id", Long.class, cliente, imovel, medidor);
        jdbc.update("INSERT INTO medicoes (instalacao_id, data_medicao, leitura) VALUES (?, '2026-09-20T03:00:00Z',100), (?, '2026-10-20T03:00:00Z',115)", instalacao, instalacao);
        var clock = Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        var service = new FaturaEmissaoService(faturas, instalacoes, medicoes,
                new Propriedades(true, new BigDecimal("3.25")), clock);
        var transactions = new TransactionTemplate(manager);
        var gate = new CountDownLatch(1);
        Callable<FaturaEmissaoService.Resultado> tarefa = () -> {
            gate.await();
            return transactions.execute(status -> service.emitir(instalacao, YearMonth.of(2026, 10)));
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(tarefa);
            var b = executor.submit(tarefa);
            gate.countDown();
            var ra = a.get(20, TimeUnit.SECONDS);
            var rb = b.get(20, TimeUnit.SECONDS);
            assertEquals(ra.faturaId(), rb.faturaId());
            assertNotEquals(ra.resultado(), rb.resultado());
        }
        var outraTarifa = new FaturaEmissaoService(faturas, instalacoes, medicoes,
                new Propriedades(true, BigDecimal.TEN), clock);
        transactions.execute(status -> outraTarifa.emitir(instalacao, YearMonth.of(2026, 10)));
        var fatura = faturas.findByInstalacaoIdAndCompetencia(instalacao, "2026-10").orElseThrow();
        assertEquals(new BigDecimal("48.75"), fatura.getTotal());
        assertEquals(new BigDecimal("3.250000"), fatura.getTarifa());
        assertEquals(Fatura.Situacao.ABERTA, fatura.getSituacao());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO faturas (instalacao_id, medicao_anterior_id, medicao_atual_id, competencia, consumo, tarifa, total, emissao, vencimento, criada_em, situacao) SELECT instalacao_id, medicao_anterior_id, medicao_atual_id, competencia, consumo, tarifa, total, emissao, vencimento, criada_em, situacao FROM faturas WHERE id=?", fatura.getId()));
    }
}
