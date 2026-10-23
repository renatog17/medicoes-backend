package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.*;
import com.rohub.medicoes.medicoes_api.dto.*;
public final class ApiMapper {
    private ApiMapper() {}
    public static MedicaoResponse response(Medicao m) {
        return new MedicaoResponse(m.getId(), m.getInstalacao().getId(), m.getDataMedicao(), m.getLeitura());
    }
    public static EnderecoResponse response(Endereco e) {
        return new EnderecoResponse(e.getId(), e.getLogradouro(), e.getNumero(), e.getComplemento(),
            e.getBairro(), e.getCidade(), e.getUf(), e.getCep());
    }
    public static void preencher(Endereco e, EnderecoRequest r) {
        e.setLogradouro(r.logradouro()); e.setNumero(r.numero()); e.setComplemento(r.complemento());
        e.setBairro(r.bairro()); e.setCidade(r.cidade()); e.setUf(r.uf()); e.setCep(r.cep());
    }
    public static ClienteResponse response(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNome(), c.getCin(), c.getEmail(),
            c.getTelefone(), response(c.getEnderecoCobranca()));
    }
    public static ImovelResponse response(Imovel i) {
        return new ImovelResponse(i.getId(), response(i.getEndereco()));
    }
    public static MedidorResponse response(Medidor m) {
        return new MedidorResponse(m.getId(), m.getNumeroSerie(), m.getFabricante(), m.getModelo());
    }
    public static InstalacaoResponse response(Instalacao i) {
        return new InstalacaoResponse(i.getId(), i.getCliente().getId(), i.getImovel().getId(),
            i.getMedidor().getId(), i.getDataInicio(), i.getDataFim(), i.getObservacoes(),
            i.getVersao(), i.getDataFim() == null);
    }
}
