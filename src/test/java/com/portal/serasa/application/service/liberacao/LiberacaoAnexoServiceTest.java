package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.service.documento.CompartilhamentoDocumentos;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoAnexoEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoAnexoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiberacaoAnexoServiceTest {

    private static final String CNPJ = "11222333000181";

    @Mock private LiberacaoAnexoJpaRepository anexoRepository;
    @Mock private LiberacaoService liberacaoService;
    @Spy private LiberacaoAutorizacao autorizacao = new LiberacaoAutorizacao();
    @Mock private CompartilhamentoDocumentos compartilhamento;

    @InjectMocks private LiberacaoAnexoService service;

    private UserEntity auxiliar;
    private UserEntity analista;
    private LiberacaoCardEntity card;
    private MockMultipartFile arquivo;

    @BeforeEach
    void setUp() {
        auxiliar = UserEntity.builder().id(UUID.randomUUID()).name("Auxiliar").email("aux@jgm.com").build();
        analista = UserEntity.builder().id(UUID.randomUUID()).name("Andressa").email("andressa@jgm.com").analista(true).build();
        card = LiberacaoCardEntity.builder().id(UUID.randomUUID()).numero(42L).etapa(EtapaLiberacao.ORIGEM)
                .cedenteCnpj(CNPJ).cedenteNome("ACME LTDA").build();
        arquivo = new MockMultipartFile("file", "Contrato Social.pdf", "application/pdf", new byte[]{1, 2, 3});

        when(liberacaoService.buscar(card.getId())).thenReturn(card);
        when(compartilhamento.validar(arquivo)).thenReturn("Contrato Social.pdf");
        when(compartilhamento.nomeSanitizado(arquivo)).thenReturn("contrato-social.pdf");
        when(anexoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private LiberacaoAnexoEntity anexo(UserEntity enviadoPor) {
        LiberacaoAnexoEntity anexo = LiberacaoAnexoEntity.builder()
                .id(UUID.randomUUID())
                .cardId(card.getId())
                .caminhoRelativo(CNPJ + "/liberacao/42/1-contrato-social.pdf")
                .nomeOriginal("Contrato Social.pdf")
                .mimeType("application/pdf")
                .tamanhoBytes(3L)
                .enviadoPorId(enviadoPor == null ? null : enviadoPor.getId())
                .enviadoPorNome(enviadoPor == null ? "Removido" : enviadoPor.getName())
                .enviadoEm(LocalDateTime.now().minusHours(1))
                .build();
        when(anexoRepository.findById(anexo.getId())).thenReturn(Optional.of(anexo));
        return anexo;
    }

    // ------------------------------------------------------------------ anexar

    @Test
    @DisplayName("anexar: valida pelo compartilhamento, grava em {cnpj}/liberacao/{numero}/{seq}-{nome} e guarda o metadado")
    void shouldValidateStoreAndRecordAttachment() {
        when(anexoRepository.countByCardId(card.getId())).thenReturn(2L);

        LiberacaoAnexoEntity salvo = service.anexar(card.getId(), arquivo, auxiliar);

        verify(compartilhamento).validar(arquivo);
        String esperado = CNPJ + "/liberacao/42/3-contrato-social.pdf";
        verify(compartilhamento).gravar(esperado, arquivo);
        assertThat(salvo.getCardId()).isEqualTo(card.getId());
        assertThat(salvo.getCaminhoRelativo()).isEqualTo(esperado);
        assertThat(salvo.getNomeOriginal()).isEqualTo("Contrato Social.pdf");
        assertThat(salvo.getMimeType()).isEqualTo("application/pdf");
        assertThat(salvo.getTamanhoBytes()).isEqualTo(3L);
        assertThat(salvo.getEnviadoPorId()).isEqualTo(auxiliar.getId());
        assertThat(salvo.getEnviadoPorNome()).isEqualTo("Auxiliar");
        assertThat(salvo.getEnviadoEm()).isNotNull();
        assertThat(salvo.getRemovidoEm()).isNull();
    }

    @Test
    @DisplayName("anexar: o primeiro anexo do card é o número 1")
    void shouldStartSequenceAtOne() {
        when(anexoRepository.countByCardId(card.getId())).thenReturn(0L);

        LiberacaoAnexoEntity salvo = service.anexar(card.getId(), arquivo, auxiliar);

        assertThat(salvo.getCaminhoRelativo()).isEqualTo(CNPJ + "/liberacao/42/1-contrato-social.pdf");
    }

    @Test
    @DisplayName("anexar: a sequência conta inclusive os removidos, para o nome no disco não se repetir")
    void shouldCountRemovedAttachmentsInSequence() {
        // countByCardId conta todos, removidos também: 5 linhas = próximo é o 6
        when(anexoRepository.countByCardId(card.getId())).thenReturn(5L);

        LiberacaoAnexoEntity salvo = service.anexar(card.getId(), arquivo, auxiliar);

        assertThat(salvo.getCaminhoRelativo()).endsWith("/6-contrato-social.pdf");
        verify(anexoRepository).countByCardId(card.getId());
        verify(anexoRepository, never()).findByCardIdAndRemovidoEmIsNullOrderByEnviadoEmDesc(any());
    }

    @Test
    @DisplayName("anexar: registra o evento de anexo adicionado no histórico, com o nome original")
    void shouldRecordAttachmentEvent() {
        service.anexar(card.getId(), arquivo, auxiliar);

        verify(liberacaoService).registrarAnexo(card, true, "Contrato Social.pdf", auxiliar);
    }

    @ParameterizedTest
    @EnumSource(EtapaLiberacao.class)
    @DisplayName("anexar: qualquer usuário anexa em qualquer etapa, como comentar")
    void shouldLetAnyoneAttachInAnyStage(EtapaLiberacao etapa) {
        card.setEtapa(etapa);

        LiberacaoAnexoEntity salvo = service.anexar(card.getId(), arquivo, auxiliar);

        assertThat(salvo.getEnviadoPorNome()).isEqualTo("Auxiliar");
    }

    @Test
    @DisplayName("anexar: arquivo recusado pela validação não é gravado, salvo nem registrado")
    void shouldStoreNothingWhenValidationFails() {
        when(compartilhamento.validar(arquivo)).thenThrow(new IllegalArgumentException("O arquivo passa de 10MB"));

        assertThatThrownBy(() -> service.anexar(card.getId(), arquivo, auxiliar))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O arquivo passa de 10MB");
        verify(compartilhamento, never()).gravar(anyString(), any());
        verify(anexoRepository, never()).save(any());
        verify(liberacaoService, never()).registrarAnexo(any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("anexar: falha ao gravar no compartilhamento não deixa metadado nem evento")
    void shouldRecordNothingWhenWritingFails() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Não foi possível gravar o arquivo no compartilhamento de rede."))
                .when(compartilhamento).gravar(anyString(), any());

        assertThatThrownBy(() -> service.anexar(card.getId(), arquivo, auxiliar))
                .isInstanceOf(IllegalArgumentException.class);
        verify(anexoRepository, never()).save(any());
        verify(liberacaoService, never()).registrarAnexo(any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("anexar: sem usuário autenticado é AcessoNegado antes de qualquer outra coisa")
    void shouldRequireAuthenticationToAttach() {
        assertThatThrownBy(() -> service.anexar(card.getId(), arquivo, null))
                .isInstanceOf(AcessoNegadoException.class);
        verify(liberacaoService, never()).buscar(any());
        verifyNoInteractions(compartilhamento);
        verify(anexoRepository, never()).save(any());
    }

    @Test
    @DisplayName("anexar: card inexistente ou apagado é EntityNotFound e o arquivo nem é validado")
    void shouldFailWhenCardDoesNotExist() {
        UUID inexistente = UUID.randomUUID();
        when(liberacaoService.buscar(inexistente)).thenThrow(new EntityNotFoundException("Card não encontrado"));

        assertThatThrownBy(() -> service.anexar(inexistente, arquivo, auxiliar))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(compartilhamento);
        verify(anexoRepository, never()).save(any());
    }

    // ----------------------------------------------------------------- remover

    @Test
    @DisplayName("remover: quem enviou remove; é remoção lógica (marca removidoEm/removidoPor) e o byte não é tocado")
    void shouldLetUploaderRemoveLogically() {
        LiberacaoAnexoEntity anexo = anexo(auxiliar);
        LocalDateTime antes = LocalDateTime.now();

        service.remover(card.getId(), anexo.getId(), auxiliar);

        assertThat(anexo.getRemovidoEm()).isNotNull().isAfterOrEqualTo(antes);
        assertThat(anexo.getRemovidoPorNome()).isEqualTo("Auxiliar");
        verify(anexoRepository).save(anexo);
        verify(anexoRepository, never()).delete(any());
        verify(anexoRepository, never()).deleteById(any());
        verifyNoInteractions(compartilhamento);
        verify(liberacaoService).registrarAnexo(card, false, "Contrato Social.pdf", auxiliar);
    }

    @Test
    @DisplayName("remover: analista remove anexo de outra pessoa")
    void shouldLetAnalystRemoveSomeoneElsesAttachment() {
        LiberacaoAnexoEntity anexo = anexo(auxiliar);

        service.remover(card.getId(), anexo.getId(), analista);

        assertThat(anexo.getRemovidoEm()).isNotNull();
        assertThat(anexo.getRemovidoPorNome()).isEqualTo("Andressa");
        verify(liberacaoService).registrarAnexo(card, false, "Contrato Social.pdf", analista);
    }

    @Test
    @DisplayName("remover: outra pessoa que não é analista é AcessoNegado citando quem enviou, e nada muda")
    void shouldDenyRemovalByStranger() {
        UserEntity outra = UserEntity.builder().id(UUID.randomUUID()).name("Outra").email("outra@jgm.com").build();
        LiberacaoAnexoEntity anexo = anexo(auxiliar);

        assertThatThrownBy(() -> service.remover(card.getId(), anexo.getId(), outra))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Só Auxiliar ou uma analista remove este anexo.");
        assertThat(anexo.getRemovidoEm()).isNull();
        verify(anexoRepository, never()).save(any());
        verify(liberacaoService, never()).registrarAnexo(any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("remover: anexo de quem foi removido do portal (enviadoPorId nulo) só a analista remove")
    void shouldOnlyLetAnalystRemoveAttachmentOfRemovedUser() {
        LiberacaoAnexoEntity anexo = anexo(null);

        assertThatThrownBy(() -> service.remover(card.getId(), anexo.getId(), auxiliar))
                .isInstanceOf(AcessoNegadoException.class);

        service.remover(card.getId(), anexo.getId(), analista);
        assertThat(anexo.getRemovidoEm()).isNotNull();
    }

    @Test
    @DisplayName("remover: anexo já removido é EntityNotFound")
    void shouldNotFindAlreadyRemovedAttachment() {
        LiberacaoAnexoEntity anexo = anexo(auxiliar);
        anexo.setRemovidoEm(LocalDateTime.now());

        assertThatThrownBy(() -> service.remover(card.getId(), anexo.getId(), auxiliar))
                .isInstanceOf(EntityNotFoundException.class);
        verify(anexoRepository, never()).save(any());
    }

    @Test
    @DisplayName("remover: anexo de outro card ou inexistente é EntityNotFound")
    void shouldNotFindAttachmentOfAnotherCard() {
        LiberacaoAnexoEntity doutro = anexo(auxiliar);
        doutro.setCardId(UUID.randomUUID());
        UUID inexistente = UUID.randomUUID();
        when(anexoRepository.findById(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(card.getId(), doutro.getId(), auxiliar))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.remover(card.getId(), inexistente, auxiliar))
                .isInstanceOf(EntityNotFoundException.class);
        verify(anexoRepository, never()).save(any());
    }

    @Test
    @DisplayName("remover: sem usuário autenticado é AcessoNegado")
    void shouldRequireAuthenticationToRemove() {
        LiberacaoAnexoEntity anexo = anexo(auxiliar);

        assertThatThrownBy(() -> service.remover(card.getId(), anexo.getId(), null))
                .isInstanceOf(AcessoNegadoException.class);
        assertThat(anexo.getRemovidoEm()).isNull();
    }

    // ------------------------------------------------------------------ leitura

    @Test
    @DisplayName("listar: só os não removidos, do mais novo para o mais antigo")
    void shouldListOnlyActiveAttachments() {
        LiberacaoAnexoEntity a = anexo(auxiliar);
        when(anexoRepository.findByCardIdAndRemovidoEmIsNullOrderByEnviadoEmDesc(card.getId())).thenReturn(List.of(a));

        assertThat(service.listar(card.getId())).containsExactly(a);
    }

    @Test
    @DisplayName("buscar: anexo removido ou de outro card não é devolvido")
    void shouldNotReturnRemovedOrForeignAttachment() {
        LiberacaoAnexoEntity ativo = anexo(auxiliar);
        LiberacaoAnexoEntity removido = anexo(auxiliar);
        removido.setRemovidoEm(LocalDateTime.now());

        assertThat(service.buscar(card.getId(), ativo.getId())).isSameAs(ativo);
        assertThatThrownBy(() -> service.buscar(card.getId(), removido.getId())).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.buscar(UUID.randomUUID(), ativo.getId())).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("conteudo: lê o byte do caminho relativo guardado no anexo")
    void shouldReadContentFromStoredPath() {
        LiberacaoAnexoEntity anexo = anexo(auxiliar);
        when(compartilhamento.ler(anexo.getCaminhoRelativo())).thenReturn(new byte[]{9, 8, 7});

        assertThat(service.conteudo(anexo)).containsExactly(9, 8, 7);
        verify(compartilhamento).ler(eq(CNPJ + "/liberacao/42/1-contrato-social.pdf"));
    }
}
