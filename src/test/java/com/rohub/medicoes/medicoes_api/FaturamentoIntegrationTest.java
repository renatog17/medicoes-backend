package com.rohub.medicoes.medicoes_api;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Propriedades;
import com.rohub.medicoes.medicoes_api.domain.Fatura;
import com.rohub.medicoes.medicoes_api.repository.*;
import com.rohub.medicoes.medicoes_api.scheduler.FaturamentoService;
import com.rohub.medicoes.medicoes_api.service.FaturaConsultaService;
import com.rohub.medicoes.medicoes_api.service.MedicaoService;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

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
    @Autowired FaturaConsultaService consulta;
    @Autowired MedicaoService medicaoService;
    @Autowired PlatformTransactionManager manager;
    final Clock clock = Clock.fixed(Instant.parse("2026-12-20T03:00:05Z"), ZoneId.of("America/Sao_Paulo"));
    int sequencia;

    @BeforeEach void limparBancoDescartavel() {
        jdbc.execute("TRUNCATE faturas, medicoes, instalacoes, medidores, imoveis, clientes, enderecos RESTART IDENTITY CASCADE");
        sequencia = 0;
    }

    FaturamentoService service(Clock execucao, BigDecimal tarifa, FaturaRepository repository) {
        return new FaturamentoService(instalacoes, medicoes, repository, new Propriedades(true, tarifa), execucao, manager);
    }

    @Test void migraPadroesEEmiteTodasAsPendentesComMesmaCompetencia() {
        Long padrao = instalacao(null);
        assertEquals(1, instalacoes.findById(padrao).orElseThrow().getDiaEmissao());
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE instalacoes SET dia_emissao = 0 WHERE id = ?", padrao));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE instalacoes SET dia_emissao = 32 WHERE id = ?", padrao));

        Long instalacao = instalacao(20);
        Long referencia = medicao(instalacao, "2026-10-01T03:00:00Z", "100");
        Long primeira = medicao(instalacao, "2026-10-02T03:00:00Z", "110");
        Long segunda = medicao(instalacao, "2026-10-20T03:00:00Z", "115");
        Long futura = medicao(instalacao, "2026-12-20T03:00:06Z", "120");
        Long foraDoDia = instalacao(21);
        medicao(foraDoDia, "2026-09-01T03:00:00Z", "0");
        Long outra = medicao(foraDoDia, "2026-10-01T03:00:00Z", "10");

        service(clock, new BigDecimal("3.25"), faturas).emitirAutomaticamente();

        var emitidas = faturas.findAllByInstalacao_IdAndCompetencia(instalacao, "2026-10");
        assertEquals(2, emitidas.size());
        assertEquals(2, consulta.listar(0, 20, instalacao, "2026-10", null).content().size());
        assertTrue(faturas.existsByMedicaoAtualId(primeira));
        assertTrue(faturas.existsByMedicaoAtualId(segunda));
        assertFalse(faturas.existsByMedicaoAtualId(referencia));
        assertFalse(faturas.existsByMedicaoAtualId(futura));
        assertFalse(faturas.existsByMedicaoAtualId(outra));
        assertFalse(medicaoService.buscar(referencia).falhaEmissaoFatura());
        for (var f : emitidas) {
            assertEquals(LocalDate.of(2026, 12, 20), f.getEmissao());
            assertEquals(LocalDate.of(2026, 12, 30), f.getVencimento());
            assertEquals(new BigDecimal("3.250000"), f.getTarifa());
            assertNull(f.getBancoId());
        }
        service(clock, BigDecimal.TEN, faturas).emitirAutomaticamente();
        assertEquals(2, faturas.count());
        assertTrue(faturas.findAllByInstalacao_IdAndCompetencia(instalacao, "2026-10").stream()
                .allMatch(f -> f.getTarifa().compareTo(new BigDecimal("3.25")) == 0));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO faturas (instalacao_id, medicao_anterior_id, medicao_atual_id, competencia,
                    consumo, tarifa, total, emissao, vencimento, criada_em, situacao)
                SELECT instalacao_id, medicao_anterior_id, medicao_atual_id, competencia,
                    consumo, tarifa, total, emissao, vencimento, criada_em, situacao
                FROM faturas WHERE medicao_atual_id = ?
                """, primeira));
    }

    @Test void rollbackDaFaturaNaoDesfazMarcacaoDeFalhaENaoImpedeProximaMedicao() {
        Long instalacao = instalacao(20);
        medicao(instalacao, "2026-09-01T03:00:00Z", "100");
        Long falha = medicao(instalacao, "2026-10-01T03:00:00Z", "110");
        Long proxima = medicao(instalacao, "2026-11-01T03:00:00Z", "120");
        var repository = mock(FaturaRepository.class, delegatesTo(faturas));
        doAnswer(inv -> {
            Fatura f = inv.getArgument(0);
            var salva = faturas.saveAndFlush(f);
            if (f.getMedicaoAtualId().equals(falha)) throw new IllegalStateException("Falha apos persistir");
            return salva;
        }).when(repository).saveAndFlush(any(Fatura.class));

        service(clock, BigDecimal.ONE, repository).emitirAutomaticamente();

        assertTrue(medicaoService.buscar(falha).falhaEmissaoFatura());
        assertFalse(faturas.existsByMedicaoAtualId(falha));
        assertTrue(faturas.existsByMedicaoAtualId(proxima));
        assertFalse(medicaoService.buscar(proxima).falhaEmissaoFatura());
        assertTrue(medicoes.buscarPendentesDeEmissao(20, 31, clock.instant()).isEmpty());
        service(clock, BigDecimal.ONE, faturas).emitirAutomaticamente();
        assertEquals(1, faturas.count());
        assertTrue(medicaoService.buscar(falha).falhaEmissaoFatura());
    }

    @Test void execucoesConcorrentesNaoDuplicamNemMarcamFalha() throws Exception {
        Long instalacao = instalacao(20);
        medicao(instalacao, "2026-09-01T03:00:00Z", "100");
        Long atual = medicao(instalacao, "2026-10-01T03:00:00Z", "115");
        var service = service(clock, new BigDecimal("3.25"), faturas);
        var iniciar = new CountDownLatch(1);
        Callable<Void> tarefa = () -> {
            iniciar.await();
            service.emitirAutomaticamente();
            return null;
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(tarefa);
            var b = executor.submit(tarefa);
            iniciar.countDown();
            a.get(20, TimeUnit.SECONDS);
            b.get(20, TimeUnit.SECONDS);
        }
        assertEquals(1, faturas.count());
        assertTrue(faturas.existsByMedicaoAtualId(atual));
        assertFalse(medicaoService.buscar(atual).falhaEmissaoFatura());
    }

    @ParameterizedTest
    @CsvSource({"2027-02-28,29,1", "2027-02-28,30,1", "2027-02-28,31,1",
                "2028-02-28,29,0", "2028-02-29,31,1", "2026-04-30,31,1"})
    void selecionaDiaConfiguradoComAjusteAoFimDoMes(LocalDate data, int dia, int esperado) {
        Long instalacao = instalacao(dia);
        medicao(instalacao, "2026-01-01T03:00:00Z", "0");
        medicao(instalacao, "2026-02-01T03:00:00Z", "10");
        var execucao = Clock.fixed(data.atStartOfDay(clock.getZone()).plusSeconds(5).toInstant(), clock.getZone());
        service(execucao, BigDecimal.ONE, faturas).emitirAutomaticamente();
        assertEquals(esperado, faturas.count());
    }

    private Long instalacao(Integer dia) {
        sequencia++;
        Long endereco = jdbc.queryForObject("INSERT INTO enderecos DEFAULT VALUES RETURNING id", Long.class);
        Long cliente = jdbc.queryForObject(
                "INSERT INTO clientes (nome, cin, endereco_cobranca_id) VALUES ('Ana', ?, ?) RETURNING id",
                Long.class, String.format("%011d", sequencia), endereco);
        Long imovel = jdbc.queryForObject("INSERT INTO imoveis (endereco_id) VALUES (?) RETURNING id", Long.class, endereco);
        Long medidor = jdbc.queryForObject("INSERT INTO medidores (numero_serie) VALUES (?) RETURNING id",
                Long.class, "FAT-" + sequencia);
        Long id = jdbc.queryForObject("""
                INSERT INTO instalacoes (cliente_id, imovel_id, medidor_id, data_inicio)
                VALUES (?, ?, ?, '2026-01-01T03:00:00Z') RETURNING id
                """, Long.class, cliente, imovel, medidor);
        if (dia != null) jdbc.update("UPDATE instalacoes SET dia_emissao = ? WHERE id = ?", dia, id);
        return id;
    }

    private Long medicao(Long instalacaoId, String data, String leitura) {
        return jdbc.queryForObject("""
                INSERT INTO medicoes (instalacao_id, data_medicao, leitura)
                VALUES (?, CAST(? AS TIMESTAMP WITH TIME ZONE), ?) RETURNING id
                """, Long.class, instalacaoId, data, new BigDecimal(leitura));
    }
}
