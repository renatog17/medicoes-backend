# Medicoes API

## Faturamento por instalação

Configure `FATURAMENTO_HABILITADO=true` e `FATURAMENTO_VALOR_M3` com o preço
real por metro cúbico. No cadastro da instalação, informe `diaEmissao` entre
1 e 31; se omitido, o padrão é 1. Em meses mais curtos, usa-se o último dia.

O scheduler executa diariamente às **00h00min05s de Brasília**
(`America/Sao_Paulo`) e processa as instalações com emissão naquele dia.
Cada medição pendente com uma leitura anterior gera sua própria fatura, inclusive
medições antigas. A competência é o mês da medição; a emissão usa a data real
e o vencimento ocorre 10 dias depois. A primeira leitura é apenas referência.
A tarifa fica preservada nas faturas, e a mesma medição não é faturada duas vezes.

Uma falha desfaz somente aquela emissão, marca `falhaEmissaoFatura=true` na
medição e permite continuar nas próximas. Medições marcadas não são tentadas
novamente. Não há recuperação de disparos perdidos se a aplicação estiver
desligada no horário da execução.

A emissão é local, sem envio de novas cobranças ao banco, logs ou resumo de lote.
A sincronização bancária existente continua consultando faturas abertas que já
tenham `bancoId`, sem registrar cobranças novas. O intervalo padrão é de
30 minutos após o término da execução anterior, configurável por
`BANCO_SINCRONIZACAO_MS=1800000` e condicionado a `BANCO_HABILITADO`.

Faturamento e integração bancária vêm desabilitados. Os parâmetros estão em
`.env.example` e são repassados pelo Compose. Alterações exigem reiniciar
(no Compose, recriar) a API. `BANCO_URL` deve ser acessível de dentro do contêiner;
`localhost` dentro dele aponta para o próprio contêiner.

Consulte [API.md](API.md) para os contratos de cadastro e consulta e
[INTEGRACAO_BANCARIA.md](INTEGRACAO_BANCARIA.md) para o contrato bancário.
A aplicação usa um bean `Clock` no fuso `America/Sao_Paulo`; testes fornecem
`Clock.fixed(instant, zone)` para datas determinísticas.

Os agendamentos ficam no pacote `scheduler`, habilitados por
`AgendamentoConfig`. `FaturamentoService` concentra a emissão e acessa os
repositórios diretamente. A rotina de cache continua preenchendo
`cache_leitura_anterior` diariamente à 01h00; o faturamento ainda consulta
as leituras originais.

## Run with Docker Engine in Ubuntu WSL 2

Run these commands in your Ubuntu WSL terminal. Compose creates PostgreSQL 17
with a new persistent volume, database `devdb` and user `devuser`. The API is
built with Java 25 and the Maven wrapper; no host Java or Maven is required.

From Windows PowerShell, open Ubuntu explicitly with `wsl.exe -d Ubuntu`.

### Docker access

```sh
docker context show
docker info
docker compose version
```

The default context should use `unix:///var/run/docker.sock` and the server
should identify Ubuntu. If access is denied, run:

```sh
sudo usermod -aG docker renat
```

Close and reopen your WSL login session, or run `newgrp docker`, then retry
`docker info`. Docker group membership grants administrative access through
Docker. If the daemon is stopped, run `sudo service docker start`.

### Check the existing network and ports

```sh
docker network inspect medicoes-dev
docker ps --format 'table {{.Names}}\t{{.Ports}}'
ss -ltn '( sport = :5432 or sport = :8080 )'
```

The external network `medicoes-dev` must exist before starting. If it is absent,
create it with `docker network create medicoes-dev`. Ports 5432 and 8080 must be
available. If the old `postgres-dev` container occupies port 5432, stop it with
`docker stop postgres-dev`; its container and data volume are preserved.

Compose creates its own `postgres-data` volume (prefixed by the project name).
The database starts empty on the first run; old data is not imported.

