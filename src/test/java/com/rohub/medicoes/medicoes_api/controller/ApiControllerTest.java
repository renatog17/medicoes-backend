package com.rohub.medicoes.medicoes_api.controller;

import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.service.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiControllerTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "", ",\"cin\":null", ",\"cin\":\"\"", ",\"cin\":\"529.982.247-25\"",
        ",\"cin\":\"52998224724\""
    })
    void rejeitaCinAusenteOuInvalidoNaCriacaoEAtualizacao(String campoCin) throws Exception {
        var clientes = mock(ClienteService.class);
        var cadastroMvc = MockMvcBuilders.standaloneSetup(new ClienteController(clientes))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        String body = "{\"nome\":\"Ana\",\"enderecoCobranca\":{}" + campoCin + "}";
        for (var request : List.of(post("/api/clientes"), put("/api/clientes/1"))) {
            cadastroMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cin"));
        }
        verifyNoInteractions(clientes);
    }

    private final MedidorService service = mock(MedidorService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MedidorController(service))
        .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void criaComLocation() throws Exception {
        when(service.criar(any())).thenReturn(new MedidorResponse(7L, "ABC", null, null));
        mvc.perform(post("/api/medidores").contentType(MediaType.APPLICATION_JSON)
            .content("{\"numeroSerie\":\"ABC\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "http://localhost/api/medidores/7"))
            .andExpect(jsonPath("$.id").value(7));
    }
    @Test
    void validaRequestEParametros() throws Exception {
        mvc.perform(post("/api/medidores").contentType(MediaType.APPLICATION_JSON)
            .content("{\"numeroSerie\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("numeroSerie"));
        mvc.perform(get("/api/medidores/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/medidores?page=abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test
    void retornaPaginaEstavel() throws Exception {
        when(service.listar(0, 20)).thenReturn(new PaginaResponse<>(
            List.of(new MedidorResponse(7L, "ABC", null, null)), 0, 20, 1, 1));
        mvc.perform(get("/api/medidores")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(7)).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test
    void traduzErrosEExclui() throws Exception {
        when(service.buscar(9L)).thenThrow(new RecursoNaoEncontradoException("Medidor", 9L));
        mvc.perform(get("/api/medidores/9")).andExpect(status().isNotFound());
        doThrow(new ConflitoException("Medidor possui instalacoes")).when(service).excluir(9L);
        mvc.perform(delete("/api/medidores/9")).andExpect(status().isConflict());
        mvc.perform(delete("/api/medidores/7")).andExpect(status().isNoContent());
    }

    @Test
    void atualizaCadastroCompleto() throws Exception {
        when(service.atualizar(eq(7L), any())).thenReturn(new MedidorResponse(7L, "NOVO", null, null));
        mvc.perform(put("/api/medidores/7").contentType(MediaType.APPLICATION_JSON)
            .content("{\"numeroSerie\":\"NOVO\"}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.numeroSerie").value("NOVO"));
        verify(service).atualizar(7L, new MedidorRequest("NOVO", null, null));
    }

    @Test
    void exigeEnderecoEValidaCamposAninhados() throws Exception {
        var clientes = mock(ClienteService.class);
        var imoveis = mock(ImovelService.class);
        var cadastroMvc = MockMvcBuilders.standaloneSetup(new ClienteController(clientes), new ImovelController(imoveis))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        cadastroMvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON)
            .content("{\"nome\":\"Ana\"}")).andExpect(status().isBadRequest());
        cadastroMvc.perform(post("/api/imoveis").contentType(MediaType.APPLICATION_JSON)
            .content("{\"endereco\":{\"uf\":\"BAHIA\"}}")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("endereco.uf"));
        verifyNoInteractions(clientes, imoveis);
    }

    @Test
    void encaminhaFiltrosCombinadosDeInstalacoes() throws Exception {
        var instalacoes = mock(InstalacaoService.class);
        when(instalacoes.listar(1, 10, 2L, 3L, 4L, false))
            .thenReturn(new PaginaResponse<>(List.of(), 1, 10, 0, 0));
        var instalacaoMvc = MockMvcBuilders.standaloneSetup(new InstalacaoController(instalacoes))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        instalacaoMvc.perform(get("/api/instalacoes?page=1&size=10&clienteId=2&imovelId=3&medidorId=4&ativa=false"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
        verify(instalacoes).listar(1, 10, 2L, 3L, 4L, false);
        instalacaoMvc.perform(put("/api/instalacoes/1")).andExpect(status().isMethodNotAllowed());
    }
}
