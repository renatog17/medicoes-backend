package com.rohub.medicoes.medicoes_api.integracao;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Banco;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class BancoHttpClientTest {
    HttpServer server;
    BancoHttpClient client;
    AtomicReference<String> auth = new AtomicReference<>();
    AtomicReference<String> chave = new AtomicReference<>();
    AtomicReference<String> corpo = new AtomicReference<>();
    AtomicReference<String> metodo = new AtomicReference<>();
    AtomicReference<String> uri = new AtomicReference<>();
    AtomicReference<String> accept = new AtomicReference<>();
    AtomicReference<String> contentType = new AtomicReference<>();
    int status = 200;
    String resposta = """
        {"id":"cob-1","referenciaExterna":"fatura-1","valor":10.00,"moeda":"BRL",
         "situacao":"PENDENTE","pixCopiaECola":"pix","valorPago":null,"pagoEm":null}
        """;
    @BeforeEach void iniciar() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/cobrancas", exchange -> {
            metodo.set(exchange.getRequestMethod());
            uri.set(exchange.getRequestURI().toString());
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            chave.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            corpo.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        client = new BancoHttpClient(new Banco(true, "http://127.0.0.1:" + server.getAddress().getPort(), "segredo", 1000));
    }
    @AfterEach void parar() { server.stop(0); }
    @Test void registraComChaveEAutenticacao() {
        status = 201;
        var resultado = client.registrar(new BancoGateway.Registro("fatura-1", BigDecimal.TEN, "BRL",
                LocalDate.of(2026, 11, 10), new BancoGateway.Pagador("Ana", "12345678909")));
        assertEquals("cob-1", resultado.id());
        assertEquals("Bearer segredo", auth.get());
        assertEquals("fatura-1", chave.get());
        assertEquals("POST", metodo.get());
        assertEquals("/api/cobrancas", uri.get());
        assertEquals("application/json", accept.get());
        assertTrue(contentType.get().startsWith("application/json"));
        assertTrue(corpo.get().contains("referenciaExterna"));
        assertTrue(corpo.get().contains("2026-11-10"));
    }
    @Test void recuperaReferenciaEConsultaPagamento() {
        assertEquals("cob-1", client.buscarPorReferencia("fatura-1").orElseThrow().id());
        assertEquals("GET", metodo.get());
        assertEquals("/api/cobrancas?referenciaExterna=fatura-1", uri.get());
        assertEquals("Bearer segredo", auth.get());
        assertEquals("application/json", accept.get());
        resposta = """
            {"id":"cob-1","referenciaExterna":"fatura-1","valor":10,"moeda":"BRL",
             "situacao":"PAGA","valorPago":10,"pagoEm":"2026-11-08T14:30:00Z"}
            """;
        assertEquals("PAGA", client.consultar("cob-1").situacao());
        assertNotNull(client.consultar("cob-1").pagoEm());
        assertEquals("/api/cobrancas/cob-1", uri.get());
        assertNull(chave.get());
    }
    @Test void aceitaRepeticaoIdempotenteComStatus200() {
        assertEquals("cob-1", client.registrar(new BancoGateway.Registro("fatura-1", BigDecimal.TEN, "BRL",
                LocalDate.of(2026, 11, 10), new BancoGateway.Pagador("Ana", "12345678909"))).id());
    }
    @Test void falhasNaoSaoInterpretadasComoReferenciaAusente() {
        for (int erro : new int[] {401, 403, 409, 429, 500, 503}) {
            status = erro;
            assertThrows(BancoIndisponivelException.class, () -> client.buscarPorReferencia("fatura-1"));
            assertThrows(BancoIndisponivelException.class, () -> client.consultar("cob-1"));
            assertThrows(BancoIndisponivelException.class, () -> client.registrar(new BancoGateway.Registro(
                    "fatura-1", BigDecimal.TEN, "BRL", LocalDate.of(2026, 11, 10),
                    new BancoGateway.Pagador("Ana", "12345678909"))));
        }
    }
    @Test void distingueAusenciaDeIndisponibilidade() {
        status = 404;
        assertTrue(client.buscarPorReferencia("fatura-1").isEmpty());
        status = 503;
        assertThrows(BancoIndisponivelException.class, () -> client.buscarPorReferencia("fatura-1"));
        assertThrows(BancoIndisponivelException.class, () -> client.consultar("cob-1"));
    }
    @Test void timeoutPermiteNovaTentativa() {
        server.removeContext("/api/cobrancas");
        server.createContext("/api/cobrancas", exchange -> {
            try { Thread.sleep(200); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        var curto = new BancoHttpClient(new Banco(true, "http://127.0.0.1:" + server.getAddress().getPort(), "segredo", 30));
        assertThrows(BancoIndisponivelException.class, () -> curto.consultar("cob-1"));
    }
}
