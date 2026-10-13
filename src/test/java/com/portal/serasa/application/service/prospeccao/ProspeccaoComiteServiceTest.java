package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.ResultadoComite;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoParecerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProspeccaoComiteServiceTest {

    @Mock private ProspeccaoService prospeccaoService;
    @Mock private ProspeccaoJpaRepository prospeccaoRepository;
    @Mock private ProspeccaoParecerJpaRepository parecerRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher acontecimentos;
    @Mock private ProspeccaoDecisaoService decisaoService;

    private ProspeccaoComiteService service;
    private UserEntity dona;
    private UserEntity membro;
    private UserEntity estranha;
    private ProspeccaoEntity card;

    @BeforeEach
    void setUp() {
        // A rodada é a de verdade, sobre os mesmos mocks: o envio ao comitê continua testado por inteiro.
        ProspeccaoRodadaComite rodada = new ProspeccaoRodadaComite(prospeccaoService, prospeccaoRepository,
                parecerRepository, userRepository, acontecimentos);
        service = new ProspeccaoComiteService(prospeccaoService, new ProspeccaoAutorizacao(), prospeccaoRepository,
                parecerRepository, acontecimentos, rodada, decisaoService);
        dona = UserEntity.builder().id(UUID.randomUUID()).name("Andressa").role("ROLE_USER").build();
        membro = UserEntity.builder().id(UUID.randomUUID()).name("Mychelly").role("ROLE_USER").comite(true).build();
        estranha = UserEntity.builder().id(UUID.randomUUID()).name("Joana").role("ROLE_USER").build();
        card = ProspeccaoEntity.builder().id(UUID.randomUUID()).razaoSocial("ACME").analistaId(dona.getId())
                .estagio(EstagioProspeccao.DOCUMENTACAO).rodadaComite(0)
                .estagioDesde(LocalDateTime.now()).prazoEstagioHoras(0).build();
        when(prospeccaoService.buscar(card.getId())).thenReturn(card);
        when(prospeccaoService.mover(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            card.setEstagio(inv.getArgument(1));
            return card;
        });
        when(prospeccaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(parecerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProspeccaoParecerEntity parecer(UserEntity quem, PosicaoParecer posicao) {
        return ProspeccaoParecerEntity.builder().id(UUID.randomUUID()).prospeccaoId(card.getId()).rodada(card.getRodadaComite())
                .usuarioId(quem.getId()).usuarioNome(quem.getName()).posicao(posicao).criadoEm(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("enviar: abre rodada nova e convoca cada membro do comitê")
    void shouldOpenRoundAndSummonMembers() {
        UserEntity outro = UserEntity.builder().id(UUID.randomUUID()).name("Diretoria").comite(true).build();
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of(membro, outro));

        service.enviar(card.getId(), dona);

        assertThat(card.getRodadaComite()).isEqualTo(1);
        verify(prospeccaoService).mover(card.getId(), EstagioProspeccao.COMITE, null, null, dona);
        verify(parecerRepository, times(2)).save(any());
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(acontecimentos).publishEvent(evento.capture());
        assertThat(((ProspeccaoAcontecimento.ComiteConvocado) evento.getValue()).membros()).hasSize(2);
    }

    @Test
    @DisplayName("enviar: sem ninguém marcado como comitê não dá para enviar")
    void shouldRequireCommitteeMembers() {
        when(userRepository.findByComiteTrueOrderByNameAsc()).thenReturn(List.of());

        assertThatThrownBy(() -> service.enviar(card.getId(), dona))
                .isInstanceOf(TransicaoInvalidaException.class).hasMessageContaining("Comitê");
        verify(prospeccaoService, never()).mover(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("enviar: só a dona do card")
    void shouldOnlyLetOwnerSend() {
        assertThatThrownBy(() -> service.enviar(card.getId(), estranha)).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("parecer: convocada registra; o último da rodada marca comitê completo")
    void shouldRegisterOpinionAndDetectComplete() {
        card.setEstagio(EstagioProspeccao.COMITE);
        card.setRodadaComite(1);
        ProspeccaoParecerEntity vazio = parecer(membro, null);
        when(parecerRepository.findByProspeccaoIdAndRodadaAndUsuarioId(card.getId(), 1, membro.getId())).thenReturn(Optional.of(vazio));
        when(parecerRepository.findByProspeccaoIdAndRodadaOrderByCriadoEmAsc(card.getId(), 1)).thenReturn(List.of(vazio));

        var registrado = service.registrarParecer(card.getId(), PosicaoParecer.COM_RESSALVAS, "Limitar cheque", membro);

        assertThat(registrado.parecer().getPosicao()).isEqualTo(PosicaoParecer.COM_RESSALVAS);
        assertThat(registrado.ultimo()).isTrue();
    }

    @Test
    @DisplayName("parecer: quem não foi convocado não registra")
    void shouldRejectUnsummoned() {
        card.setEstagio(EstagioProspeccao.COMITE);
        card.setRodadaComite(1);
        when(parecerRepository.findByProspeccaoIdAndRodadaAndUsuarioId(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarParecer(card.getId(), PosicaoParecer.FAVORAVEL, null, estranha))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("resultado: exige ao menos um parecer na rodada")
    void shouldRequireAtLeastOneOpinion() {
        card.setEstagio(EstagioProspeccao.COMITE);
        card.setRodadaComite(1);
        when(parecerRepository.findByProspeccaoIdAndRodadaOrderByCriadoEmAsc(card.getId(), 1)).thenReturn(List.of(parecer(membro, null)));

        assertThatThrownBy(() -> service.registrarResultado(card.getId(), ResultadoComite.APROVADO, null, null, dona))
                .isInstanceOf(TransicaoInvalidaException.class).hasMessageContaining("parecer");
    }

    @Test
    @DisplayName("resultado: aprovar ajustando limite e condições grava a proposta do comitê")
    void shouldApproveWithAdjustedProposal() {
        card.setEstagio(EstagioProspeccao.COMITE);
        card.setRodadaComite(1);
        when(parecerRepository.findByProspeccaoIdAndRodadaOrderByCriadoEmAsc(card.getId(), 1))
                .thenReturn(List.of(parecer(membro, PosicaoParecer.FAVORAVEL)));
        var condicoes = List.of(new ProspeccaoDecisaoService.Condicao("DUPLICATA", new java.math.BigDecimal("300000")));

        service.registrarResultado(card.getId(), ResultadoComite.APROVADO, null, "Limite menor",
                new ProspeccaoComiteService.AjusteProposta(new java.math.BigDecimal("300000"), condicoes), membro);

        verify(decisaoService).ajustarPeloComite(eq(card.getId()), eq(new java.math.BigDecimal("300000")), eq(condicoes),
                eq("Limite menor"), eq(membro));
        verify(prospeccaoService).mover(card.getId(), EstagioProspeccao.AGUARDANDO_ACEITE, null, "Limite menor", membro);
    }

    @Test
    @DisplayName("resultado: aprovado leva ao aceite do cliente e diz no histórico quem não deu parecer")
    void shouldApproveAndRecordMissing() {
        card.setEstagio(EstagioProspeccao.COMITE);
        card.setRodadaComite(1);
        UserEntity diretoria = UserEntity.builder().id(UUID.randomUUID()).name("Diretoria").build();
        when(parecerRepository.findByProspeccaoIdAndRodadaOrderByCriadoEmAsc(card.getId(), 1))
                .thenReturn(List.of(parecer(membro, PosicaoParecer.FAVORAVEL), parecer(diretoria, null)));

        service.registrarResultado(card.getId(), ResultadoComite.APROVADO, null, null, membro);

        verify(prospeccaoService).mover(card.getId(), EstagioProspeccao.AGUARDANDO_ACEITE, null, null, membro);
        verify(parecerRepository).apagarAguardando(card.getId(), 1);
        // Sem ajuste, vale a proposta da análise.
        verify(decisaoService, org.mockito.Mockito.never()).ajustarPeloComite(any(), any(), any(), any(), any());
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(prospeccaoService).registrarEvento(any(), any(), isNull(), isNull(), isNull(), texto.capture(), eq(membro));
        assertThat(texto.getValue()).contains("Aprovado no comitê").contains("sem o parecer de Diretoria");
    }

    @Test
    @DisplayName("resultado: devolver volta à análise; reprovar passa o motivo adiante")
    void shouldReturnOrReject() {
        card.setEstagio(EstagioProspeccao.COMITE);
        card.setRodadaComite(1);
        when(parecerRepository.findByProspeccaoIdAndRodadaOrderByCriadoEmAsc(card.getId(), 1))
                .thenReturn(List.of(parecer(membro, PosicaoParecer.DESFAVORAVEL)));

        service.registrarResultado(card.getId(), ResultadoComite.DEVOLVIDO, MotivoRecusa.SEGMENTO, "Rever limite", dona);
        verify(prospeccaoService).mover(card.getId(), EstagioProspeccao.EM_ANALISE, null, "Rever limite", dona);

        card.setEstagio(EstagioProspeccao.COMITE);
        service.registrarResultado(card.getId(), ResultadoComite.REPROVADO, MotivoRecusa.SEGMENTO, null, dona);
        verify(prospeccaoService).mover(card.getId(), EstagioProspeccao.REPROVADO, MotivoRecusa.SEGMENTO, null, dona);
    }

    @Test
    @DisplayName("resultado: quem não é dona nem do comitê não registra")
    void shouldRestrictResult() {
        card.setEstagio(EstagioProspeccao.COMITE);
        assertThatThrownBy(() -> service.registrarResultado(card.getId(), ResultadoComite.APROVADO, null, null, estranha))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("concluir: exige o contato completo de quem assina")
    void shouldRequireContractContact() {
        card.setEstagio(EstagioProspeccao.CONTRATO);
        card.setContratoContatoNome("Carlos");

        assertThatThrownBy(() -> service.concluir(card.getId(), dona))
                .isInstanceOf(TransicaoInvalidaException.class).hasMessageContaining("telefone");

        card.setContratoContatoTelefone("11 99999-0000");
        card.setContratoContatoEmail("carlos@acme.com.br");
        service.concluir(card.getId(), dona);
        verify(prospeccaoService).mover(card.getId(), EstagioProspeccao.HABILITADO, null, null, dona);
    }

    @Test
    @DisplayName("contrato: e-mail inválido não passa; texto em branco vira nulo")
    void shouldValidateContractContact() {
        card.setEstagio(EstagioProspeccao.CONTRATO);
        assertThatThrownBy(() -> service.salvarContrato(card.getId(), "Carlos", "11", "carlos", dona))
                .hasMessageContaining("E-mail");

        service.salvarContrato(card.getId(), "  Carlos  ", " ", "carlos@acme.com.br", dona);
        assertThat(card.getContratoContatoNome()).isEqualTo("Carlos");
        assertThat(card.getContratoContatoTelefone()).isNull();
        verify(prospeccaoService).registrarEvento(any(), any(), isNull(), isNull(), isNull(), anyString(), eq(dona));
    }
}
