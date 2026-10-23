# API de cadastros, instalações e medições

Base: `/api`. A aplicação usa a porta 8080 e PostgreSQL.
Configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e
`SPRING_DATASOURCE_PASSWORD`. Flyway cria o schema e Hibernate valida os mapeamentos.

## Endpoints

| Recurso | Operações |
|---|---|
| `/clientes`, `/imoveis`, `/medidores` | POST para criar; GET para listar |
| `/clientes/{id}`, `/imoveis/{id}`, `/medidores/{id}` | GET, PUT, DELETE |
| `/instalacoes` | POST para criar; GET para listar |
| `/instalacoes/{id}` | GET |
| `/instalacoes/{id}/encerramento` | POST |
| `/medicoes` | POST para criar; GET para listar |
| `/medicoes/{id}` | GET |

POST de criação retorna 201 com Location. PUT substitui os campos editáveis;
campos opcionais omitidos ficam nulos. DELETE retorna 204 e é bloqueado quando
o cadastro tem qualquer instalação vinculada, inclusive encerrada.

Listagens recebem `page=0&size=20` (size de 1 a 100) e ordenam por ID crescente.
A resposta contém `content`, `page`, `size`, `totalElements` e `totalPages`.
Instalações aceitam filtros combinados `clienteId`, `imovelId`, `medidorId` e `ativa`.

## Exemplo de cliente

```json
{
  "nome": "Ana",
  "cin": "123456789",
  "email": "ana@example.com",
  "telefone": "71999999999",
  "enderecoCobranca": {
    "logradouro": "Rua A",
    "numero": "10",
    "complemento": null,
    "bairro": "Centro",
    "cidade": "Salvador",
    "uf": "BA",
    "cep": "40000000"
  }
}
```

Imóvel recebe `{"endereco": {...}}` com a mesma estrutura de endereço.
Medidor recebe `numeroSerie` obrigatório, `fabricante` e `modelo` opcionais.
Objetos de endereço são obrigatórios, seus campos internos são opcionais.
Respostas incluem os IDs gerados. Não há endpoint separado para endereços.
Alterar um endereço compartilhado cria uma cópia para o cadastro atualizado.
Endereços antigos não são apagados automaticamente.

## Instalações

Criação:

```json
{
  "clienteId": 1,
  "imovelId": 1,
  "medidorId": 1,
  "dataInicio": "2026-10-14T12:00:00Z",
  "observacoes": null
}
```

Encerramento recebe `{"dataFim":"2026-10-15T12:00:00Z"}`, posterior ao início.
Para trocar titular ou medidor, encerre a instalação atual e crie outra.
As duas chamadas têm transações independentes. Não há edição ou exclusão de instalações.
Um medidor só pode ter uma instalação aberta.

## Erros e testes

Erros usam ProblemDetail: 400 para dados inválidos, 404 para registros inexistentes
e 409 para conflitos. Falhas de validação incluem `errors` com `field` e `message`.

Execute `./mvnw test` (Windows: `.\\mvnw.cmd test`) com JDK 25 ou superior.
Testes de integração usam PostgreSQL via Testcontainers e são ignorados se Docker
estiver indisponível. Para validar banco e migrations, execute com Docker ativo.

## Medições

Cada medição pertence a uma instalação. Use os DTOs abaixo como contrato HTTP;
a resposta não contém o objeto completo da instalação.

### Cadastro e consulta

`POST /api/medicoes` recebe os três campos obrigatórios:

```json
{
  "instalacaoId": 1,
  "dataMedicao": "2026-10-14T12:00:00Z",
  "leitura": 100
}
```

| Campo | Tipo JSON | Validação |
|---|---|---|
| `instalacaoId` | número inteiro | ID positivo de uma instalação existente |
| `dataMedicao` | string | Instante ISO-8601 com fuso, por exemplo `2026-10-14T12:00:00Z` |
| `leitura` | número | Não negativo, com até 13 dígitos inteiros e 6 decimais |

O cadastro retorna **201 Created**, com `Location: /api/medicoes/{id}`
(a URL pode ser absoluta) e o corpo abaixo. `GET /api/medicoes/10` retorna
**200 OK** com a mesma estrutura:

```json
{
  "id": 10,
  "instalacaoId": 1,
  "dataMedicao": "2026-10-14T12:00:00Z",
  "leitura": 100
}
```

