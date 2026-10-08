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
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.OrigemMembro;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import com.portal.serasa.domain.model.liberacao.ResultadoLiberacao;
import com.portal.serasa.domain.model.liberacao.TipoEventoLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEventoEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMembroEntity;
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

    private UserEntity auxiliar;
    private UserEntity analista;

    @BeforeEach
    void setUp() {
        when(companyDetailRepository.findByDocumentNumberIn(anyCollection())).thenAnswer(inv -> {
            java.util.Collection<String> docs = inv.getArgument(0);
            return docs.stream().map(base::get).filter(java.util.Objects::nonNull).toList();
        });
        EmpresaResolver resolverReal = new EmpresaResolver(companyDetailRepository, clientRepository);
        when(empresaResolver.resolver(anyCollection())).thenAnswer(inv -> resolverReal.resolver(inv.getArgument(0)));
        auxiliar = UserEntity.builder().id(UUID.randomUUID()).name("Auxiliar").email("aux@jgm.com").build();
        analista = UserEntity.builder().id(UUID.randomUUID()).name("Andressa").email("andressa@jgm.com")
                .analista(true).build();

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
    @DisplayName("editar: card inexistente ou apagado é EntityNotFound")
    void shouldFailWhenCardDoesNotExist() {
        UUID inexistente = UUID.randomUUID();
        when(cardRepository.findByIdAndExcluidoEmIsNull(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editar(inexistente, 1L, dadosNovos(CNPJ, "X", null), auxiliar))
                .isInstanceOf(EntityNotFoundException.class);
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

    private void sacadosSao(LiberacaoCardEntity card, LiberacaoSacadoEntity... sacados) {
        when(sacadoRepository.findByCardIdOrderByOrdem(card.getId())).thenReturn(List.of(sacados));
    }

    @ParameterizedTest
    @EnumSource(value = EtapaLiberacao.class, names = {"PENDENCIA", "FINALIZADO"})
    @DisplayName("transicionar Comitê→Pendência/Finalizado: sem nenhum parecer registrado é TransicaoInvalida e nada é gravado")
    void shouldBlockLeavingComiteWithoutAnyParecer(EtapaLiberacao destino) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), null), parecer(card, usuario("Mychelly"), null));
        sacadosSao(card, decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, destino, null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Precisa de pelo menos um parecer do Comitê.");
        verify(cardRepository, never()).saveAndFlush(any());
        verify(eventoRepository, never()).save(any());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(card.getFinalizadoEm()).isNull();
    }

    @Test
    @DisplayName("transicionar Comitê→Finalizado: rodada sem nenhuma linha de parecer também é bloqueada")
    void shouldBlockLeavingComiteWhenRodadaHasNoRows() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, List.of(), ResultadoLiberacao.APROVADO, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Precisa de pelo menos um parecer do Comitê.");
    }

    @Test
    @DisplayName("transicionar Comitê→Finalizado: com 1 de 2 pareceres libera e o histórico diz quem faltou")
    void shouldLeaveComiteWithOneOfTwoPareceresAndRecordWhoWasMissing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.FINALIZADO, null, null, List.of(), ResultadoLiberacao.APROVADO, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(ultimoEvento().getTexto()).isEqualTo("Movido sem o parecer de Mychelly.");
    }

    @Test
    @DisplayName("transicionar Comitê→Finalizado: vários pareceres faltando são unidos por ' e '")
    void shouldListEveryMissingParecerInTheHistory() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), null), parecer(card, usuario("Carla"), null));

        service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, List.of(), ResultadoLiberacao.REPROVADO, analista);

        assertThat(ultimoEvento().getTexto()).isEqualTo("Movido sem o parecer de Mychelly e Carla.");
    }

    @Test
    @DisplayName("transicionar Comitê→Finalizado: o aviso de parecer faltando vem depois da observação")
    void shouldAppendMissingParecerNoticeAfterObservation() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), null));

        service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, "  Cliente com urgência  ", List.of(), ResultadoLiberacao.APROVADO, analista);

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
    @DisplayName("transicionar Comitê→Finalizado: com todos os pareceres registrados não há aviso")
    void shouldNotRecordNoticeWhenAllPareceresAreIn() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), PosicaoParecer.COM_RESSALVAS));

        service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, List.of(), ResultadoLiberacao.APROVADO, analista);

        assertThat(ultimoEvento().getTexto()).isNull();
    }

    @Test
    @DisplayName("transicionar Comitê→Finalizado: parecer de usuário removido (usuarioId nulo) não conta como faltando")
    void shouldNotCountParecerOfRemovedUserAsMissing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, null, null), parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.FINALIZADO, null, null, List.of(), ResultadoLiberacao.APROVADO, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(ultimoEvento().getTexto()).isNull();
    }

    @Test
    @DisplayName("transicionar Pendência→Finalizado: não pede parecer nem avisa de parecer faltando")
    void shouldFinalizeFromPendenciaWithoutPareceres() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        rodadaTem(card, parecer(card, usuario("Mychelly"), null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.PENDENCIA,
                EtapaLiberacao.FINALIZADO, null, null, List.of(), ResultadoLiberacao.APROVADO, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.FINALIZADO);
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
        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.APROVADO, null)), null, auxiliar))
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
    @DisplayName("finalizar: sacado sem decisão bloqueia, a mensagem lista quem falta e nada é gravado")
    void shouldBlockFinalizeWhileASacadoIsUndecided() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, null, null));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Decida todos os sacados antes de finalizar. Falta: Sacado B (00.360.305/0001-04).");
        verify(cardRepository, never()).saveAndFlush(any());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(card.getResultado()).isNull();
        assertThat(card.getFinalizadoEm()).isNull();
    }

    @Test
    @DisplayName("finalizar: vários sacados sem decisão são listados, e o sem nome aparece só pelo documento")
    void shouldListEveryUndecidedSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                decidido(card, SACADO_A, "Sacado A", "1000", 0, null, null),
                decidido(card, SACADO_B, null, "500", 1, null, null),
                decidido(card, CPF, "Pessoa Física", "300", 2, ResultadoLiberacao.REPROVADO, null));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Decida todos os sacados antes de finalizar. Falta: "
                        + "Sacado A (45.723.174/0001-10), 00.360.305/0001-04.");
    }

    @Test
    @DisplayName("finalizar: decisão do pedido que cobre o sacado que faltava libera a finalização")
    void shouldFinalizeWhenRequestDecidesTheMissingSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity b = decidido(card, SACADO_B, "Sacado B", "500", 1, null, null);
        sacadosSao(card, decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null), b);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, List.of(new DecisaoSacado(SACADO_B, ResultadoLiberacao.APROVADO, null)), null, analista);

        assertThat(b.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
    }

    @Test
    @DisplayName("finalizar: card sem sacados exige APROVADO ou REPROVADO; ausente ou PARCIAL é recusado")
    void shouldRequireAprovadoOrReprovadoForSacadoLessCard() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Card sem sacados: informe se foi aprovado ou reprovado.");
        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, List.of(), null, analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Card sem sacados: informe se foi aprovado ou reprovado.");
        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, List.of(), ResultadoLiberacao.PARCIAL, analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Card sem sacados: informe se foi aprovado ou reprovado.");
        verify(cardRepository, never()).saveAndFlush(any());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
    }

    @ParameterizedTest
    @EnumSource(value = ResultadoLiberacao.class, names = {"APROVADO", "REPROVADO"})
    @DisplayName("finalizar: card sem sacados usa o resultado informado")
    void shouldUseInformedResultadoForSacadoLessCard(ResultadoLiberacao informado) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.FINALIZADO, null, null, List.of(), informado, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(movido.getResultado()).isEqualTo(informado);
        assertThat(ultimoEvento().getValorDepois()).isEqualTo(informado.rotulo());
    }

    @Test
    @DisplayName("finalizar: com sacados, o resultado vem deles e o informado para card sem sacados é ignorado")
    void shouldIgnoreInformedResultadoWhenThereAreSacados() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card, decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.FINALIZADO, null, null, List.of(), ResultadoLiberacao.APROVADO, analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.REPROVADO);
    }

    @Test
    @DisplayName("finalizar: todos os sacados aprovados = APROVADO")
    void shouldComputeAprovadoWhenEverySacadoIsApproved() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.APROVADO, null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.FINALIZADO, null, null, analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
    }

    @Test
    @DisplayName("finalizar: todos os sacados reprovados = REPROVADO")
    void shouldComputeReprovadoWhenEverySacadoIsRejected() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.FINALIZADO, null, null, analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.REPROVADO);
    }

    @Test
    @DisplayName("finalizar: aprovado e reprovado misturados = PARCIAL")
    void shouldComputeParcialWhenSacadosAreMixed() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.FINALIZADO, null, null, analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);
    }

    @Test
    @DisplayName("finalizar: um sacado parcial faz o card ser PARCIAL, mesmo com os outros aprovados")
    void shouldComputeParcialWhenASacadoIsPartial() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, "200"));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.FINALIZADO, null, null, analista);

        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);
    }

    @Test
    @DisplayName("finalizar: as decisões do pedido são aplicadas antes de calcular o resultado, uma por sacado, com quem e quando")
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
                EtapaLiberacao.FINALIZADO, null, null, decisoes, null, analista);

        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(b.getSituacao()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(a.getSituacaoPorNome()).isEqualTo("Andressa");
        assertThat(a.getSituacaoEm()).isNotNull().isAfterOrEqualTo(antes);
        verify(sacadoRepository).save(a);
        verify(sacadoRepository).save(b);
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);

        List<LiberacaoEventoEntity> eventos = eventosGravados();
        assertThat(eventos).extracting(LiberacaoEventoEntity::getTipo)
                .containsExactly(TipoEventoLiberacao.EDICAO, TipoEventoLiberacao.EDICAO, TipoEventoLiberacao.TRANSICAO);
        assertThat(eventos.get(0).getCampo()).isEqualTo("situacaoSacado");
        assertThat(eventos.get(0).getValorAntes()).isEqualTo("a decidir");
        assertThat(eventos.get(0).getValorDepois()).isEqualTo("Aprovado");
        assertThat(eventos.get(0).getTexto()).isEqualTo("Sacado A (45.723.174/0001-10)");
        assertThat(eventos.get(1).getValorDepois()).isEqualTo("Reprovado");
        assertThat(eventos.get(1).getTexto()).isEqualTo("Sacado B (00.360.305/0001-04)");
    }

    @Test
    @DisplayName("finalizar: o pedido pode mudar uma decisão que já existia")
    void shouldLetRequestOverrideAnExistingDecision() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.REPROVADO, null);
        sacadosSao(card, a);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.APROVADO, null)), null, analista);

        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getSituacaoPorNome()).isEqualTo("Andressa");
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(eventosGravados().get(0).getValorAntes()).isEqualTo("Reprovado");
    }

    @Test
    @DisplayName("finalizar: decisão parcial no pedido guarda o valor aprovado e o evento mostra o valor")
    void shouldStorePartialValueFromRequest() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.PARCIAL, new BigDecimal("400"))),
                null, analista);

        assertThat(a.getValorAprovado()).isEqualByComparingTo("400");
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(semEspacoInsecavel(eventosGravados().get(0).getValorDepois())).isEqualTo("Parcial (R$ 400,00)");
    }

    @Test
    @DisplayName("finalizar: decisão parcial inválida no pedido derruba a finalização inteira")
    void shouldRejectInvalidPartialInRequest() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.PARCIAL, null)), null, analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parcial: informe o valor aprovado");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("finalizar: decisão para documento que não é sacado do card é EntityNotFound")
    void shouldRejectDecisionForUnknownSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO,
                null, null, List.of(new DecisaoSacado(SACADO_B, ResultadoLiberacao.APROVADO, null)), null, analista))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Sacado 00.360.305/0001-04 não está neste card.");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("finalizar: o evento de transição tem campo 'resultado' e o rótulo do resultado em valorDepois")
    void shouldRecordResultadoInTransitionEvent() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card,
                decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null),
                decidido(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.REPROVADO, null));

        service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO, null, null, analista);

        LiberacaoEventoEntity evento = ultimoEvento();
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.TRANSICAO);
        assertThat(evento.getEtapaDe()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(evento.getEtapaPara()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(evento.getCampo()).isEqualTo("resultado");
        assertThat(evento.getValorAntes()).isNull();
        assertThat(evento.getValorDepois()).isEqualTo("Parcialmente aprovado");
    }

    @Test
    @DisplayName("finalizar: marca finalizadoEm, grava o resultado no card e publica o evento já com o resultado")
    void shouldSetFinalizadoEmAndPublishMovidoWithResultado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        sacadosSao(card, decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));
        LocalDateTime antes = LocalDateTime.now();

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.FINALIZADO, null, null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.FINALIZADO);
        assertThat(movido.getFinalizadoEm()).isNotNull().isAfterOrEqualTo(antes);
        assertThat(movido.getEtapaDesde()).isEqualTo(movido.getFinalizadoEm());
        assertThat(movido.getRodada()).isEqualTo(1);
        assertThat(movido.getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isInstanceOfSatisfying(LiberacaoEvento.Movido.class, movidoEvento -> {
            assertThat(movidoEvento.para()).isEqualTo(EtapaLiberacao.FINALIZADO);
            assertThat(movidoEvento.card().getResultado()).isEqualTo(ResultadoLiberacao.APROVADO);
        });
    }

    @Test
    @DisplayName("transicionar: decisão de sacado ou resultado só são aceitos quando o destino é Finalizado")
    void shouldRejectDecisionsWhenDestinationIsNotFinalizado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        comUmParecer(card);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);
        when(pendenciaRepository.countByCardIdAndRespondidaEmIsNull(card.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.APROVADO, null)), null, analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Decisão de sacado só ao finalizar");
        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA,
                null, null, List.of(), ResultadoLiberacao.APROVADO, analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Decisão de sacado só ao finalizar");
        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.ORIGEM,
                null, null, List.of(new DecisaoSacado(SACADO_A, ResultadoLiberacao.REPROVADO, null)), null, analista))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cardRepository, never()).saveAndFlush(any());
        verify(sacadoRepository, never()).save(any());
        assertThat(a.getSituacao()).isNull();
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
    }

    @Test
    @DisplayName("transicionar: lista de decisões vazia e resultado ausente valem para qualquer destino")
    void shouldAcceptEmptyDecisionsForAnyDestination() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.ORIGEM, null, null, List.of(), null, analista);
        LiberacaoCardEntity semLista = service.transicionar(card.getId(), EtapaLiberacao.ORIGEM,
                EtapaLiberacao.COMITE, null, null, null, null, auxiliar);

        assertThat(movido.getResultado()).isNull();
        assertThat(semLista.getResultado()).isNull();
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
        sacadosSao(card, a);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        service.transicionar(card.getId(), EtapaLiberacao.FINALIZADO, EtapaLiberacao.COMITE, null, null, analista);

        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
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
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.PENDENCIA,
                EtapaLiberacao.COMITE, null, null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(movido.getRodada()).isEqualTo(1);
        verify(parecerRepository, never()).save(any());
        assertThat(eventosGravados().get(0).getTipo()).isEqualTo(TipoEventoLiberacao.TRANSICAO);
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
    @DisplayName("decidirSacado: analista decide no Comitê ou em Pendência; grava quem, quando e o evento")
    void shouldLetAnalystDecideSacadoInComiteOrPendencia(EtapaLiberacao etapa) {
        LiberacaoCardEntity card = card(etapa);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);
        LocalDateTime antes = LocalDateTime.now();

        LiberacaoCardEntity atualizado = service.decidirSacado(card.getId(),
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), analista);

        assertThat(atualizado).isSameAs(card);
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getValorAprovado()).isNull();
        assertThat(a.getSituacaoPorNome()).isEqualTo("Andressa");
        assertThat(a.getSituacaoEm()).isNotNull().isAfterOrEqualTo(antes);
        verify(sacadoRepository).save(a);
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.EDICAO);
        assertThat(evento.getCardId()).isEqualTo(card.getId());
        assertThat(evento.getCampo()).isEqualTo("situacaoSacado");
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

        service.decidirSacado(card.getId(), decisao(SACADO_A, ResultadoLiberacao.REPROVADO, null), analista);

        ArgumentCaptor<Object> publicado = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(publicado.capture());
        assertThat(publicado.getValue()).isInstanceOfSatisfying(LiberacaoEvento.Editado.class, editado -> {
            assertThat(editado.autor()).isSameAs(analista);
            assertThat(editado.mencionados()).isEmpty();
        });
    }

    @Test
    @DisplayName("decidirSacado: não-analista é AcessoNegado e nada é gravado")
    void shouldDenyNonAnalystDecidingSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.decidirSacado(card.getId(),
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), auxiliar))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Só analista pode decidir sacado.");
        verify(sacadoRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
        verify(cardRepository, never()).tocarSemVersao(any(), any(), any(), any());
    }

    @Test
    @DisplayName("decidirSacado: sem usuário autenticado é AcessoNegado")
    void shouldDenyUnauthenticatedDecidingSacado() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> service.decidirSacado(card.getId(),
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), null))
                .isInstanceOf(AcessoNegadoException.class);
        verify(sacadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("decidirSacado: card na Origem ainda não tem decisão")
    void shouldRejectDecidingSacadoOnOrigem() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.decidirSacado(card.getId(),
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Sacado é decidido a partir do Comitê.");
        verify(sacadoRepository, never()).save(any());
        verify(cardRepository, never()).tocarSemVersao(any(), any(), any(), any());
    }

    @Test
    @DisplayName("decidirSacado: card finalizado pede reabertura antes de mudar a decisão")
    void shouldRejectDecidingSacadoOfFinalizedCard() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.decidirSacado(card.getId(),
                decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Card finalizado: reabra no Comitê para mudar a decisão.");
        verify(sacadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("decidirSacado: documento que não é sacado do card é EntityNotFound; máscara é aceita")
    void shouldFindSacadoByNormalizedDocument() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.decidirSacado(card.getId(),
                decisao(SACADO_B, ResultadoLiberacao.APROVADO, null), analista))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Sacado 00.360.305/0001-04 não está neste card.");
        assertThatThrownBy(() -> service.decidirSacado(card.getId(),
                decisao(null, ResultadoLiberacao.APROVADO, null), analista))
                .isInstanceOf(EntityNotFoundException.class);

        service.decidirSacado(card.getId(), decisao("45.723.174/0001-10", ResultadoLiberacao.APROVADO, null), analista);
        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.00"})
    @DisplayName("decidirSacado: parcial sem valor aprovado positivo é recusado")
    void shouldRejectPartialWithoutPositiveValue(String valor) {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        assertThatThrownBy(() -> service.decidirSacado(card.getId(),
                decisao(SACADO_A, ResultadoLiberacao.PARCIAL, valor), analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parcial: informe o valor aprovado de Sacado A (45.723.174/0001-10).");
        assertThat(a.getSituacao()).isNull();
        verify(sacadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("decidirSacado: parcial sem valor nenhum é recusado")
    void shouldRejectPartialWithMissingValue() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        sacadosSao(card, sacado(card, SACADO_A, "Sacado A", "1000", 0));

        assertThatThrownBy(() -> service.decidirSacado(card.getId(),
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

        assertThatThrownBy(() -> service.decidirSacado(card.getId(),
                decisao(SACADO_A, ResultadoLiberacao.PARCIAL, valor), analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Parcial: o valor aprovado precisa ser menor que R$")
                .hasMessageEndingWith("1.000,00.");
        assertThat(a.getSituacao()).isNull();
        verify(sacadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("decidirSacado: parcial com valor menor que o do sacado guarda o valor e o evento mostra quanto")
    void shouldStorePartialValue() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), decisao(SACADO_A, ResultadoLiberacao.PARCIAL, "400"), analista);

        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.PARCIAL);
        assertThat(a.getValorAprovado()).isEqualByComparingTo("400");
        assertThat(semEspacoInsecavel(eventosGravados().get(0).getValorDepois())).isEqualTo("Parcial (R$ 400,00)");
    }

    @Test
    @DisplayName("decidirSacado: um centavo abaixo do valor do sacado ainda é parcial válido")
    void shouldAcceptPartialJustBelowSacadoValue() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", "1000", 0);
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), decisao(SACADO_A, ResultadoLiberacao.PARCIAL, "999.99"), analista);

        assertThat(a.getValorAprovado()).isEqualByComparingTo("999.99");
    }

    @Test
    @DisplayName("decidirSacado: sacado sem valor informado aceita qualquer parcial positivo")
    void shouldAcceptPartialWhenSacadoValueIsUnknown() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = sacado(card, SACADO_A, "Sacado A", null, 0);
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), decisao(SACADO_A, ResultadoLiberacao.PARCIAL, "250"), analista);

        assertThat(a.getValorAprovado()).isEqualByComparingTo("250");
    }

    @Test
    @DisplayName("decidirSacado: mudar a decisão registra a anterior no evento")
    void shouldRecordPreviousDecisionWhenChangingIt() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), decisao(SACADO_A, ResultadoLiberacao.REPROVADO, null), analista);

        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(a.getSituacaoPorNome()).isEqualTo("Andressa");
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getValorAntes()).isEqualTo("Aprovado");
        assertThat(evento.getValorDepois()).isEqualTo("Reprovado");
    }

    @Test
    @DisplayName("decidirSacado: trocar de parcial para aprovado zera o valor aprovado")
    void shouldClearPartialValueWhenSwitchingAway() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.PARCIAL, "400");
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), analista);

        assertThat(a.getSituacao()).isEqualTo(ResultadoLiberacao.APROVADO);
        assertThat(a.getValorAprovado()).isNull();
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

        service.decidirSacado(card.getId(), decisao(SACADO_A, ResultadoLiberacao.APROVADO, "300"), analista);

        assertThat(a.getValorAprovado()).isNull();
    }

    @Test
    @DisplayName("decidirSacado: repetir a mesma decisão não regrava o sacado, não cria evento nem mexe no card")
    void shouldNotRecordAnythingWhenDecisionDoesNotChange() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        LiberacaoSacadoEntity b = decidido(card, SACADO_B, "Sacado B", "500", 1, ResultadoLiberacao.PARCIAL, "200");
        sacadosSao(card, a, b);

        service.decidirSacado(card.getId(), decisao(SACADO_A, ResultadoLiberacao.APROVADO, null), analista);
        // 200.00 e 200 são o mesmo valor
        service.decidirSacado(card.getId(), decisao(SACADO_B, ResultadoLiberacao.PARCIAL, "200.00"), analista);

        verify(sacadoRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
        // clique duplo não troca "última alteração por" nem avisa o quadro dos outros
        verify(cardRepository, never()).tocarSemVersao(any(), any(), any(), any());
        verify(eventos, never()).publishEvent(any());
        assertThat(a.getSituacaoPorNome()).isEqualTo("Mychelly");
        assertThat(b.getSituacaoPorNome()).isEqualTo("Mychelly");
    }

    @Test
    @DisplayName("decidirSacado: situação nula devolve o sacado para 'a decidir', sem quem nem quando")
    void shouldReturnSacadoToUndecided() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        LiberacaoSacadoEntity a = decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null);
        sacadosSao(card, a);

        service.decidirSacado(card.getId(), decisao(SACADO_A, null, null), analista);

        assertThat(a.getSituacao()).isNull();
        assertThat(a.getValorAprovado()).isNull();
        assertThat(a.getSituacaoPorNome()).isNull();
        assertThat(a.getSituacaoEm()).isNull();
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

        service.decidirSacado(card.getId(), decisao(SACADO_B, ResultadoLiberacao.REPROVADO, null), analista);

        assertThat(a.getSituacao()).isNull();
        assertThat(b.getSituacao()).isEqualTo(ResultadoLiberacao.REPROVADO);
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(card.getResultado()).isNull();
        verify(sacadoRepository, times(1)).save(any());
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

    @Test
    @DisplayName("editar: card finalizado não troca sacados — o resultado sairia dos sacados antigos")
    void shouldRejectSacadoChangesOnFinalizedCard() {
        LiberacaoCardEntity card = card(EtapaLiberacao.FINALIZADO);
        sacadosSao(card, decidido(card, SACADO_A, "Sacado A", "1000", 0, ResultadoLiberacao.APROVADO, null));
        DadosCard dados = new DadosCard(CNPJ, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null,
                List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("1000")),
                        new DadosSacado(SACADO_B, "Sacado B", new BigDecimal("500"))));

        assertThatThrownBy(() -> service.editar(card.getId(), card.getVersion(), dados, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("reabra no Comitê");
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
    @DisplayName("listarQuadro usa o corte informado, ou 30 dias para trás quando ausente")
    void shouldUseGivenOrDefaultCutoffForBoard() {
        LocalDateTime corte = LocalDateTime.of(2026, 9, 1, 0, 0);
        service.listarQuadro(corte);
        verify(cardRepository).listarQuadro(corte);

        LocalDateTime antes = LocalDateTime.now().minusDays(LiberacaoService.DIAS_FINALIZADOS_NO_QUADRO);
        service.listarQuadro(null);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cardRepository, times(2)).listarQuadro(captor.capture());
        assertThat(captor.getAllValues().get(1))
                .isAfterOrEqualTo(antes)
                .isBeforeOrEqualTo(LocalDateTime.now().minusDays(LiberacaoService.DIAS_FINALIZADOS_NO_QUADRO));
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
