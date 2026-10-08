package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import com.portal.serasa.domain.model.liberacao.TipoEventoLiberacao;
import com.portal.serasa.domain.model.liberacao.TipoOperacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoComentarioEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEventoEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoSacadoEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoCardEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoCardJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoComentarioJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoEventoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMembroJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoParecerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoPendenciaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoSacadoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiberacaoExportServiceTest {

    @Mock private LiberacaoCardJpaRepository cardRepository;
    @Mock private LiberacaoSacadoJpaRepository sacadoRepository;
    @Mock private LiberacaoParecerJpaRepository parecerRepository;
    @Mock private LiberacaoPendenciaJpaRepository pendenciaRepository;
    @Mock private LiberacaoEventoJpaRepository eventoRepository;
    @Mock private LiberacaoComentarioJpaRepository comentarioRepository;
    @Mock private LiberacaoMembroJpaRepository membroRepository;
    @Mock private LiberacaoCardEtiquetaJpaRepository cardEtiquetaRepository;
    @Mock private LiberacaoEtiquetaJpaRepository etiquetaRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private LiberacaoExportService service;

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    private LiberacaoEventoEntity transicao(UUID cardId, EtapaLiberacao de, EtapaLiberacao para, LocalDateTime quando) {
        return LiberacaoEventoEntity.builder().id(UUID.randomUUID()).cardId(cardId).tipo(TipoEventoLiberacao.TRANSICAO)
                .etapaDe(de).etapaPara(para).usuarioNome("Andressa").criadoEm(quando).build();
    }

    @Test
    @DisplayName("horasPorEtapa: soma idas e voltas e para de contar na decisão")
    void shouldSumHoursPerStage() {
        UUID id = UUID.randomUUID();
        LiberacaoCardEntity card = LiberacaoCardEntity.builder().id(id).criadoEm(T0).etapa(EtapaLiberacao.APROVADO).build();
        List<LiberacaoEventoEntity> eventos = List.of(
                transicao(id, EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, T0.plusHours(2)),
                transicao(id, EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA, T0.plusHours(5)),
                transicao(id, EtapaLiberacao.PENDENCIA, EtapaLiberacao.COMITE, T0.plusHours(6).plusMinutes(30)),
                transicao(id, EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO, T0.plusHours(8)));

        Map<EtapaLiberacao, Double> horas = LiberacaoExportService.horasPorEtapa(card, eventos, T0.plusDays(30));

        assertThat(horas).containsEntry(EtapaLiberacao.ORIGEM, 2.0)
                .containsEntry(EtapaLiberacao.COMITE, 4.5)
                .containsEntry(EtapaLiberacao.PENDENCIA, 1.5)
                .doesNotContainKey(EtapaLiberacao.APROVADO);
    }

    @Test
    @DisplayName("exportar: seis abas, valor como número, menção como @Nome, apagado fica de fora")
    void shouldBuildWorkbook() throws Exception {
        UUID id = UUID.randomUUID();
        LiberacaoCardEntity card = LiberacaoCardEntity.builder().id(id).numero(12L).etapa(EtapaLiberacao.APROVADO).rodada(1)
                .cedenteCnpj("11222333000181").cedenteNome("ACME LTDA").tipoOperacao(TipoOperacao.DUPLICATA)
                .valor(new BigDecimal("80000.50")).criadoPorNome("Aline").criadoEm(T0).atualizadoPorNome("Andressa")
                .atualizadoEm(T0.plusHours(8)).finalizadoEm(T0.plusHours(8))
                .parecerOrigem("Ver com @[Andressa](user:" + UUID.randomUUID() + ")").build();
        LiberacaoCardEntity apagado = LiberacaoCardEntity.builder().id(UUID.randomUUID()).numero(13L).excluidoEm(T0).build();
        when(cardRepository.findAllById(any())).thenReturn(List.of(card, apagado));
        when(sacadoRepository.findByCardIdIn(any())).thenReturn(List.of(
                LiberacaoSacadoEntity.builder().cardId(id).cnpj("11444777000161").nome("BETA SA").valor(new BigDecimal("80000.50")).ordem(0).build()));
        when(parecerRepository.findByCardIdOrderByRodadaDescCriadoEm(id)).thenReturn(List.of(
                LiberacaoParecerEntity.builder().cardId(id).rodada(1).usuarioNome("Andressa").posicao(PosicaoParecer.FAVORAVEL)
                        .texto("ok").registradoEm(T0.plusHours(3)).criadoEm(T0.plusHours(2)).build()));
        when(eventoRepository.findByCardIdInOrderByCriadoEm(any())).thenReturn(List.of(
                transicao(id, EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, T0.plusHours(2)),
                transicao(id, EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO, T0.plusHours(8))));
        when(comentarioRepository.findByCardIdInAndExcluidoEmIsNullOrderByCriadoEm(any())).thenReturn(List.of(
                LiberacaoComentarioEntity.builder().cardId(id).autorNome("Aline").texto("Feito").criadoEm(T0.plusHours(4)).build()));

        byte[] arquivo = service.exportar(List.of(id, apagado.getId()));

        try (XSSFWorkbook planilha = new XSSFWorkbook(new ByteArrayInputStream(arquivo))) {
            assertThat(planilha.getNumberOfSheets()).isEqualTo(6);
            assertThat(planilha.getSheetName(0)).isEqualTo("Cards");
            Sheet cards = planilha.getSheet("Cards");
            assertThat(cards.getLastRowNum()).isEqualTo(1);
            assertThat(cards.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(12);
            assertThat(cards.getRow(1).getCell(2).getStringCellValue()).isEqualTo("11.222.333/0001-81");
            assertThat(cards.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(80000.50);
            assertThat(cards.getRow(1).getCell(13).getStringCellValue()).startsWith("Andressa: Favorável");
            assertThat(cards.getRow(1).getCell(16).getStringCellValue()).isEqualTo("Aprovado");
            assertThat(cards.getRow(1).getCell(20).getNumericCellValue()).isEqualTo(6.0);
            assertThat(cards.getRow(1).getCell(22).getStringCellValue()).isEqualTo("Ver com @Andressa");
            assertThat(planilha.getSheet("Sacados").getRow(1).getCell(3).getStringCellValue()).isEqualTo("BETA SA");
            assertThat(planilha.getSheet("Histórico").getLastRowNum()).isEqualTo(2);
            assertThat(planilha.getSheet("Comentários").getRow(1).getCell(4).getStringCellValue()).isEqualTo("Feito");
        }
    }

    @Test
    @DisplayName("exportar: sem cards é recusado")
    void shouldRejectEmpty() {
        assertThatThrownBy(() -> service.exportar(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