O ID é gerado pelo banco. Não envie `id` no cadastro. A resposta contém somente
`id`, `instalacaoId`, `dataMedicao` e `leitura`; não há consumo calculado nem
campo de numeração da medição.

### Histórico e paginação

`GET /api/medicoes?instalacaoId=1&page=0&size=20` retorna **200 OK**:

```json
{
  "content": [
    {
      "id": 10,
      "instalacaoId": 1,
      "dataMedicao": "2026-10-14T12:00:00Z",
      "leitura": 100
    },
    {
      "id": 11,
      "instalacaoId": 1,
      "dataMedicao": "2026-10-14T12:30:00Z",
      "leitura": 110
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

`instalacaoId` é um filtro opcional e deve ser positivo quando informado.
Sem ele, a listagem retorna medições de todas as instalações. `page` começa
em zero e deve ser não negativo; `size` aceita de 1 a 100. Os padrões são
`page=0` e `size=20`. A ordenação é por ID crescente, sem parâmetro de ordenação.
Uma consulta sem resultados retorna `content: []`; o filtro não valida a
existência da instalação.

### Regras de negócio

- A primeira medição de cada instalação é a referência inicial, chamada
  “medição 0”. Isso não significa ID zero nem leitura obrigatoriamente zero:
  a leitura inicial pode ser zero ou positiva. Não envie um marcador especial.
- A data deve estar dentro do período da instalação: igual ou posterior a
  `dataInicio` e, se houver `dataFim`, igual ou anterior a ela.
- Após a referência inicial, a data deve ser estritamente posterior à última
  medição da instalação. Não é permitida inserção retroativa ou na mesma data.
- A leitura não pode diminuir em relação à última medição. Leituras iguais
  são permitidas, desde que a data seja posterior.
- Instalações encerradas aceitam novas medições dentro de seu período,
  respeitando a sequência. Não bloqueie o cadastro apenas por estarem encerradas.
- O encerramento da instalação deve ser posterior ao início e não pode
  anteceder a última medição; pode coincidir com a data dessa medição.
- Não existem PUT ou DELETE para medições. Cadastro de medição e encerramento
  usam bloqueio transacional por instalação para proteger essas regras.

### Orientações para o frontend

- Na tela da instalação, carregar o histórico com `instalacaoId` e oferecer
  cadastro com `dataMedicao` e `leitura`, usando o ID da instalação selecionada.
  Exibir estado vazio quando não houver medições e controles de paginação.
- Tratar o primeiro cadastro como referência inicial, sem exigir leitura zero.
  A primeira linha de uma página posterior não representa a referência inicial.
- Para campos de data/hora locais, interpretar a entrada no fuso usado pela
  interface e converter para um instante ISO-8601 com fuso antes do envio
  (por exemplo, UTC com `Z`). Converter o instante recebido para o fuso de
  exibição; não acrescentar `Z` diretamente a um horário local.
- Validar campos obrigatórios, limites decimais e período antes do envio.
  Preservar a precisão da leitura e evitar arredondamento silencioso, inclusive
  por conversão para `Number` do JavaScript nos maiores valores permitidos.
- Após 201, atualizar o histórico e os totais da paginação. Não assumir que a
  primeira página contém a última medição, pois a ordenação é crescente.
- Manter o backend como autoridade para a sequência. Em caso de 409, preservar
  os dados do formulário, atualizar o histórico e permitir a correção.
- Não apresentar ações de edição/exclusão nem depender de um campo `consumo`
  ou de um número de medição retornado pela API.

### Erros de medições

As respostas usam ProblemDetail. Exibir `detail` como mensagem geral e, quando
presente, associar cada item de `errors` (`field`, `message`) ao campo indicado.
Nem todo erro 400 inclui `errors`.

| HTTP | Situações |
|---|---|
| 400 | Campos inválidos, leitura negativa ou fora da precisão, data fora do período, parâmetros de paginação/filtro inválidos |
| 404 | Instalação inexistente no cadastro ou medição inexistente na consulta por ID |
| 409 | Data igual/anterior à última medição, leitura menor que a anterior ou conflito de persistência/concorrência |

Exemplo de conflito de sequência:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Data da medicao deve ser posterior a ultima medicao",
  "instance": "/api/medicoes"
}
```

No encerramento, `dataFim` anterior à última medição retorna 400.
