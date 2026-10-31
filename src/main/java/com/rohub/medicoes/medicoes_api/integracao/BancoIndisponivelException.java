package com.rohub.medicoes.medicoes_api.integracao;

public class BancoIndisponivelException extends RuntimeException {
    public BancoIndisponivelException(String mensagem) { super(mensagem); }
}
