package com.rohub.medicoes.medicoes_api.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({FaturamentoConfig.Propriedades.class, FaturamentoConfig.Banco.class})
public class FaturamentoConfig {
    @ConfigurationProperties("app.faturamento")
    public record Propriedades(boolean habilitado, BigDecimal valorM3) {
        public Propriedades {
            if (habilitado && (valorM3 == null || valorM3.signum() <= 0
                    || valorM3.scale() > 6 || valorM3.precision() - valorM3.scale() > 13)) {
                throw new IllegalArgumentException("Configure FATURAMENTO_VALOR_M3 positivo, com ate 6 casas decimais");
            }
        }
    }

    @ConfigurationProperties("app.banco")
    public record Banco(boolean habilitado, String url, String token, int timeoutMs) {
        public Banco {
            if (timeoutMs <= 0) timeoutMs = 5000;
            if (habilitado && (url == null || url.isBlank() || token == null || token.isBlank())) {
                throw new IllegalArgumentException("Configure BANCO_URL e BANCO_TOKEN");
            }
        }
    }
}
