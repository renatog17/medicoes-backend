package com.rohub.medicoes.medicoes_api.controller;

import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
@RestController
@RequestMapping("/api/medidores")
public class MedidorController {
    private final MedidorService service;
    public MedidorController(MedidorService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<MedidorResponse> criar(@Valid @RequestBody MedidorRequest request) {
        var response = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }
    @GetMapping
    public PaginaResponse<MedidorResponse> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listar(page, size);
    }
    @GetMapping("/{id}")
    public MedidorResponse buscar(@PathVariable @Positive Long id) { return service.buscar(id); }
    @PutMapping("/{id}")
    public MedidorResponse atualizar(@PathVariable @Positive Long id, @Valid @RequestBody MedidorRequest request) {
        return service.atualizar(id, request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable @Positive Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
