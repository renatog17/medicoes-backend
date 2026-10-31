package com.rohub.medicoes.medicoes_api.integracao;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Banco;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class BancoHttpClient implements BancoGateway {
    private final Banco config;
    private final RestClient client;

    public BancoHttpClient(Banco config) {
        this.config = config;
        var timeout = Duration.ofMillis(config.timeoutMs());
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(config.url() == null ? "http://localhost" : config.url())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Authorization", "Bearer " + config.token()).requestFactory(factory).build();
    }

    private void verificar() {
        if (!config.habilitado()) throw new BancoIndisponivelException("Integracao bancaria desabilitada");
    }

    @Override public Cobranca registrar(Registro registro) {
        verificar();
        try {
            return client.post().uri("/api/cobrancas").header("Idempotency-Key", registro.referenciaExterna())
                    .body(registro).retrieve().body(Cobranca.class);
        } catch (RestClientException ex) {
            throw new BancoIndisponivelException("Nao foi possivel registrar a cobranca; tente novamente");
        }
    }
    @Override public Cobranca consultar(String id) {
        verificar();
        try {
            return client.get().uri("/api/cobrancas/{id}", id).retrieve().body(Cobranca.class);
        } catch (RestClientException ex) {
            throw new BancoIndisponivelException("Nao foi possivel consultar a cobranca");
        }
    }
    @Override public Optional<Cobranca> buscarPorReferencia(String referencia) {
        verificar();
        try {
            return Optional.ofNullable(client.get().uri(builder -> builder.path("/api/cobrancas")
                    .queryParam("referenciaExterna", referencia).build()).retrieve().body(Cobranca.class));
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new BancoIndisponivelException("Nao foi possivel recuperar a cobranca pela referencia");
        }
    }
}
