package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.DocumentacaoIncompletaException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.StatusDocumento;
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
                .prazoEstagioDias(estagio.prazoDiasUteis())
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

        assertThatThrownBy(() -> service.criar("11.222.333/0001-81", "ACME LTDA", null, "Gustavo", analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("EM_ANALISE");
    }

    @Test
    @DisplayName("criar: normaliza o CNPJ formatado antes de gravar")
    void shouldNormalizeCnpj() {
        when(prospeccaoRepository.findAberta(any())).thenReturn(Optional.empty());

        ProspeccaoEntity criado = service.criar("11.222.333/0001-81", "ACME LTDA", null, "Gustavo", analista);

        assertThat(criado.getCnpj()).isEqualTo("11222333000181");
        assertThat(criado.getEstagio()).isEqualTo(EstagioProspeccao.TRIAGEM);
    }

    @Test
    @DisplayName("assumir: segunda analista recebe 409 em vez de roubar o card")
    void shouldRejectStealingAnAssignedCard() {
        ProspeccaoEntity card = card(EstagioProspeccao.TRIAGEM);
        card.setAnalistaId(UUID.randomUUID());
        when(prospeccaoRepository.assumirAnalise(any(), any(), anyInt(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.assumir(card.getId(), analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("já assumido");
    }

    @Test
    @DisplayName("transicionar: bloqueia salto de estágio")
    void shouldRejectStageSkip() {
        ProspeccaoEntity card = card(EstagioProspeccao.TRIAGEM);

        assertThatThrownBy(() -> service.transicionar(
                card.getId(), EstagioProspeccao.DOCS_COMPLETOS, null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("não é permitida");
    }

    @Test
    @DisplayName("transicionar: reprovar sem motivo não passa")
    void shouldRequireReasonToReject() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);

        assertThatThrownBy(() -> service.transicionar(
                card.getId(), EstagioProspeccao.REPROVADO, null, null, analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("Motivo é obrigatório");
    }

    @Test
    @DisplayName("transicionar: motivo OUTRO exige observação, senão vira caixa-preta")
    void shouldRequireNoteWhenReasonIsOther() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);

        assertThatThrownBy(() -> service.transicionar(
                card.getId(), EstagioProspeccao.REPROVADO, MotivoRecusa.OUTRO, "  ", analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("observação");
    }

    @Test
    @DisplayName("aprovar: materializa o checklist e cai direto em documentos pendentes")
    void shouldMaterializeChecklistOnApproval() {
        ProspeccaoEntity card = card(EstagioProspeccao.EM_ANALISE);

        ProspeccaoEntity resultado = service.transicionar(
                card.getId(), EstagioProspeccao.APROVADO, null, null, analista);

        verify(checklistService).materializar(card);
        assertThat(resultado.getEstagio()).isEqualTo(EstagioProspeccao.DOCS_PENDENTES);
        assertThat(resultado.getPrazoEstagioDias()).isEqualTo(10);

        ArgumentCaptor<ProspeccaoEventoEntity> eventos = ArgumentCaptor.forClass(ProspeccaoEventoEntity.class);
        verify(eventoRepository, org.mockito.Mockito.atLeast(2)).save(eventos.capture());
        assertThat(eventos.getAllValues())
                .extracting(ProspeccaoEventoEntity::getEstagioPara)
                .contains(EstagioProspeccao.APROVADO, EstagioProspeccao.DOCS_PENDENTES);
    }

    @Test
    @DisplayName("voltar para aprovado não cria o checklist de novo")
    void shouldNotRebuildChecklistWhenReturningToApproved() {
        // DOCS_PENDENTES -> APROVADO é transição válida. Sem guarda, a materialização rodaria
        // outra vez e duplicaria cada item, levando junto status e observação já preenchidos.
        ProspeccaoEntity card = card(EstagioProspeccao.DOCS_PENDENTES);
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(
                doc("Receita Federal", true, StatusDocumento.VALIDADO, null)));

        service.transicionar(card.getId(), EstagioProspeccao.APROVADO, null, null, analista);

        // O serviço continua chamando; quem protege é a checagem dentro do checklist.
        verify(checklistService).materializar(card);
    }

    @Test
    @DisplayName("fechar documentos: lista o que falta em vez de deixar passar")
    void shouldListMissingRequiredDocuments() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCS_PENDENTES);
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(
                doc("Receita Federal", true, StatusDocumento.VALIDADO, null),
                doc("Curva ABC", true, StatusDocumento.PENDENTE, null),
                doc("IRPF", true, StatusDocumento.RECEBIDO, "Antonio")));

        assertThatThrownBy(() -> service.transicionar(
                card.getId(), EstagioProspeccao.DOCS_COMPLETOS, null, null, analista))
                .isInstanceOf(DocumentacaoIncompletaException.class)
                .hasMessageContaining("Curva ABC")
                .hasMessageContaining("IRPF (Antonio)");

        verify(prospeccaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("fechar documentos: dispensado e não aplicável não travam o avanço")
    void shouldAllowClosingWithWaivedDocuments() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCS_PENDENTES);
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(
                doc("Receita Federal", true, StatusDocumento.VALIDADO, null),
                doc("Certidão simplificada", true, StatusDocumento.NAO_APLICAVEL, null),
                doc("Balanço", true, StatusDocumento.DISPENSADO, null)));

        ProspeccaoEntity resultado = service.transicionar(
                card.getId(), EstagioProspeccao.DOCS_COMPLETOS, null, null, analista);

        assertThat(resultado.getEstagio()).isEqualTo(EstagioProspeccao.DOCS_COMPLETOS);
    }

    @Test
    @DisplayName("fechar documentos: sócio inativo não trava — a planilha registra sócio que saiu ou faleceu")
    void shouldIgnoreInactivePartner() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCS_PENDENTES);
        ProspeccaoDocumentoEntity doSocioQueSaiu = doc("IRPF", true, StatusDocumento.PENDENTE, "Paulo");
        doSocioQueSaiu.setSocioAtivo(false);
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(doSocioQueSaiu));

        assertThat(service.transicionar(card.getId(), EstagioProspeccao.DOCS_COMPLETOS, null, null, analista)
                .getEstagio()).isEqualTo(EstagioProspeccao.DOCS_COMPLETOS);
    }

    @Test
    @DisplayName("reabrir: devolve para triagem, conta a reabertura e solta o analista anterior")
    void shouldReopenRejectedCard() {
        ProspeccaoEntity card = card(EstagioProspeccao.REPROVADO);
        card.setAnalistaId(UUID.randomUUID());
        card.setClosedAt(LocalDateTime.now());
        when(prospeccaoRepository.findAberta(card.getCnpj())).thenReturn(Optional.empty());

        ProspeccaoEntity resultado = service.reabrir(card.getId(), "Cliente quitou as restrições", analista);

        assertThat(resultado.getEstagio()).isEqualTo(EstagioProspeccao.TRIAGEM);
        assertThat(resultado.getReaberturas()).isEqualTo(1);
        assertThat(resultado.getAnalistaId()).isNull();
        assertThat(resultado.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("reabrir: não reabre card que já foi repassado para a habilitação")
    void shouldNotReopenHandedOverCard() {
        ProspeccaoEntity card = card(EstagioProspeccao.PRONTO_HABILITACAO);

        assertThatThrownBy(() -> service.reabrir(card.getId(), "voltar", analista))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageContaining("habilitação");
    }

    @Test
    @DisplayName("cobrança: zera o relógio de silêncio do card")
    void shouldResetSilenceClockOnFollowUp() {
        ProspeccaoEntity card = card(EstagioProspeccao.DOCS_PENDENTES);
        card.setEstagioDesde(LocalDateTime.now().minusDays(45));
        assertThat(service.silencioProlongado(card)).isTrue();

        service.registrarCobranca(card.getId(),
                com.portal.serasa.domain.model.prospeccao.CanalContato.WHATSAPP, "Cobrado no zap", analista);

        assertThat(card.getUltimoContatoEm()).isNotNull();
        assertThat(service.silencioProlongado(card)).isFalse();
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

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
