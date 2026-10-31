# Contrato da API bancária — cobranças Pix

Estes endpoints são fornecidos **pelo sistema do banco**, conforme a coleção
`bruno/banco-api.postman_collection.json`. A Medições API
atua como cliente HTTP. Valores são em reais (BRL), números JSON decimais com
até duas casas. Datas usam `AAAA-MM-DD`; instantes usam ISO 8601 UTC.
Autenticação: `Authorization: Bearer <BANCO_TOKEN>` em todas as requisições.
O banco deve validar o token e restringir consultas às cobranças do credor autenticado.
Configure HTTPS no ambiente de produção.

## 1. Registrar cobrança

`POST /api/cobrancas`

Headers: `Content-Type: application/json`, `Idempotency-Key: fatura-123`.

```json
{
  "referenciaExterna": "fatura-123",
  "valor": 48.75,
  "moeda": "BRL",
  "vencimento": "2026-11-10",
  "pagador": { "nome": "Ana Silva", "documento": "12345678909" }
}
```

O documento é o CIN de 11 dígitos armazenado no cadastro. O banco descrito na
coleção retorna Pix de simulação e não movimenta dinheiro real.

Resposta `201 Created` para criação, `200 OK` para repetição idempotente:

```json
{
  "id": "cob-987",
  "referenciaExterna": "fatura-123",
  "valor": 48.75,
  "moeda": "BRL",
  "situacao": "PENDENTE",
  "pixCopiaECola": "codigo-pix-gerado-pelo-banco",
  "valorPago": null,
  "pagoEm": null
}
```

- Exigir valor positivo, BRL, referência e chave idênticas, vencimento e pagador.
- Garantir unicidade de referência e chave por credor, inclusive em concorrência.
- Repetição com mesmo conteúdo retorna a cobrança original e o mesmo identificador.
- Mesma chave/referência com conteúdo diferente retorna `409 Conflict`.
- Preservar a referência durante toda a vida da cobrança, inclusive após pagamento.
- Uma fatura tem somente uma cobrança; não recriar cobrança expirada ou cancelada
  automaticamente. O débito permanece aberto para tratamento operacional futuro.
- Para emissões atrasadas, a data de vencimento original permanece dia 10.
  O banco deve aceitar o registro com vencimento passado e permitir o pagamento
  sem juros/multa nesta versão; a expiração técnica do Pix é independente dessa data.

## 2. Consultar cobrança por identificador

`GET /api/cobrancas/cob-987`

Retornar `200 OK` com o mesmo objeto do registro, atualizado. Exemplo de liquidação:

```json
{
  "id": "cob-987",
  "referenciaExterna": "fatura-123",
  "valor": 48.75,
  "moeda": "BRL",
  "situacao": "PAGA",
  "pixCopiaECola": "codigo-pix-gerado-pelo-banco",
  "valorPago": 48.75,
  "pagoEm": "2026-11-08T14:30:00Z"
}
```

Situações permitidas: `PENDENTE`, `PAGA`, `EXPIRADA`, `CANCELADA`.
`PENDENTE` exige `pixCopiaECola`. `PAGA` exige valor integral e instante de pagamento.
Não há pagamento parcial, estorno, juros ou multa nesta versão. A Medições API
somente quita quando o valor pago é igual ao total e existe instante de pagamento.
Expiração e cancelamento não quitam a fatura. Retornar `404` para identificador ausente.

## 3. Recuperar por referência externa

`GET /api/cobrancas?referenciaExterna=fatura-123`

Retornar **um objeto**, no mesmo formato acima, com `200 OK`; retornar `404`
se ausente. Não retornar array nem página. A consulta deve enxergar imediatamente
uma cobrança já confirmada pelo registro. É usada antes de novas tentativas para
recuperar registros cujo resultado se perdeu por timeout.

## Falhas e operação

Usar `400` para entrada inválida, `401/403` para credencial inválida/sem permissão,
`404` para ausência, `409` para conflito de idempotência, `429` para limite e
`500/503` para indisponibilidade. Corpo de erro sugerido: `application/problem+json`
com `title`, `status` e `detail`, sem credenciais ou dados sensíveis.

O cliente usa timeout configurável (padrão 5 segundos), sem repetição imediata de
POST. Falhas mantêm a fatura local em aberto. Na próxima tentativa, consulta a
referência e reutiliza a mesma chave no POST. A sincronização automática ocorre
a cada 15 minutos, sob controle interno da API. Não há endpoints manuais de
emissão, registro ou sincronização na Medições API, nem webhook nesta versão.

## Configurar a Medições API

```dotenv
FATURAMENTO_HABILITADO=true
FATURAMENTO_VALOR_M3=3.250000
BANCO_HABILITADO=true
BANCO_URL=https://banco.exemplo
BANCO_TOKEN=definir-em-segredo-do-ambiente
BANCO_TIMEOUT_MS=5000
BANCO_SINCRONIZACAO_MS=900000
```

A tarifa acima é apenas exemplo. Faturamento e banco vêm desabilitados.
A tarifa exige reinício para alteração e fica preservada em cada fatura emitida.
Use referências únicas por ambiente/credor: bancos de produção e teste não devem
compartilhar o mesmo namespace de referências `fatura-{id}`.

## Execução local e validação no Bruno

Execute o banco com `SERVER_PORT=9090` e a Medições API na porta `8080`.
Na Medições API, configure `BANCO_URL=http://localhost:9090`, `BANCO_TOKEN`
com o token do credor e as variáveis de habilitação e tarifa acima. Ao executar
pela IDE ou Maven, forneça essas variáveis no ambiente do processo: o arquivo
`.env` é usado pelo Compose e não é carregado automaticamente pelo Spring.

1. Importe as duas coleções em `bruno/`. A coleção Medições usa
   `http://localhost:8080/api`; a coleção Banco usa `http://localhost:9090`.
2. Cadastre cliente, imóvel, medidor e instalação na Medições API. Registre uma
   leitura inicial e outra leitura no mês anterior ao atual, em ordem cronológica,
   com consumo positivo e datas dentro da vigência da instalação.
3. Aguarde a emissão diária às `00:00:05` de Brasília (`America/Sao_Paulo`) ou, se a fatura já
   existir, a sincronização periódica. Consulte as faturas para obter `id`,
   `bancoId`, `pix` e `situacaoBancaria`. A emissão usa o mês anterior ao atual.
4. Na coleção Banco, informe `referenciaExterna=fatura-{id}` e consulte por
   referência. Copie o ID da cobrança para `cobrancaId`. Não registre outra
   cobrança manualmente para essa fatura.
5. Com o banco no perfil `simulacao`, configure `operadorToken` do mesmo credor
   e execute **Simular pagamento integral**. Esse token é usado somente no Bruno.
6. Aguarde a sincronização (padrão: 15 minutos) e consulte a fatura na Medições
   API: deve retornar `PAGA`, valor integral e instante do pagamento.
7. Use faturas distintas para simular cancelamento e expiração. Após sincronizar,
   elas permanecem `ABERTA`, com a situação bancária correspondente e o mesmo ID.

Para encurtar a espera pela sincronização em desenvolvimento, configure
`BANCO_SINCRONIZACAO_MS=5000` antes de iniciar a Medições API. A emissão continua
no horário diário; não há endpoint manual. Falhas de conexão preservam a fatura
para nova tentativa automática. Para diagnóstico, consulte os logs de ambas as APIs.
