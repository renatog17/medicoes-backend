# Medicoes API

## Run with Docker Engine in Ubuntu WSL 2

Run these commands in your Ubuntu WSL terminal. This setup uses the existing
`postgres-dev` container with database `devdb` and user `devuser`. The API is
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

### Connect the existing database

```sh
docker start postgres-dev
docker network inspect medicoes-dev >/dev/null 2>&1 || docker network create medicoes-dev
```

Check whether `postgres-dev` is already attached:

```sh
docker network inspect medicoes-dev
```

If `postgres-dev` is absent from the listed containers, attach it once:

```sh
docker network connect medicoes-dev postgres-dev
```

The existing `postgres-dev-data` volume is retained. This setup does not create
or replace the database container. PostgreSQL must already contain `devdb` and
`devuser`; use that user's existing password below.

### Configure and start the API

```sh
cd /mnt/c/Users/renat/Spring/medicoes-api/medicoes-api
cp -n .env.example .env
nano .env
```

Set `POSTGRES_PASSWORD` to the existing password for `devuser`. For passwords
containing `$` or `#`, enclose the value in single quotes. The `.env` file is
ignored by Git and excluded from the Docker build context.

```sh
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs -f api
```

The API connects to `postgres-dev:5432` through the shared Docker network.
Compose overrides the local datasource defaults in `application.yaml`.
Flyway applies pending migrations, then Hibernate validates the schema.

From Windows or WSL, access `http://localhost:8080/api/clientes`:

```sh
curl --fail http://localhost:8080/api/clientes
```

The response should be HTTP 200 with a paginated client list. See [API.md](API.md)
for the available endpoints. To open a shell in the API or inspect PostgreSQL:

```sh
docker compose exec api sh
docker exec -it postgres-dev psql -U devuser -d devdb
```

### Rebuild and stop

```sh
docker compose up -d --build api
docker compose down
```

Stopping this Compose project leaves `postgres-dev`, its data volume, and the
external network in place. If port 8080 is occupied, stop the other application
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
opcionais inicialmente desabilitados. A colecao contem 19 operacoes e deve ser
atualizada quando os endpoints ou DTOs mudarem. Consulte [API.md](API.md).

## Run tests

The image build skips tests. To run the suite in WSL, install JDK 25, set
`JAVA_HOME`, ensure `docker info` succeeds, then run:

```sh
sh ./mvnw test
```

Integration tests use a disposable PostgreSQL 17 Testcontainers instance,
separate from `postgres-dev`. They are configured to skip without Docker;
check the Maven test report to confirm they ran.
# medicoes-backend
# medicoes-backend
