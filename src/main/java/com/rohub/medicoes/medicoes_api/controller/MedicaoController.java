package com.rohub.medicoes.medicoes_api.controller;

import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.service.MedicaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/medicoes")
public class MedicaoController {
    private final MedicaoService service;

    public MedicaoController(MedicaoService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<MedicaoResponse> criar(@Valid @RequestBody MedicaoRequest request) {
        var response = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public MedicaoResponse buscar(@PathVariable @Positive Long id) { return service.buscar(id); }

    @GetMapping
    public PaginaResponse<MedicaoResponse> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @Positive Long instalacaoId) {
        return service.listar(page, size, instalacaoId);
    }
}
