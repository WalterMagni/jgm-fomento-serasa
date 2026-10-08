package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.application.service.liberacao.LiberacaoService.DadosCard;
import com.portal.serasa.application.service.liberacao.LiberacaoService.DadosSacado;
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
    @Spy private LiberacaoAutorizacao autorizacao = new LiberacaoAutorizacao();

    @Mock private ApplicationEventPublisher eventos;

    @InjectMocks private LiberacaoService service;

    private UserEntity auxiliar;
    private UserEntity analista;

    @BeforeEach
    void setUp() {
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
                card.getPrazo(), card.getParecerOrigem(), null);
    }

    private DadosCard dadosNovos(String cnpj, String nome, List<DadosSacado> sacados) {
        return new DadosCard(cnpj, nome, "Duplicata", new BigDecimal("5000"),
                LocalDateTime.of(2026, 11, 1, 10, 0), "  Parecer da origem  ", sacados);
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
        when(companyDetailRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.of(empresa(CNPJ, "ACME DA BASE S/A")));

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
        when(companyDetailRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.empty());

        LiberacaoCardEntity criado = service.criar(dadosNovos(CNPJ, "  Empresa Nova Ltda  ", null), auxiliar);

        assertThat(criado.getCedenteNome()).isEqualTo("Empresa Nova Ltda");
    }

    @Test
    @DisplayName("criar: base com razão social em branco cai no nome informado")
    void shouldFallBackToInformedNameWhenBaseNameIsBlank() {
        when(companyDetailRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.of(empresa(CNPJ, "  ")));

        LiberacaoCardEntity criado = service.criar(dadosNovos(CNPJ, "Nome informado", null), auxiliar);

        assertThat(criado.getCedenteNome()).isEqualTo("Nome informado");
    }

    @Test
    @DisplayName("criar: CNPJ fora da base e sem nome informado é recusado")
    void shouldRejectCnpjNotInBaseWithoutName() {
        when(companyDetailRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.empty());

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
        when(companyDetailRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.of(empresa(CNPJ, "ACME")));
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
        when(companyDetailRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.of(empresa(CNPJ, "ACME")));
        List<DadosSacado> sacados = List.of(new DadosSacado("529.982.247-25", null, new BigDecimal("300")));

        service.criar(dadosNovos(CNPJ, null, sacados), auxiliar);

        List<LiberacaoSacadoEntity> gravados = sacadosGravados();
        assertThat(gravados).hasSize(1);
        assertThat(gravados.get(0).getCnpj()).isEqualTo(CPF);
        assertThat(gravados.get(0).getNome()).isNull();
        assertThat(gravados.get(0).getOrdem()).isZero();
        verify(companyDetailRepository, never()).findByDocumentNumberIn(anyCollection());
    }

    @Test
    @DisplayName("criar: sacado com tamanho de documento inválido é recusado")
    void shouldRejectSacadoWithInvalidDocument() {
        when(companyDetailRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.of(empresa(CNPJ, "ACME")));
        List<DadosSacado> sacados = List.of(new DadosSacado("1234567890", "Curto", null));

        assertThatThrownBy(() -> service.criar(dadosNovos(CNPJ, null, sacados), auxiliar))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Documento de sacado inválido");
    }

    @Test
    @DisplayName("criar: linhas vazias de sacado são descartadas, o nome vem da base e a ordem é preservada")
    void shouldDropBlankSacadoRowsAndCompleteNamesFromBase() {
        when(companyDetailRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.of(empresa(CNPJ, "ACME")));
        when(companyDetailRepository.findByDocumentNumberIn(anyCollection()))
                .thenReturn(List.of(empresa(SACADO_B, "SEGUNDO SACADO S/A")));
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
        verify(companyDetailRepository).findByDocumentNumberIn(List.of(SACADO_B));
    }

    @Test
    @DisplayName("criar: o criador entra como membro CRIADOR")
    void shouldAddCreatorAsMember() {
        when(companyDetailRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.of(empresa(CNPJ, "ACME")));

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
        when(companyDetailRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.of(empresa(CNPJ, "ACME")));

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
                card.getParecerOrigem(), null);

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
                card.getPrazo(), card.getParecerOrigem(), null);

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
                card.getPrazo(), card.getParecerOrigem(), null);

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
                LocalDateTime.of(2026, 12, 31, 18, 0), null, null);

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
        when(companyDetailRepository.findByDocumentNumber(novoCnpj)).thenReturn(Optional.of(empresa(novoCnpj, "NOVA EMPRESA")));
        DadosCard dados = new DadosCard(novoCnpj, null, card.getTipoOperacao(), card.getValor(), card.getPrazo(),
                card.getParecerOrigem(), null);

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
                card.getPrazo(), card.getParecerOrigem(), null);

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
                card.getParecerOrigem(), List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("10"))));

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
                card.getParecerOrigem(), List.of(new DadosSacado(SACADO_A, "Sacado A", new BigDecimal("10.00"))));

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

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.PENDENCIA, EtapaLiberacao.APROVADO,
                List.of(new NovaPendencia(UUID.randomUUID(), "Algo")), null, analista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pendência só é aberta ao mover para Pendência");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("transicionar: caminho que a máquina de estados não permite é recusado")
    void shouldRejectInvalidPath() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.ORIGEM, EtapaLiberacao.APROVADO,
                null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageStartingWith("Não dá para ir");
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("transicionar Comitê→Aprovado: bloqueado enquanto há parecer com posição nula e usuário presente")
    void shouldBlockDecisionWhileAParecerIsMissing() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        UserEntity mychelly = usuario("Mychelly");
        UserEntity bruna = usuario("Bruna");
        rodadaTem(card, parecer(card, bruna, PosicaoParecer.FAVORAVEL), parecer(card, mychelly, null));

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO,
                null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Aguardando parecer de Mychelly.");
        verify(cardRepository, never()).saveAndFlush(any());
        assertThat(card.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(card.getFinalizadoEm()).isNull();
    }

    @Test
    @DisplayName("transicionar Comitê→Aprovado: parecer de usuário removido (usuarioId nulo) não trava")
    void shouldNotBlockOnParecerOfRemovedUser() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, null, null), parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
    }

    @Test
    @DisplayName("transicionar Comitê→Aprovado: com todos os pareceres registrados passa e marca finalizadoEm")
    void shouldApproveAndSetFinalizadoEmWhenAllPareceresAreIn() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.FAVORAVEL),
                parecer(card, usuario("Mychelly"), PosicaoParecer.COM_RESSALVAS));
        LocalDateTime antes = LocalDateTime.now();

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.APROVADO, null, null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(movido.getFinalizadoEm()).isNotNull().isAfterOrEqualTo(antes);
        assertThat(movido.getEtapaDesde()).isEqualTo(movido.getFinalizadoEm());
        assertThat(movido.getRodada()).isEqualTo(1);
        assertThat(eventosGravados().get(0).getTipo()).isEqualTo(TipoEventoLiberacao.TRANSICAO);
    }

    @Test
    @DisplayName("transicionar Comitê→Reprovado: também marca finalizadoEm")
    void shouldSetFinalizadoEmOnRejection() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
        rodadaTem(card, parecer(card, usuario("Bruna"), PosicaoParecer.DESFAVORAVEL));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.COMITE,
                EtapaLiberacao.REPROVADO, null, null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.REPROVADO);
        assertThat(movido.getFinalizadoEm()).isNotNull();
    }

    @Test
    @DisplayName("transicionar: não-analista não move do Comitê em diante")
    void shouldDenyNonAnalystMovingCardFromComite() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.ORIGEM,
                null, null, auxiliar))
                .isInstanceOf(AcessoNegadoException.class);
        verify(parecerRepository, never()).apagarAguardando(any(), anyInt());
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
        assertThat(evento.getTexto()).isEqualTo("Falta anexar o contrato");
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

    @Test
    @DisplayName("transicionar Aprovado→Comitê: reabertura soma rodada, limpa finalizadoEm, registra REABERTURA e convoca o Comitê")
    void shouldReopenFinalizedCardIntoNewRodada() {
        LiberacaoCardEntity card = card(EtapaLiberacao.APROVADO);
        card.setFinalizadoEm(LocalDateTime.now().minusDays(2));
        UserEntity bruna = usuario("Bruna");
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(bruna));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.APROVADO,
                EtapaLiberacao.COMITE, null, "Cliente trouxe fato novo", analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.COMITE);
        assertThat(movido.getRodada()).isEqualTo(2);
        assertThat(movido.getFinalizadoEm()).isNull();
        LiberacaoEventoEntity evento = eventosGravados().get(0);
        assertThat(evento.getTipo()).isEqualTo(TipoEventoLiberacao.REABERTURA);
        assertThat(evento.getEtapaDe()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(evento.getEtapaPara()).isEqualTo(EtapaLiberacao.COMITE);
        ArgumentCaptor<LiberacaoParecerEntity> pareceres = ArgumentCaptor.forClass(LiberacaoParecerEntity.class);
        verify(parecerRepository).save(pareceres.capture());
        assertThat(pareceres.getValue().getRodada()).isEqualTo(2);
        assertThat(pareceres.getValue().getUsuarioId()).isEqualTo(bruna.getId());
        verify(parecerRepository, never()).apagarAguardando(any(), anyInt());
    }

    @Test
    @DisplayName("transicionar Reprovado→Comitê: também é reabertura")
    void shouldReopenRejectedCardToo() {
        LiberacaoCardEntity card = card(EtapaLiberacao.REPROVADO);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.REPROVADO,
                EtapaLiberacao.COMITE, null, null, analista);

        assertThat(movido.getRodada()).isEqualTo(2);
        assertThat(eventosGravados().get(0).getTipo()).isEqualTo(TipoEventoLiberacao.REABERTURA);
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

    @Test
    @DisplayName("transicionar →Pendência sem pendências novas e sem abertas é TransicaoInvalida")
    void shouldRequirePendenciaWhenMovingToPendencia() {
        LiberacaoCardEntity card = card(EtapaLiberacao.COMITE);
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
        UUID fantasma = UUID.randomUUID();
        when(userRepository.findById(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transicionar(card.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA,
                List.of(new NovaPendencia(fantasma, "Algo")), null, analista))
                .isInstanceOf(EntityNotFoundException.class);

        LiberacaoCardEntity outro = card(EtapaLiberacao.COMITE);
        assertThatThrownBy(() -> service.transicionar(outro.getId(), EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA,
                List.of(new NovaPendencia(UUID.randomUUID(), "  ")), null, analista))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("transicionar Pendência→Aprovado: não espera pareceres e marca finalizadoEm")
    void shouldApproveFromPendenciaWithoutWaitingForPareceres() {
        LiberacaoCardEntity card = card(EtapaLiberacao.PENDENCIA);
        rodadaTem(card, parecer(card, usuario("Mychelly"), null));

        LiberacaoCardEntity movido = service.transicionar(card.getId(), EtapaLiberacao.PENDENCIA,
                EtapaLiberacao.APROVADO, null, null, analista);

        assertThat(movido.getEtapa()).isEqualTo(EtapaLiberacao.APROVADO);
        assertThat(movido.getFinalizadoEm()).isNotNull();
    }

    @Test
    @DisplayName("transicionar: observação em branco vira nula no evento")
    void shouldStoreBlankObservationAsNull() {
        LiberacaoCardEntity card = card(EtapaLiberacao.ORIGEM);
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(usuario("Bruna")));

        service.transicionar(card.getId(), EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, null, "   ", auxiliar);

        assertThat(eventosGravados().get(0).getTexto()).isNull();
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
        when(companyDetailRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.of(empresa(CNPJ, "ACME")));
        when(userRepository.findAllById(any())).thenReturn(List.of(mychelly));
        String parecer = "Ver com @[Mychelly](user:" + mychelly.getId() + ") e @[Ninguém](user:" + fantasma + ")";

        service.criar(new DadosCard(CNPJ, null, "Duplicata", null, null, parecer, null), auxiliar);

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
