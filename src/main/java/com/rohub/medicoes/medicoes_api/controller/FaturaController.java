package com.rohub.medicoes.medicoes_api.controller;

import com.rohub.medicoes.medicoes_api.domain.Fatura;
import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.exception.RequisicaoInvalidaException;
import com.rohub.medicoes.medicoes_api.service.FaturaConsultaService;
import jakarta.validation.constraints.Positive;
import java.time.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/faturas")
public class FaturaController {
    private final FaturaConsultaService consulta;
    public FaturaController(FaturaConsultaService consulta) {
        this.consulta = consulta;
    }
    @GetMapping("/{id}")
    public FaturaResponse buscar(@PathVariable @Positive Long id) { return consulta.buscar(id); }
    @GetMapping
    public PaginaResponse<FaturaResponse> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) @Positive Long instalacaoId,
            @RequestParam(required = false) String competencia, @RequestParam(required = false) Fatura.Situacao situacao) {
        if (competencia != null) competencia(competencia);
        return consulta.listar(page, size, instalacaoId, competencia, situacao);
    }
    private YearMonth competencia(String valor) {
        try {
            if (valor == null || !valor.matches("\\d{4}-\\d{2}")) throw new DateTimeException("Formato invalido");
            return YearMonth.parse(valor);
        } catch (DateTimeException ex) { throw new RequisicaoInvalidaException("Competencia deve usar AAAA-MM valido"); }
    }
}
