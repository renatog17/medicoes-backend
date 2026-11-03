package com.rohub.medicoes.medicoes_api;

import com.rohub.medicoes.medicoes_api.scheduler.CacheLeituraAnteriorService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class CacheLeituraAnteriorIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource static void banco(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CacheLeituraAnteriorService cache;

    @Test void preencheHistoricoPorDataSemMisturarInstalacoesEPermiteLeituraZero() {
        Long primeiraInstalacao = instalacao("CACHE-1", "00000000001");
        Long primeira = medicao(primeiraInstalacao, "2020-01-01T12:00:00Z", "0");
        Long terceira = medicao(primeiraInstalacao, "2020-03-01T12:00:00Z", "20");
        Long segunda = medicao(primeiraInstalacao, "2020-02-01T12:00:00Z", "10");
        Long outraInstalacao = instalacao("CACHE-2", "00000000002");
        Long outraPrimeira = medicao(outraInstalacao, "2020-01-15T12:00:00Z", "100");
        Long outraSegunda = medicao(outraInstalacao, "2020-02-15T12:00:00Z", "130");

        assertEquals(3, cache.preencherPendentes());

        assertNull(leituraAnterior(primeira));
        assertEquals(new BigDecimal("0.000000"), leituraAnterior(segunda));
        assertEquals(new BigDecimal("10.000000"), leituraAnterior(terceira));
        assertNull(leituraAnterior(outraPrimeira));
        assertEquals(new BigDecimal("100.000000"), leituraAnterior(outraSegunda));
        assertEquals(0, cache.preencherPendentes());
    }

    @Test void preservaCachePreenchidoEPreencheNovasMedicoesNaProximaExecucao() {
        Long instalacao = instalacao("CACHE-1", "00000000001");
        Long primeira = medicao(instalacao, "2020-01-01T12:00:00Z", "100");
        Long segunda = medicao(instalacao, "2020-02-01T12:00:00Z", "110");
        jdbc.update("UPDATE medicoes SET cache_leitura_anterior = 42 WHERE id = ?", segunda);

        assertEquals(0, cache.preencherPendentes());
        assertNull(leituraAnterior(primeira));
        assertEquals(new BigDecimal("42.000000"), leituraAnterior(segunda));

        Long terceira = medicao(instalacao, "2020-03-01T12:00:00Z", "120");
        assertEquals(1, cache.preencherPendentes());
        assertEquals(new BigDecimal("110.000000"), leituraAnterior(terceira));
        assertEquals(new BigDecimal("42.000000"), leituraAnterior(segunda));
        assertEquals(0, cache.preencherPendentes());
    }

    @Test void exigeDataAnteriorEDesempataLeiturasAnterioresPeloMaiorId() {
        Long instalacao = instalacao("CACHE-1", "00000000001");
        Long primeira = medicao(instalacao, "2020-01-01T12:00:00Z", "100");
        Long mesmoInstante = medicao(instalacao, "2020-01-01T12:00:00Z", "101");
        Long seguinte = medicao(instalacao, "2020-02-01T12:00:00Z", "120");

        assertEquals(1, cache.preencherPendentes());
        assertNull(leituraAnterior(primeira));
        assertNull(leituraAnterior(mesmoInstante));
        assertEquals(new BigDecimal("101.000000"), leituraAnterior(seguinte));
    }

    private Long instalacao(String serie, String cin) {
        Long endereco = jdbc.queryForObject("INSERT INTO enderecos DEFAULT VALUES RETURNING id", Long.class);
        Long cliente = jdbc.queryForObject("""
                INSERT INTO clientes (nome, cin, endereco_cobranca_id)
                VALUES ('Ana', ?, ?) RETURNING id
                """, Long.class, cin, endereco);
        Long imovel = jdbc.queryForObject("INSERT INTO imoveis (endereco_id) VALUES (?) RETURNING id", Long.class, endereco);
        Long medidor = jdbc.queryForObject("INSERT INTO medidores (numero_serie) VALUES (?) RETURNING id", Long.class, serie);
        return jdbc.queryForObject("""
                INSERT INTO instalacoes (cliente_id, imovel_id, medidor_id, data_inicio)
                VALUES (?, ?, ?, '2020-01-01T00:00:00Z') RETURNING id
                """, Long.class, cliente, imovel, medidor);
    }

    private Long medicao(Long instalacaoId, String data, String leitura) {
        return jdbc.queryForObject("""
                INSERT INTO medicoes (instalacao_id, data_medicao, leitura)
                VALUES (?, CAST(? AS TIMESTAMP WITH TIME ZONE), ?) RETURNING id
                """, Long.class, instalacaoId, data, new BigDecimal(leitura));
    }

    private BigDecimal leituraAnterior(Long medicaoId) {
        return jdbc.queryForObject("SELECT cache_leitura_anterior FROM medicoes WHERE id = ?", BigDecimal.class, medicaoId);
    }
}
