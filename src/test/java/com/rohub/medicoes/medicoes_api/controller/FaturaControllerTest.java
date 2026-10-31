package com.rohub.medicoes.medicoes_api.controller;

import com.rohub.medicoes.medicoes_api.domain.Fatura;
import com.rohub.medicoes.medicoes_api.dto.PaginaResponse;
import com.rohub.medicoes.medicoes_api.exception.*;
import com.rohub.medicoes.medicoes_api.service.FaturaConsultaService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FaturaControllerTest {
    final FaturaConsultaService consulta = mock(FaturaConsultaService.class);
    final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new FaturaController(consulta))
            .setControllerAdvice(new ApiExceptionHandler()).build();
    @Test void naoExpoeAcoesManuais() throws Exception {
        mvc.perform(post("/api/faturas/emissoes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"competencia\":\"2026-10\"}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/faturas/1/cobranca")).andExpect(status().isNotFound());
        mvc.perform(post("/api/faturas/1/sincronizacao")).andExpect(status().isNotFound());
        verifyNoInteractions(consulta);
    }
    @Test void rejeitaCompetenciaInvalidaNaConsulta() throws Exception {
        for (String valor : List.of("2026-13", "2026-1", "outubro")) {
            mvc.perform(get("/api/faturas").param("competencia", valor))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(consulta);
    }
    @Test void consultaPorIdPreservaErroDeAusencia() throws Exception {
        when(consulta.buscar(1L)).thenThrow(new RecursoNaoEncontradoException("Fatura", 1L));
        mvc.perform(get("/api/faturas/1")).andExpect(status().isNotFound());
        verify(consulta).buscar(1L);
    }
    @Test void consultaFiltrada() throws Exception {
        when(consulta.listar(0, 20, 1L, "2026-10", Fatura.Situacao.ABERTA))
                .thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/faturas?instalacaoId=1&competencia=2026-10&situacao=ABERTA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        verify(consulta).listar(0, 20, 1L, "2026-10", com.rohub.medicoes.medicoes_api.domain.Fatura.Situacao.ABERTA);
    }
}
