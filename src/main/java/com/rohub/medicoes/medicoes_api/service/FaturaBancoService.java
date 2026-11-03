package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.Fatura;
import com.rohub.medicoes.medicoes_api.dto.FaturaResponse;
import com.rohub.medicoes.medicoes_api.exception.*;
import com.rohub.medicoes.medicoes_api.integracao.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Set;

@Service
public class FaturaBancoService {
    private final FaturaRepository faturas;
    private final ClienteRepository clientes;
    private final BancoGateway banco;
    private final TransactionTemplate transactions;
    public FaturaBancoService(FaturaRepository faturas, ClienteRepository clientes, BancoGateway banco,
            org.springframework.transaction.PlatformTransactionManager manager) {
        this.faturas = faturas; this.clientes = clientes; this.banco = banco;
        this.transactions = new TransactionTemplate(manager);
    }
    private Fatura buscar(Long id) {
        return faturas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Fatura", id));
    }
    public FaturaResponse registrar(Long id) {
        var fatura = buscar(id);
        if (fatura.getSituacao() != Fatura.Situacao.ABERTA) return FaturaResponse.from(fatura);
        if (fatura.getBancoId() != null) return sincronizar(id);
        String referencia = "fatura-" + id;
        var existente = banco.buscarPorReferencia(referencia);
        BancoGateway.Cobranca cobranca;
        if (existente.isPresent()) cobranca = existente.get();
        else {
            var cliente = clientes.findById(fatura.getClienteId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", fatura.getClienteId()));
            cobranca = banco.registrar(new BancoGateway.Registro(referencia, fatura.getTotal(), "BRL",
                    fatura.getVencimento(), new BancoGateway.Pagador(cliente.getNome(), cliente.getCin())));
        }
        return salvar(id, cobranca);
    }
    public FaturaResponse sincronizar(Long id) {
        var fatura = buscar(id);
        if (fatura.getSituacao() != Fatura.Situacao.ABERTA) return FaturaResponse.from(fatura);
        if (fatura.getBancoId() == null) return FaturaResponse.from(fatura);
        return salvar(id, banco.consultar(fatura.getBancoId()));
    }
    private FaturaResponse salvar(Long id, BancoGateway.Cobranca resposta) {
        return transactions.execute(status -> {
            var fatura = buscar(id);
            if (resposta == null || resposta.id() == null || resposta.id().isBlank()
                    || ! ("fatura-" + id).equals(resposta.referenciaExterna())
                    || resposta.valor() == null || resposta.valor().compareTo(fatura.getTotal()) != 0
                    || !"BRL".equals(resposta.moeda())
                    || resposta.situacao() == null || !Set.of("PENDENTE", "PAGA", "EXPIRADA", "CANCELADA").contains(resposta.situacao())
                    || (fatura.getBancoId() != null && !fatura.getBancoId().equals(resposta.id()))
                    || ("PENDENTE".equals(resposta.situacao()) && (resposta.pixCopiaECola() == null || resposta.pixCopiaECola().isBlank()))) {
                throw new BancoIndisponivelException("Resposta bancaria invalida ou divergente da fatura");
            }
            if (fatura.getSituacao() == Fatura.Situacao.ABERTA) {
                fatura.atualizarBanco(resposta.id(), resposta.pixCopiaECola(), resposta.situacao(), resposta.valorPago(), resposta.pagoEm());
                faturas.saveAndFlush(fatura);
            }
            return FaturaResponse.from(fatura);
        });
    }
}
