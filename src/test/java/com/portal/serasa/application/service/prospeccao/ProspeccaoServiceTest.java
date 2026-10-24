package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.DocumentacaoIncompletaException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.StatusDocumento;
import com.portal.serasa.infrastructure.persistence.entity.DadosComerciais;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEventoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDocumentoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoEventoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProspeccaoServiceTest {

    @Mock private ProspeccaoJpaRepository prospeccaoRepository;
    @Mock private ProspeccaoEventoJpaRepository eventoRepository;
    @Mock private ProspeccaoDocumentoJpaRepository documentoRepository;
    @Mock private ProspeccaoChecklistService checklistService;
    @Mock private org.springframework.context.ApplicationEventPublisher acontecimentos;

    @InjectMocks private ProspeccaoService service;

    private UserEntity analista;

    @BeforeEach
    void setUp() {
        analista = UserEntity.builder().id(UUID.randomUUID()).name("Andressa").build();
        when(prospeccaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProspeccaoEntity card(EstagioProspeccao estagio) {
        ProspeccaoEntity card = ProspeccaoEntity.builder()
                .id(UUID.randomUUID())
                .cnpj("11222333000181")
                .razaoSocial("ACME LTDA")
                .estagio(estagio)
                .estagioDesde(LocalDateTime.now())
                .prazoEstagioHoras(estagio.prazoHorasUteis())
                .reaberturas(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(prospeccaoRepository.findById(card.getId())).thenReturn(Optional.of(card));
        return card;
    }

    @Test
    @DisplayName("criar: recusa segundo card aberto para o mesmo CNPJ")
    void shouldRejectDuplicateOpenCard() {
        ProspeccaoEntity aberta = card(EstagioProspeccao.EM_ANALISE);
        when(prospeccaoRepository.findAberta("11222333000181")).thenReturn(Optional.of(aberta));

        assertThatThrownBy(() -> service.criar("11.222.333/0001-81", "ACME LTDA", null, "Gustavo", null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("EM_ANALISE");
    }

    @Test
    @DisplayName("criar: normaliza o CNPJ, nasce em Nova e já com checklist")
    void shouldNormalizeCnpj() {
        when(prospeccaoRepository.findAberta(any())).thenReturn(Optional.empty());

        ProspeccaoEntity criado = service.criar("11.222.333/0001-81", "ACME LTDA", null, "Gustavo", null, analista);

        assertThat(criado.getCnpj()).isEqualTo("11222333000181");
        assertThat(criado.getEstagio()).isEqualTo(EstagioProspeccao.NOVA);
        // A equipe anexa documento desde o primeiro dia; a trava fica na saída da documentação.
        verify(checklistService).materializar(criado);
    }

    @Test
    @DisplayName("criar: grava os dados comerciais informados na abertura")
    void shouldKeepCommercialDataOnCreate() {
        when(prospeccaoRepository.findAberta(any())).thenReturn(Optional.empty());
        DadosComerciais dados = DadosComerciais.builder()
                .faturamentoMensal(new java.math.BigDecimal("250000"))
                .regiaoAtendimento("Interior de SP")
                .build();

        ProspeccaoEntity criado = service.criar("11222333000181", "ACME LTDA", null, null, dados, analista);

        assertThat(criado.getDadosComerciais()).isEqualTo(dados);
        assertThat(criado.getComercialId()).isNull();
    }

    @Test
    @DisplayName("dados comerciais: card encerrado não aceita edição")
    void shouldRejectCommercialDataOnClosedCard() {
        ProspeccaoEntity card = card(EstagioProspeccao.HABILITADO);

        assertThatThrownBy(() -> service.atualizarDadosComerciais(card.getId(), new DadosComerciais(), analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("reabra");
    }

    @Test
    @DisplayName("dados comerciais: substitui o perfil e registra na timeline")
    void shouldReplaceCommercialData() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);
        DadosComerciais dados = DadosComerciais.builder().ticketMedio(new java.math.BigDecimal("1200")).build();

        ProspeccaoEntity resultado = service.atualizarDadosComerciais(card.getId(), dados, analista);

        assertThat(resultado.getDadosComerciais()).isEqualTo(dados);
        ArgumentCaptor<ProspeccaoEventoEntity> evento = ArgumentCaptor.forClass(ProspeccaoEventoEntity.class);
        verify(eventoRepository).save(evento.capture());
        assertThat(evento.getValue().getTipo())
                .isEqualTo(com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao.DADOS_COMERCIAIS);
    }

    @Test
    @DisplayName("devolver para Nova solta a analista: o card volta para a fila e qualquer uma assume")
    void shouldReleaseAnalystWhenReturnedToNew() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);
        card.setAnalistaId(analista.getId());

        service.transicionar(card.getId(), EstagioProspeccao.NOVA, null, null, analista);

        assertThat(card.getEstagio()).isEqualTo(EstagioProspeccao.NOVA);
        assertThat(card.getAnalistaId()).isNull();
    }

    @Test
    @DisplayName("assumir: card devolvido que ficou com a analista antiga é liberado antes de assumir")
    void shouldReleaseStaleAnalystBeforeAssuming() {
        ProspeccaoEntity card = card(EstagioProspeccao.NOVA);
        card.setAnalistaId(UUID.randomUUID());
        when(prospeccaoRepository.assumirAnalise(any(), any(), anyInt(), any())).thenReturn(1);

        service.assumir(card.getId(), analista);

        var ordem = org.mockito.Mockito.inOrder(prospeccaoRepository);
        ordem.verify(prospeccaoRepository).soltarAnalistaEmNova(card.getId());
        ordem.verify(prospeccaoRepository).assumirAnalise(eq(card.getId()), eq(analista.getId()), anyInt(), any());
    }

    @Test
    @DisplayName("assumir: segunda analista recebe 409 em vez de roubar o card")
    void shouldRejectStealingAnAssignedCard() {
        // Assumido de verdade é card em análise: a liberação de card devolvido não mexe nele.
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);
        card.setAnalistaId(UUID.randomUUID());
        when(prospeccaoRepository.assumirAnalise(any(), any(), anyInt(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.assumir(card.getId(), analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("já assumido");
    }

    @Test
    @DisplayName("transicionar: bloqueia salto de estágio")
    void shouldRejectStageSkip() {
        ProspeccaoEntity card = card(EstagioProspeccao.NOVA);

        assertThatThrownBy(() -> service.transicionar(
                card.getId(), EstagioProspeccao.DOCUMENTACAO, null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("não é permitida");
    }

    @Test
    @DisplayName("transicionar: inerte sem motivo não passa")
    void shouldRequireReasonToReject() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCUMENTACAO);

        assertThatThrownBy(() -> service.transicionar(
                card.getId(), EstagioProspeccao.INERTE, null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("Motivo é obrigatório");
    }

    @Test
    @DisplayName("transicionar: motivo OUTRO exige observação, senão vira caixa-preta")
    void shouldRequireNoteWhenReasonIsOther() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCUMENTACAO);

        assertThatThrownBy(() -> service.transicionar(
                card.getId(), EstagioProspeccao.INERTE, MotivoRecusa.OUTRO, "  ", analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("observação");
    }

    @Test
    @DisplayName("entrar na documentação: completa o checklist com o que o QSA já tiver")
    void shouldCompleteChecklistWhenEnteringDocumentation() {
        ProspeccaoEntity card = card(EstagioProspeccao.APROVADO);

        ProspeccaoEntity resultado = service.mover(
                card.getId(), EstagioProspeccao.DOCUMENTACAO, null, null, analista);

        verify(checklistService).materializar(card);
        assertThat(resultado.getEstagio()).isEqualTo(EstagioProspeccao.DOCUMENTACAO);
        // A coleta de documentos não tem prazo de estágio: quem envia é o cliente.
        assertThat(resultado.getPrazoEstagioHoras()).isZero();
    }

    @Test
    @DisplayName("aprovar na análise leva a Aprovado e completa o checklist para o comercial")
    void shouldMoveToApproved() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);

        ProspeccaoEntity resultado = service.mover(
                card.getId(), EstagioProspeccao.APROVADO, null, null, analista);

        assertThat(resultado.getEstagio()).isEqualTo(EstagioProspeccao.APROVADO);
        verify(checklistService).materializar(card);
    }

    @Test
    @DisplayName("aprovado com todos os obrigatórios entregues segue sozinho para a documentação")
    void shouldAdvanceWhenAllRequiredDelivered() {
        ProspeccaoEntity card = card(EstagioProspeccao.APROVADO);
        ProspeccaoDocumentoEntity dispensado = doc("Curva ABC", true, StatusDocumento.DISPENSADO, null);
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(
                doc("Contrato social", true, StatusDocumento.RECEBIDO, null),
                doc("IRPF", true, StatusDocumento.VALIDADO, "Antonio"),
                dispensado,
                // Opcional pendente não segura o card.
                doc("Foto da fachada", false, StatusDocumento.PENDENTE, null)));

        assertThat(service.seguirParaConferenciaSeCompleto(card.getId(), analista)).isTrue();

        assertThat(card.getEstagio()).isEqualTo(EstagioProspeccao.DOCUMENTACAO);
        verify(acontecimentos).publishEvent(any(ProspeccaoAcontecimento.DocumentosCompletos.class));
    }

    @Test
    @DisplayName("aprovado com obrigatório pendente ou rejeitado fica esperando o comercial")
    void shouldWaitWhileRequiredMissing() {
        ProspeccaoEntity pendente = card(EstagioProspeccao.APROVADO);
        when(documentoRepository.findByProspeccaoId(pendente.getId())).thenReturn(List.of(
                doc("Contrato social", true, StatusDocumento.RECEBIDO, null),
                doc("Curva ABC", true, StatusDocumento.PENDENTE, null)));
        ProspeccaoEntity rejeitado = card(EstagioProspeccao.APROVADO);
        when(documentoRepository.findByProspeccaoId(rejeitado.getId())).thenReturn(List.of(
                doc("IRPF", true, StatusDocumento.REJEITADO, "Antonio")));

        assertThat(service.seguirParaConferenciaSeCompleto(pendente.getId(), analista)).isFalse();
        assertThat(service.seguirParaConferenciaSeCompleto(rejeitado.getId(), analista)).isFalse();
        assertThat(pendente.getEstagio()).isEqualTo(EstagioProspeccao.APROVADO);
        assertThat(rejeitado.getEstagio()).isEqualTo(EstagioProspeccao.APROVADO);
    }

    @Test
    @DisplayName("fora do Aprovado, completar o checklist não move o card")
    void shouldNotAdvanceOutsideApproved() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCUMENTACAO);
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(
                doc("Contrato social", true, StatusDocumento.VALIDADO, null)));

        assertThat(service.seguirParaConferenciaSeCompleto(card.getId(), analista)).isFalse();
        verify(acontecimentos, never()).publishEvent(any(ProspeccaoAcontecimento.DocumentosCompletos.class));
    }

    @Test
    @DisplayName("troca simples não pula a decisão nem a resposta do cliente")
    void shouldRequireDedicatedActions() {
        ProspeccaoEntity emAnalise = card(EstagioProspeccao.EM_ANALISE);
        ProspeccaoEntity aguardando = card(EstagioProspeccao.AGUARDANDO_ACEITE);

        assertThatThrownBy(() -> service.transicionar(emAnalise.getId(), EstagioProspeccao.APROVADO, null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class).hasMessageContaining("decisão");
        assertThatThrownBy(() -> service.transicionar(emAnalise.getId(), EstagioProspeccao.REPROVADO,
                MotivoRecusa.SEGMENTO, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class).hasMessageContaining("decisão");
        assertThatThrownBy(() -> service.transicionar(aguardando.getId(), EstagioProspeccao.CONTRATO, null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class).hasMessageContaining("resposta do cliente");
        // Devolver para Nova e mover para inerte continuam sendo troca simples.
        assertThat(service.transicionar(emAnalise.getId(), EstagioProspeccao.NOVA, null, null, analista).getEstagio())
                .isEqualTo(EstagioProspeccao.NOVA);
    }

    @Test
    @DisplayName("enviar ao comitê: lista o que falta em vez de deixar passar")
    void shouldListMissingRequiredDocuments() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCUMENTACAO);
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(
                doc("Receita Federal", true, StatusDocumento.VALIDADO, null),
                doc("Curva ABC", true, StatusDocumento.PENDENTE, null),
                doc("IRPF", true, StatusDocumento.RECEBIDO, "Antonio")));

        assertThatThrownBy(() -> service.mover(
                card.getId(), EstagioProspeccao.COMITE, null, null, analista))
                .isInstanceOf(DocumentacaoIncompletaException.class)
                .hasMessageContaining("Curva ABC")
                .hasMessageContaining("IRPF (Antonio)");

        verify(prospeccaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("item de exceção liberado com justificativa não trava o fechamento")
    void shouldAllowClosingWithJustifiedException() {
        // Endividamento e curva ABC o cliente pode legitimamente não ter; a autorização SCR ele pode
        // não aceitar assinar. O time libera com motivo, e o card segue.
        ProspeccaoEntity card = card(EstagioProspeccao.DOCUMENTACAO);
        ProspeccaoDocumentoEntity curvaAbc = doc("Curva ABC", true, StatusDocumento.DISPENSADO, null);
        curvaAbc.setAdmiteExcecaoSnapshot(true);
        curvaAbc.setMotivo("Cliente não possui");
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(
                doc("Contrato social", true, StatusDocumento.VALIDADO, null),
                curvaAbc));

        assertThat(service.mover(card.getId(), EstagioProspeccao.COMITE, null, null, analista)
                .getEstagio()).isEqualTo(EstagioProspeccao.COMITE);
    }

    @Test
    @DisplayName("fechar documentos: dispensado e não aplicável não travam o avanço")
    void shouldAllowClosingWithWaivedDocuments() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCUMENTACAO);
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(
                doc("Receita Federal", true, StatusDocumento.VALIDADO, null),
                doc("Certidão simplificada", true, StatusDocumento.NAO_APLICAVEL, null),
                doc("Balanço", true, StatusDocumento.DISPENSADO, null)));

        ProspeccaoEntity resultado = service.mover(
                card.getId(), EstagioProspeccao.COMITE, null, null, analista);

        assertThat(resultado.getEstagio()).isEqualTo(EstagioProspeccao.COMITE);
    }

    @Test
    @DisplayName("fechar documentos: sócio inativo não trava — a planilha registra sócio que saiu ou faleceu")
    void shouldIgnoreInactivePartner() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCUMENTACAO);
        ProspeccaoDocumentoEntity doSocioQueSaiu = doc("IRPF", true, StatusDocumento.PENDENTE, "Paulo");
        doSocioQueSaiu.setSocioAtivo(false);
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(doSocioQueSaiu));

        assertThat(service.mover(card.getId(), EstagioProspeccao.COMITE, null, null, analista)
                .getEstagio()).isEqualTo(EstagioProspeccao.COMITE);
    }

    @Test
    @DisplayName("reabrir: devolve para Nova, conta a reabertura e solta o analista anterior")
    void shouldReopenRejectedCard() {
        ProspeccaoEntity card = card(EstagioProspeccao.REPROVADO);
        card.setAnalistaId(UUID.randomUUID());
        card.setClosedAt(LocalDateTime.now());
        when(prospeccaoRepository.findAberta(card.getCnpj())).thenReturn(Optional.empty());

        ProspeccaoEntity resultado = service.reabrir(card.getId(), "Cliente quitou as restrições", analista);

        assertThat(resultado.getEstagio()).isEqualTo(EstagioProspeccao.NOVA);
        assertThat(resultado.getReaberturas()).isEqualTo(1);
        assertThat(resultado.getAnalistaId()).isNull();
        assertThat(resultado.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("reabrir: card em aberto não é reaberto")
    void shouldNotReopenOpenCard() {
        ProspeccaoEntity card = card(EstagioProspeccao.COMITE);

        assertThatThrownBy(() -> service.reabrir(card.getId(), "voltar", analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    @DisplayName("reabrir: habilitado também volta, para nova análise de limite")
    void shouldReopenEnabledCard() {
        ProspeccaoEntity card = card(EstagioProspeccao.HABILITADO);
        when(prospeccaoRepository.findAberta(card.getCnpj())).thenReturn(Optional.empty());

        assertThat(service.reabrir(card.getId(), "Revisão de limite", analista).getEstagio())
                .isEqualTo(EstagioProspeccao.NOVA);
    }

    @Test
    @DisplayName("silêncio corre também no aguardando aceite")
    void shouldCountSilenceWhileAwaitingAcceptance() {
        ProspeccaoEntity card = card(EstagioProspeccao.AGUARDANDO_ACEITE);
        card.setEstagioDesde(LocalDateTime.now().minusDays(31));

        assertThat(service.silencioEmAtencao(card)).isTrue();
        assertThat(service.diasEmSilencio(card(EstagioProspeccao.EM_ANALISE))).isZero();
    }

    @Test
    @DisplayName("cobrança: zera o relógio de silêncio do card")
    void shouldResetSilenceClockOnFollowUp() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCUMENTACAO);
        card.setEstagioDesde(LocalDateTime.now().minusDays(45));
        assertThat(service.silencioProlongado(card)).isTrue();

        service.registrarCobranca(card.getId(),
                com.portal.serasa.domain.model.prospeccao.CanalContato.WHATSAPP, "Cobrado no zap", analista);

        assertThat(card.getUltimoContatoEm()).isNotNull();
        assertThat(service.silencioProlongado(card)).isFalse();
    }

    @Test
    @DisplayName("prazo de Nova é 24 horas úteis: recém-chegado não está atrasado; parado há duas semanas, está")
    void shouldGiveNewCardsTwentyFourBusinessHours() {
        ProspeccaoEntity novo = card(EstagioProspeccao.NOVA);
        assertThat(novo.getPrazoEstagioHoras()).isEqualTo(24);
        novo.setEstagioDesde(LocalDateTime.now().minusMinutes(5));
        assertThat(service.slaEstourado(novo)).isFalse();
        assertThat(service.slaEmAtencao(novo)).isFalse();

        // Duas semanas têm dez dias úteis, por mais feriados que caiam no meio.
        novo.setEstagioDesde(LocalDateTime.now().minusDays(14));
        assertThat(service.slaEstourado(novo)).isTrue();
    }

    @Test
    @DisplayName("prazo: tempo com pendência aberta não conta, nem a pausa fechada nem a corrente")
    void shouldDiscountPausedHours() {
        // Segunda-feira 9h até quarta 9h: 48 horas úteis corridas no estágio.
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);
        LocalDateTime segunda = LocalDateTime.of(2026, 10, 5, 9, 0);
        card.setEstagioDesde(segunda);
        long semPausa = service.horasNoEstagio(card);

        card.setHorasPausadas(10);
        assertThat(service.horasNoEstagio(card)).isEqualTo(semPausa - 10);

        card.setPausadoDesde(segunda);
        assertThat(service.horasNoEstagio(card)).isZero();
    }

    @Test
    @DisplayName("pausar e retomar: a pausa fechada soma no total do estágio")
    void shouldAccumulatePause() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);
        service.pausar(card);
        assertThat(card.getPausadoDesde()).isNotNull();

        card.setPausadoDesde(LocalDateTime.now().minusDays(3));
        service.retomar(card);
        assertThat(card.getPausadoDesde()).isNull();
        assertThat(card.getHorasPausadas()).isPositive();
    }

    @Test
    @DisplayName("trocar de estágio zera a pausa; com pendência aberta, o novo estágio nasce pausado")
    void shouldResetPauseOnStageChange() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);
        card.setHorasPausadas(7);
        card.setPausadoDesde(LocalDateTime.now().minusDays(2));

        service.mover(card.getId(), EstagioProspeccao.NOVA, null, null, analista);

        assertThat(card.getHorasPausadas()).isZero();
        assertThat(card.getPausadoDesde()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    @DisplayName("abrir manualmente avisa as analistas; entrada automática não")
    void shouldPublishCreationOnlyWhenManual() {
        when(prospeccaoRepository.findAberta(any())).thenReturn(Optional.empty());
        when(prospeccaoRepository.existsByCreditAnalysisId(any())).thenReturn(false);

        service.criar("11222333000181", "ACME", null, null, null, analista);
        service.criarPorAnalise("99888777000166", "BETA", 10L);

        ArgumentCaptor<Object> eventos = ArgumentCaptor.forClass(Object.class);
        verify(acontecimentos, org.mockito.Mockito.atLeastOnce()).publishEvent(eventos.capture());
        assertThat(eventos.getAllValues()).filteredOn(e -> e instanceof ProspeccaoAcontecimento.Criado).hasSize(1);
    }

    private ProspeccaoDocumentoEntity doc(String nome, boolean obrigatorio,
                                          StatusDocumento status, String socio) {
        return ProspeccaoDocumentoEntity.builder()
                .id(UUID.randomUUID())
                .prospeccaoId(UUID.randomUUID())
                .codigoSnapshot(nome.toLowerCase().replace(' ', '-'))
                .nomeSnapshot(nome)
                .obrigatorioSnapshot(obrigatorio)
                .informativoSnapshot(false)
                .escopo(socio == null ? EscopoDocumento.EMPRESA : EscopoDocumento.SOCIO)
                .socioNome(socio)
                .socioAtivo(true)
                .status(status)
                .atualizadoEm(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("excluir: apaga só os cards que existem e devolve quantos foram")
    void shouldDeleteOnlyExistingCards() {
        ProspeccaoEntity existente = card(EstagioProspeccao.NOVA);
        UUID sumido = UUID.randomUUID();
        when(prospeccaoRepository.findAllById(List.of(existente.getId(), sumido))).thenReturn(List.of(existente));

        int apagados = service.excluir(List.of(existente.getId(), sumido), analista);

        assertThat(apagados).isEqualTo(1);
        verify(prospeccaoRepository).deleteAllByIdInBatch(List.of(existente.getId()));
    }

    @Test
    @DisplayName("excluir: lista vazia não toca no banco")
    void shouldIgnoreEmptyDeletion() {
        assertThat(service.excluir(List.of(), analista)).isZero();
        verify(prospeccaoRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    @DisplayName("excluir: recusa lote acima do teto")
    void shouldRejectOversizedDeletion() {
        List<UUID> ids = java.util.stream.Stream.generate(UUID::randomUUID)
                .limit(ProspeccaoService.LIMITE_EXCLUSAO + 1L).toList();

        assertThatThrownBy(() -> service.excluir(ids, analista)).isInstanceOf(IllegalArgumentException.class);
        verify(prospeccaoRepository, never()).deleteAllByIdInBatch(any());
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
