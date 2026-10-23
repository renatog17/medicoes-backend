package com.rohub.medicoes.medicoes_api.controller;

import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
@RestController
@RequestMapping("/api/instalacoes")
public class InstalacaoController {
    private final InstalacaoService service;
    public InstalacaoController(InstalacaoService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<InstalacaoResponse> criar(@Valid @RequestBody InstalacaoRequest request) {
        var response = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }
    @GetMapping("/{id}")
    public InstalacaoResponse buscar(@PathVariable @Positive Long id) { return service.buscar(id); }
    @GetMapping
    public PaginaResponse<InstalacaoResponse> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @Positive Long clienteId,
            @RequestParam(required = false) @Positive Long imovelId,
            @RequestParam(required = false) @Positive Long medidorId,
            @RequestParam(required = false) Boolean ativa) {
        return service.listar(page, size, clienteId, imovelId, medidorId, ativa);
    }
    @PostMapping("/{id}/encerramento")
    public InstalacaoResponse encerrar(@PathVariable @Positive Long id,
            @Valid @RequestBody EncerramentoRequest request) {
        return service.encerrar(id, request);
    }
}
