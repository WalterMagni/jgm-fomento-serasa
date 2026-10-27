package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.application.service.liberacao.LiberacaoService.DadosCard;
import com.portal.serasa.application.service.liberacao.LiberacaoService.DadosSacado;
import com.portal.serasa.application.service.liberacao.LiberacaoService.DecisaoAnterior;
import com.portal.serasa.application.service.liberacao.LiberacaoService.DecisaoSacado;
import com.portal.serasa.application.service.liberacao.LiberacaoService.NovaPendencia;
import com.portal.serasa.application.service.liberacao.LiberacaoService.ParecerRegistrado;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.ConflitoEdicaoException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.liberacao.CarteiraSacado;
import com.portal.serasa.domain.model.liberacao.EmpresaOperacao;
import com.portal.serasa.domain.model.liberacao.OpiniaoOperador;
import com.portal.serasa.domain.model.liberacao.QuadroOpiniao;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.OrigemMembro;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import com.portal.serasa.domain.model.liberacao.PropostaAr;
import com.portal.serasa.domain.model.liberacao.ResultadoLiberacao;
import com.portal.serasa.domain.model.liberacao.TipoEventoLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEventoEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMembroEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMotivoReprovacaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoSacadoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoCardJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoEventoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMembroJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoParecerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoPendenciaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoSacadoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiberacaoServiceTest {

    private static final String CNPJ = "11222333000181";
    private static final String CNPJ_MASCARADO = "11.222.333/0001-81";
    private static final String SACADO_A = "45723174000110";
    private static final String SACADO_B = "00360305000104";
    private static final String CPF = "52998224725";

    @Mock private LiberacaoCardJpaRepository cardRepository;
    @Mock private LiberacaoSacadoJpaRepository sacadoRepository;
    @Mock private LiberacaoMembroJpaRepository membroRepository;
    @Mock private LiberacaoParecerJpaRepository parecerRepository;
    @Mock private LiberacaoPendenciaJpaRepository pendenciaRepository;
    @Mock private LiberacaoEventoJpaRepository eventoRepository;
    @Mock private com.portal.serasa.infrastructure.persistence.repository.LiberacaoMotivoReprovacaoJpaRepository motivoReprovacaoRepository;
    @Mock private UserRepository userRepository;
    @Mock private CompanyDetailRepository companyDetailRepository;
    @Mock private com.portal.serasa.application.port.out.ClientRepository clientRepository;
    @Mock private EmpresaResolver empresaResolver;

    /** Empresas "na base" deste teste, servidas pelo resolvedor real sobre os repositórios simulados. */
    private final java.util.Map<String, CompanyDetail> base = new java.util.HashMap<>();

    private void naBase(String documento, String nome) {
        base.put(documento, empresa(documento, nome));
    }
    @Spy private LiberacaoAutorizacao autorizacao = new LiberacaoAutorizacao();

    @Mock private ApplicationEventPublisher eventos;

    @InjectMocks private LiberacaoService service;

    // Catálogo de motivos de reprovação (V95), fora de ordem de propósito: o card guarda na ordem do catálogo.
    private static final LiberacaoMotivoReprovacaoEntity MOTIVO_PRACA = motivoReprovacao("Praça de Pgto", 16, true);
    private static final LiberacaoMotivoReprovacaoEntity MOTIVO_SEM_LIMITE = motivoReprovacao("Sacado sem Limite", 11, true);
    private static final LiberacaoMotivoReprovacaoEntity MOTIVO_DESATIVADO = motivoReprovacao("Motivo antigo", 20, false);

    private static LiberacaoMotivoReprovacaoEntity motivoReprovacao(String nome, int ordem, boolean ativo) {
        return LiberacaoMotivoReprovacaoEntity.builder().id(UUID.randomUUID()).nome(nome).ordem(ordem).ativo(ativo).build();
    }

    /** Reprova com um motivo do catálogo: reprovar sem motivo é recusado. */
    private LiberacaoCardEntity reprovar(LiberacaoCardEntity card, EtapaLiberacao de, UserEntity autor) {
        return service.transicionar(card.getId(), de, EtapaLiberacao.REPROVADO, null, null, List.of(), null,
                List.of(MOTIVO_PRACA.getId()), autor);
    }

    private UserEntity auxiliar;
    private UserEntity analista;
    /** Analista com o perfil Diretoria: única que decide com o card na Diretoria. */
    private UserEntity diretor;

    @BeforeEach
    void setUp() {
        when(companyDetailRepository.findByDocumentNumberIn(anyCollection())).thenAnswer(inv -> {
            java.util.Collection<String> docs = inv.getArgument(0);
            return docs.stream().map(base::get).filter(java.util.Objects::nonNull).toList();
        });
        EmpresaResolver resolverReal = new EmpresaResolver(companyDetailRepository, clientRepository,
                org.mockito.Mockito.mock(com.portal.serasa.infrastructure.persistence.repository.PersonAnalysisJpaRepository.class));
        when(empresaResolver.resolver(anyCollection())).thenAnswer(inv -> resolverReal.resolver(inv.getArgument(0)));
        auxiliar = UserEntity.builder().id(UUID.randomUUID()).name("Auxiliar").email("aux@jgm.com").build();
        analista = UserEntity.builder().id(UUID.randomUUID()).name("Andressa").email("andressa@jgm.com")
                .analista(true).build();
        diretor = UserEntity.builder().id(UUID.randomUUID()).name("Walter").email("walter@jgm.com")
                .analista(true).diretoria(true).build();
        when(motivoReprovacaoRepository.findAllById(any())).thenAnswer(inv -> {
            java.util.Set<UUID> ids = new java.util.HashSet<>();
            inv.<Iterable<UUID>>getArgument(0).forEach(ids::add);
            return java.util.stream.Stream.of(MOTIVO_SEM_LIMITE, MOTIVO_PRACA, MOTIVO_DESATIVADO)
                    .filter(motivo -> ids.contains(motivo.getId()))
                    .toList();
        });

        // O banco gera id e numero; aqui o save simula isso.
        when(cardRepository.saveAndFlush(any())).thenAnswer(inv -> {
            LiberacaoCardEntity card = inv.getArgument(0);
            if (card.getId() == null) {
                card.setId(UUID.randomUUID());
                card.setNumero(7L);
                card.setVersion(0L);
            }
            return card;
        });
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membroRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(parecerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pendenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sacadoRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ----------------------------------------------------------------- fixtures

    private LiberacaoCardEntity card(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = LiberacaoCardEntity.builder()
                .id(UUID.randomUUID())
                .numero(42L)
                .version(3L)
                .etapa(etapa)
                .etapaDesde(LocalDateTime.now().minusHours(1))
                .rodada(1)
                .cedenteCnpj(CNPJ)
                .cedenteNome("ACME LTDA")
                .tipoOperacao("Duplicata")
                .valor(new BigDecimal("1000.00"))
                .prazo(LocalDateTime.of(2026, 10, 30, 12, 0))
                .parecerOrigem("Cliente antigo")
                .criadoPorId(auxiliar.getId())
                .criadoPorNome("Auxiliar")
                .criadoEm(LocalDateTime.now().minusDays(1))
                .atualizadoPorId(UUID.randomUUID())
                .atualizadoPorNome("Beatriz")
                .atualizadoEm(LocalDateTime.of(2026, 10, 7, 9, 30))
                .build();
        when(cardRepository.findByIdAndExcluidoEmIsNull(card.getId())).thenReturn(Optional.of(card));
        return card;
    }

    /** Dados idênticos ao estado de {@link #card}: salvar com isto não pode mudar nada. */
    private DadosCard dadosIguaisA(LiberacaoCardEntity card) {
        return new DadosCard(card.getCedenteCnpj(), null, card.getTipoOperacao(), card.getValor(),
                card.getPrazo(), card.getParecerOrigem(), null, null);
    }

    private DadosCard dadosNovos(String cnpj, String nome, List<DadosSacado> sacados) {
        return new DadosCard(cnpj, nome, "Duplicata", new BigDecimal("5000"),
                LocalDateTime.of(2026, 11, 1, 10, 0), "  Parecer da origem  ", null, sacados);
    }

    private UserEntity usuario(String nome) {
        return UserEntity.builder().id(UUID.randomUUID()).name(nome).email(nome.toLowerCase() + "@jgm.com").build();
    }

    private LiberacaoParecerEntity parecer(LiberacaoCardEntity card, UserEntity dono, PosicaoParecer posicao) {
        return LiberacaoParecerEntity.builder()
                .id(UUID.randomUUID())
                .cardId(card.getId())
                .rodada(card.getRodada())
                .usuarioId(dono == null ? null : dono.getId())
                .usuarioNome(dono == null ? "Removida" : dono.getName())
                .posicao(posicao)
                .registradoEm(posicao == null ? null : LocalDateTime.now())
                .criadoEm(LocalDateTime.now())
                .build();
    }

    private void rodadaTem(LiberacaoCardEntity card, LiberacaoParecerEntity... pareceres) {
        List<LiberacaoParecerEntity> lista = List.of(pareceres);
        when(parecerRepository.findByCardIdAndRodadaOrderByCriadoEm(card.getId(), card.getRodada())).thenReturn(lista);
        for (LiberacaoParecerEntity parecer : lista) {
            if (parecer.getUsuarioId() != null) {
                when(parecerRepository.findByCardIdAndRodadaAndUsuarioId(card.getId(), card.getRodada(),
                        parecer.getUsuarioId())).thenReturn(Optional.of(parecer));
            }
        }
    }

    private List<LiberacaoEventoEntity> eventosGravados() {
        ArgumentCaptor<LiberacaoEventoEntity> captor = ArgumentCaptor.forClass(LiberacaoEventoEntity.class);
        verify(eventoRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private List<LiberacaoMembroEntity> membrosGravados() {
        ArgumentCaptor<LiberacaoMembroEntity> captor = ArgumentCaptor.forClass(LiberacaoMembroEntity.class);
        verify(membroRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @SuppressWarnings("unchecked")
    private List<LiberacaoSacadoEntity> sacadosGravados() {
        ArgumentCaptor<List<LiberacaoSacadoEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(sacadoRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    /** NumberFormat pt-BR separa "R$" do número com espaço inseparável. */
    private static String semEspacoInsecavel(String texto) {
        return texto == null ? null : texto.replace(' ', ' ');
    }

    private CompanyDetail empresa(String documento, String nome) {
        return CompanyDetail.builder().documentNumber(documento).companyName(nome).build();
    }

    private LiberacaoSacadoEntity sacado(LiberacaoCardEntity card, String documento, String nome, String valor, int ordem) {
        return LiberacaoSacadoEntity.builder()
                .id(UUID.randomUUID())
                .cardId(card.getId())
                .cnpj(documento)
                .nome(nome)
                .valor(valor == null ? null : new BigDecimal(valor))
                .ordem(ordem)
                .build();
    }

    // ------------------------------------------------------------------- criar

    @Test
    @DisplayName("criar: normaliza o CNPJ e usa a razão social da base quando existe")
    void shouldNormalizeCnpjAndUseCompanyNameFromBase() {
        naBase(CNPJ, "ACME DA BASE S/A");

        LiberacaoCardEntity criado = service.criar(dadosNovos(CNPJ_MASCARADO, "Nome digitado", null), auxiliar);

        assertThat(criado.getCedenteCnpj()).isEqualTo(CNPJ);
        assertThat(criado.getCedenteNome()).isEqualTo("ACME DA BASE S/A");
        assertThat(criado.getEtapa()).isEqualTo(EtapaLiberacao.ORIGEM);
        assertThat(criado.getRodada()).isEqualTo(1);
        assertThat(criado.getParecerOrigem()).isEqualTo("Parecer da origem");
        assertThat(criado.getCriadoPorId()).isEqualTo(auxiliar.getId());
        assertThat(criado.getCriadoPorNome()).isEqualTo("Auxiliar");
        assertThat(criado.getAtualizadoPorNome()).isEqualTo("Auxiliar");
    }

    @Test
    @DisplayName("criar: empresa fora da base com nome informado usa o nome (aparado)")
    void shouldUseInformedNameWhenCompanyIsNotInBase() {

        LiberacaoCardEntity criado = service.criar(dadosNovos(CNPJ, "  Empresa Nova Ltda  ", null), auxiliar);

        assertThat(criado.getCedenteNome()).isEqualTo("Empresa Nova Ltda");
    }

    @Test
    @DisplayName("criar: base com razão social em branco cai no nome informado")
    void shouldFallBackToInformedNameWhenBaseNameIsBlank() {
        naBase(CNPJ, "  ");

        LiberacaoCardEntity criado = service.criar(dadosNovos(CNPJ, "Nome informado", null), auxiliar);

        assertThat(criado.getCedenteNome()).isEqualTo("Nome informado");
    }

    @Test
    @DisplayName("criar: CNPJ fora da base e sem nome informado é recusado")
    void shouldRejectCnpjNotInBaseWithoutName() {

        assertThatThrownBy(() -> service.criar(dadosNovos(CNPJ, null, null), auxiliar))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empresa não cadastrada");
        assertThatThrownBy(() -> service.criar(dadosNovos(CNPJ, "   ", null), auxiliar))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("criar: CNPJ com tamanho errado é recusado (cedente não aceita CPF)")
    void shouldRejectCnpjWithWrongLength() {
        assertThatThrownBy(() -> service.criar(dadosNovos("1122233300018", "X", null), auxiliar))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CNPJ do cedente inválido");
        assertThatThrownBy(() -> service.criar(dadosNovos(null, "X", null), auxiliar))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.criar(dadosNovos(CPF, "X", null), auxiliar))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("criar: sacado repetido (mesmo documento, formatações diferentes) é recusado")
    void shouldRejectDuplicateSacado() {
        naBase(CNPJ, "ACME");
        List<DadosSacado> sacados = List.of(
                new DadosSacado(SACADO_A, "Sacado A", null),
                new DadosSacado("45.723.174/0001-10", "Sacado A de novo", null));

        assertThatThrownBy(() -> service.criar(dadosNovos(CNPJ, null, sacados), auxiliar))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sacado repetido")
                .hasMessageContaining("45.723.174/0001-10");
    }

    @Test
    @DisplayName("criar: sacado com CPF (11 dígitos) é aceito e não consulta a base de empresas")
    void shouldAcceptCpfAsSacado() {
        naBase(CNPJ, "ACME");
        List<DadosSacado> sacados = List.of(new DadosSacado("529.982.247-25", null, new BigDecimal("300")));

        service.criar(dadosNovos(CNPJ, null, sacados), auxiliar);

        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        assertThat(gravados).hasSize(1);
        assertThat(gravados.get(0).getCnpj()).isEqualTo(CPF);
        assertThat(gravados.get(0).getNome()).isNull();
        assertThat(gravados.get(0).getOrdem()).isZero();
        // só o cedente é procurado; CPF não tem página de empresa
        verify(empresaResolver, times(1)).resolver(anyCollection());
    }

    @Test
    @DisplayName("criar: sacado com tamanho de documento inválido é recusado")
    void shouldRejectSacadoWithInvalidDocument() {
        naBase(CNPJ, "ACME");
        List<DadosSacado> sacados = List.of(new DadosSacado("1234567890", "Curto", null));

        assertThatThrownBy(() -> service.criar(dadosNovos(CNPJ, null, sacados), auxiliar))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Documento de sacado inválido");
    }

    @Test
    @DisplayName("criar: linhas vazias de sacado são descartadas, o nome vem da base e a ordem é preservada")
    void shouldDropBlankSacadoRowsAndCompleteNamesFromBase() {
        naBase(CNPJ, "ACME");
        naBase(SACADO_B, "SEGUNDO SACADO S/A");
        List<DadosSacado> sacados = new ArrayList<>();
        sacados.add(new DadosSacado(SACADO_A, "Primeiro Informado", new BigDecimal("10")));
        sacados.add(new DadosSacado("  ", "linha vazia", null));
        sacados.add(null);
        sacados.add(new DadosSacado(SACADO_B, null, new BigDecimal("20")));

        service.criar(dadosNovos(CNPJ, null, sacados), auxiliar);

        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        assertThat(gravados).extracting(LiberacaoSacadoEntity::getCnpj).containsExactly(SACADO_A, SACADO_B);
        assertThat(gravados).extracting(LiberacaoSacadoEntity::getNome)
                .containsExactly("Primeiro Informado", "SEGUNDO SACADO S/A");
        assertThat(gravados).extracting(LiberacaoSacadoEntity::getOrdem).containsExactly(0, 1);
        // só quem veio sem nome é procurado na base
        verify(empresaResolver).resolver(List.of(SACADO_B));
    }

    @Test
    @DisplayName("criar: o criador entra como membro CRIADOR")
    void shouldAddCreatorAsMember() {
        naBase(CNPJ, "ACME");

        LiberacaoCardEntity criado = service.criar(dadosNovos(CNPJ, null, null), auxiliar);

        List<LiberacaoMembroEntity> membros = membrosGravados();
        assertThat(membros).hasSize(1);
        assertThat(membros.get(0).getCardId()).isEqualTo(criado.getId());
        assertThat(membros.get(0).getUsuarioId()).isEqualTo(auxiliar.getId());
        assertThat(membros.get(0).getOrigem()).isEqualTo(OrigemMembro.CRIADOR);
    }

    @Test
    @DisplayName("criar: grava o evento CRIACAO com o autor")
    void shouldRecordCriacaoEvent() {
        naBase(CNPJ, "ACME");

        LiberacaoCardEntity criado = service.criar(dadosNovos(CNPJ, null, null), auxiliar);

        List<LiberacaoEventoEntity> eventos = eventosGravados();
        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).getTipo()).isEqualTo(TipoEventoLiberacao.CRIACAO);
        assertThat(eventos.get(0).getCardId()).isEqualTo(criado.getId());
        assertThat(eventos.get(0).getUsuarioId()).isEqualTo(auxiliar.getId());
        assertThat(eventos.get(0).getUsuarioNome()).isEqualTo("Auxiliar");
    }

    @Test
    @DisplayName("criar: sem usuário autenticado é recusado antes de qualquer gravação")
    void shouldRejectCreateWithoutAuthenticatedUser() {
        assertThatThrownBy(() -> service.criar(dadosNovos(CNPJ, "X", null), null))
                .isInstanceOf(AcessoNegadoException.class);
        verify(cardRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ editar

    @Test
    @DisplayName("editar: versão diferente gera ConflitoEdicaoException citando quem salvou por último")
    void shouldRejectEditOnVersionMismatch() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThatThrownBy(() -> service.editar(card.getId(), 2L, dadosIguaisA(card), auxiliar))
                .isInstanceOf(ConflitoEdicaoException.class)
                .hasMessageContaining("Beatriz")
                .hasMessageContaining("07/10 às 09:30");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(eventoRepository, never()).save(any());
    }

    @Test
    @DisplayName("editar: versão ausente também é conflito")
    void shouldRejectEditWithoutVersion() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThatThrownBy(() -> service.editar(card.getId(), null, dadosIguaisA(card), auxiliar))
                .isInstanceOf(ConflitoEdicaoException.class);
    }

    @Test
    @DisplayName("editar: não-analista editando card no Comitê é AcessoNegado (antes de checar versão)")
    void shouldDenyNonAnalystEditingCardInComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> service.editar(card.getId(), 999L, dadosIguaisA(card), auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("editar: analista edita card no Comitê")
    void shouldLetAnalystEditCardInComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        DadosCard dados = new DadosCard(CNPJ, null, "Cheque", card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null, null);

        LiberacaoCardEntity editado = service.editar(card.getId(), card.getVersion(), dados, analista);

        assertThat(editado.getTipoOperacao()).isEqualTo("Cheque");
        assertThat(editado.getAtualizadoPorId()).isEqualTo(analista.getId());
        assertThat(editado.getAtualizadoPorNome()).isEqualTo("Andressa");
    }

    @Test
    @DisplayName("editarParecerOrigem: quem criou o card edita o parecer no Comitê, mesmo sem ser analista")
    void shouldLetCreatorEditOriginOpinionInComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        LiberacaoCardEntity editado = service.editarParecerOrigem(card.getId(), card.getVersion(),
                "  Cliente antigo, pagou em dia as três últimas.  ", PosicaoParecer.FAVORAVEL, auxiliar);

        assertThat(editado.getParecerOrigem()).isEqualTo("Cliente antigo, pagou em dia as três últimas.");
        assertThat(editado.getPosicaoOrigem()).isEqualTo(PosicaoParecer.FAVORAVEL);
        assertThat(editado.getAtualizadoPorId()).isEqualTo(auxiliar.getId());
        verify(cardRepository).saveAndFlush(card);
        verify(eventoRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("editarParecerOrigem: no Comitê, quem não é analista nem criou o card é AcessoNegado")
    void shouldDenyOtherNonAnalystEditingOriginOpinion() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        UserEntity outro = UserEntity.builder().id(UUID.randomUUID()).name("Carla").email("carla@jgm.com").build();

        assertThatThrownBy(() -> service.editarParecerOrigem(card.getId(), card.getVersion(), "x", null, outro))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("quem criou o card");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("editarParecerOrigem: versão diferente é conflito; sem mudança não grava nada")
    void shouldCheckVersionAndSkipNoOpOriginOpinionEdit() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        assertThatThrownBy(() -> service.editarParecerOrigem(card.getId(), 999L, "x", null, analista))
                .isInstanceOf(ConflitoEdicaoException.class);

        service.editarParecerOrigem(card.getId(), card.getVersion(), "Cliente antigo", null, analista);

        verify(cardRepository, never()).saveAndFlush(any());
        verify(eventoRepository, never()).save(any());
    }

    @Test
    @DisplayName("editar: card inexistente ou apagado é EntityNotFound")
    void shouldFailWhenCardDoesNotExist() {
        UUID inexistente = UUID.randomUUID();
        when(cardRepository.findByIdAndExcluidoEmIsNull(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editar(inexistente, 1L, dadosNovos(CNPJ, "X", null), auxiliar))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ------------------------------------------- opinião O e decisões C e D (quadrados do sacado)

    @Test
    @DisplayName("opinarSacado: O (operador) é marcado por analista; grava quem, quando e o evento, sem mexer na decisão")
    void shouldMarkOriginOpinion() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        service.opinarSacado(card.getId(), "45.723.174/0001-10", QuadroOpiniao.ORIGEM, OpiniaoOperador.PARCIAL, analista);

        assertThat(a.getOpiniaoOrigem()).isEqualTo(OpiniaoOperador.PARCIAL);
        assertThat(a.getOpiniaoOrigemPorNome()).isEqualTo("Andressa");
        assertThat(a.getOpiniaoOrigemEm()).isNotNull();
        assertThat(a.getSituacao()).as("a situação final não muda").isNull();
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getCampo()).isEqualTo("opiniaoOrigem");
        assertThat(evento.getValorDepois()).isEqualTo("Parcial");
        assertThat(a.getOpiniaoComite()).as("o C não muda").isNull();
        verify(cardRepository).tocarSemVersao(eq(card.getId()), any(LocalDateTime.class), eq(analista.getId()), eq("Andressa"));
    }

    @Test
    @DisplayName("opinarSacado: O aceita Amostragem, que fica no histórico e não vira decisão do sacado")
    void shouldMarkSamplingOpinion() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        service.opinarSacado(card.getId(), SACADO_A, QuadroOpiniao.ORIGEM, OpiniaoOperador.AMOSTRAGEM, analista);

        assertThat(a.getOpiniaoOrigem()).isEqualTo(OpiniaoOperador.AMOSTRAGEM);
        assertThat(eventosGravados().get(0).getValorDepois()).isEqualTo("Amostragem");
        assertThat(a.getSituacao()).as("a situação final não muda").isNull();
        assertThat(a.getOpiniaoComite()).as("o C não muda").isNull();
    }

    // ------------------------------------------------------------ vários sacados de uma vez

    @Test
    @DisplayName("lote no O: a mesma resposta e o mesmo parecer em todos, um evento por sacado e um aviso só")
    void shouldMarkOriginOpinionInBatch() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        LiberacaoSacadoEntity b = sacado(card, SACADO_B, "Sacado B", "2000", 1);
        sacadosSao(card, a, b);

        int mudaram = service.marcarSacadosEmLote(card.getId(), List.of("45.723.174/0001-10", SACADO_B), QuadroOpiniao.ORIGEM,
                "amostragem", "  Conferir amostra dos títulos  ", analista);

        assertThat(mudaram).isEqualTo(2);
        assertThat(List.of(a, b)).allSatisfy(s -> {
            assertThat(s.getOpiniaoOrigem()).isEqualTo(OpiniaoOperador.AMOSTRAGEM);
            assertThat(s.getParecerOrigem()).isEqualTo("Conferir amostra dos títulos");
        });
        assertThat(eventosGravados()).hasSize(4);
        verify(cardRepository, times(1)).tocarSemVersao(eq(card.getId()), any(LocalDateTime.class), eq(analista.getId()), eq("Andressa"));
        verify(eventos, times(1)).publishEvent(any(LiberacaoEvento.Editado.class));
    }

    @Test
    @DisplayName("lote no C: aprova vários no Comitê; Parcial fica de fora (pede o valor de cada sacado)")
    void shouldDecideCommitteeInBatchButNotPartial() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        LiberacaoSacadoEntity b = sacado(card, SACADO_B, "Sacado B", "2000", 1);
        sacadosSao(card, a, b);

        assertThatThrownBy(() -> service.marcarSacadosEmLote(card.getId(), List.of(SACADO_A, SACADO_B), QuadroOpiniao.COMITE,
                "PARCIAL", null, analista))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("um por um");

        service.marcarSacadosEmLote(card.getId(), List.of(SACADO_A, SACADO_B), QuadroOpiniao.COMITE, "APROVADO", null, analista);

        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(b.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getSituacao()).as("a situação final só muda quando o card é decidido").isNull();
    }

    @Test
    @DisplayName("lote no C: Amostragem também vale para vários de uma vez")
    void shouldDecideSamplingInBatch() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        LiberacaoSacadoEntity b = sacado(card, SACADO_B, "Sacado B", "500", 1);
        sacadosSao(card, a, b);

        assertThat(service.marcarSacadosEmLote(card.getId(), List.of(SACADO_A, SACADO_B), QuadroOpiniao.COMITE,
                "AMOSTRAGEM", null, analista)).isEqualTo(2);

        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.AMOSTRAGEM);
        assertThat(b.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.AMOSTRAGEM);
    }

    @Test
    @DisplayName("lote: sacado fora do card, auxiliar ou nada a fazer recusam sem gravar nada")
    void shouldRefuseInvalidBatch() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.marcarSacadosEmLote(card.getId(), List.of(SACADO_A, "11222333000181"),
                QuadroOpiniao.ORIGEM, "APROVADO", null, analista))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.marcarSacadosEmLote(card.getId(), List.of(SACADO_A), QuadroOpiniao.ORIGEM,
                "APROVADO", null, auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.marcarSacadosEmLote(card.getId(), List.of(SACADO_A), QuadroOpiniao.ORIGEM,
                null, null, analista))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(a.getOpiniaoOrigem()).isNull();
        verify(sacadoRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ORIGEM", "COMITE", "PENDENCIA"})
    @DisplayName("opinarSacado: O vale em qualquer etapa antes da decisão")
    void shouldAllowOperatorOpinionBeforeDecision(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        service.opinarSacado(card.getId(), SACADO_A, QuadroOpiniao.ORIGEM, OpiniaoOperador.REPROVADO, analista);

        assertThat(a.getOpiniaoOrigem()).isEqualTo(OpiniaoOperador.REPROVADO);
    }

    @Test
    @DisplayName("opinarSacado: O é de analista — auxiliar é AcessoNegado, mesmo na Origem")
    void shouldRejectOperatorOpinionFromNonAnalyst() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.opinarSacado(card.getId(), SACADO_A, QuadroOpiniao.ORIGEM, OpiniaoOperador.APROVADO, auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        verify(sacadoRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"DIRETORIA", "APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("opinarSacado: O depois da decisão (ou na Diretoria) é recusado")
    void shouldRejectOperatorOpinionAfterDecision(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.opinarSacado(card.getId(), SACADO_A, QuadroOpiniao.ORIGEM, OpiniaoOperador.APROVADO, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining(etapa == EtapaLiberacao.DIRETORIA ? "antes da decisão" : "reabra no Comitê");
        verify(sacadoRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = QuadroOpiniao.class, names = {"COMITE", "DIRETORIA"})
    @DisplayName("opinarSacado: C e D são decisão, não opinião — a recusa manda marcar pela decisão do sacado e nada é gravado")
    void shouldRejectCommitteeAndDiretoriaAsOpinion(QuadroOpiniao quadro) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.opinarSacado(card.getId(), SACADO_A, quadro, OpiniaoOperador.APROVADO, analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O quadrado " + quadro.letra() + " é decisão: marque pela decisão do sacado.");
        assertThat(a.getOpiniaoComite()).isNull();
        assertThat(a.getDecisaoDiretoria()).isNull();
        verify(sacadoRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
    }

    @Test
    @DisplayName("opinarSacado: repetir o O não grava; situação nula limpa quem e quando")
    void shouldIgnoreRepeatedOpinionAndClear() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        a.setOpiniaoOrigem(OpiniaoOperador.APROVADO);
        a.setOpiniaoOrigemPorNome("Aline");
        sacadosSao(card, a);

        service.opinarSacado(card.getId(), SACADO_A, QuadroOpiniao.ORIGEM, OpiniaoOperador.APROVADO, analista);
        verify(sacadoRepository, never()).save(any());

        service.opinarSacado(card.getId(), SACADO_A, QuadroOpiniao.ORIGEM, null, analista);
        assertThat(a.getOpiniaoOrigem()).isNull();
        assertThat(a.getOpiniaoOrigemPorNome()).isNull();
        assertThat(a.getOpiniaoOrigemEm()).isNull();
    }

    @Test
    @DisplayName("opinarSacado: documento que não é sacado do card é EntityNotFound")
    void shouldRejectUnknownSacadoOpinion() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.opinarSacado(card.getId(), SACADO_B, QuadroOpiniao.ORIGEM, OpiniaoOperador.APROVADO, analista))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("parecerSacado: grava o texto do quadrado, registra evento com a letra e avisa quem foi marcado")
    void shouldWriteSacadoParecerAndNotifyMentions() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);
        UUID mychelly = UUID.randomUUID();
        when(userRepository.findAllById(any())).thenReturn(List.of(UserEntity.builder().id(mychelly).name("Mychelly").build()));

        service.parecerSacado(card.getId(), SACADO_A, QuadroOpiniao.DIRETORIA,
                "  Parcial. @[Mychelly](user:" + mychelly + ") confirma?  ", diretor);

        assertThat(a.getParecerDiretoria()).isEqualTo("Parcial. @[Mychelly](user:" + mychelly + ") confirma?");
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getCampo()).isEqualTo("parecerSacado");
        assertThat(evento.getValorDepois()).isEqualTo("D");
        ArgumentCaptor<Object> publicado = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(publicado.capture());
        assertThat(publicado.getValue()).isInstanceOfSatisfying(LiberacaoEvento.Editado.class,
                editado -> assertThat(editado.mencionados()).containsExactly(mychelly));
    }

    @Test
    @DisplayName("parecerSacado: mesmas regras de quem marca o quadrado; texto igual não grava; vazio apaga")
    void shouldApplySquareRulesToSacadoParecer() {
        LiberacaoCardEntity naOrigem = card(EtapaLiberacao.ORIGEM);
        LiberacaoSacadoEntity a = sacado(naOrigem, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(naOrigem, a);
        service.parecerSacado(naOrigem.getId(), SACADO_A, QuadroOpiniao.ORIGEM, "Cliente antigo.", analista);
        assertThat(a.getParecerOrigem()).isEqualTo("Cliente antigo.");
        assertThatThrownBy(() -> service.parecerSacado(naOrigem.getId(), SACADO_A, QuadroOpiniao.ORIGEM, "x", auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.parecerSacado(naOrigem.getId(), SACADO_A, QuadroOpiniao.COMITE, "x", analista))
                .isInstanceOf(TransicaoInvalidaException.class);

        LiberacaoCardEntity noComite = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity b = sacado(noComite, SACADO_A, "Sacado A", "1000", 0);
        b.setParecerComite("Ok.");
        sacadosSao(noComite, b);
        assertThatThrownBy(() -> service.parecerSacado(noComite.getId(), SACADO_A, QuadroOpiniao.COMITE, "x", auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.parecerSacado(noComite.getId(), SACADO_A, QuadroOpiniao.ORIGEM, "x", auxiliar))
                .isInstanceOf(AcessoNegadoException.class);

        service.parecerSacado(noComite.getId(), SACADO_A, QuadroOpiniao.COMITE, " Ok. ", analista);
        verify(sacadoRepository, times(1)).save(any());
        // o operador também escreve com o card no Comitê
        service.parecerSacado(noComite.getId(), SACADO_A, QuadroOpiniao.ORIGEM, "Sacado pontual.", analista);
        assertThat(b.getParecerOrigem()).isEqualTo("Sacado pontual.");
        service.parecerSacado(noComite.getId(), SACADO_A, QuadroOpiniao.COMITE, "   ", analista);
        assertThat(b.getParecerComite()).isNull();
    }

    @Test
    @DisplayName("parecerSacado: C vale no Comitê e em Pendência, só para analista; fora disso é recusado")
    void shouldLimitComiteParecerToAnalystInComiteOrPendencia() {
        for (EtapaLiberacao etapa : List.of(EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA)) {
            LiberacaoCardEntity card = card(etapa);
            LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
            sacadosSao(card, a);
            service.parecerSacado(card.getId(), SACADO_A, QuadroOpiniao.COMITE, "Ok no " + etapa, analista);
            assertThat(a.getParecerComite()).isEqualTo("Ok no " + etapa);
        }
        for (EtapaLiberacao etapa : List.of(EtapaLiberacao.DIRETORIA, EtapaLiberacao.APROVADO)) {
            LiberacaoCardEntity card = card(etapa);
            LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
            sacadosSao(card, a);
            assertThatThrownBy(() -> service.parecerSacado(card.getId(), SACADO_A, QuadroOpiniao.COMITE, "x", analista))
                    .as("C com o card em %s", etapa)
                    .isInstanceOf(TransicaoInvalidaException.class);
            assertThat(a.getParecerComite()).isNull();
        }
    }

    @Test
    @DisplayName("parecerSacado: D é só de quem tem o perfil Diretoria, com o card na Diretoria")
    void shouldLimitDiretoriaParecerToDiretoriaInDiretoriaStage() {
        LiberacaoCardEntity naDiretoria = card(EtapaLiberacao.DIRETORIA);
        LiberacaoSacadoEntity a = sacado(naDiretoria, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(naDiretoria, a);

        // analista sem o perfil Diretoria e quem nem é analista: recusados, mesmo com o card no lugar certo
        assertThatThrownBy(() -> service.parecerSacado(naDiretoria.getId(), SACADO_A, QuadroOpiniao.DIRETORIA, "x", analista))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Só a Diretoria pode marcar o quadrado D.");
        assertThatThrownBy(() -> service.parecerSacado(naDiretoria.getId(), SACADO_A, QuadroOpiniao.DIRETORIA, "x", auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        assertThat(a.getParecerDiretoria()).isNull();

        service.parecerSacado(naDiretoria.getId(), SACADO_A, QuadroOpiniao.DIRETORIA, " Limite curto. ", diretor);
        assertThat(a.getParecerDiretoria()).isEqualTo("Limite curto.");

        // a Diretoria também não escreve o D fora da etapa dela
        LiberacaoCardEntity noComite = card(EtapaLiberacao.COMITE);
        sacadosSao(noComite, sacado(noComite, SACADO_A, "Sacado A", "1000", 0));
        assertThatThrownBy(() -> service.parecerSacado(noComite.getId(), SACADO_A, QuadroOpiniao.DIRETORIA, "x", diretor))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("O quadrado D é marcado com o card na Diretoria.");
    }

    @Test
    @DisplayName("editar: opiniões O e C ficam com o sacado mesmo quando o valor muda")
    void shouldKeepOpinionsWhenEditingSacados() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        a.setOpiniaoOrigem(OpiniaoOperador.APROVADO);
        a.setOpiniaoComite(ResultadoLiberacao.REPROVADO);
        a.setParecerDiretoria("Limite curto.");
        sacadosSao(card, a);
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null, List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("800"))));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        assertThat(sacadosGravados()).singleElement().satisfies(sacado -> {
            assertThat(sacado.getOpiniaoOrigem()).isEqualTo(OpiniaoOperador.APROVADO);
            assertThat(sacado.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.REPROVADO);
            assertThat(sacado.getParecerDiretoria()).isEqualTo("Limite curto.");
        });
    }

    @Test
    @DisplayName("criar: grava taxa (%) e TAC (R$), opcionais")
    void shouldStoreTaxaAndTacOnCreate() {
        naBase(CNPJ, "ACME DA BASE S/A");
        DadosCard dados = new DadosCard(CNPJ, null, "Duplicata", new BigDecimal("5000"), null, null, null, null, null,
                new BigDecimal("2.35"), new BigDecimal("350"));

        LiberacaoCardEntity criado = service.criar(dados, auxiliar);

        assertThat(criado.getTaxa()).isEqualByComparingTo("2.35");
        assertThat(criado.getTac()).isEqualByComparingTo("350");
        // sem taxa e TAC também cria
        assertThat(service.criar(dadosNovos(CNPJ, null, null), auxiliar).getTaxa()).isNull();
    }

    @Test
    @DisplayName("editar: mudar taxa e TAC grava um evento de cada, taxa em % e TAC em reais")
    void shouldRecordTaxaAndTacChanges() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        card.setTaxa(new BigDecimal("2.35"));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null, null, null, new BigDecimal("1.875"), new BigDecimal("420"));

        service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        List<LiberacaoEventoEntity> eventos = eventosGravados();
        assertThat(eventos).extracting(LiberacaoEventoEntity::getCampo).containsExactlyInAnyOrder("taxa", "tac");
        LiberacaoEventoEntity taxa = eventos.stream().filter(evento -> "taxa".equals(evento.getCampo())).findFirst().orElseThrow();
        assertThat(taxa.getValorAntes()).isEqualTo("2,35%");
        assertThat(taxa.getValorDepois()).isEqualTo("1,875%");
        LiberacaoEventoEntity tac = eventos.stream().filter(evento -> "tac".equals(evento.getCampo())).findFirst().orElseThrow();
        assertThat(tac.getValorAntes()).isEqualTo("—");
        assertThat(semEspacoInsecavel(tac.getValorDepois())).isEqualTo("R$ 420,00");
    }

    @Test
    @DisplayName("criar e editar: tranche grava e a mudança entra no histórico em reais")
    void shouldStoreAndRecordTranche() {
        naBase(CNPJ, "ACME DA BASE S/A");
        DadosCard nova = new DadosCard(CNPJ, null, "Duplicata", new BigDecimal("5000"), null, null, null, null, null,
                null, null, new BigDecimal("3126267.01"));
        assertThat(service.criar(nova, auxiliar).getTranche()).isEqualByComparingTo("3126267.01");

        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null, null, null, null, null, new BigDecimal("2931563.41"));
        service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        LiberacaoEventoEntity evento = eventosGravados().stream().filter(e -> "tranche".equals(e.getCampo())).findFirst().orElseThrow();
        assertThat(evento.getValorAntes()).isEqualTo("—");
        assertThat(semEspacoInsecavel(evento.getValorDepois())).isEqualTo("R$ 2.931.563,41");
    }

    @Test
    @DisplayName("editar: mesma taxa com escala diferente (2.5 e 2.5000) não é mudança")
    void shouldIgnoreTaxaScaleChange() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        card.setTaxa(new BigDecimal("2.5000"));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null, null, null, new BigDecimal("2.5"), null);

        service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        verify(eventoRepository, never()).save(any());
    }

    @Test
    @DisplayName("editar: mudar o valor grava evento EDICAO do campo 'valor' com antes/depois em reais")
    void shouldRecordValueChangeWithFormattedBeforeAndAfter() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), new BigDecimal("2500.50"),
                card.getPrazo(), card.getParecerOrigem(), null, null);

        LiberacaoCardEntity editado = service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        assertThat(editado.getValor()).isEqualByComparingTo("2500.50");
        List<LiberacaoEventoEntity> eventos = eventosGravados();
        assertThat(eventos).hasSize(1);
        LiberacaoEventoEntity evento = eventos.get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.EDICAO);
        assertThat(evento.getCampo()).isEqualTo("valor");
        assertThat(semEspacoInsecavel(evento.getValorAntes())).isEqualTo("R$ 1.000,00");
        assertThat(semEspacoInsecavel(evento.getValorDepois())).isEqualTo("R$ 2.500,50");
        assertThat(evento.getUsuarioId()).isEqualTo(auxiliar.getId());
        verify(cardRepository).saveAndFlush(card);
    }

    @Test
    @DisplayName("editar: marcar a empresa grava evento EDICAO do campo 'empresa'; tirar volta a nula")
    void shouldRecordEmpresaChange() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null, null, null, null, null, null, EmpresaOperacao.FIDC);

        LiberacaoCardEntity editado = service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        assertThat(editado.getEmpresa()).isEqualTo(EmpresaOperacao.FIDC);
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getCampo()).isEqualTo("empresa");
        assertThat(evento.getValorAntes()).isEqualTo("—");
        assertThat(evento.getValorDepois()).isEqualTo("FIDC");

        DadosCard semEmpresa = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null, null);
        assertThat(service.editar(card.getId(), card.getVersion(), semEmpresa, auxiliar).getEmpresa()).isNull();
    }

    @Test
    @DisplayName("criar: guarda a empresa da operação")
    void shouldStoreEmpresaOnCreate() {
        naBase(CNPJ, "ACME");

        LiberacaoCardEntity criado = service.criar(new DadosCard(CNPJ, null, "Duplicata", null, null, null, null, null,
                null, null, null, null, EmpresaOperacao.SEC), auxiliar);

        assertThat(criado.getEmpresa()).isEqualTo(EmpresaOperacao.SEC);
    }

    @Test
    @DisplayName("editar: valor igual em outra escala (1000 vs 1000.00) não conta como mudança")
    void shouldTreatSameValueWithDifferentScaleAsUnchanged() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), new BigDecimal("1000"),
                card.getPrazo(), card.getParecerOrigem(), null, null);

        service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        verify(eventoRepository, never()).save(any());
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("editar: sem nenhuma mudança não grava evento nem card")
    void shouldNotSaveNorRecordEventWhenNothingChanged() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        LocalDateTime atualizadoAntes = card.getAtualizadoEm();

        LiberacaoCardEntity retorno = service.editar(card.getId(), card.getVersion(), dadosIguaisA(card), auxiliar);

        assertThat(retorno).isSameAs(card);
        assertThat(retorno.getAtualizadoEm()).isEqualTo(atualizadoAntes);
        assertThat(retorno.getAtualizadoPorNome()).isEqualTo("Beatriz");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(cardRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
        verify(sacadoRepository, never()).apagarDoCard(any());
        verify(sacadoRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("editar: um evento por campo alterado")
    void shouldRecordOneEventPerChangedField() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        DadosCard dados = new DadosCard(CNPJ, null, "Cheque", new BigDecimal("1.50"),
                LocalDateTime.of(2026, 12, 31, 18, 0), null, null, null);

        service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        List<LiberacaoEventoEntity> eventos = eventosGravados();
        assertThat(eventos).extracting(LiberacaoEventoEntity::getCampo)
                .containsExactlyInAnyOrder("tipoOperacao", "valor", "prazo", "parecerOrigem");
        LiberacaoEventoEntity tipo = eventos.stream().filter(e -> "tipoOperacao".equals(e.getCampo())).findFirst().orElseThrow();
        assertThat(tipo.getValorAntes()).isEqualTo("Duplicata");
        assertThat(tipo.getValorDepois()).isEqualTo("Cheque");
        LiberacaoEventoEntity prazo = eventos.stream().filter(e -> "prazo".equals(e.getCampo())).findFirst().orElseThrow();
        assertThat(prazo.getValorAntes()).isEqualTo("30/10/2026 12:00");
        assertThat(prazo.getValorDepois()).isEqualTo("31/12/2026 18:00");
        LiberacaoEventoEntity parecer = eventos.stream().filter(e -> "parecerOrigem".equals(e.getCampo())).findFirst().orElseThrow();
        assertThat(parecer.getValorAntes()).isEqualTo("Cliente antigo");
        assertThat(parecer.getValorDepois()).isNull();
        assertThat(card.getParecerOrigem()).isNull();
    }

    @Test
    @DisplayName("editar: trocar o CNPJ do cedente grava 'cedente' com 'nome · CNPJ' antes/depois")
    void shouldRecordCedenteChange() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        String novoCnpj = "45723174000110";
        naBase(novoCnpj, "NOVA EMPRESA");
        DadosCard dados = new DadosCard(novoCnpj, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null, null);

        LiberacaoCardEntity editado = service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        assertThat(editado.getCedenteCnpj()).isEqualTo(novoCnpj);
        assertThat(editado.getCedenteNome()).isEqualTo("NOVA EMPRESA");
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getCampo()).isEqualTo("cedente");
        assertThat(evento.getValorAntes()).isEqualTo("ACME LTDA · 11.222.333/0001-81");
        assertThat(evento.getValorDepois()).isEqualTo("NOVA EMPRESA · 45.723.174/0001-10");
    }

    @Test
    @DisplayName("editar: mesmo CNPJ com razão social corrigida grava a mudança de nome")
    void shouldRecordCedenteNameChangeForSameCnpj() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        DadosCard dados = new DadosCard(CNPJ, "  ACME COMERCIO LTDA ", card.getTipoOperacao(), card.getValor(),
                card.getPrazo(), card.getParecerOrigem(), null, null);

        service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getCampo()).isEqualTo("cedente");
        assertThat(evento.getValorAntes()).isEqualTo("ACME LTDA");
        assertThat(evento.getValorDepois()).isEqualTo("ACME COMERCIO LTDA");
        assertThat(card.getCedenteNome()).isEqualTo("ACME COMERCIO LTDA");
    }

    @Test
    @DisplayName("editar: mudar só os sacados regrava a lista e grava um evento 'sacados' com o resumo")
    void shouldRewriteSacadosAndRecordSummary() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        when(sacadoRepository.findByCardIdOrderByOrdem(card.getId())).thenReturn(List.of());
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null, List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("10"))));

        service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        verify(sacadoRepository).apagarDoCard(card.getId());
        assertThat(sacadosGravados()).hasSize(1);
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.EDICAO);
        assertThat(evento.getCampo()).isEqualTo("sacados");
        assertThat(evento.getTexto()).isEqualTo("+ Sacado A (45.723.174/0001-10)");
    }

    @Test
    @DisplayName("editar: sacados idênticos não regravam a lista")
    void shouldNotRewriteSacadosWhenIdentical() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        when(sacadoRepository.findByCardIdOrderByOrdem(card.getId())).thenReturn(List.of(
                sacado(card, SACADO_A, "Sacado A", "10", 0)));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null, List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("10.00"))));

        service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        verify(sacadoRepository, never()).apagarDoCard(any());
        verify(eventoRepository, never()).save(any());
    }

    // ------------------------------------------------------------ transicionar

    @Test
    @DisplayName("transicionar: etapa 'de' desatualizada é ConflitoEdicaoException (outra pessoa já moveu)")
    void shouldRejectTransitionWhenCardAlreadyMoved() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE,
                null, null, auxiliar))
                .isInstanceOf(ConflitoEdicaoException.class)
                .hasMessageContaining("Comitê")
                .hasMessageContaining("Beatriz");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("transicionar Origem→Comitê: cria um parecer pendente por membro do Comitê e os adiciona como membros")
    void shouldConvokeEveryComiteMemberOnMoveToComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        UserEntity bruna = usuario("Bruna");
        UserEntity carla = usuario("Carla");
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(bruna, carla));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.ORIGEM,
                EtapaLiberacao.COMITE, null, "Pode seguir", auxiliar);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(movido.getFinalizadoEm()).isNull();
        assertThat(movido.getAtualizadoPorNome()).isEqualTo("Auxiliar");

        ArgumentCaptor<LiberacaoParecerEntity> pareceres = ArgumentCaptor.forClass(LiberacaoParecerEntity.class);
        verify(parecerRepository, times(2)).save(pareceres.capture());
        assertThat(pareceres.getAllValues()).extracting(LiberacaoParecerEntity::getUsuarioId)
                .containsExactly(bruna.getId(), carla.getId());
        assertThat(pareceres.getAllValues()).extracting(LiberacaoParecerEntity::getUsuarioNome)
                .containsExactly("Bruna", "Carla");
        assertThat(pareceres.getAllValues()).allSatisfy(parecer -> {
            assertThat(parecer.getCardId()).isEqualTo(card.getId());
            assertThat(parecer.getRodada()).isEqualTo(1);
            assertThat(parecer.getPosicao()).isNull();
            assertThat(parecer.registrado()).isFalse();
        });

        List<LiberacaoMembroEntity> membros = membrosGravados();
        assertThat(membros).extracting(LiberacaoMembroEntity::getUsuarioId).containsExactly(bruna.getId(), carla.getId());
        assertThat(membros).extracting(LiberacaoMembroEntity::getOrigem).containsOnly(OrigemMembro.COMITE);

        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.TRANSICAO);
        assertThat(evento.getEtapaDe()).isEqualTo(EtapaLiberacao.ORIGEM);
        assertThat(evento.getEtapaPara()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(evento.getTexto()).isEqualTo("Pode seguir");
    }

    @Test
    @DisplayName("transicionar →Comitê: quem já tem parecer na rodada não é duplicado e quem já é membro não é regravado")
    void shouldNotDuplicateParecerOrMembership() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        UserEntity bruna = usuario("Bruna");
        UserEntity carla = usuario("Carla");
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(bruna, carla));
        when(parecerRepository.findByCardIdAndRodadaAndUsuarioId(card.getId(), 1, bruna.getId()))
                .thenReturn(Optional.of(parecer(card, bruna, PosicaoParecer.FAVORAVEL)));
        when(membroRepository.existsByCardIdAndUsuarioId(card.getId(), bruna.getId())).thenReturn(true);

        service.transicionar(card.getId(), EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, null, null, auxiliar);

        ArgumentCaptor<LiberacaoParecerEntity> pareceres = ArgumentCaptor.forClass(LiberacaoParecerEntity.class);
        verify(parecerRepository).save(pareceres.capture());
        assertThat(pareceres.getValue().getUsuarioId()).isEqualTo(carla.getId());
        assertThat(membrosGravados()).extracting(LiberacaoMembroEntity::getUsuarioId).containsExactly(carla.getId());
    }

    @Test
    @DisplayName("transicionar →Comitê: sem ninguém marcado como Comitê é TransicaoInvalida e nada é gravado")
    void shouldRejectMoveToComiteWhenNobodyIsComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of());

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE,
                null, null, auxiliar))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("Ninguém está marcado como Comitê");
        verify(cardRepository, never()).saveAndFlush(any());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.ORIGEM);
    }

    @Test
    @DisplayName("transicionar: pendência só é aceita quando o destino é Pendência")
    void shouldRejectPendenciaOutsidePendenciaStage() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.PENDENCIA, EtapaLiberacao.COMITE,
                List.of(new NovaPendencia(UUID.randomUUID(), "Algo")), null, analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pendência só é aberta ao mover para Pendência");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("transicionar: caminho que a máquina de estados não permite é recusado")
    void shouldRejectInvalidPath() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.ORIGEM, EtapaLiberacao.FINALIZADO,
                null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Não dá para ir de Origem para Finalizados.");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("transicionar: Finalizado só volta ao Comitê; qualquer outro destino é recusado")
    void shouldRejectInvalidPathFromFinalizado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.FINALIZADO, EtapaLiberacao.PENDENCIA,
                null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Não dá para ir de Finalizados para Pendência.");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------- saída do Comitê: pelo menos um parecer

    private void comUmParecer(LiberacaoCardEntity card) {
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL));
    }

    private void diretoriaTem(UserEntity... membros) {
        when(userRepository.findByDiretoriaTrueOrderByNameAsc()).thenReturn(List.of(membros));
    }

    private LiberacaoEventoEntity ultimoEvento() {
        List<LiberacaoEventoEntity> gravados = eventosGravados();
        return gravados.get(gravados.size() - 1);
    }

    private LiberacaoSacadoEntity decidido(LiberacaoCardEntity card, String documento, String nome, String valor, int ordem,
                                           ResultadoLiberacao situacao, String valorAprovado) {
        LiberacaoSacadoEntity sacado = sacado(card, documento, nome, valor, ordem);
        sacado.setSituacao(situacao);
        sacado.setValorAprovado(valorAprovado == null ? null : new BigDecimal(valorAprovado));
        sacado.setSituacaoPorNome(situacao == null ? null : "Mychelly");
        sacado.setSituacaoEm(situacao == null ? null : LocalDateTime.of(2026, 10, 7, 15, 0));
        return sacado;
    }

    /** Sacado com o C marcado: a decisão do Comitê. A situação final só nasce quando o card é decidido. */
    private LiberacaoSacadoEntity comiteDecidiu(LiberacaoCardEntity card, String documento, String nome, String valor, int ordem,
                                                ResultadoLiberacao decisao, String valorAprovado) {
        LiberacaoSacadoEntity sacado = sacado(card, documento, nome, valor, ordem);
        sacado.setOpiniaoComite(decisao);
        sacado.setValorAprovadoComite(valorAprovado == null ? null : new BigDecimal(valorAprovado));
        sacado.setOpiniaoComitePorNome(decisao == null ? null : "Mychelly");
        sacado.setOpiniaoComiteEm(decisao == null ? null : LocalDateTime.of(2026, 10, 7, 15, 0));
        return sacado;
    }

    /** Fixture de um D já gravado num sacado que tem o C (card decidido pela Diretoria ou antigo). Hoje o D só nasce na decisão do card. */
    private LiberacaoSacadoEntity comD(LiberacaoSacadoEntity sacado, ResultadoLiberacao decisao, String valorAprovado) {
        sacado.setDecisaoDiretoria(decisao);
        sacado.setValorAprovadoDiretoria(valorAprovado == null ? null : new BigDecimal(valorAprovado));
        sacado.setDecisaoDiretoriaPorNome(decisao == null ? null : "Walter");
        sacado.setDecisaoDiretoriaEm(decisao == null ? null : LocalDateTime.of(2026, 10, 14, 10, 0));
        return sacado;
    }

    private void sacadosSao(LiberacaoCardEntity card, LiberacaoSacadoEntity... sacados) {
        when(sacadoRepository.findByCardIdOrderByOrdem(card.getId())).thenReturn(List.of(sacados));
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"PENDENCIA", "DIRETORIA", "APROVADO", "REPROVADO"})
    @DisplayName("transicionar Comitê→Pendência/Diretoria/Aprovado/Reprovado: sem nenhum parecer registrado é TransicaoInvalida e nada é gravado")
    void shouldBlockLeavingComiteWithoutAnyParecer(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), null), parecer(card, usuario("Mychelly"), null));
        sacadosSao(card, comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));
        // com a Diretoria preenchida, o que barra é a falta de parecer, não a falta de diretor
        diretoriaTem(diretor);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, destino, null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Precisa de pelo menos um parecer do Comitê.");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(eventoRepository, never()).save(any());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(card.getFinalizadoEm()).isNull();
    }

    @Test
    @DisplayName("transicionar Comitê→Aprovado: rodada sem nenhuma linha de parecer também é bloqueada")
    void shouldBlockLeavingComiteWhenRodadaHasNoRows() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, List.of(), analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Precisa de pelo menos um parecer do Comitê.");
    }

    @Test
    @DisplayName("transicionar Comitê→Aprovado: com 1 de 2 pareceres libera e o histórico diz quem faltou")
    void shouldLeaveComiteWithOneOfTwoPareceresAndRecordWhoWasMissing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, List.of(), analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(ultimoEvento().getTexto()).isEqualTo("Movido sem o parecer de Mychelly.");
    }

    @Test
    @DisplayName("transicionar Comitê→Aprovado com ressalva: o card guarda a ressalva e o histórico diz qual")
    void shouldApproveWithReservation() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.COM_RESSALVAS));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, List.of(), "  Só com nota fiscal conferida  ", analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(movido.getRessalva()).isEqualTo("Só com nota fiscal conferida");
        assertThat(ultimoEvento().getTexto()).isEqualTo("Aprovado com ressalva: Só com nota fiscal conferida");
    }

    @Test
    @DisplayName("ressalva só vale indo para Aprovado")
    void shouldRejectReservationOutsideApproval() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.REPROVADO,
                null, "não", List.of(), "ressalva", analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ressalva só ao aprovar");
    }

    @Test
    @DisplayName("transicionar Comitê→Aprovado: vários pareceres faltando são unidos por ' e '")
    void shouldListEveryMissingParecerInTheHistory() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), null), parecer(card, usuario("Carla"), null));

        service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, List.of(), analista);

        assertThat(ultimoEvento().getTexto()).isEqualTo("Movido sem o parecer de Mychelly e Carla.");
    }

    @Test
    @DisplayName("transicionar Comitê→Aprovado: o aviso de parecer faltando vem depois da observação")
    void shouldAppendMissingParecerNoticeAfterObservation() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), null));

        service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, "  Cliente com urgência  ", List.of(), analista);

        assertThat(ultimoEvento().getTexto())
                .isEqualTo("Cliente com urgência\n\nMovido sem o parecer de Mychelly.");
    }

    @Test
    @DisplayName("transicionar Comitê→Pendência: parecer faltando também fica dito no histórico")
    void shouldRecordMissingParecerWhenMovingToPendencia() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), null));
        when(pendenciaRepository.countByCardIdAndRespondidaEmIsNull(card.getId())).thenReturn(1L);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.PENDENCIA, null, null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.PENDENCIA);
        assertThat(ultimoEvento().getTexto()).isEqualTo("Movido sem o parecer de Mychelly.");
    }

    @Test
    @DisplayName("transicionar Comitê→Aprovado: com todos os pareceres registrados não há aviso")
    void shouldNotRecordNoticeWhenAllPareceresAreIn() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), PosicaoParecer.COM_RESSALVAS));

        service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, List.of(), analista);

        assertThat(ultimoEvento().getTexto()).isNull();
    }

    @Test
    @DisplayName("transicionar Comitê→Aprovado: parecer de usuário removido (usuarioId nulo) não conta como faltando")
    void shouldNotCountParecerOfRemovedUserAsMissing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, null, null), parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, List.of(), analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(ultimoEvento().getTexto()).isNull();
    }

    @Test
    @DisplayName("transicionar Pendência→Aprovado: não pede todos os pareceres nem avisa de parecer faltando")
    void shouldFinalizeFromPendenciaWithoutAllPareceres() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL), parecer(card, usuario("Mychelly"), null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.PENDENCIA,
                EtapaLiberacao.APROVADO, null, null, List.of(), analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(movido.getFinalizadoEm()).isNotNull();
        assertThat(ultimoEvento().getTexto()).isNull();
    }

    @Test
    @DisplayName("transicionar: não-analista não move do Comitê em diante e nenhuma decisão é aplicada")
    void shouldDenyNonAnalystMovingCardFromComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity sacado = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, sacado);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.ORIGEM,
                null, null, auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.APROVADO, null)), auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        verify(parecerRepository, never()).apagarAguardando(any(), anyInt());
        verify(sacadoRepository, never()).save(any());
        assertThat(sacado.getSituacao()).isNull();
    }

    @Test
    @DisplayName("transicionar Comitê→Origem: apaga pareceres aguardando da rodada e abre rodada nova, mesmo com parecer faltando")
    void shouldDropWaitingPareceresAndBumpRodadaOnReturnToOrigem() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.ORIGEM, null, "Falta anexar o contrato", analista);

        verify(parecerRepository).apagarAguardando(card.getId(), 1);
        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.ORIGEM);
        assertThat(movido.getRodada()).isEqualTo(2);
        assertThat(movido.getFinalizadoEm()).isNull();
        verify(parecerRepository, never()).save(any());
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.TRANSICAO);
        // devolver não é decidir: nada de "Movido sem o parecer de ..."
        assertThat(evento.getTexto()).isEqualTo("Falta anexar o contrato");
    }

    @Test
    @DisplayName("transicionar Comitê→Origem: devolver sem nenhum parecer registrado é permitido")
    void shouldAllowReturnToOrigemWithoutAnyParecer() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.ORIGEM, null, null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.ORIGEM);
    }

    @Test
    @DisplayName("transicionar Origem→Comitê depois de uma devolução convoca o Comitê na rodada nova")
    void shouldConvokeComiteInNewRodadaAfterReturn() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        UserEntity bruna = usuario("Bruna");
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(bruna));
        service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.ORIGEM, null, null, analista);
        assertThat(card.getRodada()).isEqualTo(2);

        service.transicionar(card.getId(), EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, null, null, auxiliar);

        ArgumentCaptor<LiberacaoParecerEntity> pareceres = ArgumentCaptor.forClass(LiberacaoParecerEntity.class);
        verify(parecerRepository).save(pareceres.capture());
        assertThat(pareceres.getValue().getRodada()).isEqualTo(2);
    }

    // ----------------------------------------------------------- Finalizados

    @Test
    @DisplayName("aprovar: sacado sem decisão bloqueia, a mensagem lista quem falta e nada é gravado")
    void shouldBlockFinalizeWhileASacadoIsUndecided() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, null, null));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Decida todos os sacados antes de aprovar. Falta: Sacado B (00.360.305/0001-04).");
        verify(cardRepository, never()).saveAndFlush(any());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(card.getResultado()).isNull();
        assertThat(card.getFinalizadoEm()).isNull();
    }

    @Test
    @DisplayName("aprovar: vários sacados sem decisão são listados, e o sem nome aparece só pelo documento")
    void shouldListEveryUndecidedSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, null, null),
                comiteDecidiu(card, SACADO_B, null, "500", 1, null, null),
                comiteDecidiu(card, CPF, "Pessoa Física", "300", 2, ResultadoLiberacao.REPROVADO, null));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Decida todos os sacados antes de aprovar. Falta: "
                        + "Sacado A (45.723.174/0001-10), 00.360.305/0001-04.");
    }

    @Test
    @DisplayName("aprovar: decisão do pedido que cobre o sacado que faltava libera a finalização")
    void shouldFinalizeWhenRequestDecidesTheMissingSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity b = comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, null, null);
        sacadosSao(card, comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null), b);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, List.of(new DecisaoSacado(SACADO_B, ResultadoLiberacao.APROVADO, null)), analista);

        assertThat(b.getOpiniaoComite()).as("o pedido marca o C").isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(b.getSituacao()).as("e ao decidir o card o C vira a situação").isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
    }

    @Test
    @DisplayName("aprovar: card sem sacados é aprovado direto, sem decisão, com resultado APROVADO")
    void shouldApproveSacadoLessCardWithoutDecisions() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, List.of(), analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(movido.getFinalizadoEm()).isNotNull();
        assertThat(ultimoEvento().getValorDepois()).isEqualTo(ResultadoLiberacao.APROVADO.rotulo());
    }

    @Test
    @DisplayName("reprovar: card sem sacados é reprovado com resultado REPROVADO e sem eventos de sacado")
    void shouldRejectSacadoLessCard() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card);

        LiberacaoCardEntity movido = reprovar(card, EtapaLiberacao.COMITE, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.REPROVADO);
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(eventosGravados()).extracting(LiberacaoEventoEntity::getTipo)
                .containsExactly(TipoEventoLiberacao.TRANSICAO);
        assertThat(ultimoEvento().getValorDepois()).isEqualTo(ResultadoLiberacao.REPROVADO.rotulo());
    }

    @Test
    @DisplayName("aprovar: todos os sacados aprovados = APROVADO")
    void shouldComputeAprovadoWhenEverySacadoIsApproved() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.APROVADO, null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
    }

    @Test
    @DisplayName("aprovar: todos os sacados reprovados é recusado e manda mover para Reprovado; nada é gravado")
    void shouldRejectApprovingWhenEverySacadoIsRejected() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null);
        sacadosSao(card, a, comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Todos os sacados estão reprovados: mova o card para Reprovado.");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(sacadoRepository, never()).saveAll(anyList());
        assertThat(a.getSituacao()).as("recusado: a situação final não foi copiada").isNull();
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(card.getResultado()).isNull();
        assertThat(card.getFinalizadoEm()).isNull();
    }

    @Test
    @DisplayName("aprovar: decisões do pedido que reprovam todos também são recusadas")
    void shouldRejectApprovingWhenRequestRejectsEverySacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.REPROVADO, null)), analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("Todos os sacados estão reprovados");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("reprovar: todos os sacados viram REPROVADO, cada mudança gera evento e o card fica REPROVADO")
    void shouldRejectEverySacadoWhenMovingToReprovado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        LiberacaoSacadoEntity b = comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.APROVADO, null);
        sacadosSao(card, a, b);

        LiberacaoCardEntity movido = reprovar(card, EtapaLiberacao.COMITE, analista);

        // reprovar marca o C de todos e o C é copiado para a situação final
        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(b.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(b.getSituacao()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(a.getSituacaoPorNome()).isEqualTo("Andressa");
        assertThat(a.getSituacaoEm()).isEqualTo(a.getOpiniaoComiteEm());
        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.REPROVADO);
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(movido.getFinalizadoEm()).isNotNull();
        List<LiberacaoEventoEntity> eventos = eventosGravados();
        assertThat(eventos).extracting(LiberacaoEventoEntity::getTipo)
                .containsExactly(TipoEventoLiberacao.EDICAO, TipoEventoLiberacao.EDICAO, TipoEventoLiberacao.TRANSICAO);
        assertThat(eventos.get(0).getCampo()).isEqualTo("decisaoComite");
        assertThat(eventos.get(0).getValorAntes()).isEqualTo("a decidir");
        assertThat(eventos.get(0).getValorDepois()).isEqualTo("Reprovado");
        assertThat(eventos.get(1).getValorAntes()).isEqualTo("Aprovado");
        assertThat(eventos.get(2).getCampo()).isEqualTo("resultado");
        assertThat(eventos.get(2).getValorDepois()).isEqualTo(ResultadoLiberacao.REPROVADO.rotulo());
    }

    // ------------------------------------------------------ motivos da reprovação

    @Test
    @DisplayName("reprovar sem motivo é recusado antes de mexer nos sacados")
    void shouldRequireAReasonToReject() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.REPROVADO,
                null, "sem motivo", analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Marque ao menos um motivo da reprovação.");
        assertThat(a.getOpiniaoComite()).isNull();
        verify(cardRepository, never()).saveAndFlush(any());
        verify(eventoRepository, never()).save(any());
    }

    @Test
    @DisplayName("reprovar com motivos: o card guarda os nomes na ordem do catálogo, e histórico e notificação os trazem antes da observação")
    void shouldKeepRejectionReasonsInCatalogOrder() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.REPROVADO,
                null, "Ver e-mail do sacado.", List.of(), null,
                List.of(MOTIVO_PRACA.getId(), MOTIVO_SEM_LIMITE.getId(), MOTIVO_PRACA.getId()), analista);

        assertThat(movido.getMotivosReprovacao()).containsExactly("Sacado sem Limite", "Praça de Pgto");
        assertThat(ultimoEvento().getTexto())
                .isEqualTo("Motivos da reprovação: Sacado sem Limite, Praça de Pgto.\n\nVer e-mail do sacado.");
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isInstanceOfSatisfying(LiberacaoEvento.Movido.class, movidoEvento ->
                assertThat(movidoEvento.trecho()).startsWith("Motivos da reprovação: Sacado sem Limite, Praça de Pgto."));
    }

    @Test
    @DisplayName("reprovar com motivo desativado ou fora do catálogo é recusado")
    void shouldRefuseInactiveOrUnknownReasons() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);

        for (UUID motivo : List.of(MOTIVO_DESATIVADO.getId(), UUID.randomUUID())) {
            assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.REPROVADO,
                    null, null, List.of(), null, List.of(MOTIVO_PRACA.getId(), motivo), analista))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Motivo de reprovação fora da lista atual.");
        }
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("motivo de reprovação só vale indo para Reprovado")
    void shouldRefuseReasonsOutsideRejection() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, List.of(), null, List.of(MOTIVO_PRACA.getId()), analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Motivo de reprovação só ao reprovar.");
    }

    @Test
    @DisplayName("Diretoria→Reprovado também pede motivo e o guarda no card")
    void shouldRequireAndKeepReasonsWhenDiretoriaRejects() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        sacadosSao(card, comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA, EtapaLiberacao.REPROVADO,
                null, null, diretor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Marque ao menos um motivo da reprovação.");

        assertThat(reprovar(card, EtapaLiberacao.DIRETORIA, diretor).getMotivosReprovacao()).containsExactly("Praça de Pgto");
    }

    @Test
    @DisplayName("os motivos seguem para Finalizados e saem ao reabrir no Comitê")
    void shouldKeepReasonsUntilReopened() {
        LiberacaoCardEntity card = card(EtapaLiberacao.REPROVADO);
        card.setResultado(ResultadoLiberacao.REPROVADO);
        card.setFinalizadoEm(LocalDateTime.now().minusDays(1));
        card.setMotivosReprovacao(List.of("Praça de Pgto"));

        LiberacaoCardEntity finalizado = service.transicionar(card.getId(), EtapaLiberacao.REPROVADO,
                EtapaLiberacao.FINALIZADO, null, null, analista);
        assertThat(finalizado.getMotivosReprovacao()).containsExactly("Praça de Pgto");

        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));
        LiberacaoCardEntity reaberto = service.transicionar(card.getId(), EtapaLiberacao.FINALIZADO,
                EtapaLiberacao.COMITE, null, "Cliente trouxe garantia", analista);
        assertThat(reaberto.getMotivosReprovacao()).isNull();
    }

    @Test
    @DisplayName("reprovar: sacado que já estava reprovado não gera evento; só os que mudaram")
    void shouldOnlyRecordEventsForSacadosThatChangedOnReject() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null);
        LiberacaoSacadoEntity b = sacado(card, SACADO_B, "Sacado B", "500", 1);
        sacadosSao(card, a, b);

        reprovar(card, EtapaLiberacao.COMITE, analista);

        List<LiberacaoEventoEntity> eventos = eventosGravados();
        assertThat(eventos).extracting(LiberacaoEventoEntity::getTipo)
                .containsExactly(TipoEventoLiberacao.EDICAO, TipoEventoLiberacao.TRANSICAO);
        assertThat(eventos.get(0).getTexto()).isEqualTo("Sacado B (00.360.305/0001-04)");
        // quem já tinha reprovado no C mantém a autoria, que passa para a situação junto com a decisão
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(a.getSituacaoPorNome()).isEqualTo("Mychelly");
        assertThat(b.getSituacao()).isEqualTo(ResultadoLiberacao.REPROVADO);
    }

    @Test
    @DisplayName("reprovar: não precisa de decisão nenhuma, mesmo com sacados a decidir")
    void shouldNotRequireDecisionsToReject() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        comUmParecer(card);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0), sacado(card, SACADO_B, "Sacado B", "500", 1));

        LiberacaoCardEntity movido = reprovar(card, EtapaLiberacao.PENDENCIA, analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.REPROVADO);
    }

    @Test
    @DisplayName("reprovar: decisões enviadas junto com REPROVADO são recusadas e nada é gravado")
    void shouldRejectDecisionsSentWithReprovado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.REPROVADO,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.APROVADO, null)), analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Decisão de sacado só ao aprovar");
        verify(cardRepository, never()).saveAndFlush(any());
        assertThat(a.getSituacao()).isNull();
    }

    @Test
    @DisplayName("aprovar: aprovado e reprovado misturados = PARCIAL")
    void shouldComputeParcialWhenSacadosAreMixed() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);
    }

    @Test
    @DisplayName("aprovar: um sacado parcial faz o card ser PARCIAL, mesmo com os outros aprovados")
    void shouldComputeParcialWhenASacadoIsPartial() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, "200"));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);
    }

    @Test
    @DisplayName("aprovar: as decisões do pedido são aplicadas antes de calcular o resultado, uma por sacado, com quem e quando")
    void shouldApplyRequestDecisionsBeforeComputingResultado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        LiberacaoSacadoEntity b = sacado(card, SACADO_B, "Sacado B", "500", 1);
        sacadosSao(card, a, b);
        // documento com máscara: o serviço normaliza
        List<DecisaoSacado> decisoes = List.of(
                new DecisaoSacado("45.723.174/0001-10", ResultadoLiberacao.APROVADO, null),
                new DecisaoSacado(SACADO_B, ResultadoLiberacao.REPROVADO, null));
        LocalDateTime antes = LocalDateTime.now();

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, decisoes, analista);

        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(b.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(a.getOpiniaoComitePorNome()).isEqualTo("Andressa");
        assertThat(a.getOpiniaoComiteEm()).isNotNull().isAfterOrEqualTo(antes);
        // decidido o card, o C vira a situação final, com a mesma autoria
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(b.getSituacao()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(a.getSituacaoPorNome()).isEqualTo("Andressa");
        assertThat(a.getSituacaoEm()).isEqualTo(a.getOpiniaoComiteEm());
        verify(sacadoRepository).save(a);
        verify(sacadoRepository).save(b);
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);

        List<LiberacaoEventoEntity> eventos = eventosGravados();
        assertThat(eventos).extracting(LiberacaoEventoEntity::getTipo)
                .containsExactly(TipoEventoLiberacao.EDICAO, TipoEventoLiberacao.EDICAO, TipoEventoLiberacao.TRANSICAO);
        assertThat(eventos.get(0).getCampo()).isEqualTo("decisaoComite");
        assertThat(eventos.get(0).getValorAntes()).isEqualTo("a decidir");
        assertThat(eventos.get(0).getValorDepois()).isEqualTo("Aprovado");
        assertThat(eventos.get(0).getTexto()).isEqualTo("Sacado A (45.723.174/0001-10)");
        assertThat(eventos.get(1).getValorDepois()).isEqualTo("Reprovado");
        assertThat(eventos.get(1).getTexto()).isEqualTo("Sacado B (00.360.305/0001-04)");
    }

    @Test
    @DisplayName("aprovar: o pedido pode mudar uma decisão que já existia")
    void shouldLetRequestOverrideAnExistingDecision() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null);
        sacadosSao(card, a);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.APROVADO, null)), analista);

        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getSituacaoPorNome()).isEqualTo("Andressa");
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(eventosGravados().get(0).getValorAntes()).isEqualTo("Reprovado");
    }

    @Test
    @DisplayName("aprovar: decisão parcial no pedido guarda o valor aprovado e o evento mostra o valor")
    void shouldStorePartialValueFromRequest() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.PARCIAL, new BigDecimal("400"))), analista);

        assertThat(a.getValorAprovadoComite()).isEqualByComparingTo("400");
        assertThat(a.getValorAprovado()).as("copiado para a situação ao decidir o card").isEqualByComparingTo("400");
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(semEspacoInsecavel(eventosGravados().get(0).getValorDepois())).isEqualTo("Parcial (R$ 400,00)");
    }

    @Test
    @DisplayName("aprovar: decisão parcial inválida no pedido derruba a finalização inteira")
    void shouldRejectInvalidPartialInRequest() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.PARCIAL, null)), analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parcial: informe o valor aprovado");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("aprovar: decisão para documento que não é sacado do card é EntityNotFound")
    void shouldRejectDecisionForUnknownSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, List.of(new DecisaoSacado(SACADO_B, ResultadoLiberacao.APROVADO, null)), analista))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Sacado 00.360.305/0001-04 não está neste card.");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("aprovar: o evento de transição tem campo 'resultado' e o rótulo do resultado em valorDepois")
    void shouldRecordResultadoInTransitionEvent() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null));

        service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO, null, null, analista);

        LiberacaoEventoEntity evento = ultimoEvento();
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.TRANSICAO);
        assertThat(evento.getEtapaDe()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(evento.getEtapaPara()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(evento.getCampo()).isEqualTo("resultado");
        assertThat(evento.getValorAntes()).isNull();
        assertThat(evento.getValorDepois()).isEqualTo("Parcialmente aprovado");
    }

    @Test
    @DisplayName("aprovar: marca finalizadoEm, grava o resultado no card e publica o evento já com o resultado")
    void shouldSetFinalizadoEmAndPublishMovidoWithResultado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card, comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));
        LocalDateTime antes = LocalDateTime.now();

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(movido.getFinalizadoEm()).isNotNull().isAfterOrEqualTo(antes);
        assertThat(movido.getEtapaDesde()).isEqualTo(movido.getFinalizadoEm());
        assertThat(movido.getRodada()).isEqualTo(1);
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isInstanceOfSatisfying(LiberacaoEvento.Movido.class, movidoEvento -> {
            assertThat(movidoEvento.para()).isEqualTo(EtapaLiberacao.APROVADO);
            assertThat(movidoEvento.card().getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
        });
    }

    @Test
    @DisplayName("transicionar: decisão de sacado só é aceita quando o destino é Aprovado")
    void shouldRejectDecisionsWhenDestinationIsNotAprovado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);
        when(pendenciaRepository.countByCardIdAndRespondidaEmIsNull(card.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.APROVADO, null)), analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Decisão de sacado só ao aprovar");
        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.ORIGEM,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.REPROVADO, null)), analista))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cardRepository, never()).saveAndFlush(any());
        verify(sacadoRepository, never()).save(any());
        assertThat(a.getSituacao()).isNull();
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
    }

    @Test
    @DisplayName("transicionar: lista de decisões vazia ou ausente vale para qualquer destino")
    void shouldAcceptEmptyDecisionsForAnyDestination() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.ORIGEM, null, null, List.of(), analista);
        LiberacaoCardEntity semLista = service.transicionar(card.getId(), EtapaLiberacao.ORIGEM,
                EtapaLiberacao.COMITE, null, null, (List<DecisaoSacado>) null, auxiliar);

        assertThat(movido.getResultado()).isNull();
        assertThat(semLista.getResultado()).isNull();
    }

    // ------------------------------------------------------------- declínio

    @Test
    @DisplayName("declinar antes da decisão: vai para Finalizados sem resultado, com data de finalização, marca e evento")
    void shouldDeclineOpenCardStraightToFinalizado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        LiberacaoCardEntity declinado = service.declinar(card.getId(), EtapaLiberacao.COMITE, "  Cliente fechou com outro fundo ", analista);

        assertThat(declinado.getEtapa()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(declinado.getResultado()).isNull();
        assertThat(declinado.getFinalizadoEm()).isNotNull();
        assertThat(declinado.getDeclinadoEm()).isNotNull();
        assertThat(declinado.getDeclinadoPorNome()).isEqualTo("Andressa");
        assertThat(declinado.getMotivoDeclinio()).isEqualTo("Cliente fechou com outro fundo");
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.TRANSICAO);
        assertThat(evento.getEtapaDe()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(evento.getEtapaPara()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(evento.getCampo()).isEqualTo("declinio");
        assertThat(evento.getValorDepois()).isEqualTo("Declinado pelo cliente");
        assertThat(evento.getTexto()).isEqualTo("Cliente fechou com outro fundo");
        verify(eventos).publishEvent(any(com.portal.serasa.application.service.liberacao.LiberacaoEvento.Movido.class));
    }

    @Test
    @DisplayName("declinar aprovado: o resultado e a data da decisão ficam; motivo é opcional")
    void shouldKeepCreditDecisionWhenApprovedCardIsDeclined() {
        LiberacaoCardEntity card = card(EtapaLiberacao.APROVADO);
        LocalDateTime decidido = LocalDateTime.now().minusDays(1);
        card.setFinalizadoEm(decidido);
        card.setResultado(ResultadoLiberacao.APROVADO);

        LiberacaoCardEntity declinado = service.declinar(card.getId(), EtapaLiberacao.APROVADO, "   ", analista);

        assertThat(declinado.getEtapa()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(declinado.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(declinado.getFinalizadoEm()).isEqualTo(decidido);
        assertThat(declinado.getMotivoDeclinio()).isNull();
    }

    @Test
    @DisplayName("declinar: na Origem qualquer um; do Comitê em diante só analista")
    void shouldRequireAnalystToDeclineAfterOrigem() {
        LiberacaoCardEntity naOrigem = card(EtapaLiberacao.ORIGEM);
        assertThat(service.declinar(naOrigem.getId(), EtapaLiberacao.ORIGEM, null, auxiliar).getDeclinadoEm()).isNotNull();

        LiberacaoCardEntity noComite = card(EtapaLiberacao.COMITE);
        assertThatThrownBy(() -> service.declinar(noComite.getId(), EtapaLiberacao.COMITE, null, auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        assertThat(noComite.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
    }

    @Test
    @DisplayName("declinar: finalizado não declina; tela desatualizada toma conflito")
    void shouldRejectDeclineOfFinalizedOrStaleCard() {
        LiberacaoCardEntity finalizado = card(EtapaLiberacao.FINALIZADO);
        assertThatThrownBy(() -> service.declinar(finalizado.getId(), EtapaLiberacao.FINALIZADO, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class);

        LiberacaoCardEntity movido = card(EtapaLiberacao.ASSINATURA);
        assertThatThrownBy(() -> service.declinar(movido.getId(), EtapaLiberacao.APROVADO, null, analista))
                .isInstanceOf(ConflitoEdicaoException.class);
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("reabrir declinado no Comitê tira a marca de declínio")
    void shouldClearDeclineWhenReopened() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);
        card.setFinalizadoEm(LocalDateTime.now().minusDays(1));
        card.setDeclinadoEm(LocalDateTime.now().minusDays(1));
        card.setDeclinadoPorNome("Andressa");
        card.setMotivoDeclinio("Desistiu");
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        LiberacaoCardEntity reaberto = service.transicionar(card.getId(), EtapaLiberacao.FINALIZADO,
                EtapaLiberacao.COMITE, null, "Cliente voltou", analista);

        assertThat(reaberto.getDeclinadoEm()).isNull();
        assertThat(reaberto.getDeclinadoPorNome()).isNull();
        assertThat(reaberto.getMotivoDeclinio()).isNull();
    }

    // ----------------------------------------------------------- reabertura

    @Test
    @DisplayName("transicionar Finalizado→Comitê: reabertura soma rodada, limpa finalizadoEm e resultado, registra REABERTURA e convoca o Comitê")
    void shouldReopenFinalizedCardIntoNewRodada() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);
        card.setFinalizadoEm(LocalDateTime.now().minusDays(2));
        card.setResultado(ResultadoLiberacao.APROVADO);
        UserEntity bruna = usuario("Bruna");
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(bruna));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.FINALIZADO,
                EtapaLiberacao.COMITE, null, "Cliente trouxe fato novo", analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(movido.getRodada()).isEqualTo(2);
        assertThat(movido.getFinalizadoEm()).isNull();
        assertThat(movido.getResultado()).isNull();
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.REABERTURA);
        assertThat(evento.getEtapaDe()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(evento.getEtapaPara()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(evento.getCampo()).isNull();
        assertThat(evento.getValorDepois()).isNull();
        assertThat(evento.getTexto()).isEqualTo("Cliente trouxe fato novo");
        ArgumentCaptor<LiberacaoParecerEntity> pareceres = ArgumentCaptor.forClass(LiberacaoParecerEntity.class);
        verify(parecerRepository).save(pareceres.capture());
        assertThat(pareceres.getValue().getRodada()).isEqualTo(2);
        assertThat(pareceres.getValue().getUsuarioId()).isEqualTo(bruna.getId());
        verify(parecerRepository, never()).apagarAguardando(any(), anyInt());
    }

    @ParameterizedTest
    @EnumSource(value = ResultadoLiberacao.class)
    @DisplayName("transicionar Finalizado→Comitê: reabre qualquer resultado (aprovado, reprovado ou parcial) e limpa o resultado")
    void shouldReopenAnyResultado(ResultadoLiberacao resultado) {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);
        card.setResultado(resultado);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.FINALIZADO,
                EtapaLiberacao.COMITE, null, null, analista);

        assertThat(movido.getRodada()).isEqualTo(2);
        assertThat(movido.getResultado()).isNull();
        assertThat(eventosGravados().get(0).getTipo()).isEqualTo(TipoEventoLiberacao.REABERTURA);
    }

    @Test
    @DisplayName("transicionar Finalizado→Comitê: as decisões dos sacados continuam gravadas")
    void shouldKeepSacadoDecisionsWhenReopening() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);
        card.setResultado(ResultadoLiberacao.APROVADO);
        LiberacaoSacadoEntity a = decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        a.setOpiniaoComite(ResultadoLiberacao.APROVADO);
        a.setOpiniaoComitePorNome("Mychelly");
        sacadosSao(card, a);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        service.transicionar(card.getId(), EtapaLiberacao.FINALIZADO, EtapaLiberacao.COMITE, null, null, analista);

        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getOpiniaoComite()).as("o C da rodada anterior continua como ponto de partida").isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getOpiniaoComitePorNome()).isEqualTo("Mychelly");
        verify(sacadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("transicionar Finalizado→Comitê: sem ninguém marcado como Comitê é recusado e o card continua finalizado")
    void shouldNotReopenWhenComiteIsEmpty() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);
        card.setResultado(ResultadoLiberacao.REPROVADO);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of());

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.FINALIZADO,
                EtapaLiberacao.COMITE, null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("Ninguém está marcado como Comitê");
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(card.getResultado()).isEqualTo(ResultadoLiberacao.REPROVADO);
    }

    @Test
    @DisplayName("transicionar Finalizado→Comitê: não-analista não reabre")
    void shouldDenyNonAnalystReopening() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.FINALIZADO,
                EtapaLiberacao.COMITE, null, null, auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("transicionar Comitê→Pendência: o card segue sem resultado")
    void shouldNotSetResultadoWhenMovingToPendencia() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        when(pendenciaRepository.countByCardIdAndRespondidaEmIsNull(card.getId())).thenReturn(1L);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.PENDENCIA, null, null, analista);

        assertThat(movido.getResultado()).isNull();
        assertThat(ultimoEvento().getCampo()).isNull();
        assertThat(ultimoEvento().getValorDepois()).isNull();
    }

    @Test
    @DisplayName("transicionar Pendência→Comitê: mantém a rodada e não convoca de novo (pareceres valem)")
    void shouldKeepRodadaAndNotReconvokeWhenReturningFromPendencia() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        comUmParecer(card);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.PENDENCIA,
                EtapaLiberacao.COMITE, null, null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(movido.getRodada()).isEqualTo(1);
        verify(parecerRepository, never()).save(any());
        assertThat(eventosGravados().get(0).getTipo()).isEqualTo(TipoEventoLiberacao.TRANSICAO);
    }

    @Test
    @DisplayName("Origem→Pendência (só analista) abre a pendência sem convocar o Comitê; Pendência→Comitê então convoca")
    void shouldGoFromOrigemThroughPendenciaToComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        UserEntity carla = usuario("Carla");
        when(userRepository.findById(carla.getId())).thenReturn(Optional.of(carla));
        comiteTem(usuario("Bruna"));
        List<NovaPendencia> pendencia = List.of(new NovaPendencia(carla.getId(), "Falta o contrato social"));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.ORIGEM, EtapaLiberacao.PENDENCIA,
                pendencia, null, auxiliar))
                .isInstanceOf(AcessoNegadoException.class);

        LiberacaoCardEntity naPendencia = service.transicionar(card.getId(), EtapaLiberacao.ORIGEM,
                EtapaLiberacao.PENDENCIA, pendencia, null, analista);

        assertThat(naPendencia.getEtapa()).isEqualTo(EtapaLiberacao.PENDENCIA);
        verify(pendenciaRepository).save(any());
        verify(parecerRepository, never()).save(any());

        service.transicionar(card.getId(), EtapaLiberacao.PENDENCIA, EtapaLiberacao.COMITE, null, null, analista);

        // a rodada não tinha parecer: o Comitê é convocado como se o card viesse direto da Origem
        ArgumentCaptor<LiberacaoParecerEntity> pareceres = ArgumentCaptor.forClass(LiberacaoParecerEntity.class);
        verify(parecerRepository).save(pareceres.capture());
        assertThat(pareceres.getValue().getUsuarioNome()).isEqualTo("Bruna");
        assertThat(pareceres.getValue().getRodada()).isEqualTo(1);
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
    }

    // ------------------------------------------------------------- pendência

    @Test
    @DisplayName("transicionar →Pendência sem pendências novas e sem abertas é TransicaoInvalida")
    void shouldRequirePendenciaWhenMovingToPendencia() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        when(pendenciaRepository.countByCardIdAndRespondidaEmIsNull(card.getId())).thenReturn(0L);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA,
                null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("Informe para quem é a pendência");
        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA,
                List.of(), null, analista))
                .isInstanceOf(TransicaoInvalidaException.class);
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("transicionar →Pendência sem novas mas com pendência aberta já existente é permitido")
    void shouldAllowMoveToPendenciaWhenThereAreOpenOnes() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        when(pendenciaRepository.countByCardIdAndRespondidaEmIsNull(card.getId())).thenReturn(1L);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.PENDENCIA, null, null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.PENDENCIA);
        assertThat(movido.getFinalizadoEm()).isNull();
        verify(pendenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("transicionar →Pendência com uma pendência: cria, adiciona o destinatário como membro PENDENCIA e registra evento")
    void shouldCreatePendenciaAndAddRecipientAsMember() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        UserEntity destinatario = usuario("Carla");
        when(userRepository.findById(destinatario.getId())).thenReturn(Optional.of(destinatario));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.PENDENCIA, List.of(new NovaPendencia(destinatario.getId(), "  Enviar o contrato social  ")),
                null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.PENDENCIA);
        ArgumentCaptor<LiberacaoPendenciaEntity> captor = ArgumentCaptor.forClass(LiberacaoPendenciaEntity.class);
        verify(pendenciaRepository).save(captor.capture());
        LiberacaoPendenciaEntity pendencia = captor.getValue();
        assertThat(pendencia.getCardId()).isEqualTo(card.getId());
        assertThat(pendencia.getAbertaPorId()).isEqualTo(analista.getId());
        assertThat(pendencia.getAbertaPorNome()).isEqualTo("Andressa");
        assertThat(pendencia.getDestinatarioId()).isEqualTo(destinatario.getId());
        assertThat(pendencia.getDestinatarioNome()).isEqualTo("Carla");
        assertThat(pendencia.getTexto()).isEqualTo("Enviar o contrato social");
        assertThat(pendencia.aberta()).isTrue();

        List<LiberacaoMembroEntity> membros = membrosGravados();
        assertThat(membros).hasSize(1);
        assertThat(membros.get(0).getUsuarioId()).isEqualTo(destinatario.getId());
        assertThat(membros.get(0).getOrigem()).isEqualTo(OrigemMembro.PENDENCIA);

        assertThat(eventosGravados()).extracting(LiberacaoEventoEntity::getTipo)
                .containsExactly(TipoEventoLiberacao.TRANSICAO, TipoEventoLiberacao.PENDENCIA_ABERTA);
    }

    @Test
    @DisplayName("transicionar →Pendência: destinatário inexistente é EntityNotFound; pendência sem texto é IllegalArgument")
    void shouldRejectInvalidPendencia() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        UUID fantasma = UUID.randomUUID();
        when(userRepository.findById(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA,
                List.of(new NovaPendencia(fantasma, "Algo")), null, analista))
                .isInstanceOf(EntityNotFoundException.class);

        LiberacaoCardEntity outro = card(EtapaLiberacao.COMITE);
        comUmParecer(outro);
        assertThatThrownBy(() -> service.transicionar(outro.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA,
                List.of(new NovaPendencia(UUID.randomUUID(), "  ")), null, analista))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("transicionar: observação em branco vira nula no evento")
    void shouldStoreBlankObservationAsNull() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        service.transicionar(card.getId(), EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, null, "   ", auxiliar);

        assertThat(eventosGravados().get(0).getTexto()).isNull();
    }

    // ------------------------------------------------------------ decidirSacado

    private DecisaoSacado decisao(String documento, ResultadoLiberacao situacao, String valorAprovado) {
        return new DecisaoSacado(documento, situacao, valorAprovado == null ? null : new BigDecimal(valorAprovado));
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA"})
    @DisplayName("decidirSacado C: analista decide no Comitê ou em Pendência; grava a decisão do Comitê com quem, quando e o evento, sem tocar a situação final")
    void shouldLetAnalystDecideSacadoInComiteOrPendencia(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);
        LocalDateTime antes = LocalDateTime.now();

        LiberacaoCardEntity atualizado = service.decidirSacado(card.getId(), QuadroOpiniao.COMITE,
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), analista);

        assertThat(atualizado).isSameAs(card);
        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getValorAprovadoComite()).isNull();
        assertThat(a.getOpiniaoComitePorNome()).isEqualTo("Andressa");
        assertThat(a.getOpiniaoComiteEm()).isNotNull().isAfterOrEqualTo(antes);
        // a situação é a decisão final: só é gravada quando o card é decidido
        assertThat(a.getSituacao()).isNull();
        assertThat(a.getSituacaoPorNome()).isNull();
        assertThat(a.getSituacaoEm()).isNull();
        verify(sacadoRepository).save(a);
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.EDICAO);
        assertThat(evento.getCardId()).isEqualTo(card.getId());
        assertThat(evento.getCampo()).isEqualTo("decisaoComite");
        assertThat(evento.getValorAntes()).isEqualTo("a decidir");
        assertThat(evento.getValorDepois()).isEqualTo("Aprovado");
        assertThat(evento.getTexto()).isEqualTo("Sacado A (45.723.174/0001-10)");
        assertThat(evento.getUsuarioId()).isEqualTo(analista.getId());
        // fora da trava de versão: decidir sacado não pode derrubar com 409 quem edita o card
        verify(cardRepository).tocarSemVersao(eq(card.getId()), any(LocalDateTime.class), eq(analista.getId()), eq("Andressa"));
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("decidirSacado: publica Editado sem menções para o quadro dos outros atualizar")
    void shouldPublishEditedWhenDecidingSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao(SACADO_A, ResultadoLiberacao.REPROVADO, null), analista);

        ArgumentCaptor<Object> publicado = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(publicado.capture());
        assertThat(publicado.getValue()).isInstanceOfSatisfying(LiberacaoEvento.Editado.class, editado -> {
            assertThat(editado.autor()).isSameAs(analista);
            assertThat(editado.mencionados()).isEmpty();
        });
    }

    @Test
    @DisplayName("decidirSacado C: não-analista é AcessoNegado e nada é gravado")
    void shouldDenyNonAnalystDecidingSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.COMITE,
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), auxiliar))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Só analista pode marcar o quadrado C.");
        verify(sacadoRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
        verify(cardRepository, never()).tocarSemVersao(any(), any(), any(), any());
    }

    @Test
    @DisplayName("decidirSacado: sem usuário autenticado é AcessoNegado")
    void shouldDenyUnauthenticatedDecidingSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.COMITE,
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), null))
                .isInstanceOf(AcessoNegadoException.class);
        verify(sacadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("decidirSacado C: card na Origem ainda não tem decisão do Comitê")
    void shouldRejectDecidingSacadoOnOrigem() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.COMITE,
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("O quadrado C é marcado com o card no Comitê ou em Pendência.");
        verify(sacadoRepository, never()).save(any());
        verify(cardRepository, never()).tocarSemVersao(any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ORIGEM", "COMITE", "DIRETORIA"})
    @DisplayName("decidirSacado: a Origem não decide sacado, em etapa nenhuma, e nada é gravado")
    void shouldNeverLetOrigemDecideSacado(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.ORIGEM,
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), diretor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A Origem não decide sacado.");
        assertThat(a.getOpiniaoOrigem()).isNull();
        assertThat(a.getOpiniaoComite()).isNull();
        verify(sacadoRepository, never()).save(any());
        verify(cardRepository, never()).tocarSemVersao(any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("decidirSacado C: card já decidido (aprovado, reprovado, assinatura, finalizado) pede reabertura")
    void shouldRejectDecidingSacadoOfDecidedCard(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.COMITE,
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Card já decidido: reabra no Comitê para mudar a decisão.");
        verify(sacadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("decidirSacado: documento que não é sacado do card é EntityNotFound; máscara é aceita")
    void shouldFindSacadoByNormalizedDocument() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.COMITE,
                decisao(SACADO_B, ResultadoLiberacao.APROVADO, null), analista))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Sacado 00.360.305/0001-04 não está neste card.");
        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.COMITE,
                decisao(null, ResultadoLiberacao.APROVADO, null), analista))
                .isInstanceOf(EntityNotFoundException.class);

        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao("45.723.174/0001-10", ResultadoLiberacao.APROVADO, null), analista);
        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.00"})
    @DisplayName("decidirSacado: parcial sem valor aprovado positivo é recusado")
    void shouldRejectPartialWithoutPositiveValue(String valor) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.COMITE,
                decisao(SACADO_A, ResultadoLiberacao.PARCIAL, valor), analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parcial: informe o valor aprovado de Sacado A (45.723.174/0001-10).");
        assertThat(a.getOpiniaoComite()).isNull();
        verify(sacadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("decidirSacado: parcial sem valor nenhum é recusado")
    void shouldRejectPartialWithMissingValue() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.COMITE,
                decisao(SACADO_A, ResultadoLiberacao.PARCIAL, null), analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Parcial: informe o valor aprovado");
        verify(eventoRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000", "1000.00", "1500"})
    @DisplayName("decidirSacado: parcial com valor aprovado igual ou maior que o do sacado é recusado")
    void shouldRejectPartialValueNotBelowSacadoValue(String valor) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.COMITE,
                decisao(SACADO_A, ResultadoLiberacao.PARCIAL, valor), analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Parcial: o valor aprovado precisa ser menor que R$")
                .hasMessageEndingWith("1.000,00.");
        assertThat(a.getOpiniaoComite()).isNull();
        verify(sacadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("decidirSacado C: parcial com valor menor que o do sacado guarda o valor no C e o evento mostra quanto")
    void shouldStorePartialValue() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao(SACADO_A, ResultadoLiberacao.PARCIAL, "400"), analista);

        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(a.getValorAprovadoComite()).isEqualByComparingTo("400");
        assertThat(a.getSituacao()).isNull();
        assertThat(a.getValorAprovado()).isNull();
        assertThat(semEspacoInsecavel(eventosGravados().get(0).getValorDepois())).isEqualTo("Parcial (R$ 400,00)");
    }

    @Test
    @DisplayName("decidirSacado: um centavo abaixo do valor do sacado ainda é parcial válido")
    void shouldAcceptPartialJustBelowSacadoValue() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao(SACADO_A, ResultadoLiberacao.PARCIAL, "999.99"), analista);

        assertThat(a.getValorAprovadoComite()).isEqualByComparingTo("999.99");
    }

    @Test
    @DisplayName("decidirSacado: sacado sem valor informado aceita qualquer parcial positivo")
    void shouldAcceptPartialWhenSacadoValueIsUnknown() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", null, 0);
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao(SACADO_A, ResultadoLiberacao.PARCIAL, "250"), analista);

        assertThat(a.getValorAprovadoComite()).isEqualByComparingTo("250");
    }

    @Test
    @DisplayName("decidirSacado: mudar a decisão registra a anterior no evento")
    void shouldRecordPreviousDecisionWhenChangingIt() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao(SACADO_A, ResultadoLiberacao.REPROVADO, null), analista);

        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(a.getOpiniaoComitePorNome()).isEqualTo("Andressa");
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getValorAntes()).isEqualTo("Aprovado");
        assertThat(evento.getValorDepois()).isEqualTo("Reprovado");
    }

    @Test
    @DisplayName("decidirSacado: trocar de parcial para aprovado zera o valor aprovado")
    void shouldClearPartialValueWhenSwitchingAway() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.PARCIAL, "400");
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), analista);

        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getValorAprovadoComite()).isNull();
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(semEspacoInsecavel(evento.getValorAntes())).isEqualTo("Parcial (R$ 400,00)");
        assertThat(evento.getValorDepois()).isEqualTo("Aprovado");
    }

    @Test
    @DisplayName("decidirSacado: valor enviado junto de aprovado ou reprovado é descartado")
    void shouldDropValueSentWithNonPartialDecision() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao(SACADO_A, ResultadoLiberacao.APROVADO, "300"), analista);

        assertThat(a.getValorAprovadoComite()).isNull();
    }

    @Test
    @DisplayName("decidirSacado: repetir a mesma decisão não regrava o sacado, não cria evento nem mexe no card")
    void shouldNotRecordAnythingWhenDecisionDoesNotChange() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        LiberacaoSacadoEntity b = comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, "200");
        sacadosSao(card, a, b);

        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), analista);
        // 200.00 e 200 são o mesmo valor
        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao(SACADO_B, ResultadoLiberacao.PARCIAL, "200.00"), analista);

        verify(sacadoRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
        // clique duplo não troca "última alteração por" nem avisa o quadro dos outros
        verify(cardRepository, never()).tocarSemVersao(any(), any(), any(), any());
        verify(eventos, never()).publishEvent(any());
        assertThat(a.getOpiniaoComitePorNome()).isEqualTo("Mychelly");
        assertThat(b.getOpiniaoComitePorNome()).isEqualTo("Mychelly");
    }

    @Test
    @DisplayName("decidirSacado: situação nula devolve o sacado para 'a decidir', sem quem nem quando")
    void shouldReturnSacadoToUndecided() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao(SACADO_A, null, null), analista);

        assertThat(a.getOpiniaoComite()).isNull();
        assertThat(a.getValorAprovadoComite()).isNull();
        assertThat(a.getOpiniaoComitePorNome()).isNull();
        assertThat(a.getOpiniaoComiteEm()).isNull();
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getValorAntes()).isEqualTo("Aprovado");
        assertThat(evento.getValorDepois()).isEqualTo("a decidir");
    }

    @Test
    @DisplayName("decidirSacado: decidir só um sacado não finaliza nem mexe nos outros")
    void shouldOnlyTouchTheDecidedSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        LiberacaoSacadoEntity b = sacado(card, SACADO_B, "Sacado B", "500", 1);
        sacadosSao(card, a, b);

        service.decidirSacado(card.getId(), QuadroOpiniao.COMITE, decisao(SACADO_B, ResultadoLiberacao.REPROVADO, null), analista);

        assertThat(a.getOpiniaoComite()).isNull();
        assertThat(b.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(b.getSituacao()).as("decidir o C não decide o card").isNull();
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(card.getResultado()).isNull();
        verify(sacadoRepository, times(1)).save(any());
    }

    // ------------------------------------------- decidirSacado: o D não se marca à mão

    private static final String MENSAGEM_D_AUTOMATICO = "O D é preenchido quando a Diretoria aprova ou reprova o card.";

    @ParameterizedTest
    @EnumSource(EtapaLiberacao.class)
    @DisplayName("decidirSacado D: ninguém marca o D à mão, em etapa nenhuma, nem quem tem o perfil Diretoria; nada é carregado nem gravado")
    void shouldNeverLetAnyoneMarkDByHand(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.DIRETORIA,
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), diretor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAGEM_D_AUTOMATICO);
        assertThat(a.getDecisaoDiretoria()).isNull();
        assertThat(a.getDecisaoDiretoriaPorNome()).isNull();
        verify(cardRepository, never()).findByIdAndExcluidoEmIsNull(any());
        verify(sacadoRepository, never()).findByCardIdOrderByOrdem(any());
        verify(sacadoRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
        verify(cardRepository, never()).tocarSemVersao(any(), any(), any(), any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    @DisplayName("decidirSacado D: a recusa vem antes da permissão — analista comum, quem não é analista e quem não está autenticado recebem a mesma mensagem")
    void shouldRejectMarkingDByHandBeforeCheckingPermission() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));
        DecisaoSacado decisao = decisao(SACADO_A, ResultadoLiberacao.APROVADO, null);

        for (UserEntity autor : java.util.Arrays.asList(analista, auxiliar, null)) {
            assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.DIRETORIA, decisao, autor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(MENSAGEM_D_AUTOMATICO);
        }
        // decisão nula, parcial sem valor e documento que nem é sacado: nada disso chega a ser avaliado
        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.DIRETORIA, decisao(SACADO_A, null, null), diretor))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MENSAGEM_D_AUTOMATICO);
        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.DIRETORIA,
                decisao(SACADO_A, ResultadoLiberacao.PARCIAL, null), diretor))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MENSAGEM_D_AUTOMATICO);
        assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.DIRETORIA,
                decisao(SACADO_B, ResultadoLiberacao.APROVADO, null), diretor))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MENSAGEM_D_AUTOMATICO);
        verify(sacadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("decidirSacado C: com o card na Diretoria o Comitê não decide mais, nem quem também tem o perfil Diretoria")
    void shouldRejectDecidingCWhileCardIsInDiretoria() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        for (UserEntity autor : List.of(analista, diretor)) {
            assertThatThrownBy(() -> service.decidirSacado(card.getId(), QuadroOpiniao.COMITE,
                    decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), autor))
                    .isInstanceOf(TransicaoInvalidaException.class)
                    .hasMessage("O quadrado C é marcado com o card no Comitê ou em Pendência.");
        }
        assertThat(a.getOpiniaoComite()).isNull();
        verify(sacadoRepository, never()).save(any());
    }

    // ------------------------------------------- editar preserva a decisão dos sacados

    @Test
    @DisplayName("criar: sacados nascem sem decisão")
    void shouldCreateSacadosUndecided() {
        naBase(CNPJ, "ACME");

        service.criar(dadosNovos(CNPJ, null, List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("10")))), auxiliar);

        assertThat(sacadosGravados()).singleElement().satisfies(sacado -> {
            assertThat(sacado.getSituacao()).isNull();
            assertThat(sacado.getValorAprovado()).isNull();
            assertThat(sacado.getSituacaoPorNome()).isNull();
            assertThat(sacado.getSituacaoEm()).isNull();
        });
    }

    @Test
    @DisplayName("editar: sacado que continua na lista mantém decisão, quem e quando; novo entra sem decisão; removido sai")
    void shouldKeepDecisionOfSacadosThatStayWhenEditing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        sacadosSao(card,
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.PARCIAL, "400"),
                decidido(card, CPF, "Pessoa Física", "300", 1, ResultadoLiberacao.APROVADO, null));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null,
                List.of(new DadosSacado(SACADO_B, "Sacado B", new BigDecimal("500")),
                        new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("1200"))));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        verify(sacadoRepository).apagarDoCard(card.getId());
        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        assertThat(gravados).extracting(LiberacaoSacadoEntity::getCnpj).containsExactly(SACADO_B, SACADO_A);
        assertThat(gravados).extracting(LiberacaoSacadoEntity::getOrdem).containsExactly(0, 1);

        LiberacaoSacadoEntity novo = gravados.get(0);
        assertThat(novo.getSituacao()).isNull();
        assertThat(novo.getValorAprovado()).isNull();
        assertThat(novo.getSituacaoPorNome()).isNull();
        assertThat(novo.getSituacaoEm()).isNull();

        LiberacaoSacadoEntity mantido = gravados.get(1);
        assertThat(mantido.getValor()).isEqualByComparingTo("1200");
        assertThat(mantido.getSituacao()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(mantido.getValorAprovado()).isEqualByComparingTo("400");
        assertThat(mantido.getSituacaoPorNome()).isEqualTo("Mychelly");
        assertThat(mantido.getSituacaoEm()).isEqualTo(LocalDateTime.of(2026, 10, 7, 15, 0));
    }

    @Test
    @DisplayName("editar: só reordenar os sacados também preserva as decisões")
    void shouldKeepDecisionsWhenOnlyReorderingSacados() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        sacadosSao(card,
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null,
                List.of(new DadosSacado(SACADO_B, "Sacado B", new BigDecimal("500")),
                        new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("1000"))));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        assertThat(gravados).extracting(LiberacaoSacadoEntity::getCnpj).containsExactly(SACADO_B, SACADO_A);
        assertThat(gravados).extracting(LiberacaoSacadoEntity::getSituacao)
                .containsExactly(ResultadoLiberacao.REPROVADO, ResultadoLiberacao.APROVADO);
    }

    @Test
    @DisplayName("editar: parcial cujo valor do sacado baixou até o aprovado volta para 'a decidir'")
    void shouldDropPartialDecisionThatNoLongerFitsEditedValue() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        sacadosSao(card,
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.PARCIAL, "400"),
                decidido(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, "200"));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null,
                List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("400")),
                        new DadosSacado(SACADO_B, "Sacado B", new BigDecimal("300"))));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        LiberacaoSacadoEntity a = gravados.get(0);
        assertThat(a.getSituacao()).isNull();
        assertThat(a.getValorAprovado()).isNull();
        assertThat(a.getSituacaoPorNome()).isNull();
        assertThat(a.getSituacaoEm()).isNull();
        // 200 ainda cabe em 300: a decisão fica
        assertThat(gravados.get(1).getSituacao()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(gravados.get(1).getValorAprovado()).isEqualByComparingTo("200");
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("editar: card decidido não troca sacados — o resultado sairia dos sacados antigos")
    void shouldRejectSacadoChangesOnDecidedCard(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        sacadosSao(card, decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null,
                List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("1000")),
                        new DadosSacado(SACADO_B, "Sacado B", new BigDecimal("500"))));

        assertThatThrownBy(() -> service.editar(card.getId(), card.getVersion(), dados, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Card já decidido: reabra no Comitê para mudar os sacados.");
        verify(sacadoRepository, never()).apagarDoCard(any());
    }

    @Test
    @DisplayName("editar: card finalizado ainda aceita mudar campos que não são sacados")
    void shouldAllowNonSacadoEditsOnFinalizedCard() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);
        sacadosSao(card, decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                "Parecer revisto depois da liberação.", null,
                List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("1000"))));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        assertThat(card.getParecerOrigem()).isEqualTo("Parecer revisto depois da liberação.");
        verify(sacadoRepository, never()).apagarDoCard(any());
    }

    @Test
    @DisplayName("editar: sacado removido e incluído de volta numa edição seguinte volta sem decisão")
    void shouldNotResurrectDecisionOfRemovedSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        // lista atual já não tem o sacado A; quem volta a incluí-lo começa do zero
        sacadosSao(card, decidido(card, SACADO_B, "Sacado B", "500", 0, ResultadoLiberacao.APROVADO, null));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null,
                List.of(new DadosSacado(SACADO_B, "Sacado B", new BigDecimal("500")),
                        new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("1000"))));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        assertThat(gravados.get(0).getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(gravados.get(1).getCnpj()).isEqualTo(SACADO_A);
        assertThat(gravados.get(1).getSituacao()).isNull();
    }

    @Test
    @DisplayName("editar: C e D (decisão, valor, quem e quando) ficam com o sacado que continua na lista, e o novo entra sem nenhum")
    void shouldKeepComiteAndDiretoriaDecisionsWhenEditing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        sacadosSao(card, comD(comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.PARCIAL, "400"),
                ResultadoLiberacao.APROVADO, null));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null,
                List.of(new DadosSacado(SACADO_B, "Sacado B", new BigDecimal("500")),
                        new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("1200"))));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        LiberacaoSacadoEntity novo = gravados.get(0);
        assertThat(novo.getOpiniaoComite()).isNull();
        assertThat(novo.getValorAprovadoComite()).isNull();
        assertThat(novo.getOpiniaoComitePorNome()).isNull();
        assertThat(novo.getOpiniaoComiteEm()).isNull();
        assertThat(novo.getDecisaoDiretoria()).isNull();
        assertThat(novo.getValorAprovadoDiretoria()).isNull();
        assertThat(novo.getDecisaoDiretoriaPorNome()).isNull();
        assertThat(novo.getDecisaoDiretoriaEm()).isNull();

        LiberacaoSacadoEntity mantido = gravados.get(1);
        assertThat(mantido.getValor()).isEqualByComparingTo("1200");
        assertThat(mantido.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(mantido.getValorAprovadoComite()).isEqualByComparingTo("400");
        assertThat(mantido.getOpiniaoComitePorNome()).isEqualTo("Mychelly");
        assertThat(mantido.getOpiniaoComiteEm()).isEqualTo(LocalDateTime.of(2026, 10, 7, 15, 0));
        assertThat(mantido.getDecisaoDiretoria()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(mantido.getDecisaoDiretoriaPorNome()).isEqualTo("Walter");
        assertThat(mantido.getDecisaoDiretoriaEm()).isEqualTo(LocalDateTime.of(2026, 10, 14, 10, 0));
    }

    @Test
    @DisplayName("editar: parcial do C ou do D cujo valor do sacado baixou até ele volta para 'a decidir', e o outro quadrado fica")
    void shouldDropPartialComiteAndDiretoriaDecisionsThatNoLongerFit() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.PARCIAL, "400"),
                comD(comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.APROVADO, null),
                        ResultadoLiberacao.PARCIAL, "300"));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null,
                List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("400")),
                        new DadosSacado(SACADO_B, "Sacado B", new BigDecimal("300"))));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        // A: o C de 400 não cabe mais num sacado de 400
        LiberacaoSacadoEntity a = gravados.get(0);
        assertThat(a.getOpiniaoComite()).isNull();
        assertThat(a.getValorAprovadoComite()).isNull();
        assertThat(a.getOpiniaoComitePorNome()).isNull();
        assertThat(a.getOpiniaoComiteEm()).isNull();
        // B: o D de 300 também não cabe, mas o C (aprovado inteiro) não depende do valor
        LiberacaoSacadoEntity b = gravados.get(1);
        assertThat(b.getDecisaoDiretoria()).isNull();
        assertThat(b.getValorAprovadoDiretoria()).isNull();
        assertThat(b.getDecisaoDiretoriaPorNome()).isNull();
        assertThat(b.getDecisaoDiretoriaEm()).isNull();
        assertThat(b.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(b.getOpiniaoComitePorNome()).isEqualTo("Mychelly");
    }

    // ------------------------------------------------- proposta (AR) importada do PDF

    private static PropostaAr proposta(String emitidaEm) {
        return new PropostaAr("4821", emitidaEm, null, new BigDecimal("120500.00"), BigDecimal.ZERO,
                3, new BigDecimal("28.50"), new BigDecimal("21300.00"), new BigDecimal("745.25"), new BigDecimal("20554.75"),
                4, new BigDecimal("25800.00"), new BigDecimal("547000.00"), new BigDecimal("12000.00"), new BigDecimal("8000.00"),
                new BigDecimal("3300.00"), new BigDecimal("40000.00"), new BigDecimal("43300.00"),
                new BigDecimal("35.25"), BigDecimal.ZERO, new BigDecimal("1.10"),
                new BigDecimal("41.75"), BigDecimal.ZERO, new BigDecimal("1.35"));
    }

    private static CarteiraSacado carteira(int titulos, String abertos) {
        return new CarteiraSacado(titulos, BigDecimal.ZERO, new BigDecimal(abertos), new BigDecimal(abertos),
                new BigDecimal("45500.00"), BigDecimal.ZERO);
    }

    private LiberacaoSacadoEntity comCarteira(LiberacaoSacadoEntity sacado, CarteiraSacado carteira) {
        sacado.setCarteira(carteira);
        return sacado;
    }

    /** Mesmos campos do card, com a proposta e os sacados informados. */
    private DadosCard dadosComProposta(LiberacaoCardEntity card, PropostaAr proposta, List<DadosSacado> sacados) {
        return new DadosCard(card.getCedenteCnpj(), null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null, sacados, proposta);
    }

    private static DadosSacado dadosSacado(String documento, String nome, String valor, CarteiraSacado carteira) {
        return new DadosSacado(documento, nome, new BigDecimal(valor), carteira);
    }

    private static List<String> camposDosEventos(List<LiberacaoEventoEntity> eventos) {
        return eventos.stream().map(LiberacaoEventoEntity::getCampo).toList();
    }

    @Test
    @DisplayName("DadosCard e DadosSacado: construtores antigos continuam valendo, sem proposta nem carteira")
    void shouldKeepOldConstructorsWithoutProposalNorPortfolio() {
        DadosCard card = new DadosCard(CNPJ, null, "Duplicata", null, null, null, null, null);
        DadosSacado sacado = new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("1"));

        assertThat(card.proposta()).isNull();
        assertThat(sacado.carteira()).isNull();
    }

    @Test
    @DisplayName("criar: guarda a proposta no card e a carteira em cada sacado (nome vindo da base não a perde)")
    void shouldStorePropostaAndCarteiraOnCreate() {
        naBase(CNPJ, "ACME");
        naBase(SACADO_B, "SACADO B DA BASE");
        PropostaAr ar = proposta("2026-10-08T15:59:13");
        CarteiraSacado carteiraA = carteira(1, "7200.00");
        CarteiraSacado carteiraB = carteira(2, "4100.00");
        List<DadosSacado> sacados = List.of(
                dadosSacado(SACADO_A, "Sacado A", "6000", carteiraA),
                new DadosSacado(SACADO_B, null, new BigDecimal("9300"), carteiraB));

        LiberacaoCardEntity criado = service.criar(new DadosCard(CNPJ, null, "Duplicata", new BigDecimal("15300"),
                LocalDateTime.of(2026, 11, 1, 10, 0), null, null, sacados, ar), auxiliar);

        assertThat(criado.getProposta()).isEqualTo(ar);
        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        assertThat(gravados).extracting(LiberacaoSacadoEntity::getCnpj).containsExactly(SACADO_A, SACADO_B);
        assertThat(gravados).extracting(LiberacaoSacadoEntity::getCarteira).containsExactly(carteiraA, carteiraB);
        assertThat(gravados).extracting(LiberacaoSacadoEntity::getNome).containsExactly("Sacado A", "SACADO B DA BASE");
        // importar a AR não gera evento próprio na criação: só o "Card criado"
        assertThat(eventosGravados()).extracting(LiberacaoEventoEntity::getTipo).containsExactly(TipoEventoLiberacao.CRIACAO);
    }

    @Test
    @DisplayName("criar: card digitado à mão fica sem proposta e sem carteira")
    void shouldCreateWithoutProposalWhenTypedByHand() {
        naBase(CNPJ, "ACME");

        LiberacaoCardEntity criado = service.criar(
                dadosNovos(CNPJ, null, List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("10")))), auxiliar);

        assertThat(criado.getProposta()).isNull();
        assertThat(sacadosGravados()).singleElement().satisfies(sacado -> assertThat(sacado.getCarteira()).isNull());
    }

    @Test
    @DisplayName("editar: proposta nova num card sem AR grava EDICAO 'proposta' com antes nulo e depois 'AR de dd/MM/yyyy HH:mm'")
    void shouldRecordProposalImportedOnCardWithoutAr() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        PropostaAr ar = proposta("2026-10-08T15:59:13");

        LiberacaoCardEntity editado = service.editar(card.getId(), card.getVersion(), dadosComProposta(card, ar, null), auxiliar);

        assertThat(editado.getProposta()).isEqualTo(ar);
        List<LiberacaoEventoEntity> eventos = eventosGravados();
        assertThat(eventos).singleElement().satisfies(evento -> {
            assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.EDICAO);
            assertThat(evento.getCampo()).isEqualTo("proposta");
            assertThat(evento.getValorAntes()).isNull();
            assertThat(evento.getValorDepois()).isEqualTo("AR de 08/10/2026 15:59");
            assertThat(evento.getUsuarioId()).isEqualTo(auxiliar.getId());
            assertThat(evento.getCardId()).isEqualTo(card.getId());
        });
        verify(cardRepository).saveAndFlush(card);
        // sem sacados novos: nenhum evento 'sacados' nem regravação da lista
        verify(sacadoRepository, never()).apagarDoCard(any());
    }

    @Test
    @DisplayName("editar: reimportar outra AR troca a proposta e mostra a anterior em 'antes'")
    void shouldRecordReplacedProposal() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        card.setProposta(proposta("2026-10-07T10:30:00"));

        LiberacaoCardEntity editado = service.editar(card.getId(), card.getVersion(),
                dadosComProposta(card, proposta("2026-10-08T15:59:13"), null), auxiliar);

        assertThat(editado.getProposta().emitidaEm()).isEqualTo("2026-10-08T15:59:13");
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getCampo()).isEqualTo("proposta");
        assertThat(evento.getValorAntes()).isEqualTo("AR de 07/10/2026 10:30");
        assertThat(evento.getValorDepois()).isEqualTo("AR de 08/10/2026 15:59");
    }

    @Test
    @DisplayName("editar: a mesma AR de novo (igual à gravada) não é mudança")
    void shouldNotRecordSameProposalAgain() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        card.setProposta(proposta("2026-10-08T15:59:13"));

        LiberacaoCardEntity retorno = service.editar(card.getId(), card.getVersion(),
                dadosComProposta(card, proposta("2026-10-08T15:59:13"), null), auxiliar);

        assertThat(retorno).isSameAs(card);
        verify(eventoRepository, never()).save(any());
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("editar: proposta nula mantém a que o card já tem e não grava evento 'proposta'")
    void shouldKeepExistingProposalWhenEditingWithoutOne() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        PropostaAr existente = proposta("2026-10-08T15:59:13");
        card.setProposta(existente);
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), new BigDecimal("2500.50"), card.getPrazo(),
                card.getParecerOrigem(), null, null, null);

        LiberacaoCardEntity editado = service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        assertThat(editado.getProposta()).isSameAs(existente);
        assertThat(camposDosEventos(eventosGravados())).containsExactly("valor");
    }

    @Test
    @DisplayName("editar: proposta nula num card sem AR continua sem AR e sem evento")
    void shouldKeepCardWithoutProposalWhenNoneIsSent() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        service.editar(card.getId(), card.getVersion(), dadosComProposta(card, null, null), auxiliar);

        assertThat(card.getProposta()).isNull();
        verify(eventoRepository, never()).save(any());
    }

    @Test
    @DisplayName("editar: AR sem data de emissão aparece no histórico como 'AR importada'")
    void shouldLabelProposalWithoutIssueDate() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        service.editar(card.getId(), card.getVersion(), dadosComProposta(card, proposta(null), null), auxiliar);

        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getCampo()).isEqualTo("proposta");
        assertThat(evento.getValorDepois()).isEqualTo("AR importada");
    }

    @Test
    @DisplayName("rotuloProposta: nulo sem AR, 'AR importada' sem data e 'AR de dd/MM/yyyy HH:mm' (sem segundos) com data")
    void shouldLabelProposal() {
        assertThat(LiberacaoService.rotuloProposta(null)).isNull();
        assertThat(LiberacaoService.rotuloProposta(proposta(null))).isEqualTo("AR importada");
        // data que não existe (31/02) não derruba a edição: vira "AR importada"
        assertThat(LiberacaoService.rotuloProposta(proposta("2026-02-31T15:59:13"))).isEqualTo("AR importada");
        assertThat(LiberacaoService.rotuloProposta(proposta("2026-10-08T15:59:13"))).isEqualTo("AR de 08/10/2026 15:59");
        assertThat(LiberacaoService.rotuloProposta(proposta("2026-01-02T03:04"))).isEqualTo("AR de 02/01/2026 03:04");
        assertThat(LiberacaoService.rotuloProposta(proposta("2026-12-31T00:00:00"))).isEqualTo("AR de 31/12/2026 00:00");
    }

    @Test
    @DisplayName("editar: sacado que fica na lista sem carteira na edição mantém a carteira que já tinha")
    void shouldKeepPreviousCarteiraWhenIncomingIsNull() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        CarteiraSacado antiga = carteira(1, "7200.00");
        CarteiraSacado doNovo = carteira(2, "4100.00");
        sacadosSao(card,
                comCarteira(decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null), antiga),
                comCarteira(sacado(card, CPF, "Pessoa Física", "300", 1), carteira(1, "100.00")));
        DadosCard dados = dadosComProposta(card, null, List.of(
                dadosSacado(SACADO_B, "Sacado B", "500", doNovo),
                dadosSacado(SACADO_A, "Sacado A", "1200", null)));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        verify(sacadoRepository).apagarDoCard(card.getId());
        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        assertThat(gravados).extracting(LiberacaoSacadoEntity::getCnpj).containsExactly(SACADO_B, SACADO_A);
        assertThat(gravados.get(0).getCarteira()).isEqualTo(doNovo);
        assertThat(gravados.get(1).getCarteira()).as("carteira antiga preservada").isEqualTo(antiga);
        assertThat(gravados.get(1).getSituacao()).as("a decisão também").isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(camposDosEventos(eventosGravados())).containsExactly("sacados");
    }

    @Test
    @DisplayName("editar: sacado que fica com carteira nova na edição troca a carteira")
    void shouldReplaceCarteiraWhenIncomingIsPresent() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        sacadosSao(card,
                comCarteira(sacado(card, SACADO_A, "Sacado A", "1000", 0), carteira(1, "7200.00")),
                sacado(card, SACADO_B, "Sacado B", "500", 1));
        CarteiraSacado nova = carteira(3, "9999.00");
        DadosCard dados = dadosComProposta(card, null, List.of(
                dadosSacado(SACADO_A, "Sacado A", "1000", nova),
                dadosSacado(SACADO_B, "Sacado B", "700", null)));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        assertThat(gravados.get(0).getCarteira()).isEqualTo(nova);
        assertThat(gravados.get(1).getCarteira()).as("sem carteira antes e sem carteira agora").isNull();
    }

    @Test
    @DisplayName("editar: valor do sacado baixou e derrubou a decisão parcial, mas a carteira dele continua")
    void shouldKeepCarteiraEvenWhenPartialDecisionIsDropped() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        CarteiraSacado antiga = carteira(1, "7200.00");
        sacadosSao(card, comCarteira(
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.PARCIAL, "400"), antiga));
        DadosCard dados = dadosComProposta(card, null, List.of(dadosSacado(SACADO_A, "Sacado A", "400", null)));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        assertThat(sacadosGravados()).singleElement().satisfies(sacado -> {
            assertThat(sacado.getSituacao()).isNull();
            assertThat(sacado.getCarteira()).isEqualTo(antiga);
        });
    }

    @Test
    @DisplayName("editar: mesmos sacados e carteira diferente (AR reimportada) regrava a lista, sem evento 'sacados'")
    void shouldRewriteSacadosWhenOnlyCarteiraChanges() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        card.setProposta(proposta("2026-10-07T10:30:00"));
        sacadosSao(card, comCarteira(sacado(card, SACADO_A, "Sacado A", "10", 0), carteira(1, "7200.00")));
        CarteiraSacado nova = carteira(2, "9100.00");
        DadosCard dados = dadosComProposta(card, proposta("2026-10-08T15:59:13"),
                List.of(dadosSacado(SACADO_A, "Sacado A", "10.00", nova)));

        service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        verify(sacadoRepository).apagarDoCard(card.getId());
        assertThat(sacadosGravados()).singleElement().satisfies(sacado -> {
            assertThat(sacado.getCnpj()).isEqualTo(SACADO_A);
            assertThat(sacado.getCarteira()).isEqualTo(nova);
            assertThat(sacado.getValor()).isEqualByComparingTo("10");
        });
        // o histórico registra a reimportação uma vez, como 'proposta'
        assertThat(camposDosEventos(eventosGravados())).containsExactly("proposta");
    }

    @Test
    @DisplayName("editar: só a carteira mudou (proposta igual ou ausente): a lista é regravada e nenhum evento 'sacados' é gravado")
    void shouldRewriteSacadosWithoutSacadosEventWhenNothingElseChanged() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        sacadosSao(card, comCarteira(sacado(card, SACADO_A, "Sacado A", "10", 0), carteira(1, "7200.00")));
        CarteiraSacado nova = carteira(2, "9100.00");
        DadosCard dados = dadosComProposta(card, null, List.of(dadosSacado(SACADO_A, "Sacado A", "10", nova)));

        service.editar(card.getId(), card.getVersion(), dados, auxiliar);

        verify(sacadoRepository).apagarDoCard(card.getId());
        assertThat(sacadosGravados()).singleElement().satisfies(sacado -> assertThat(sacado.getCarteira()).isEqualTo(nova));
        verify(eventoRepository, never()).save(org.mockito.ArgumentMatchers.<LiberacaoEventoEntity>argThat(
                evento -> "sacados".equals(evento.getCampo())));
        // mesmo sem evento, o card registra quem mexeu e o quadro dos outros atualiza
        verify(cardRepository).saveAndFlush(card);
        verify(eventos).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(evento -> evento instanceof LiberacaoEvento.Editado));
    }

    @Test
    @DisplayName("editar: sacado que antes não tinha carteira e agora tem também regrava a lista")
    void shouldRewriteSacadosWhenCarteiraAppearsForTheFirstTime() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "10", 0));
        CarteiraSacado nova = carteira(1, "7200.00");

        service.editar(card.getId(), card.getVersion(),
                dadosComProposta(card, proposta("2026-10-08T15:59:13"), List.of(dadosSacado(SACADO_A, "Sacado A", "10", nova))), auxiliar);

        assertThat(sacadosGravados()).singleElement().satisfies(sacado -> assertThat(sacado.getCarteira()).isEqualTo(nova));
    }

    @Test
    @DisplayName("editar: mesmos sacados e mesma carteira (instância diferente, valores iguais) não regravam nada")
    void shouldNotRewriteSacadosWhenCarteiraIsEqual() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        sacadosSao(card, comCarteira(sacado(card, SACADO_A, "Sacado A", "10", 0), carteira(1, "7200.00")));

        service.editar(card.getId(), card.getVersion(),
                dadosComProposta(card, null, List.of(dadosSacado(SACADO_A, "Sacado A", "10", carteira(1, "7200.00")))), auxiliar);

        verify(sacadoRepository, never()).apagarDoCard(any());
        verify(sacadoRepository, never()).saveAll(anyList());
        verify(eventoRepository, never()).save(any());
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("editar: sacados sem carteira na edição (formulário sem AR) não regravam a lista nem apagam a carteira")
    void shouldNotTouchCarteiraWhenIncomingHasNone() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        LiberacaoSacadoEntity existente = comCarteira(sacado(card, SACADO_A, "Sacado A", "10", 0), carteira(1, "7200.00"));
        sacadosSao(card, existente);

        service.editar(card.getId(), card.getVersion(),
                dadosComProposta(card, null, List.of(dadosSacado(SACADO_A, "Sacado A", "10", null))), auxiliar);

        verify(sacadoRepository, never()).apagarDoCard(any());
        assertThat(existente.getCarteira()).isNotNull();
    }

    @Test
    @DisplayName("editar: card finalizado aceita reimportar a AR (mesmos sacados) e mantém as decisões")
    void shouldAllowReimportingArOnFinalizedCardKeepingDecisions() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);
        sacadosSao(card, comCarteira(
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null), carteira(1, "7200.00")));
        CarteiraSacado nova = carteira(2, "9100.00");
        DadosCard dados = dadosComProposta(card, proposta("2026-10-08T15:59:13"),
                List.of(dadosSacado(SACADO_A, "Sacado A", "1000", nova)));

        service.editar(card.getId(), card.getVersion(), dados, analista);

        assertThat(card.getProposta()).isNotNull();
        assertThat(sacadosGravados()).singleElement().satisfies(sacado -> {
            assertThat(sacado.getCarteira()).isEqualTo(nova);
            assertThat(sacado.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
            assertThat(sacado.getSituacaoPorNome()).isEqualTo("Mychelly");
        });
        assertThat(camposDosEventos(eventosGravados())).containsExactly("proposta");
    }

    // ------------------------------------------------------------ valorAprovado

    @Test
    @DisplayName("valorAprovado: aprovado vale o valor inteiro, parcial o valor aprovado, reprovado zero")
    void shouldSumApprovedValue() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        BigDecimal total = LiberacaoService.valorAprovado(List.of(
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, "200"),
                decidido(card, CPF, "Pessoa Física", "300", 2, ResultadoLiberacao.REPROVADO, null)));

        assertThat(total).isEqualByComparingTo("1200");
    }

    @Test
    @DisplayName("valorAprovado: Amostragem vale o valor inteiro, como aprovado")
    void shouldSumSamplingAsApproved() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        BigDecimal total = LiberacaoService.valorAprovado(List.of(
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.AMOSTRAGEM, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null)));

        assertThat(total).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Comitê→Aprovado: Amostragem no C conta como aprovado — card APROVADO, situação Amostragem, valor inteiro")
    void shouldApproveCardWithSamplingInC() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.AMOSTRAGEM, null);
        LiberacaoSacadoEntity b = comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.APROVADO, null);
        sacadosSao(card, a, b);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, List.of(), analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.AMOSTRAGEM);
        assertThat(LiberacaoService.valorAprovado(List.of(a, b))).isEqualByComparingTo("1500");
    }

    @Test
    @DisplayName("valorAprovado: tudo reprovado dá zero, e não nulo")
    void shouldBeZeroWhenEverythingIsRejected() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        BigDecimal total = LiberacaoService.valorAprovado(List.of(
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null)));

        assertThat(total).isNotNull().isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("valorAprovado: nulo enquanto ninguém foi decidido (ou não há sacados)")
    void shouldBeNullWhenNothingIsDecided() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThat(LiberacaoService.valorAprovado(List.of())).isNull();
        assertThat(LiberacaoService.valorAprovado(List.of(
                decidido(card, SACADO_A, "Sacado A", "1000", 0, null, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, null, null)))).isNull();
    }

    @Test
    @DisplayName("valorAprovado: sacado ainda sem decisão conta zero quando outro já foi decidido")
    void shouldCountUndecidedSacadoAsZero() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        BigDecimal total = LiberacaoService.valorAprovado(List.of(
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, null, null)));

        assertThat(total).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("valorAprovado: valores ausentes contam zero (aprovado sem valor, parcial sem valor aprovado)")
    void shouldTreatMissingValuesAsZero() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        BigDecimal total = LiberacaoService.valorAprovado(List.of(
                decidido(card, SACADO_A, "Sacado A", null, 0, ResultadoLiberacao.APROVADO, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, null),
                decidido(card, CPF, "Pessoa Física", "300", 2, ResultadoLiberacao.APROVADO, null)));

        assertThat(total).isEqualByComparingTo("300");
    }

    // ------------------------------------------------------ decisoesAnteriores

    @Test
    @DisplayName("decisoesAnteriores: sem documentos devolve lista vazia sem consultar o banco")
    void shouldNotQueryPreviousDecisionsWithoutDocuments() {
        assertThat(service.decisoesAnteriores(List.of())).isEmpty();
        verify(sacadoRepository, never()).decisoesAnteriores(any());
    }

    @Test
    @DisplayName("decisoesAnteriores: monta cada linha com o card, a decisão, quem decidiu e quando")
    void shouldMapPreviousDecisions() {
        LiberacaoCardEntity outro = card(EtapaLiberacao.FINALIZADO);
        outro.setCedenteNome("OUTRA LTDA");
        LiberacaoSacadoEntity reprovado = decidido(outro, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null);
        LiberacaoSacadoEntity parcial = decidido(outro, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, "200");
        when(sacadoRepository.decisoesAnteriores(List.of(SACADO_A, SACADO_B))).thenReturn(List.of(
                new Object[]{reprovado, outro}, new Object[]{parcial, outro}));

        List<DecisaoAnterior> anteriores = service.decisoesAnteriores(List.of(SACADO_A, SACADO_B));

        assertThat(anteriores).hasSize(2);
        DecisaoAnterior primeira = anteriores.get(0);
        assertThat(primeira.documento()).isEqualTo(SACADO_A);
        assertThat(primeira.cardId()).isEqualTo(outro.getId());
        assertThat(primeira.numero()).isEqualTo(42L);
        assertThat(primeira.cedenteNome()).isEqualTo("OUTRA LTDA");
        assertThat(primeira.situacao()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(primeira.valorAprovado()).isNull();
        assertThat(primeira.decididoPor()).isEqualTo("Mychelly");
        assertThat(primeira.decididoEm()).isEqualTo(LocalDateTime.of(2026, 10, 7, 15, 0));
        assertThat(anteriores.get(1).situacao()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(anteriores.get(1).valorAprovado()).isEqualByComparingTo("200");
    }

    // --------------------------------------------------------- registrarParecer

    @Test
    @DisplayName("registrarParecer: card fora do Comitê é TransicaoInvalida")
    void shouldRejectParecerOutsideComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);

        assertThatThrownBy(() -> service.registrarParecer(card.getId(), PosicaoParecer.FAVORAVEL, "ok", analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("Comitê");
        verify(parecerRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrarParecer: usuário sem linha de parecer na rodada é AcessoNegado")
    void shouldDenyParecerFromUserWithoutRow() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        when(parecerRepository.findByCardIdAndRodadaAndUsuarioId(card.getId(), 1, analista.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarParecer(card.getId(), PosicaoParecer.FAVORAVEL, "ok", analista))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("Seu parecer não é esperado");
        verify(parecerRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrarParecer: posição ausente é IllegalArgument")
    void shouldRejectParecerWithoutPosicao() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> service.registrarParecer(card.getId(), null, "ok", analista))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("registrarParecer: sem usuário autenticado é AcessoNegado")
    void shouldRejectParecerWithoutAuthenticatedUser() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> service.registrarParecer(card.getId(), PosicaoParecer.FAVORAVEL, "ok", null))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("registrarParecer: o último que faltava devolve ultimo=true e grava posição, texto e evento PARECER")
    void shouldFlagLastPendingParecer() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoParecerEntity meu = parecer(card, analista, null);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL), meu);

        ParecerRegistrado resultado = service.registrarParecer(card.getId(), PosicaoParecer.COM_RESSALVAS,
                "  Exigir aval  ", analista);

        assertThat(resultado.ultimo()).isTrue();
        assertThat(resultado.card()).isSameAs(card);
        assertThat(resultado.parecer().getPosicao()).isEqualTo(PosicaoParecer.COM_RESSALVAS);
        assertThat(resultado.parecer().getTexto()).isEqualTo("Exigir aval");
        assertThat(resultado.parecer().getRegistradoEm()).isNotNull();
        assertThat(resultado.parecer().getUsuarioNome()).isEqualTo("Andressa");
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.PARECER);
        assertThat(evento.getValorDepois()).isEqualTo("Com ressalvas");
        assertThat(evento.getTexto()).isEqualTo("Parecer registrado");
    }

    @Test
    @DisplayName("registrarParecer: ultimo=false quando ainda falta outra pessoa")
    void shouldNotFlagLastWhenSomeoneElseIsStillPending() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), null), parecer(card, analista, null));

        ParecerRegistrado resultado = service.registrarParecer(card.getId(), PosicaoParecer.FAVORAVEL, null, analista);

        assertThat(resultado.ultimo()).isFalse();
        assertThat(resultado.parecer().getTexto()).isNull();
    }

    @Test
    @DisplayName("registrarParecer: revisar parecer já registrado devolve ultimo=false e evento 'Parecer revisto'")
    void shouldNotFlagLastOnRevision() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, analista, PosicaoParecer.FAVORAVEL));

        ParecerRegistrado resultado = service.registrarParecer(card.getId(), PosicaoParecer.DESFAVORAVEL,
                "Mudei de ideia", analista);

        assertThat(resultado.ultimo()).isFalse();
        assertThat(resultado.parecer().getPosicao()).isEqualTo(PosicaoParecer.DESFAVORAVEL);
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTexto()).isEqualTo("Parecer revisto");
        assertThat(evento.getValorDepois()).isEqualTo("Desfavorável");
    }

    // ------------------------------------------------------ responderPendencia

    private LiberacaoPendenciaEntity pendencia(LiberacaoCardEntity card, UserEntity destinatario) {
        LiberacaoPendenciaEntity pendencia = LiberacaoPendenciaEntity.builder()
                .id(UUID.randomUUID())
                .cardId(card.getId())
                .abertaPorId(analista.getId())
                .abertaPorNome("Andressa")
                .destinatarioId(destinatario.getId())
                .destinatarioNome(destinatario.getName())
                .texto("Enviar contrato")
                .abertaEm(LocalDateTime.now().minusHours(2))
                .build();
        when(pendenciaRepository.findById(pendencia.getId())).thenReturn(Optional.of(pendencia));
        return pendencia;
    }

    @Test
    @DisplayName("responderPendencia: destinatário não-analista responde; grava resposta, quem e quando, e evento")
    void shouldLetRecipientAnswer() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        UserEntity carla = usuario("Carla");
        LiberacaoPendenciaEntity pendencia = pendencia(card, carla);

        LiberacaoPendenciaEntity salva = service.responderPendencia(card.getId(), pendencia.getId(), "  Enviado.  ", carla);

        assertThat(salva.getResposta()).isEqualTo("Enviado.");
        assertThat(salva.getRespondidaEm()).isNotNull();
        assertThat(salva.getRespondidaPorNome()).isEqualTo("Carla");
        assertThat(salva.aberta()).isFalse();
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.PENDENCIA_RESPONDIDA);
        assertThat(evento.getTexto()).isEqualTo("Respondeu a pendência de Andressa");
    }

    @Test
    @DisplayName("responderPendencia: já respondida é TransicaoInvalida citando quem respondeu")
    void shouldRejectAnsweringTwice() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        UserEntity carla = usuario("Carla");
        LiberacaoPendenciaEntity pendencia = pendencia(card, carla);
        pendencia.setRespondidaEm(LocalDateTime.now());
        pendencia.setRespondidaPorNome("Carla");

        assertThatThrownBy(() -> service.responderPendencia(card.getId(), pendencia.getId(), "De novo", carla))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Pendência já respondida por Carla.");
        verify(pendenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("responderPendencia: estranho não-analista é AcessoNegado")
    void shouldDenyAnswerByStranger() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        LiberacaoPendenciaEntity pendencia = pendencia(card, usuario("Carla"));

        assertThatThrownBy(() -> service.responderPendencia(card.getId(), pendencia.getId(), "Eu respondo", auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        verify(pendenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("responderPendencia: analista cobre a colega")
    void shouldLetAnalystAnswerForRecipient() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        LiberacaoPendenciaEntity pendencia = pendencia(card, usuario("Carla"));

        LiberacaoPendenciaEntity salva = service.responderPendencia(card.getId(), pendencia.getId(), "Resolvido", analista);

        assertThat(salva.getRespondidaPorNome()).isEqualTo("Andressa");
    }

    @Test
    @DisplayName("responderPendencia: resposta em branco é IllegalArgument")
    void shouldRejectBlankAnswer() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        UserEntity carla = usuario("Carla");
        LiberacaoPendenciaEntity pendencia = pendencia(card, carla);

        assertThatThrownBy(() -> service.responderPendencia(card.getId(), pendencia.getId(), "   ", carla))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(pendencia.aberta()).isTrue();
    }

    @Test
    @DisplayName("responderPendencia: pendência de outro card ou inexistente é EntityNotFound")
    void shouldNotFindPendenciaOfAnotherCard() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        LiberacaoCardEntity outro = card(EtapaLiberacao.PENDENCIA);
        UserEntity carla = usuario("Carla");
        LiberacaoPendenciaEntity doOutro = pendencia(outro, carla);

        assertThatThrownBy(() -> service.responderPendencia(card.getId(), doOutro.getId(), "x", carla))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.responderPendencia(card.getId(), UUID.randomUUID(), "x", carla))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // -------------------------------------------------------------- novaPendencia

    @Test
    @DisplayName("novaPendencia: só com o card em Pendência, e só analista abre")
    void shouldOnlyOpenNewPendenciaInPendenciaByAnalyst() {
        LiberacaoCardEntity noComite = card(EtapaLiberacao.COMITE);
        LiberacaoCardEntity emPendencia = card(EtapaLiberacao.PENDENCIA);
        UserEntity carla = usuario("Carla");
        when(userRepository.findById(carla.getId())).thenReturn(Optional.of(carla));
        NovaPendencia nova = new NovaPendencia(carla.getId(), "Falta o balanço");

        assertThatThrownBy(() -> service.novaPendencia(noComite.getId(), nova, analista))
                .isInstanceOf(TransicaoInvalidaException.class);
        assertThatThrownBy(() -> service.novaPendencia(emPendencia.getId(), nova, auxiliar))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Só analista pode abrir pendência.");

        LiberacaoPendenciaEntity aberta = service.novaPendencia(emPendencia.getId(), nova, analista);
        assertThat(aberta.getDestinatarioNome()).isEqualTo("Carla");
    }

    // -------------------------------------------------------------------- excluir

    @Test
    @DisplayName("excluir: marca excluidoEm/excluidoPor, salva e registra EXCLUSAO com a etapa")
    void shouldSoftDeleteAndRecordExclusion() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        LocalDateTime antes = LocalDateTime.now();

        service.excluir(card.getId(), auxiliar);

        assertThat(card.getExcluidoEm()).isNotNull().isAfterOrEqualTo(antes);
        assertThat(card.getExcluidoPorId()).isEqualTo(auxiliar.getId());
        assertThat(card.getExcluidoPorNome()).isEqualTo("Auxiliar");
        verify(cardRepository).save(card);
        verify(cardRepository, never()).delete(any());
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.EXCLUSAO);
        assertThat(evento.getEtapaDe()).isEqualTo(EtapaLiberacao.ORIGEM);
        assertThat(evento.getTexto()).isEqualTo("Card apagado");
        assertThat(evento.getUsuarioId()).isEqualTo(auxiliar.getId());
    }

    @Test
    @DisplayName("excluir: não-analista não apaga card do Comitê")
    void shouldDenyNonAnalystDeletingCardInComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> service.excluir(card.getId(), auxiliar)).isInstanceOf(AcessoNegadoException.class);
        assertThat(card.getExcluidoEm()).isNull();
        verify(cardRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
    }

    @Test
    @DisplayName("excluir: analista apaga card do Comitê")
    void shouldLetAnalystDeleteCardInComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        service.excluir(card.getId(), analista);

        assertThat(card.getExcluidoPorNome()).isEqualTo("Andressa");
        assertThat(eventosGravados().get(0).getEtapaDe()).isEqualTo(EtapaLiberacao.COMITE);
    }

    // ------------------------------------------------------------------ leitura

    @Test
    @DisplayName("aguardando: ignora registrados e pareceres de usuário removido")
    void shouldListOnlyPresentUsersWithoutPosition() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        List<String> nomes = LiberacaoService.aguardando(List.of(
                parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), null),
                parecer(card, null, null),
                parecer(card, usuario("Carla"), null)));

        assertThat(nomes).containsExactly("Mychelly", "Carla");
    }

    @Test
    @DisplayName("aguardandoParecer consulta a rodada vigente do card")
    void shouldQueryCurrentRodadaForWaitingNames() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        card.setRodada(3);
        rodadaTem(card, parecer(card, usuario("Mychelly"), null));

        assertThat(service.aguardandoParecer(card)).containsExactly("Mychelly");
        verify(parecerRepository).findByCardIdAndRodadaOrderByCriadoEm(card.getId(), 3);
    }

    @Test
    @DisplayName("comiteVazio reflete a consulta por usuários com a marca de Comitê")
    void shouldReportEmptyComite() {
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of());
        assertThat(service.comiteVazio()).isTrue();

        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));
        assertThat(service.comiteVazio()).isFalse();
    }

    @Test
    @DisplayName("resumo soma pareceres aguardando e pendências abertas para a pessoa")
    void shouldSummarizeWhatWaitsForTheUser() {
        when(parecerRepository.contarAguardando(analista.getId())).thenReturn(2L);
        when(pendenciaRepository.contarAbertasPara(analista.getId())).thenReturn(3L);

        assertThat(service.resumo(analista)).containsEntry("pareceresAguardando", 2L)
                .containsEntry("pendenciasParaMim", 3L)
                .containsEntry("total", 5L);
    }

    @Test
    @DisplayName("listarQuadro repassa a opção de incluir arquivados ao repositório")
    void shouldPassArchivedFlagToBoardQuery() {
        service.listarQuadro(false);
        service.listarQuadro(true);

        verify(cardRepository).listarQuadro(false);
        verify(cardRepository).listarQuadro(true);
    }

    // ------------------------------------------- decisão, assinatura e reabertura (esteira nova)

    private void comiteTem(UserEntity... membros) {
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(membros));
    }

    @Test
    @DisplayName("transicionar Comitê→Aprovado→Assinatura→Finalizado: resultado e finalizadoEm herdados ao longo do caminho")
    void shouldInheritResultadoAndDecidedAtThroughAssinaturaAndFinalizado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null));

        service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO, null, null, analista);
        LocalDateTime decididoEm = card.getFinalizadoEm();
        assertThat(decididoEm).isNotNull();
        assertThat(card.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);

        service.transicionar(card.getId(), EtapaLiberacao.APROVADO, EtapaLiberacao.ASSINATURA, null, null, analista);
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.ASSINATURA);
        assertThat(card.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(card.getFinalizadoEm()).isEqualTo(decididoEm);

        service.transicionar(card.getId(), EtapaLiberacao.ASSINATURA, EtapaLiberacao.FINALIZADO, null, null, analista);
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(card.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(card.getFinalizadoEm()).isEqualTo(decididoEm);
        assertThat(card.getRodada()).isEqualTo(1);
    }

    @Test
    @DisplayName("transicionar Aprovado→Assinatura: o evento não tem campo 'resultado' (só a decisão grava)")
    void shouldNotRecordResultadoOnTransitionsThatAreNotDecisions() {
        LiberacaoCardEntity card = card(EtapaLiberacao.APROVADO);
        card.setResultado(ResultadoLiberacao.APROVADO);
        card.setFinalizadoEm(LocalDateTime.now().minusHours(3));

        service.transicionar(card.getId(), EtapaLiberacao.APROVADO, EtapaLiberacao.ASSINATURA, null, null, analista);

        LiberacaoEventoEntity evento = ultimoEvento();
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.TRANSICAO);
        assertThat(evento.getCampo()).isNull();
        assertThat(evento.getValorDepois()).isNull();
    }

    @Test
    @DisplayName("transicionar Reprovado→Finalizado: mantém o resultado REPROVADO e a data da decisão")
    void shouldFinalizeReprovadoKeepingResultado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.REPROVADO);
        LocalDateTime decididoEm = LocalDateTime.now().minusHours(5);
        card.setResultado(ResultadoLiberacao.REPROVADO);
        card.setFinalizadoEm(decididoEm);

        service.transicionar(card.getId(), EtapaLiberacao.REPROVADO, EtapaLiberacao.FINALIZADO, null, null, analista);

        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(card.getResultado()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(card.getFinalizadoEm()).isEqualTo(decididoEm);
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("reabertura: de qualquer etapa decidida volta ao Comitê com rodada+1, sem resultado nem data de decisão, e evento REABERTURA")
    void shouldReopenFromAnyDecidedStage(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        card.setResultado(ResultadoLiberacao.APROVADO);
        card.setFinalizadoEm(LocalDateTime.now().minusDays(1));
        UserEntity bruna = usuario("Bruna");
        comiteTem(bruna);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), etapa, EtapaLiberacao.COMITE,
                null, "Fato novo", analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(movido.getRodada()).isEqualTo(2);
        assertThat(movido.getResultado()).isNull();
        assertThat(movido.getFinalizadoEm()).isNull();
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.REABERTURA);
        assertThat(evento.getEtapaDe()).isEqualTo(etapa);
        assertThat(evento.getEtapaPara()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(evento.getCampo()).isNull();
        assertThat(evento.getTexto()).isEqualTo("Fato novo");
        ArgumentCaptor<LiberacaoParecerEntity> pareceres = ArgumentCaptor.forClass(LiberacaoParecerEntity.class);
        verify(parecerRepository).save(pareceres.capture());
        assertThat(pareceres.getValue().getRodada()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("reabertura: quem não é analista não reabre de nenhuma etapa decidida")
    void shouldDenyNonAnalystReopeningFromAnyDecidedStage(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        comiteTem(usuario("Bruna"));

        assertThatThrownBy(() -> service.transicionar(card.getId(), etapa, EtapaLiberacao.COMITE,
                null, null, auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("transicionar Pendência→Comitê e Comitê→Origem não são reabertura e limpam resultado e data de decisão")
    void shouldNotTreatBackwardMovesBeforeDecisionAsReopening() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        card.setFinalizadoEm(LocalDateTime.now());
        comiteTem(usuario("Bruna"));

        service.transicionar(card.getId(), EtapaLiberacao.PENDENCIA, EtapaLiberacao.COMITE, null, null, analista);

        assertThat(card.getRodada()).isEqualTo(1);
        assertThat(card.getFinalizadoEm()).isNull();
        assertThat(card.getResultado()).isNull();
        assertThat(eventosGravados().get(0).getTipo()).isEqualTo(TipoEventoLiberacao.TRANSICAO);
    }

    @Test
    @DisplayName("aprovar: com o Comitê sem decisão registrada na Pendência, decisões do pedido valem e Pendência→Aprovado calcula o resultado")
    void shouldApproveFromPendenciaUsingRequestDecisions() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        comUmParecer(card);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.PENDENCIA,
                EtapaLiberacao.APROVADO, null, null,
                List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.PARCIAL, new BigDecimal("300"))), analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(movido.getFinalizadoEm()).isNotNull();
        assertThat(ultimoEvento().getValorDepois()).isEqualTo("Parcialmente aprovado");
        // da Pendência vale o C, como no Comitê
        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(a.getValorAprovadoComite()).isEqualByComparingTo("300");
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(a.getValorAprovado()).isEqualByComparingTo("300");
        assertThat(a.getDecisaoDiretoria()).as("o D não é tocado").isNull();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ASSINATURA", "FINALIZADO", "ORIGEM", "PENDENCIA"})
    @DisplayName("transicionar: decisão de sacado só aceita com destino Aprovado (Assinatura, Finalizado, Origem e Pendência recusam)")
    void shouldRejectDecisionsForNonAprovadoDestinations(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, destino, null, null,
                List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.APROVADO, null)), analista))
                .isInstanceOf(RuntimeException.class);
        verify(sacadoRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("transicionar: qualquer movimento limpa arquivadoEm e arquivadoPorNome")
    void shouldClearArchiveMarksOnAnyTransition(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        card.setArquivadoEm(LocalDateTime.now().minusDays(1));
        card.setArquivadoPorNome("Andressa");
        comiteTem(usuario("Bruna"));

        service.transicionar(card.getId(), etapa, EtapaLiberacao.COMITE, null, null, analista);

        assertThat(card.getArquivadoEm()).isNull();
        assertThat(card.getArquivadoPorNome()).isNull();
    }

    // ----------------------------------------------------------------- Diretoria

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA"})
    @DisplayName("transicionar Comitê/Pendência→Diretoria: guarda o motivo e quem enviou, não decide nada e leva a Diretoria a acompanhar o card")
    void shouldSendCardToDiretoriaWithMotivo(EtapaLiberacao de) {
        LiberacaoCardEntity card = card(de);
        comUmParecer(card);
        UserEntity marcia = UserEntity.builder().id(UUID.randomUUID()).name("Márcia").analista(true).diretoria(true).build();
        diretoriaTem(diretor, marcia);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        sacadosSao(card, a);
        LocalDateTime antes = LocalDateTime.now();

        LiberacaoCardEntity movido = service.transicionar(card.getId(), de, EtapaLiberacao.DIRETORIA,
                null, "  Valor acima da alçada do Comitê  ", analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.DIRETORIA);
        assertThat(movido.getMotivoDiretoria()).isEqualTo("Valor acima da alçada do Comitê");
        assertThat(movido.getEnviadoDiretoriaPorNome()).isEqualTo("Andressa");
        assertThat(movido.getEnviadoDiretoriaEm()).isNotNull().isAfterOrEqualTo(antes);
        // ainda não há decisão: sem resultado nem data de decisão, e a situação final fica para quando decidirem
        assertThat(movido.getResultado()).isNull();
        assertThat(movido.getFinalizadoEm()).isNull();
        assertThat(movido.getRodada()).isEqualTo(1);
        assertThat(a.getSituacao()).isNull();
        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
        verify(sacadoRepository, never()).saveAll(anyList());
        // a Diretoria inteira passa a acompanhar o card; a Diretoria não é convocada como o Comitê, não há parecer a dar
        assertThat(membrosGravados()).extracting(LiberacaoMembroEntity::getUsuarioId)
                .containsExactly(diretor.getId(), marcia.getId());
        assertThat(membrosGravados()).extracting(LiberacaoMembroEntity::getOrigem).containsOnly(OrigemMembro.DIRETORIA);
        verify(parecerRepository, never()).save(any());

        LiberacaoEventoEntity evento = ultimoEvento();
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.TRANSICAO);
        assertThat(evento.getEtapaDe()).isEqualTo(de);
        assertThat(evento.getEtapaPara()).isEqualTo(EtapaLiberacao.DIRETORIA);
        assertThat(evento.getCampo()).as("só a decisão grava o resultado").isNull();
        assertThat(evento.getTexto()).isEqualTo("Valor acima da alçada do Comitê");
        ArgumentCaptor<Object> publicado = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(publicado.capture());
        assertThat(publicado.getValue()).isInstanceOfSatisfying(LiberacaoEvento.Movido.class, movidoEvento -> {
            assertThat(movidoEvento.de()).isEqualTo(de);
            assertThat(movidoEvento.para()).isEqualTo(EtapaLiberacao.DIRETORIA);
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    @DisplayName("transicionar →Diretoria: sem motivo (nulo, vazio ou só espaços) é IllegalArgument e nada é gravado")
    void shouldRequireMotivoToGoToDiretoria(String motivo) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        diretoriaTem(diretor);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.DIRETORIA,
                null, motivo, analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Conte por que o card vai para a Diretoria.");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(eventoRepository, never()).save(any());
        verify(membroRepository, never()).save(any());
        verify(eventos, never()).publishEvent(any());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(card.getMotivoDiretoria()).isNull();
        assertThat(card.getEnviadoDiretoriaPorNome()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA"})
    @DisplayName("transicionar →Diretoria: sem ninguém marcado como Diretoria é recusado e o card continua onde estava")
    void shouldNotGoToDiretoriaWhenNobodyIsDiretoria(EtapaLiberacao de) {
        LiberacaoCardEntity card = card(de);
        comUmParecer(card);
        diretoriaTem();

        assertThatThrownBy(() -> service.transicionar(card.getId(), de, EtapaLiberacao.DIRETORIA,
                null, "Acima da alçada", analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Ninguém está marcado como Diretoria. Peça ao admin para marcar em Configurações.");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(eventoRepository, never()).save(any());
        assertThat(card.getEtapa()).isEqualTo(de);
        assertThat(card.getMotivoDiretoria()).isNull();
    }

    @Test
    @DisplayName("transicionar Pendência→Diretoria: não pede todos os pareceres, só o motivo, e o histórico não fala de parecer faltando")
    void shouldMoveFromPendenciaToDiretoriaWithoutAllPareceres() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL), parecer(card, usuario("Mychelly"), null));
        diretoriaTem(diretor);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.PENDENCIA,
                EtapaLiberacao.DIRETORIA, null, "Pendência resolvida, falta a palavra final", analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.DIRETORIA);
        assertThat(ultimoEvento().getTexto()).isEqualTo("Pendência resolvida, falta a palavra final");
    }

    @Test
    @DisplayName("transicionar Comitê→Diretoria com 1 de 2 pareceres: libera, e o histórico traz o motivo e quem faltou")
    void shouldRecordMotivoAndMissingParecerWhenMovingToDiretoria() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), null));
        diretoriaTem(diretor);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.DIRETORIA, null, "Acima da alçada", analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.DIRETORIA);
        assertThat(ultimoEvento().getTexto()).isEqualTo("Acima da alçada\n\nMovido sem o parecer de Mychelly.");
    }

    @Test
    @DisplayName("transicionar Comitê→Diretoria: quem não é analista não manda o card, e nada é gravado")
    void shouldDenyNonAnalystSendingCardToDiretoria() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        diretoriaTem(diretor);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.DIRETORIA,
                null, "Acima da alçada", auxiliar))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("A partir do Comitê, só analista move o card.");
        verify(cardRepository, never()).saveAndFlush(any());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
    }

    @Test
    @DisplayName("transicionar →Diretoria: decisão de sacado no pedido é recusada, porque o C se marca pela lista e não pelo movimento")
    void shouldRejectDecisionsWhenMovingToDiretoria() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        diretoriaTem(diretor);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.DIRETORIA,
                null, "Acima da alçada", List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.REPROVADO, null)), analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Decisão de sacado só ao aprovar");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(sacadoRepository, never()).save(any());
        assertThat(a.getOpiniaoComite()).as("o C da proposta fica como estava").isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA"})
    @DisplayName("transicionar →Diretoria: todo sacado precisa do C; a mensagem lista quem falta e nada é gravado")
    void shouldRequireCOfEverySacadoBeforeSendingToDiretoria(EtapaLiberacao de) {
        LiberacaoCardEntity card = card(de);
        comUmParecer(card);
        diretoriaTem(diretor);
        LiberacaoSacadoEntity b = sacado(card, SACADO_B, "Sacado B", "500", 1);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                b,
                sacado(card, CPF, null, "300", 2));

        assertThatThrownBy(() -> service.transicionar(card.getId(), de, EtapaLiberacao.DIRETORIA,
                null, "Acima da alçada", analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Decida o C de todos os sacados antes de mandar para a Diretoria. "
                        + "Falta: Sacado B (00.360.305/0001-04), 529.982.247-25.");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(eventoRepository, never()).save(any());
        verify(membroRepository, never()).save(any());
        verify(eventos, never()).publishEvent(any());
        assertThat(card.getEtapa()).isEqualTo(de);
        assertThat(card.getMotivoDiretoria()).isNull();
        assertThat(card.getEnviadoDiretoriaPorNome()).isNull();
        assertThat(b.getOpiniaoComite()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"COMITE", "PENDENCIA"})
    @DisplayName("transicionar →Diretoria: com todos os sacados reprovados no C é recusado — o Comitê mesmo reprova o card — e nada é gravado")
    void shouldRefuseSendingToDiretoriaWhenEverySacadoIsRejectedInC(EtapaLiberacao de) {
        LiberacaoCardEntity card = card(de);
        comUmParecer(card);
        diretoriaTem(diretor);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null),
                comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null));

        assertThatThrownBy(() -> service.transicionar(card.getId(), de, EtapaLiberacao.DIRETORIA,
                null, "Acima da alçada", analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Todos os sacados estão reprovados no C: o Comitê mesmo reprova o card.");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(eventoRepository, never()).save(any());
        verify(membroRepository, never()).save(any());
        assertThat(card.getEtapa()).isEqualTo(de);
        assertThat(card.getMotivoDiretoria()).isNull();
    }

    @Test
    @DisplayName("transicionar →Diretoria: proposta mista do Comitê (aprovado, parcial e reprovado) segue, e o C não é alterado pelo envio")
    void shouldSendMixedProposalToDiretoria() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        diretoriaTem(diretor);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null);
        LiberacaoSacadoEntity b = comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, "200");
        sacadosSao(card, a, b);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.DIRETORIA,
                null, "Acima da alçada", analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.DIRETORIA);
        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(b.getValorAprovadoComite()).isEqualByComparingTo("200");
        assertThat(a.getDecisaoDiretoria()).as("o D só nasce quando a Diretoria responde").isNull();
        assertThat(b.getDecisaoDiretoria()).isNull();
        verify(sacadoRepository, never()).save(any());
        verify(sacadoRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("transicionar →Diretoria: card sem sacados não tem C a exigir e segue para a Diretoria")
    void shouldSendSacadoLessCardToDiretoria() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        diretoriaTem(diretor);
        sacadosSao(card);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.DIRETORIA,
                null, "Acima da alçada", analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.DIRETORIA);
        assertThat(movido.getMotivoDiretoria()).isEqualTo("Acima da alçada");
    }

    @Test
    @DisplayName("transicionar →Diretoria: as regras anteriores continuam na frente — sem motivo, o motivo é cobrado antes de se falar do C")
    void shouldAskForMotivoBeforeCheckingC() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        diretoriaTem(diretor);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.DIRETORIA,
                null, "  ", analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Conte por que o card vai para a Diretoria.");
    }

    @Test
    @DisplayName("transicionar: etapa 'de' desatualizada com o card já na Diretoria diz 'Diretoria' e quem moveu")
    void shouldReportDiretoriaWhenCardWasAlreadyMovedThere() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, analista))
                .isInstanceOf(ConflitoEdicaoException.class)
                .hasMessageContaining("Diretoria")
                .hasMessageContaining("Beatriz");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    // ---------------------------------------- quem move e decide com o card na Diretoria

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"APROVADO", "REPROVADO"})
    @DisplayName("transicionar Diretoria→Aprovado/Reprovado: analista sem o perfil Diretoria e quem não é analista são AcessoNegado, e nada é aplicado")
    void shouldDenyWhoIsNotDiretoriaMovingCardFromDiretoria(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        LiberacaoSacadoEntity a = comD(comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                ResultadoLiberacao.APROVADO, null);
        sacadosSao(card, a);

        for (UserEntity autor : List.of(analista, auxiliar)) {
            assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA, destino, null, null, autor))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasMessage("Na Diretoria, só quem tem o perfil Diretoria decide.");
        }
        verify(cardRepository, never()).saveAndFlush(any());
        verify(sacadoRepository, never()).save(any());
        verify(sacadoRepository, never()).saveAll(anyList());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.DIRETORIA);
        assertThat(a.getSituacao()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ORIGEM", "COMITE", "PENDENCIA", "ASSINATURA", "FINALIZADO"})
    @DisplayName("transicionar a partir da Diretoria: só Aprovado ou Reprovado; devolver ao Comitê, à Pendência ou à Origem é TransicaoInvalida")
    void shouldNotLetDiretoriaGoBack(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        comiteTem(usuario("Bruna"));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA, destino, null, null, diretor))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageStartingWith("Não dá para ir de Diretoria para");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(parecerRepository, never()).save(any());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.DIRETORIA);
        assertThat(card.getRodada()).isEqualTo(1);
    }

    @Test
    @DisplayName("Diretoria→Aprovado: Amostragem do C se repete no D e na situação final; o card sai APROVADO")
    void shouldCopySamplingFromCToD() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.AMOSTRAGEM, null);
        sacadosSao(card, a);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA,
                EtapaLiberacao.APROVADO, null, null, List.of(), diretor);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getDecisaoDiretoria()).isEqualTo(ResultadoLiberacao.AMOSTRAGEM);
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.AMOSTRAGEM);
    }

    @Test
    @DisplayName("Diretoria→Aprovado: o D de cada sacado repete o C (decisão e valor), assinado por quem aprovou, e vira a situação final; o C fica como o Comitê deixou")
    void shouldApproveFromDiretoriaCopyingCToDAndSituacao() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        // a proposta do Comitê: A aprovado inteiro, B só em parte
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        LiberacaoSacadoEntity b = comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, "200");
        sacadosSao(card, a, b);
        LocalDateTime antes = LocalDateTime.now();

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA,
                EtapaLiberacao.APROVADO, null, null, List.of(), diretor);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(movido.getResultado()).as("o sim da Diretoria vale a proposta inteira do Comitê").isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(movido.getFinalizadoEm()).isNotNull().isAfterOrEqualTo(antes);
        // D = cópia do C, com o nome de quem aprovou (a Diretoria) e a hora da aprovação
        assertThat(a.getDecisaoDiretoria()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getValorAprovadoDiretoria()).isNull();
        assertThat(a.getDecisaoDiretoriaPorNome()).isEqualTo("Walter");
        assertThat(a.getDecisaoDiretoriaEm()).isNotNull().isAfterOrEqualTo(antes);
        assertThat(b.getDecisaoDiretoria()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(b.getValorAprovadoDiretoria()).isEqualByComparingTo("200");
        assertThat(b.getDecisaoDiretoriaPorNome()).isEqualTo("Walter");
        // a situação final sai do D, então também é assinada pela Diretoria
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getSituacaoPorNome()).isEqualTo("Walter");
        assertThat(a.getSituacaoEm()).isEqualTo(a.getDecisaoDiretoriaEm());
        assertThat(b.getSituacao()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(b.getValorAprovado()).isEqualByComparingTo("200");
        assertThat(b.getSituacaoPorNome()).isEqualTo("Walter");
        assertThat(LiberacaoService.valorAprovado(List.of(a, b))).isEqualByComparingTo("1200");
        // o C continua mostrando quem do Comitê decidiu, e quando
        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getOpiniaoComitePorNome()).isEqualTo("Mychelly");
        assertThat(a.getOpiniaoComiteEm()).isEqualTo(LocalDateTime.of(2026, 10, 7, 15, 0));
        assertThat(b.getValorAprovadoComite()).isEqualByComparingTo("200");
        assertThat(sacadosGravados()).containsExactly(a, b);
        // um evento por D preenchido, assinado pela Diretoria, e a transição
        List<LiberacaoEventoEntity> gravados = eventosGravados();
        assertThat(gravados).extracting(LiberacaoEventoEntity::getTipo)
                .containsExactly(TipoEventoLiberacao.EDICAO, TipoEventoLiberacao.EDICAO, TipoEventoLiberacao.TRANSICAO);
        assertThat(gravados.get(0).getCampo()).isEqualTo("decisaoDiretoria");
        assertThat(gravados.get(0).getValorAntes()).isEqualTo("a decidir");
        assertThat(gravados.get(0).getValorDepois()).isEqualTo("Aprovado");
        assertThat(gravados.get(0).getTexto()).isEqualTo("Sacado A (45.723.174/0001-10)");
        assertThat(gravados.get(0).getUsuarioId()).isEqualTo(diretor.getId());
        assertThat(gravados.get(0).getUsuarioNome()).isEqualTo("Walter");
        assertThat(semEspacoInsecavel(gravados.get(1).getValorDepois())).isEqualTo("Parcial (R$ 200,00)");
        assertThat(gravados.get(2).getEtapaDe()).isEqualTo(EtapaLiberacao.DIRETORIA);
        assertThat(gravados.get(2).getValorDepois()).isEqualTo("Parcialmente aprovado");
    }

    @Test
    @DisplayName("Diretoria→Aprovado: o sim copia também o C reprovado — o sacado que o Comitê reprovou continua reprovado, só o aprovado passa")
    void shouldCopyRejectedCToDWhenDiretoriaApproves() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        LiberacaoSacadoEntity b = comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null);
        sacadosSao(card, a, b);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA,
                EtapaLiberacao.APROVADO, null, null, List.of(), diretor);

        assertThat(b.getDecisaoDiretoria()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(b.getDecisaoDiretoriaPorNome()).isEqualTo("Walter");
        assertThat(b.getSituacao()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(LiberacaoService.valorAprovado(List.of(a, b))).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Diretoria→Aprovado: um D que já estava lá (card antigo) é sobrescrito pela cópia do C, e o evento registra o valor anterior")
    void shouldOverwriteStaleDWithCopyOfC() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        LiberacaoSacadoEntity a = comD(comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                ResultadoLiberacao.REPROVADO, null);
        a.setDecisaoDiretoriaPorNome("Diretor anterior");
        sacadosSao(card, a);

        service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA, EtapaLiberacao.APROVADO, null, null, List.of(), diretor);

        assertThat(a.getDecisaoDiretoria()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getDecisaoDiretoriaPorNome()).isEqualTo("Walter");
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getCampo()).isEqualTo("decisaoDiretoria");
        assertThat(evento.getValorAntes()).isEqualTo("Reprovado");
        assertThat(evento.getValorDepois()).isEqualTo("Aprovado");
    }

    @Test
    @DisplayName("Diretoria→Aprovado: decisão de sacado no pedido é recusada — a decisão de cada sacado é a do Comitê — e nada é gravado")
    void shouldRejectSacadoDecisionsWhenApprovingFromDiretoria() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA, EtapaLiberacao.APROVADO,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.REPROVADO, null)), diretor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A Diretoria aprova ou reprova; a decisão de cada sacado é a do Comitê.");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(sacadoRepository, never()).save(any());
        verify(sacadoRepository, never()).saveAll(anyList());
        verify(eventoRepository, never()).save(any());
        assertThat(a.getDecisaoDiretoria()).isNull();
        assertThat(a.getSituacao()).isNull();
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.DIRETORIA);
    }

    @Test
    @DisplayName("Diretoria→Aprovado/Reprovado: a permissão é checada antes das regras do pedido, e Reprovado com decisão cai na regra geral de decisão")
    void shouldCheckDiretoriaProfileBeforeValidatingTheRequest() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        sacadosSao(card, comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));
        List<DecisaoSacado> decisoes = List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.APROVADO, null));

        // analista sem o perfil: AcessoNegado, e não a recusa do pedido
        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA, EtapaLiberacao.APROVADO,
                null, null, decisoes, analista))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Na Diretoria, só quem tem o perfil Diretoria decide.");
        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA, EtapaLiberacao.APROVADO,
                null, null, decisoes, "Só com garantia", analista))
                .isInstanceOf(AcessoNegadoException.class);
        // reprovar com decisões é recusado pela regra de que decisão de sacado só vai ao aprovar
        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA, EtapaLiberacao.REPROVADO,
                null, null, decisoes, diretor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Decisão de sacado só ao aprovar");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Diretoria→Aprovado: lista de decisões vazia ou ausente não atrapalha o sim da Diretoria")
    void shouldAcceptEmptyOrMissingDecisionsWhenDiretoriaApproves() {
        for (List<DecisaoSacado> decisoes : java.util.Arrays.<List<DecisaoSacado>>asList(List.of(), null)) {
            LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
            sacadosSao(card, comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));

            LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA,
                    EtapaLiberacao.APROVADO, null, null, decisoes, diretor);

            assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
            assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
        }
    }

    @Test
    @DisplayName("Diretoria→Aprovado de card antigo com sacado sem C: o sim não inventa decisão, a mensagem lista quem falta e nada é finalizado")
    void shouldNotInventDecisionForSacadoWithoutCWhenDiretoriaApproves() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                sacado(card, SACADO_B, "Sacado B", "500", 1));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA, EtapaLiberacao.APROVADO,
                null, null, diretor))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Decida todos os sacados antes de aprovar. Falta: Sacado B (00.360.305/0001-04).");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(sacadoRepository, never()).saveAll(anyList());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.DIRETORIA);
        assertThat(card.getResultado()).isNull();
    }

    @Test
    @DisplayName("Diretoria→Aprovado de card antigo com todos reprovados no C: recusado e manda mover para Reprovado")
    void shouldRejectApprovingFromDiretoriaWhenEverySacadoIsRejectedInC() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        sacadosSao(card,
                comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null),
                comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA, EtapaLiberacao.APROVADO,
                null, null, diretor))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Todos os sacados estão reprovados: mova o card para Reprovado.");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(sacadoRepository, never()).saveAll(anyList());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.DIRETORIA);
    }

    @Test
    @DisplayName("Diretoria→Reprovado: o D de todos vira REPROVADO, assinado por quem reprovou, e é copiado para a situação; o C do Comitê fica como estava")
    void shouldRejectFromDiretoriaMarkingEveryDAndCopyingToSituacao() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        LiberacaoSacadoEntity b = comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, "200");
        sacadosSao(card, a, b);

        LiberacaoCardEntity movido = reprovar(card, EtapaLiberacao.DIRETORIA, diretor);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.REPROVADO);
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(movido.getFinalizadoEm()).isNotNull();
        for (LiberacaoSacadoEntity sacado : List.of(a, b)) {
            assertThat(sacado.getDecisaoDiretoria()).isEqualTo(ResultadoLiberacao.REPROVADO);
            assertThat(sacado.getValorAprovadoDiretoria()).isNull();
            assertThat(sacado.getDecisaoDiretoriaPorNome()).isEqualTo("Walter");
            assertThat(sacado.getSituacao()).isEqualTo(ResultadoLiberacao.REPROVADO);
            assertThat(sacado.getSituacaoPorNome()).isEqualTo("Walter");
            assertThat(sacado.getValorAprovado()).as("o valor do parcial do C não vai junto").isNull();
        }
        // o C segue sendo o registro do que o Comitê propôs
        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(b.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(b.getValorAprovadoComite()).isEqualByComparingTo("200");
        List<LiberacaoEventoEntity> gravados = eventosGravados();
        assertThat(gravados).extracting(LiberacaoEventoEntity::getTipo)
                .containsExactly(TipoEventoLiberacao.EDICAO, TipoEventoLiberacao.EDICAO, TipoEventoLiberacao.TRANSICAO);
        assertThat(gravados.get(0).getCampo()).isEqualTo("decisaoDiretoria");
        assertThat(gravados.get(0).getTexto()).isEqualTo("Sacado A (45.723.174/0001-10)");
        assertThat(gravados.get(2).getCampo()).isEqualTo("resultado");
        assertThat(gravados.get(2).getValorDepois()).isEqualTo(ResultadoLiberacao.REPROVADO.rotulo());
    }

    @Test
    @DisplayName("Comitê→Aprovado: o C de cada sacado vira a situação final (decisão, valor, quem e quando) e o D continua em branco")
    void shouldApproveFromComiteCopyingCToSituacao() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        LiberacaoSacadoEntity b = comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, "200");
        sacadosSao(card, a, b);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getSituacaoPorNome()).isEqualTo("Mychelly");
        assertThat(a.getSituacaoEm()).isEqualTo(LocalDateTime.of(2026, 10, 7, 15, 0));
        assertThat(b.getSituacao()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(b.getValorAprovado()).isEqualByComparingTo("200");
        assertThat(LiberacaoService.valorAprovado(List.of(a, b))).isEqualByComparingTo("1200");
        assertThat(a.getDecisaoDiretoria()).isNull();
        assertThat(b.getDecisaoDiretoria()).isNull();
        assertThat(sacadosGravados()).containsExactly(a, b);
    }

    @Test
    @DisplayName("Diretoria→Aprovado: ressalva é recusada — a Diretoria aprova sem ressalva — e nada é gravado")
    void shouldRejectReservationWhenDiretoriaApproves() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        LiberacaoSacadoEntity a = comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA, EtapaLiberacao.APROVADO,
                null, null, List.of(), "Só com garantia real", diretor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A Diretoria aprova sem ressalva.");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(sacadoRepository, never()).saveAll(anyList());
        verify(eventoRepository, never()).save(any());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.DIRETORIA);
        assertThat(card.getRessalva()).isNull();
        assertThat(a.getDecisaoDiretoria()).isNull();
    }

    @Test
    @DisplayName("Diretoria→Aprovado: ressalva em branco conta como ausente e aprova normalmente, sem etiqueta")
    void shouldTreatBlankReservationAsNoneWhenDiretoriaApproves() {
        LiberacaoCardEntity card = card(EtapaLiberacao.DIRETORIA);
        sacadosSao(card, comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.DIRETORIA,
                EtapaLiberacao.APROVADO, null, null, List.of(), "   ", diretor);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(movido.getRessalva()).isNull();
    }

    @Test
    @DisplayName("Comitê→Aprovado com ressalva continua valendo; só a Diretoria não põe ressalva")
    void shouldStillLetComiteApproveWithReservationNextToDiretoriaRule() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card, comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, List.of(), "Só com garantia real", analista);

        assertThat(movido.getRessalva()).isEqualTo("Só com garantia real");
    }

    // ------------------------------------------------ reabertura limpa a Diretoria

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"APROVADO", "REPROVADO", "ASSINATURA", "FINALIZADO"})
    @DisplayName("reabertura: voltar ao Comitê limpa o motivo do envio e o D de todo sacado; o C e a situação anteriores ficam")
    void shouldClearDiretoriaWhenReopening(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        card.setResultado(ResultadoLiberacao.PARCIAL);
        card.setFinalizadoEm(LocalDateTime.now().minusDays(1));
        card.setMotivoDiretoria("Valor acima da alçada do Comitê");
        card.setEnviadoDiretoriaPorNome("Andressa");
        card.setEnviadoDiretoriaEm(LocalDateTime.now().minusDays(2));
        LiberacaoSacadoEntity a = comD(comiteDecidiu(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null),
                ResultadoLiberacao.APROVADO, null);
        a.setSituacao(ResultadoLiberacao.APROVADO);
        a.setSituacaoPorNome("Walter");
        LiberacaoSacadoEntity b = comD(comiteDecidiu(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.APROVADO, null),
                ResultadoLiberacao.PARCIAL, "200");
        b.setSituacao(ResultadoLiberacao.PARCIAL);
        b.setValorAprovado(new BigDecimal("200"));
        sacadosSao(card, a, b);
        comiteTem(usuario("Bruna"));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), etapa, EtapaLiberacao.COMITE,
                null, "Fato novo", analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(movido.getRodada()).isEqualTo(2);
        assertThat(movido.getMotivoDiretoria()).isNull();
        assertThat(movido.getEnviadoDiretoriaPorNome()).isNull();
        assertThat(movido.getEnviadoDiretoriaEm()).isNull();
        for (LiberacaoSacadoEntity sacado : List.of(a, b)) {
            assertThat(sacado.getDecisaoDiretoria()).isNull();
            assertThat(sacado.getValorAprovadoDiretoria()).isNull();
            assertThat(sacado.getDecisaoDiretoriaPorNome()).isNull();
            assertThat(sacado.getDecisaoDiretoriaEm()).isNull();
        }
        // o C da rodada anterior e a situação seguem como ponto de partida da nova rodada
        assertThat(a.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(b.getOpiniaoComite()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(b.getSituacao()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(b.getValorAprovado()).isEqualByComparingTo("200");
        assertThat(sacadosGravados()).containsExactly(a, b);
        assertThat(eventosGravados().get(0).getTipo()).isEqualTo(TipoEventoLiberacao.REABERTURA);
    }

    // ------------------------------------------------------------- arquivamento

    @Test
    @DisplayName("arquivar: card finalizado recebe arquivadoEm/PorNome, evento EDICAO 'arquivado' e publica Editado")
    void shouldArchiveFinalizedCard() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);
        LocalDateTime antes = LocalDateTime.now();

        LiberacaoCardEntity salvo = service.arquivar(card.getId(), true, analista);

        assertThat(salvo.getArquivadoEm()).isNotNull().isAfterOrEqualTo(antes);
        assertThat(salvo.getArquivadoPorNome()).isEqualTo("Andressa");
        assertThat(salvo.getAtualizadoPorNome()).isEqualTo("Andressa");
        LiberacaoEventoEntity evento = ultimoEvento();
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.EDICAO);
        assertThat(evento.getCampo()).isEqualTo("arquivado");
        assertThat(evento.getValorDepois()).isEqualTo("Arquivado");
        ArgumentCaptor<Object> publicado = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(publicado.capture());
        assertThat(publicado.getValue()).isInstanceOf(LiberacaoEvento.Editado.class);
    }

    @Test
    @DisplayName("arquivar(false): desarquivar limpa as marcas e grava o evento 'Desarquivado'")
    void shouldUnarchiveCard() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);
        card.setArquivadoEm(LocalDateTime.now().minusDays(2));
        card.setArquivadoPorNome("Beatriz");

        LiberacaoCardEntity salvo = service.arquivar(card.getId(), false, analista);

        assertThat(salvo.getArquivadoEm()).isNull();
        assertThat(salvo.getArquivadoPorNome()).isNull();
        assertThat(ultimoEvento().getValorDepois()).isEqualTo("Desarquivado");
    }

    @Test
    @DisplayName("arquivar: idempotente — arquivar o já arquivado e desarquivar o não arquivado não gravam nada")
    void shouldBeIdempotentWhenArchiving() {
        LiberacaoCardEntity arquivado = card(EtapaLiberacao.FINALIZADO);
        LocalDateTime quando = LocalDateTime.now().minusDays(2);
        arquivado.setArquivadoEm(quando);
        arquivado.setArquivadoPorNome("Beatriz");
        LiberacaoCardEntity solto = card(EtapaLiberacao.FINALIZADO);

        assertThat(service.arquivar(arquivado.getId(), true, analista).getArquivadoEm()).isEqualTo(quando);
        assertThat(service.arquivar(solto.getId(), false, analista).getArquivadoEm()).isNull();

        verify(cardRepository, never()).saveAndFlush(any());
        verify(eventoRepository, never()).save(any());
        verify(eventos, never()).publishEvent(any());
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"ORIGEM", "COMITE", "PENDENCIA", "APROVADO", "REPROVADO", "ASSINATURA"})
    @DisplayName("arquivar: só card finalizado é arquivado")
    void shouldOnlyArchiveFinalizedCards(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);

        assertThatThrownBy(() -> service.arquivar(card.getId(), true, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Só card finalizado é arquivado.");
        assertThat(card.getArquivadoEm()).isNull();
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("arquivar: só analista arquiva ou desarquiva; usuário ausente também é negado")
    void shouldRestrictArchivingToAnalysts() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);

        assertThatThrownBy(() -> service.arquivar(card.getId(), true, auxiliar)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.arquivar(card.getId(), false, auxiliar)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.arquivar(card.getId(), true, null)).isInstanceOf(AcessoNegadoException.class);
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("arquivar: card inexistente é EntityNotFound")
    void shouldFailArchivingUnknownCard() {
        assertThatThrownBy(() -> service.arquivar(UUID.randomUUID(), true, analista))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("arquivarFinalizados: arquiva todos os finalizados não arquivados e devolve a contagem")
    void shouldArchiveAllFinalizedAndReturnCount() {
        LiberacaoCardEntity um = card(EtapaLiberacao.FINALIZADO);
        LiberacaoCardEntity dois = card(EtapaLiberacao.FINALIZADO);
        LiberacaoCardEntity tres = card(EtapaLiberacao.FINALIZADO);
        when(cardRepository.finalizadosNaoArquivados()).thenReturn(List.of(um, dois, tres));

        int arquivados = service.arquivarFinalizados(analista);

        assertThat(arquivados).isEqualTo(3);
        assertThat(List.of(um, dois, tres)).allSatisfy(card -> {
            assertThat(card.getArquivadoEm()).isNotNull();
            assertThat(card.getArquivadoPorNome()).isEqualTo("Andressa");
        });
        assertThat(eventosGravados()).hasSize(3).extracting(LiberacaoEventoEntity::getCampo).containsOnly("arquivado");
        verify(eventos, times(3)).publishEvent(any(LiberacaoEvento.Editado.class));
    }

    @Test
    @DisplayName("arquivarFinalizados: coluna vazia devolve 0 e não grava nada")
    void shouldReturnZeroWhenNothingToArchive() {
        when(cardRepository.finalizadosNaoArquivados()).thenReturn(List.of());

        assertThat(service.arquivarFinalizados(analista)).isZero();
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("arquivarFinalizados: só analista")
    void shouldRestrictArchiveAllToAnalysts() {
        assertThatThrownBy(() -> service.arquivarFinalizados(auxiliar)).isInstanceOf(AcessoNegadoException.class);
        verify(cardRepository, never()).finalizadosNaoArquivados();
    }

    // ----------------------------------------------------------- diferencaSacados

    @Test
    @DisplayName("diferencaSacados: sacado novo aparece como '+ nome (documento)'")
    void shouldDescribeAddedSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        String resumo = LiberacaoService.diferencaSacados(
                List.of(sacado(card, SACADO_A, "Sacado A", "10", 0)),
                List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("10")),
                        new DadosSacado(SACADO_B, "Sacado B", new BigDecimal("20"))));

        assertThat(resumo).isEqualTo("+ Sacado B (00.360.305/0001-04)");
    }

    @Test
    @DisplayName("diferencaSacados: sacado removido aparece como '− nome (documento)', ou só o documento sem nome")
    void shouldDescribeRemovedSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        String resumo = LiberacaoService.diferencaSacados(
                List.of(sacado(card, SACADO_A, "Sacado A", "10", 0), sacado(card, SACADO_B, null, "20", 1)),
                List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("10"))));

        assertThat(resumo).isEqualTo("− 00.360.305/0001-04");
    }

    @Test
    @DisplayName("diferencaSacados: valor alterado aparece como 'nome (doc): R$ antes → R$ depois'")
    void shouldDescribeChangedSacadoValue() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        String resumo = LiberacaoService.diferencaSacados(
                List.of(sacado(card, SACADO_A, "Sacado A", "10.00", 0)),
                List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("25.50"))));

        assertThat(semEspacoInsecavel(resumo)).isEqualTo("Sacado A (45.723.174/0001-10): R$ 10,00 → R$ 25,50");
    }

    @Test
    @DisplayName("diferencaSacados: valor que vira nulo mostra '—' e mesmo valor em outra escala não é mudança")
    void shouldHandleNullValuesAndScale() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        String viraNulo = LiberacaoService.diferencaSacados(
                List.of(sacado(card, SACADO_A, "Sacado A", "10.00", 0)),
                List.of(new DadosSacado(SACADO_A, "Sacado A", null)));
        String mesmaEscala = LiberacaoService.diferencaSacados(
                List.of(sacado(card, SACADO_A, "Sacado A", "10.00", 0)),
                List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("10"))));

        assertThat(semEspacoInsecavel(viraNulo)).isEqualTo("Sacado A (45.723.174/0001-10): R$ 10,00 → —");
        assertThat(mesmaEscala).isNull();
    }

    @Test
    @DisplayName("diferencaSacados: mudanças múltiplas são unidas por '; ' (adições, remoções, valores)")
    void shouldJoinMultipleChanges() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        String resumo = LiberacaoService.diferencaSacados(
                List.of(sacado(card, SACADO_A, "Sacado A", "10", 0), sacado(card, SACADO_B, "Sacado B", "20", 1)),
                List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("11")),
                        new DadosSacado(CPF, "Pessoa Física", null)));

        assertThat(semEspacoInsecavel(resumo)).isEqualTo(
                "+ Pessoa Física (529.982.247-25); − Sacado B (00.360.305/0001-04); "
                        + "Sacado A (45.723.174/0001-10): R$ 10,00 → R$ 11,00");
    }

    @Test
    @DisplayName("diferencaSacados: só reordenar vira 'Ordem dos sacados alterada'")
    void shouldDescribeReorderOnly() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        String resumo = LiberacaoService.diferencaSacados(
                List.of(sacado(card, SACADO_A, "Sacado A", "10", 0), sacado(card, SACADO_B, "Sacado B", "20", 1)),
                List.of(new DadosSacado(SACADO_B, "Sacado B", new BigDecimal("20")),
                        new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("10"))));

        assertThat(resumo).isEqualTo("Ordem dos sacados alterada");
    }

    @Test
    @DisplayName("diferencaSacados: listas idênticas (ou ambas vazias) dão null")
    void shouldReturnNullWhenIdentical() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThat(LiberacaoService.diferencaSacados(
                List.of(sacado(card, SACADO_A, "Sacado A", "10", 0)),
                List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("10"))))).isNull();
        assertThat(LiberacaoService.diferencaSacados(List.of(), List.of())).isNull();
    }

    @Test
    @DisplayName("diferencaSacados: só o nome mudar (mesmo documento e valor) não é mudança")
    void shouldIgnoreNameOnlyChange() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThat(LiberacaoService.diferencaSacados(
                List.of(sacado(card, SACADO_A, "Nome Antigo", "10", 0)),
                List.of(new DadosSacado(SACADO_A, "Nome Novo", new BigDecimal("10"))))).isNull();
    }

    // ------------------------------------------------------------------- anexos

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("registrarAnexo: grava o evento ANEXO_ADICIONADO/ANEXO_REMOVIDO com o nome do arquivo e avisa o quadro")
    void shouldRecordAttachmentEvents(boolean adicionado) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        service.registrarAnexo(card, adicionado, "Contrato Social.pdf", auxiliar);

        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(adicionado ? TipoEventoLiberacao.ANEXO_ADICIONADO : TipoEventoLiberacao.ANEXO_REMOVIDO);
        assertThat(evento.getCardId()).isEqualTo(card.getId());
        assertThat(evento.getTexto()).isEqualTo("Contrato Social.pdf");
        assertThat(evento.getUsuarioId()).isEqualTo(auxiliar.getId());
        assertThat(evento.getUsuarioNome()).isEqualTo("Auxiliar");
        assertThat(evento.getEtapaDe()).isNull();
        assertThat(evento.getEtapaPara()).isNull();
        ArgumentCaptor<Object> publicado = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(publicado.capture());
        assertThat(publicado.getValue()).isInstanceOfSatisfying(LiberacaoEvento.Editado.class,
                editado -> assertThat(editado.mencionados()).isEmpty());
        // anexo não passa pela trava de versão do card
        verify(cardRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ helpers

    @Test
    @DisplayName("moeda: null vira '—' e valores saem em reais pt-BR")
    void shouldFormatCurrency() {
        assertThat(LiberacaoService.moeda(null)).isEqualTo("—");
        assertThat(semEspacoInsecavel(LiberacaoService.moeda(new BigDecimal("1234567.8")))).isEqualTo("R$ 1.234.567,80");
    }

    @Test
    @DisplayName("normalizarCnpj: tira máscara e exige 14 dígitos")
    void shouldNormalizeCnpj() {
        assertThat(LiberacaoService.normalizarCnpj(CNPJ_MASCARADO)).isEqualTo(CNPJ);
        assertThatThrownBy(() -> LiberacaoService.normalizarCnpj("123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LiberacaoService.normalizarCnpj(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ eventos

    @Test
    @DisplayName("criar: publica Criado com quem foi marcado no parecer, descartando id que não é usuário")
    void shouldPublishCreatedWithExistingMentionsOnly() {
        UserEntity mychelly = usuario("Mychelly");
        UUID fantasma = UUID.randomUUID();
        naBase(CNPJ, "ACME");
        when(userRepository.findAllById(any())).thenReturn(List.of(mychelly));
        String parecer = "Ver com @[Mychelly](user:" + mychelly.getId() + ") e @[Ninguém](user:" + fantasma + ")";

        service.criar(new DadosCard(CNPJ, null, "Duplicata", null, null, parecer, null, null), auxiliar);

        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isInstanceOfSatisfying(LiberacaoEvento.Criado.class, criado -> {
            assertThat(criado.mencionados()).containsExactly(mychelly.getId());
            assertThat(criado.trecho()).isEqualTo("Ver com @Mychelly e @Ninguém");
        });
    }

    @Test
    @DisplayName("excluir: publica Excluido para o quadro dos outros tirar o card")
    void shouldPublishDeleted() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        service.excluir(card.getId(), auxiliar);
        verify(eventos).publishEvent(org.mockito.ArgumentMatchers.isA(LiberacaoEvento.Excluido.class));
    }

    // ------------------------------------------------------------------- resumo

    @Test
    @DisplayName("resumo: precisamAtencao junta atrasados e o que é seu sem contar card repetido")
    void shouldMergeOverdueAndMineWithoutDuplicates() {
        UUID atrasado = UUID.randomUUID();
        UUID atrasadoEMeu = UUID.randomUUID();
        UUID soMeu = UUID.randomUUID();
        when(cardRepository.idsAtrasados(any())).thenReturn(List.of(atrasado, atrasadoEMeu));
        when(parecerRepository.cardsAguardando(analista.getId())).thenReturn(List.of(atrasadoEMeu));
        when(pendenciaRepository.cardsComPendenciaPara(analista.getId())).thenReturn(List.of(soMeu));
        when(parecerRepository.contarAguardando(analista.getId())).thenReturn(1L);
        when(pendenciaRepository.contarAbertasPara(analista.getId())).thenReturn(1L);

        var resumo = service.resumo(analista);

        assertThat(resumo).containsEntry("atrasados", 2L)
                .containsEntry("total", 2L)
                .containsEntry("precisamAtencao", 3L);
    }
}
