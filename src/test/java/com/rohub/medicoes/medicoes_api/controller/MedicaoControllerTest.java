package com.rohub.medicoes.medicoes_api.controller;

import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.service.MedicaoService;
import com.rohub.medicoes.medicoes_api.exception.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MedicaoControllerTest {
    private final MedicaoService service = mock(MedicaoService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new MedicaoController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    private final MedicaoResponse resposta = new MedicaoResponse(7L, 1L, Instant.parse("2026-01-01T00:00:00Z"), BigDecimal.ZERO);

    @Test
    void criaReferenciaEConsulta() throws Exception {
        when(service.criar(any())).thenReturn(resposta);
        mvc.perform(post("/api/medicoes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"instalacaoId\":1,\"dataMedicao\":\"2026-01-01T00:00:00Z\",\"leitura\":0}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "http://localhost/api/medicoes/7"))
                .andExpect(jsonPath("$.leitura").value(0)).andExpect(jsonPath("$.instalacaoId").value(1));
        when(service.buscar(7L)).thenReturn(resposta);
        mvc.perform(get("/api/medicoes/7")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
        when(service.buscar(99L)).thenThrow(new RecursoNaoEncontradoException("Medicao", 99L));
        mvc.perform(get("/api/medicoes/99")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1", "10000000000000", "0.1234567"})
    void validaLeitura(String leitura) throws Exception {
        mvc.perform(post("/api/medicoes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"instalacaoId\":1,\"dataMedicao\":\"2026-01-01T00:00:00Z\",\"leitura\":" + leitura + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("leitura"));
        verifyNoInteractions(service);
    }

    @Test
    void validaCamposParametrosEMetodos() throws Exception {
        mvc.perform(post("/api/medicoes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        for (String url : List.of("/api/medicoes/0", "/api/medicoes?instalacaoId=0", "/api/medicoes?page=abc")) {
            mvc.perform(get(url)).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/medicoes/1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/medicoes/1")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    @Test
    void encaminhaPaginacaoEFiltro() throws Exception {
        when(service.listar(0, 20, null)).thenReturn(new PaginaResponse<>(List.of(resposta), 0, 20, 1, 1));
        mvc.perform(get("/api/medicoes")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(7));
        when(service.listar(1, 10, 1L)).thenReturn(new PaginaResponse<>(List.of(), 1, 10, 0, 0));
        mvc.perform(get("/api/medicoes?page=1&size=10&instalacaoId=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
        verify(service).listar(1, 10, 1L);
    }
}
