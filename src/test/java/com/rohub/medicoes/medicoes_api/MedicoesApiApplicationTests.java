package com.rohub.medicoes.medicoes_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.service.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import com.rohub.medicoes.medicoes_api.domain.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MedicoesApiApplicationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ClienteService clientes;
    @Autowired ImovelService imoveis;
    @Autowired MedidorService medidores;
    @Autowired InstalacaoService instalacoes;
    @Autowired ClienteRepository clienteRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired InstalacaoRepository instalacaoRepository;
    @Autowired ImovelRepository imovelRepository;
    @Autowired MedidorRepository medidorRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void medidorExcluidoPreservaDadosEReservaSerie() {
        var request = new MedidorRequest("M-EXCLUSAO", "Fabricante", "Modelo");
        var medidor = medidores.criar(request);
        assertEquals(medidor.id(), medidores.atualizar(medidor.id(), request).id());
        assertThrows(ConflitoException.class, () -> medidores.criar(request));
        var outro = medidores.criar(new MedidorRequest("M-OUTRO", null, null));
        assertThrows(ConflitoException.class, () -> medidores.atualizar(outro.id(), request));
        long totalAntes = medidores.listar(0, 100).totalElements();
        medidores.excluir(medidor.id());
        var preservado = medidorRepository.findById(medidor.id()).orElseThrow();
        assertTrue(preservado.isExcluido());
        assertEquals(request.numeroSerie(), preservado.getNumeroSerie());
        assertEquals(request.fabricante(), preservado.getFabricante());
        assertEquals(request.modelo(), preservado.getModelo());
        assertEquals(totalAntes - 1, medidores.listar(0, 100).totalElements());
        assertTrue(medidorRepository.findAllByExcluidoFalse(org.springframework.data.domain.Pageable.unpaged())
            .stream().noneMatch(m -> m.getId().equals(medidor.id())));
        assertThrows(RecursoNaoEncontradoException.class, () -> medidores.buscar(medidor.id()));
        assertThrows(RecursoNaoEncontradoException.class, () -> medidores.atualizar(medidor.id(), request));
        assertThrows(RecursoNaoEncontradoException.class, () -> medidores.excluir(medidor.id()));
        assertThrows(ConflitoException.class, () -> medidores.criar(request));
        assertThrows(ConflitoException.class, () -> medidores.atualizar(outro.id(), request));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> medidorRepository.saveAndFlush(new Medidor(request.numeroSerie())));
        var endereco = new EnderecoRequest(null, null, null, null, null, null, null);
        var cliente = clientes.criar(new ClienteRequest("Ana", cin(), null, null, endereco));
        var imovel = imoveis.criar(new ImovelRequest(endereco));
        assertThrows(RecursoNaoEncontradoException.class, () -> instalacoes.criar(
            new InstalacaoRequest(cliente.id(), imovel.id(), medidor.id(), Instant.now(), null)));
    }

    @Test
    void excluiImovelLogicamenteSemPerderEnderecoOuContarNaPagina() {
        var endereco = new EnderecoRequest(null, null, null, null, "Salvador", null, null);
        var imovel = imoveis.criar(new ImovelRequest(endereco));
        long totalAntes = imoveis.listar(0, 100).totalElements();
        imoveis.excluir(imovel.id());
        assertTrue(jdbc.queryForObject("select excluido from imoveis where id = ?", Boolean.class, imovel.id()));
        assertEquals(imovel.endereco().id(), jdbc.queryForObject(
            "select endereco_id from imoveis where id = ?", Long.class, imovel.id()));
        assertEquals(totalAntes - 1, imoveis.listar(0, 100).totalElements());
        assertTrue(imovelRepository.findAllByExcluidoFalse(org.springframework.data.domain.Pageable.unpaged())
            .stream().noneMatch(i -> i.getId().equals(imovel.id())));
        assertThrows(RecursoNaoEncontradoException.class, () -> imoveis.buscar(imovel.id()));
        assertThrows(RecursoNaoEncontradoException.class, () -> imoveis.atualizar(imovel.id(), new ImovelRequest(endereco)));
        assertThrows(RecursoNaoEncontradoException.class, () -> imoveis.excluir(imovel.id()));
        var cliente = clientes.criar(new ClienteRequest("Ana", cin(), null, null, endereco));
        assertThrows(RecursoNaoEncontradoException.class, () -> instalacoes.criar(
            new InstalacaoRequest(cliente.id(), imovel.id(), 1L, Instant.now(), null)));

        var entidadeExcluida = imovelRepository.findById(imovel.id()).orElseThrow();
        var compartilhado = salvarCliente("Compartilhado", entidadeExcluida.getEndereco());
        var novoEndereco = new EnderecoRequest(null, null, null, null, "Feira de Santana", null, null);
        var atualizado = clientes.atualizar(compartilhado.getId(),
            new ClienteRequest("Compartilhado", compartilhado.getCin(), null, null, novoEndereco));
        assertNotEquals(imovel.endereco().id(), atualizado.enderecoCobranca().id());
        assertEquals("Salvador", enderecoRepository.findById(imovel.endereco().id()).orElseThrow().getCidade());
    }

    private static final java.util.concurrent.atomic.AtomicInteger sequencia =
        new java.util.concurrent.atomic.AtomicInteger(123456780);

    private static String cin() {
        String valor = Integer.toString(sequencia.incrementAndGet());
        for (int tamanho = 9; tamanho <= 10; tamanho++) {
            int soma = 0;
            for (int i = 0; i < tamanho; i++) soma += (valor.charAt(i) - '0') * (tamanho + 1 - i);
            int resto = soma % 11;
            valor += resto < 2 ? 0 : 11 - resto;
        }
        return valor;
    }

    private Cliente salvarCliente(String nome, Endereco endereco) {
        var cliente = new Cliente(nome, endereco);
        cliente.setCin(cin());
        return clienteRepository.saveAndFlush(cliente);
    }

    @Test
    void cinPermaneceUnicoAposExclusaoLogica() {
        var endereco = new EnderecoRequest(null, null, null, null, "Salvador", null, null);
        var request = new ClienteRequest("Cliente CIN", cin(), null, null, endereco);
        var cliente = clientes.criar(request);
        assertEquals(cliente.id(), clientes.atualizar(cliente.id(), request).id());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> clientes.criar(request));
        var outro = clientes.criar(new ClienteRequest("Outro", cin(), null, null, endereco));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> clientes.atualizar(outro.id(), request));

        clientes.excluir(cliente.id());
        assertTrue(jdbc.queryForObject("select excluido from clientes where id = ?", Boolean.class, cliente.id()));
        assertEquals(cliente.enderecoCobranca().id(), jdbc.queryForObject(
            "select endereco_cobranca_id from clientes where id = ?", Long.class, cliente.id()));
        assertFalse(clienteRepository.findByIdAndExcluidoFalse(cliente.id()).isPresent());
        assertTrue(clienteRepository.findAllByExcluidoFalse(org.springframework.data.domain.Pageable.unpaged())
            .stream().noneMatch(c -> c.getId().equals(cliente.id())));
        assertThrows(RecursoNaoEncontradoException.class, () -> clientes.buscar(cliente.id()));
        assertThrows(RecursoNaoEncontradoException.class, () -> clientes.atualizar(cliente.id(), request));
        assertThrows(RecursoNaoEncontradoException.class, () -> clientes.excluir(cliente.id()));
        assertThrows(RecursoNaoEncontradoException.class, () -> instalacoes.criar(
            new InstalacaoRequest(cliente.id(), 1L, 1L, Instant.now(), null)));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> clientes.criar(request));

    }

    @Test
    void preservaEnderecoCompartilhadoComClienteExcluido() {
        var endereco = new Endereco();
        endereco.setCidade("Salvador");
        endereco = enderecoRepository.saveAndFlush(endereco);
        var excluido = salvarCliente("Excluido", endereco);
        var ativo = salvarCliente("Ativo", endereco);
        clientes.excluir(excluido.getId());
        var novo = new EnderecoRequest(null, null, null, null, "Feira de Santana", null, null);
        var atualizado = clientes.atualizar(ativo.getId(), new ClienteRequest("Ativo", ativo.getCin(), null, null, novo));
        assertNotEquals(endereco.getId(), atualizado.enderecoCobranca().id());
        assertEquals("Salvador", enderecoRepository.findById(endereco.getId()).orElseThrow().getCidade());
    }

    private Instalacao criarEntidade(String serie) {
        var cobranca = enderecoRepository.saveAndFlush(new Endereco());
        var local = enderecoRepository.saveAndFlush(new Endereco());
        var cliente = salvarCliente("Teste", cobranca);
        var imovel = imovelRepository.saveAndFlush(new Imovel(local));
        var medidor = medidorRepository.saveAndFlush(new Medidor(serie));
        return instalacaoRepository.saveAndFlush(
            new Instalacao(cliente, imovel, medidor, Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void bancoImpedeDuasInstalacoesAbertasMesmoSemPreValidacao() {
        var primeira = criarEntidade("M-CONSTRAINT");
        var duplicada = new Instalacao(primeira.getCliente(), primeira.getImovel(),
            primeira.getMedidor(), primeira.getDataInicio());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> instalacaoRepository.saveAndFlush(duplicada));
    }

    @Test
    void versaoImpedeSobrescreverEncerramentoConcorrente() {
        var original = criarEntidade("M-VERSAO");
        var primeira = instalacaoRepository.findById(original.getId()).orElseThrow();
        var segunda = instalacaoRepository.findById(original.getId()).orElseThrow();
        primeira.setDataFim(original.getDataInicio().plusSeconds(60));
        instalacaoRepository.saveAndFlush(primeira);
        segunda.setDataFim(original.getDataInicio().plusSeconds(120));
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
            () -> instalacaoRepository.saveAndFlush(segunda));
    }

    @Test
    void cadastraFiltraEncerraEReutilizaMedidor() {
        var endereco = new EnderecoRequest("Rua A", "10", null, "Centro", "Salvador", "BA", "40000000");
        var cliente = clientes.criar(new ClienteRequest("Ana", cin(), null, null, endereco));
        var imovel = imoveis.criar(new ImovelRequest(endereco));
        var medidor = medidores.criar(new MedidorRequest("M-INTEGRACAO", null, null));
        var inicio = Instant.parse("2026-01-01T00:00:00Z");
        var aberta = instalacoes.criar(new InstalacaoRequest(cliente.id(), imovel.id(), medidor.id(), inicio, null));
        assertTrue(aberta.ativa());
        assertThrows(ConflitoException.class, () -> medidores.excluir(medidor.id()));
        assertThrows(ConflitoException.class, () -> instalacoes.criar(
            new InstalacaoRequest(cliente.id(), imovel.id(), medidor.id(), inicio, null)));
        assertThrows(ConflitoException.class, () -> clientes.excluir(cliente.id()));
        assertEquals(1, instalacoes.listar(0, 20, cliente.id(), imovel.id(), medidor.id(), true).totalElements());
        assertThrows(ConflitoException.class, () -> imoveis.excluir(imovel.id()));
        var fim = inicio.plusSeconds(60);
        assertFalse(instalacoes.encerrar(aberta.id(), new EncerramentoRequest(fim)).ativa());
        assertThrows(ConflitoException.class, () -> imoveis.excluir(imovel.id()));
        assertThrows(ConflitoException.class, () -> clientes.excluir(cliente.id()));
        assertThrows(ConflitoException.class, () -> medidores.excluir(medidor.id()));
        assertTrue(instalacoes.criar(new InstalacaoRequest(cliente.id(), imovel.id(), medidor.id(), fim, null)).ativa());
    }

    @Test
    void atualizaEnderecoCompartilhadoSemAfetarOutroCliente() {
        var endereco = new Endereco();
        endereco.setCidade("Salvador");
        endereco = enderecoRepository.saveAndFlush(endereco);
        var primeiro = salvarCliente("Primeiro", endereco);
        var segundo = salvarCliente("Segundo", endereco);
        var novo = new EnderecoRequest(null, null, null, null, "Feira de Santana", null, null);
        var alterado = clientes.atualizar(primeiro.getId(), new ClienteRequest("Primeiro", primeiro.getCin(), null, null, novo));
        assertNotEquals(endereco.getId(), alterado.enderecoCobranca().id());
        assertEquals("Salvador", clientes.buscar(segundo.getId()).enderecoCobranca().cidade());
    }

    @Test
    void rollbackNaoDeixaEnderecoAoFalharCadastro() {
        long antes = enderecoRepository.count();
        var request = new ClienteRequest(null, cin(), null, null,
            new EnderecoRequest(null, null, null, null, "Salvador", null, null));
        assertThrows(RuntimeException.class, () -> clientes.criar(request));
        assertEquals(antes, enderecoRepository.count());
    }

	@Test
	void contextLoads() {
	}

    @Autowired MedicaoService medicoes;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void limitaMedicaoMensalPorInstalacao() {
        var primeira = criarEntidade("M-MENSAL-1");
        var segunda = criarEntidade("M-MENSAL-2");
        var janeiro = Instant.parse("2026-01-15T12:00:00Z");
        medicoes.criar(new MedicaoRequest(primeira.getId(), janeiro, java.math.BigDecimal.TEN));
        medicoes.criar(new MedicaoRequest(segunda.getId(), janeiro, java.math.BigDecimal.TEN));
        assertThrows(ConflitoException.class, () -> medicoes.criar(new MedicaoRequest(
                primeira.getId(), Instant.parse("2026-02-01T02:59:59Z"), java.math.BigDecimal.TEN)));
        medicoes.criar(new MedicaoRequest(primeira.getId(), Instant.parse("2026-02-01T03:00:00Z"), java.math.BigDecimal.TEN));
        assertEquals(2, medicoes.listar(0, 20, primeira.getId()).totalElements());
        assertEquals(1, medicoes.listar(0, 20, segunda.getId()).totalElements());
    }

    @Test
    void medicoesConcorrentesCriamUmaUnicaReferencia() throws Exception {
        var instalacao = criarEntidade("M-CONCORRENCIA-MEDICAO");
        var request = new MedicaoRequest(instalacao.getId(), instalacao.getDataInicio(), java.math.BigDecimal.TEN);
        var prontas = new java.util.concurrent.CountDownLatch(2);
        var iniciar = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> cadastrar = () -> {
                prontas.countDown();
                if (!iniciar.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Inicio nao liberado");
                try {
                    medicoes.criar(request);
                    return true;
                } catch (ConflitoException ex) {
                    return false;
                }
            };
            var primeira = executor.submit(cadastrar);
            var segunda = executor.submit(cadastrar);
            try {
                assertTrue(prontas.await(10, java.util.concurrent.TimeUnit.SECONDS));
            } finally {
                iniciar.countDown();
            }
            assertNotEquals(primeira.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    segunda.get(20, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1, medicoes.listar(0, 20, instalacao.getId()).totalElements());
    }

    @Test
    void encerramentoEsperaCadastroEPreservaUltimaMedicao() throws Exception {
        var instalacao = criarEntidade("M-CONCORRENCIA-ENCERRAMENTO");
        var inicio = instalacao.getDataInicio();
        var transacoes = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var bloqueada = new java.util.concurrent.CountDownLatch(1);
        var liberar = new java.util.concurrent.CountDownLatch(1);
        var encerrando = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var cadastro = executor.submit(() -> transacoes.execute(status -> {
                instalacaoRepository.buscarComBloqueio(instalacao.getId()).orElseThrow();
                bloqueada.countDown();
                try {
                    if (!liberar.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Bloqueio nao liberado");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return medicoes.criar(new MedicaoRequest(instalacao.getId(), inicio.plusSeconds(20), java.math.BigDecimal.TEN));
            }));
            try {
                assertTrue(bloqueada.await(10, java.util.concurrent.TimeUnit.SECONDS));
                var encerramento = executor.submit(() -> {
                    encerrando.countDown();
                    return instalacoes.encerrar(instalacao.getId(), new EncerramentoRequest(inicio.plusSeconds(10)));
                });
                assertTrue(encerrando.await(10, java.util.concurrent.TimeUnit.SECONDS));
                try {
                    assertThrows(java.util.concurrent.TimeoutException.class,
                            () -> encerramento.get(300, java.util.concurrent.TimeUnit.MILLISECONDS));
                } finally {
                    liberar.countDown();
                }
                cadastro.get(20, java.util.concurrent.TimeUnit.SECONDS);
                var erro = assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> encerramento.get(20, java.util.concurrent.TimeUnit.SECONDS));
                assertInstanceOf(RequisicaoInvalidaException.class, erro.getCause());
            } finally {
                liberar.countDown();
            }
        }
        assertTrue(instalacoes.buscar(instalacao.getId()).ativa());
        assertFalse(instalacoes.encerrar(instalacao.getId(), new EncerramentoRequest(inicio.plusSeconds(20))).ativa());
    }
}