### Configure and start the API and PostgreSQL

```sh
cd /mnt/c/Users/renat/Spring/medicoes-api/medicoes-api
cp -n .env.example .env
nano .env
```

Set `POSTGRES_PASSWORD` to a strong password for the new database. Optionally set
`POSTGRES_DB` and `POSTGRES_USER` (defaults: `devdb` and `devuser`). For passwords
containing `$` or `#`, enclose the value in single quotes. The `.env` file is
ignored by Git and excluded from the Docker build context.

```sh
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs -f api
```

The API waits for PostgreSQL to become healthy and connects to `postgres:5432`
through the shared Docker network. Both services use the same database credentials.
Compose overrides the local datasource defaults in `application.yaml`.
Flyway creates the schema in the empty database and applies pending migrations,
then Hibernate validates the schema. PostgreSQL initialization variables only
take effect when the data volume is empty; changing `.env` does not update
credentials in an existing database.

From Windows or WSL, access `http://localhost:8080/api/clientes`:

```sh
curl --fail http://localhost:8080/api/clientes
```

The response should be HTTP 200 with a paginated client list. See [API.md](API.md)
for the available endpoints. To open a shell in the API or inspect PostgreSQL:

```sh
docker compose exec api sh
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

### Rebuild and stop

```sh
docker compose up -d --build api
docker compose down
```

`docker compose down` removes both containers but preserves the database volume
and external network. Starting again reuses the saved data. To deliberately
delete this project's database and start empty, use `docker compose down -v`;
this permanently removes its data volume. Old containers and external volumes
are not removed. If port 8080 is occupied, stop the other application
or change the host mapping to `127.0.0.1:8081:8080`.

## Importar endpoints no Bruno

A colecao [Medicoes API](bruno/medicoes-api.postman_collection.json) usa o formato
Postman Collection v2.1 para importacao no Bruno. Nao exige Postman, dependencias
Java adicionais ou novo deploy.

1. No Bruno, selecione **Import Collection**, escolha **Postman** e abra
   `bruno/medicoes-api.postman_collection.json` nesta pasta do projeto.
2. Escolha uma pasta de destino para a colecao convertida pelo Bruno.
3. Confira as variaveis da colecao: `baseUrl` deve ser
   `http://localhost:8080/api`. Os quatro IDs comecam vazios.
   Se preferir, defina essas variaveis em um ambiente Local e selecione-o.
4. Execute **Clientes > Listar clientes**. A resposta esperada e HTTP 200.
5. Execute as criacoes de cliente, imovel e medidor. Copie o `id` de cada
   resposta para `clienteId`, `imovelId` e `medidorId`, respectivamente.
   Use um `numeroSerie` diferente ao criar outro medidor.
6. Execute **Instalacoes > Criar instalacao**, copie o `id` retornado para
   `instalacaoId` e depois consulte ou encerre essa instalacao.

Execute as requisicoes individualmente; a colecao inclui PUT e DELETE que
alteram dados. Os IDs nao sao preenchidos automaticamente. No encerramento,
`dataFim` deve ser posterior a `dataInicio`. Cadastros vinculados a instalacoes
nao podem ser excluidos, mesmo depois do encerramento (HTTP 409).

As listagens incluem `page=0&size=20`; instalacoes tambem possuem filtros
opcionais inicialmente desabilitados. A colecao deve ser
atualizada quando os endpoints ou DTOs mudarem. Consulte [API.md](API.md).

## Run tests

The image build skips tests. To run the suite in WSL, install JDK 25, set
`JAVA_HOME`, ensure `docker info` succeeds, then run:

```sh
sh ./mvnw test
```

Integration tests use a disposable PostgreSQL 17 Testcontainers instance,
separate from the Compose database. They are configured to skip without Docker;
check the Maven test report to confirm they ran.
# medicoes-backend
# medicoes-backend
