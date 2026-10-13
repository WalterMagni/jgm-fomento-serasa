package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.prospeccao.CondicaoSugerida;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.RespostaCliente;
import com.portal.serasa.domain.model.prospeccao.ResultadoDecisao;
import com.portal.serasa.application.service.prospeccao.ProspeccaoDecisaoService.ComandoAceite;
import com.portal.serasa.application.service.prospeccao.ProspeccaoDecisaoService.ComandoDecisao;
import com.portal.serasa.application.service.prospeccao.ProspeccaoDecisaoService.Condicao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoAceiteEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDecisaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoAceiteJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDecisaoCondicaoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDecisaoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProspeccaoDecisaoServiceTest {

    @Mock private ProspeccaoService prospeccaoService;
    @Mock private ProspeccaoDecisaoJpaRepository decisaoRepository;
    @Mock private ProspeccaoDecisaoCondicaoJpaRepository condicaoRepository;
    @Mock private ProspeccaoAceiteJpaRepository aceiteRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher acontecimentos;
    @Mock private ProspeccaoRodadaComite rodadaComite;

    private final ProspeccaoAutorizacao autorizacao = new ProspeccaoAutorizacao();
    private ProspeccaoDecisaoService service;
    private UserEntity dona;
    private UserEntity outra;

    @BeforeEach
    void setUp() {
        service = new ProspeccaoDecisaoService(prospeccaoService, autorizacao, decisaoRepository, condicaoRepository, aceiteRepository, acontecimentos, rodadaComite);
        dona = UserEntity.builder().id(UUID.randomUUID()).name("Andressa").role("ROLE_USER").build();
        outra = UserEntity.builder().id(UUID.randomUUID()).name("Mychelly").role("ROLE_USER").build();
        when(decisaoRepository.save(any())).thenAnswer(inv -> {
            ProspeccaoDecisaoEntity d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(condicaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aceiteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProspeccaoEntity card(EstagioProspeccao estagio) {
        ProspeccaoEntity card = ProspeccaoEntity.builder()
                .id(UUID.randomUUID()).cnpj("11222333000181").razaoSocial("ACME LTDA")
                .estagio(estagio).analistaId(dona.getId())
                .estagioDesde(LocalDateTime.now()).prazoEstagioHoras(0).reaberturas(0)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(prospeccaoService.buscar(card.getId())).thenReturn(card);
        return card;
    }

    private ComandoDecisao aprovacao(String limite, Condicao... condicoes) {
        return new ComandoDecisao(ResultadoDecisao.APROVADO, new BigDecimal(limite), List.of(condicoes), "Bom pagador", null, null, null);
    }

    @Test
    @DisplayName("aprovar: grava decisão vigente com condições, leva o card a Aprovado e avisa o comercial")
    void shouldApproveWithConditions() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);
        when(decisaoRepository.ultimaVersao(card.getId())).thenReturn(0);

        var completa = service.decidir(card.getId(),
                aprovacao("500000", new Condicao("DUPLICATA", new BigDecimal("400000")), new Condicao("CHEQUE PRÓPRIO", new BigDecimal("100000"))),
                dona);

        assertThat(completa.decisao().getVersao()).isEqualTo(1);
        assertThat(completa.decisao().getVigente()).isTrue();
        assertThat(completa.decisao().getLimite()).isEqualByComparingTo("500000");
        assertThat(completa.condicoes()).extracting("produto").containsExactly("DUPLICATA", "CHEQUE PRÓPRIO");
        verify(decisaoRepository).encerrarVigencia(card.getId());
        verify(prospeccaoService).mover(eq(card.getId()), eq(EstagioProspeccao.APROVADO), isNull(), isNull(), eq(dona));
        verify(acontecimentos).publishEvent(any(ProspeccaoAcontecimento.AprovadoNaAnalise.class));
    }

    @Test
    @DisplayName("aprovar com ressalva: a ressalva fica na decisão e no resumo da timeline")
    void shouldApproveWithReservation() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);
        when(decisaoRepository.ultimaVersao(card.getId())).thenReturn(0);

        var completa = service.decidir(card.getId(), new ComandoDecisao(ResultadoDecisao.APROVADO, new BigDecimal("200000"),
                List.of(new Condicao("DUPLICATA", new BigDecimal("200000"))), "ok", null, null, "  Exigir aval dos sócios  "), dona);

        assertThat(completa.decisao().getRessalva()).isEqualTo("Exigir aval dos sócios");
        assertThat(ProspeccaoDecisaoService.resumo(completa.decisao(), completa.condicoes()))
                .startsWith("Aprovado com ressalva").contains("Exigir aval dos sócios");
    }

    @Test
    @DisplayName("comitê ajusta o limite e a ressalva da análise continua valendo")
    void shouldKeepReservationWhenCommitteeAdjusts() {
        ProspeccaoEntity card = card(EstagioProspeccao.COMITE);
        when(decisaoRepository.ultimaVersao(card.getId())).thenReturn(1);
        ProspeccaoDecisaoEntity daAnalise = ProspeccaoDecisaoEntity.builder().id(UUID.randomUUID()).prospeccaoId(card.getId())
                .resultado(ResultadoDecisao.APROVADO).ressalva("Exigir aval dos sócios").vigente(true).build();
        when(decisaoRepository.findByProspeccaoIdAndVigenteTrue(card.getId())).thenReturn(java.util.Optional.of(daAnalise));

        var completa = service.ajustarPeloComite(card.getId(), new BigDecimal("150000"),
                List.of(new Condicao("DUPLICATA", new BigDecimal("150000"))), null, dona);

        assertThat(completa.decisao().getRessalva()).isEqualTo("Exigir aval dos sócios");
        assertThat(completa.decisao().getLimite()).isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("comitê ajusta a proposta: decisão nova vigente, sem mover o card")
    void shouldRecordCommitteeAdjustment() {
        ProspeccaoEntity card = card(EstagioProspeccao.COMITE);
        when(decisaoRepository.ultimaVersao(card.getId())).thenReturn(1);

        var completa = service.ajustarPeloComite(card.getId(), new BigDecimal("300000"),
                List.of(new Condicao("DUPLICATA", new BigDecimal("300000"))), "Reduzido pelo comitê", dona);

        assertThat(completa.decisao().getVersao()).isEqualTo(2);
        assertThat(completa.decisao().getLimite()).isEqualByComparingTo("300000");
        verify(decisaoRepository).encerrarVigencia(card.getId());
        verify(prospeccaoService, never()).mover(any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.ajustarPeloComite(card.getId(), BigDecimal.ZERO, List.of(), null, dona))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("segunda decisão vira versão 2: a anterior perde a vigência, não some")
    void shouldVersionDecisions() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);
        when(decisaoRepository.ultimaVersao(card.getId())).thenReturn(1);

        var completa = service.decidir(card.getId(), aprovacao("300000", new Condicao("PEDIDO", new BigDecimal("300000"))), dona);

        assertThat(completa.decisao().getVersao()).isEqualTo(2);
        verify(decisaoRepository).encerrarVigencia(card.getId());
    }

    @Test
    @DisplayName("aprovar sem limite ou sem condição não passa")
    void shouldRequireLimitAndConditions() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);

        assertThatThrownBy(() -> service.decidir(card.getId(), aprovacao("0", new Condicao("PEDIDO", BigDecimal.TEN)), dona))
                .hasMessageContaining("limite");
        assertThatThrownBy(() -> service.decidir(card.getId(), aprovacao("1000"), dona))
                .hasMessageContaining("condição");
        assertThatThrownBy(() -> service.decidir(card.getId(), aprovacao("1000", new Condicao("PEDIDO", BigDecimal.ZERO)), dona))
                .hasMessageContaining("maior que zero");
        verify(decisaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("reprovar: leva a reprovado com o motivo, sem limite")
    void shouldReject() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);

        var completa = service.decidir(card.getId(),
                new ComandoDecisao(ResultadoDecisao.REPROVADO, new BigDecimal("999"), List.of(), "Muitas restrições",
                        MotivoRecusa.QUANTIDADE_DE_RESTRICOES, null, null),
                dona);

        assertThat(completa.decisao().getLimite()).isNull();
        assertThat(completa.decisao().getMotivoRecusa()).isEqualTo(MotivoRecusa.QUANTIDADE_DE_RESTRICOES);
        verify(prospeccaoService).mover(card.getId(), EstagioProspeccao.REPROVADO, MotivoRecusa.QUANTIDADE_DE_RESTRICOES, null, dona);
    }

    @Test
    @DisplayName("só a analista dona decide; card sem dona pede para assumir antes")
    void shouldOnlyLetOwnerDecide() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);
        assertThatThrownBy(() -> service.decidir(card.getId(), aprovacao("1000", new Condicao("PEDIDO", BigDecimal.TEN)), outra))
                .isInstanceOf(AcessoNegadoException.class).hasMessageContaining("dona");

        card.setAnalistaId(null);
        assertThatThrownBy(() -> service.decidir(card.getId(), aprovacao("1000", new Condicao("PEDIDO", BigDecimal.TEN)), outra))
                .isInstanceOf(AcessoNegadoException.class).hasMessageContaining("Assuma");
    }

    @Test
    @DisplayName("admin decide card de outra analista")
    void shouldLetAdminDecide() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);
        UserEntity admin = UserEntity.builder().id(UUID.randomUUID()).name("Admin").role("ADMIN").build();

        service.decidir(card.getId(), aprovacao("1000", new Condicao("PEDIDO", BigDecimal.TEN)), admin);

        verify(decisaoRepository).save(any());
    }

    @Test
    @DisplayName("decidir fora da análise não passa")
    void shouldDecideOnlyInAnalysis() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCUMENTACAO);

        assertThatThrownBy(() -> service.decidir(card.getId(), aprovacao("1000", new Condicao("PEDIDO", BigDecimal.TEN)), dona))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    // ------------------------------------------------------------------ aceite

    private ProspeccaoDecisaoEntity vigente(ProspeccaoEntity card, ResultadoDecisao resultado) {
        ProspeccaoDecisaoEntity decisao = ProspeccaoDecisaoEntity.builder()
                .id(UUID.randomUUID()).prospeccaoId(card.getId()).versao(1).resultado(resultado)
                .limite(resultado == ResultadoDecisao.APROVADO ? new BigDecimal("1000") : null)
                .vigente(true).decididoEm(LocalDateTime.now()).build();
        when(decisaoRepository.findByProspeccaoIdAndVigenteTrue(card.getId())).thenReturn(Optional.of(decisao));
        return decisao;
    }

    @Test
    @DisplayName("aceite: cliente aceitou leva ao contrato, marcado como em nome do comercial")
    void shouldAcceptOnBehalfOfCommercial() {
        ProspeccaoEntity card = card(EstagioProspeccao.AGUARDANDO_ACEITE);
        card.setComercialNome("Gustavo");
        ProspeccaoDecisaoEntity decisao = vigente(card, ResultadoDecisao.APROVADO);

        ProspeccaoAceiteEntity aceite = service.registrarAceite(card.getId(),
                new ComandoAceite(RespostaCliente.ACEITO, null, null, null), dona);

        assertThat(aceite.getDecisaoId()).isEqualTo(decisao.getId());
        assertThat(aceite.getEmNomeDoComercial()).isTrue();
        verify(prospeccaoService).mover(card.getId(), EstagioProspeccao.CONTRATO, null, null, dona);
    }

    @Test
    @DisplayName("contraproposta: exige mensagem e volta ao comitê numa rodada nova, com o pedido do cliente")
    void shouldReturnToCommitteeOnCounterOffer() {
        ProspeccaoEntity card = card(EstagioProspeccao.AGUARDANDO_ACEITE);
        vigente(card, ResultadoDecisao.APROVADO);

        assertThatThrownBy(() -> service.registrarAceite(card.getId(),
                new ComandoAceite(RespostaCliente.CONTRAPROPOSTA, "  ", null, null), dona))
                .hasMessageContaining("mensagem");

        ProspeccaoAceiteEntity aceite = service.registrarAceite(card.getId(),
                new ComandoAceite(RespostaCliente.CONTRAPROPOSTA, "Quer mais duplicata", new BigDecimal("2000"),
                        List.of(new CondicaoSugerida("DUPLICATA", new BigDecimal("2000")), new CondicaoSugerida("", BigDecimal.ONE))),
                dona);

        assertThat(aceite.getLimiteSugerido()).isEqualByComparingTo("2000");
        // Linha incompleta do formulário não vira pedido do cliente.
        assertThat(aceite.getCondicoesSugeridas()).hasSize(1);
        verify(rodadaComite).abrir(eq(card.getId()), any(), eq(dona));
        verify(prospeccaoService, never()).mover(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("recusa: encerra em recusado pelo cliente, com o que ele disse")
    void shouldCloseOnRefusal() {
        ProspeccaoEntity card = card(EstagioProspeccao.AGUARDANDO_ACEITE);
        vigente(card, ResultadoDecisao.APROVADO);

        service.registrarAceite(card.getId(), new ComandoAceite(RespostaCliente.RECUSADO, "Taxa alta", null, null), dona);

        verify(prospeccaoService).mover(card.getId(), EstagioProspeccao.RECUSADO_CLIENTE, null, "Taxa alta", dona);
    }

    @Test
    @DisplayName("sem proposta aprovada vigente não há o que o cliente responder")
    void shouldRequireApprovedProposal() {
        ProspeccaoEntity card = card(EstagioProspeccao.AGUARDANDO_ACEITE);
        when(decisaoRepository.findByProspeccaoIdAndVigenteTrue(card.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarAceite(card.getId(),
                new ComandoAceite(RespostaCliente.ACEITO, null, null, null), dona))
                .isInstanceOf(TransicaoInvalidaException.class).hasMessageContaining("proposta");
        verify(aceiteRepository, never()).save(any());
    }

    @Test
    @DisplayName("timeline: resumo da aprovação diz limite e produtos")
    void shouldSummarizeApproval() {
        ProspeccaoDecisaoEntity decisao = ProspeccaoDecisaoEntity.builder()
                .versao(2).resultado(ResultadoDecisao.APROVADO).limite(new BigDecimal("500000")).build();
        var condicao = com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDecisaoCondicaoEntity.builder()
                .produto("DUPLICATA").valor(new BigDecimal("500000")).build();

        String texto = ProspeccaoDecisaoService.resumo(decisao, List.of(condicao));

        assertThat(texto).contains("500.000,00").contains("DUPLICATA").contains("decisão 2");
    }

    @Test
    @DisplayName("evento de decisão gravado na timeline")
    void shouldRecordDecisionEvent() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);

        service.decidir(card.getId(), aprovacao("1000", new Condicao("PEDIDO", BigDecimal.TEN)), dona);

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(prospeccaoService).registrarEvento(any(), eq(com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao.DECISAO),
                isNull(), isNull(), isNull(), texto.capture(), eq(dona));
        assertThat(texto.getValue()).startsWith("Aprovado com limite");
    }

    @Test
    @DisplayName("cliente direto: resposta registrada pela equipe não é em nome de comercial")
    void shouldNotFlagDirectClient() {
        ProspeccaoEntity card = card(EstagioProspeccao.AGUARDANDO_ACEITE);
        assertThat(ProspeccaoDecisaoService.emNomeDoComercial(card, dona)).isFalse();

        UserEntity comercial = UserEntity.builder().id(UUID.randomUUID()).name("Gustavo").build();
        card.setComercialId(comercial.getId());
        card.setComercialNome("Gustavo");
        assertThat(ProspeccaoDecisaoService.emNomeDoComercial(card, comercial)).isFalse();
        assertThat(ProspeccaoDecisaoService.emNomeDoComercial(card, dona)).isTrue();
    }
}
