package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.model.liberacao.CarteiraSacado;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import com.portal.serasa.domain.model.liberacao.PropostaAr;
import com.portal.serasa.domain.model.liberacao.ResultadoLiberacao;
import com.portal.serasa.domain.model.liberacao.TipoEventoLiberacao;
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
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
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
import static org.mockito.ArgumentMatchers.anyCollection;
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
    @Mock private EmpresaResolver empresaResolver;

    @InjectMocks private LiberacaoExportService service;

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    private static final String CEDENTE = "11222333000181";
    private static final String SACADO_A = "11444777000161";
    private static final String SACADO_B = "45723174000110";
    private static final String CPF = "52998224725";

    private LiberacaoEventoEntity transicao(UUID cardId, EtapaLiberacao de, EtapaLiberacao para, LocalDateTime quando) {
        return LiberacaoEventoEntity.builder().id(UUID.randomUUID()).cardId(cardId).tipo(TipoEventoLiberacao.TRANSICAO)
                .etapaDe(de).etapaPara(para).usuarioNome("Andressa").criadoEm(quando).build();
    }

    private LiberacaoEventoEntity reabertura(UUID cardId, EtapaLiberacao de, EtapaLiberacao para, LocalDateTime quando) {
        return LiberacaoEventoEntity.builder().id(UUID.randomUUID()).cardId(cardId).tipo(TipoEventoLiberacao.REABERTURA)
                .etapaDe(de).etapaPara(para).usuarioNome("Andressa").criadoEm(quando).build();
    }

    private LiberacaoCardEntity card(UUID id, long numero, EtapaLiberacao etapa, ResultadoLiberacao resultado) {
        return LiberacaoCardEntity.builder().id(id).numero(numero).etapa(etapa).rodada(1).resultado(resultado)
                .cedenteCnpj(CEDENTE).cedenteNome("ACME LTDA").tipoOperacao("Duplicata")
                .valor(new BigDecimal("80000.50")).criadoPorNome("Aline").criadoEm(T0).atualizadoPorNome("Andressa")
                .atualizadoEm(T0.plusHours(8)).build();
    }

    private LiberacaoSacadoEntity sacado(UUID cardId, String cnpj, String nome, String valor, int ordem,
                                         ResultadoLiberacao situacao, String valorAprovado, LocalDateTime em) {
        return LiberacaoSacadoEntity.builder().cardId(cardId).cnpj(cnpj).nome(nome)
                .valor(valor == null ? null : new BigDecimal(valor)).ordem(ordem).situacao(situacao)
                .valorAprovado(valorAprovado == null ? null : new BigDecimal(valorAprovado))
                .situacaoPorNome(situacao == null ? null : "Andressa").situacaoEm(em).build();
    }

    private XSSFWorkbook exportar(UUID... ids) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(service.exportar(List.of(ids))));
    }

    @Test
    @DisplayName("horasPorEtapa: soma idas e voltas e para de contar na finalização")
    void shouldSumHoursPerStage() {
        UUID id = UUID.randomUUID();
        LiberacaoCardEntity card = LiberacaoCardEntity.builder().id(id).criadoEm(T0).etapa(EtapaLiberacao.FINALIZADO).build();
        List<LiberacaoEventoEntity> eventos = List.of(
                transicao(id, EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, T0.plusHours(2)),
                transicao(id, EtapaLiberacao.COMITE, EtapaLiberacao.PENDENCIA, T0.plusHours(5)),
                transicao(id, EtapaLiberacao.PENDENCIA, EtapaLiberacao.COMITE, T0.plusHours(6).plusMinutes(30)),
                transicao(id, EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO, T0.plusHours(8)));

        Map<EtapaLiberacao, Double> horas = LiberacaoExportService.horasPorEtapa(card, eventos, T0.plusDays(30));

        assertThat(horas).containsEntry(EtapaLiberacao.ORIGEM, 2.0)
                .containsEntry(EtapaLiberacao.COMITE, 4.5)
                .containsEntry(EtapaLiberacao.PENDENCIA, 1.5)
                .doesNotContainKey(EtapaLiberacao.FINALIZADO);
    }

    @Test
    @DisplayName("horasPorEtapa: reabertura volta a contar no Comitê, e o tempo finalizado fica de fora")
    void shouldCountReopenedTimeInComiteButNotFinalizedTime() {
        UUID id = UUID.randomUUID();
        LiberacaoCardEntity card = LiberacaoCardEntity.builder().id(id).criadoEm(T0).etapa(EtapaLiberacao.COMITE).build();
        List<LiberacaoEventoEntity> eventos = List.of(
                transicao(id, EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, T0.plusHours(2)),
                transicao(id, EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO, T0.plusHours(8)),
                reabertura(id, EtapaLiberacao.FINALIZADO, EtapaLiberacao.COMITE, T0.plusHours(20)));

        Map<EtapaLiberacao, Double> horas = LiberacaoExportService.horasPorEtapa(card, eventos, T0.plusHours(22));

        // Comitê: 6h na primeira rodada + 2h depois de reaberto. As 12h finalizado não contam.
        assertThat(horas).containsEntry(EtapaLiberacao.ORIGEM, 2.0)
                .containsEntry(EtapaLiberacao.COMITE, 8.0)
                .doesNotContainKey(EtapaLiberacao.FINALIZADO);
    }

    @Test
    @DisplayName("exportar: seis abas, colunas novas do card finalizado, menção como @Nome, apagado fica de fora")
    void shouldBuildWorkbook() throws Exception {
        UUID id = UUID.randomUUID();
        LiberacaoCardEntity card = card(id, 12L, EtapaLiberacao.FINALIZADO, ResultadoLiberacao.APROVADO);
        card.setFinalizadoEm(T0.plusHours(8));
        card.setPosicaoOrigem(PosicaoParecer.COM_RESSALVAS);
        card.setParecerOrigem("Ver com @[Andressa](user:" + UUID.randomUUID() + ")");
        LiberacaoCardEntity apagado = LiberacaoCardEntity.builder().id(UUID.randomUUID()).numero(13L).excluidoEm(T0).build();
        when(cardRepository.findAllById(any())).thenReturn(List.of(card, apagado));
        when(empresaResolver.resolver(anyCollection())).thenReturn(Map.of(
                CEDENTE, new EmpresaResolver.Empresa("ACME LTDA", "Bauru", "SP", true),
                SACADO_A, new EmpresaResolver.Empresa("BETA SA DA BASE", "Campinas", "SP", true)));
        when(sacadoRepository.findByCardIdIn(any())).thenReturn(List.of(
                sacado(id, SACADO_A, "BETA SA", "80000.50", 0, ResultadoLiberacao.APROVADO, null, T0.plusHours(8))));
        when(parecerRepository.findByCardIdOrderByRodadaDescCriadoEm(id)).thenReturn(List.of(
                LiberacaoParecerEntity.builder().cardId(id).rodada(1).usuarioNome("Andressa").posicao(PosicaoParecer.FAVORAVEL)
                        .texto("ok").registradoEm(T0.plusHours(3)).criadoEm(T0.plusHours(2)).build()));
        when(eventoRepository.findByCardIdInOrderByCriadoEm(any())).thenReturn(List.of(
                transicao(id, EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, T0.plusHours(2)),
                transicao(id, EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO, T0.plusHours(8))));
        when(comentarioRepository.findByCardIdInAndExcluidoEmIsNullOrderByCriadoEm(any())).thenReturn(List.of(
                LiberacaoComentarioEntity.builder().cardId(id).autorNome("Aline").texto("Feito").criadoEm(T0.plusHours(4)).build()));

        try (XSSFWorkbook planilha = exportar(id, apagado.getId())) {
            assertThat(planilha.getNumberOfSheets()).isEqualTo(6);
            assertThat(planilha.getSheetName(0)).isEqualTo("Cards");
            Sheet cards = planilha.getSheet("Cards");
            assertThat(cards.getLastRowNum()).isEqualTo(1);
            assertThat(cards.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Praça do cedente");
            assertThat(cards.getRow(0).getCell(17).getStringCellValue()).isEqualTo("Resultado");
            assertThat(cards.getRow(0).getCell(18).getStringCellValue()).isEqualTo("Valor aprovado");
            assertThat(cards.getRow(0).getCell(19).getStringCellValue()).isEqualTo("Decidido por");
            assertThat(cards.getRow(0).getCell(20).getStringCellValue()).isEqualTo("Decidido em");
            assertThat(cards.getRow(0).getCell(25).getStringCellValue()).isEqualTo("Parecer da origem");
            assertThat(cards.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(12);
            assertThat(cards.getRow(1).getCell(2).getStringCellValue()).isEqualTo("11.222.333/0001-81");
            assertThat(cards.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Bauru/SP");
            assertThat(cards.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Finalizados");
            assertThat(cards.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(80000.50);
            assertThat(cards.getRow(1).getCell(14).getStringCellValue()).startsWith("Andressa: Favorável");
            assertThat(cards.getRow(1).getCell(17).getStringCellValue()).isEqualTo("Aprovado");
            assertThat(cards.getRow(1).getCell(18).getNumericCellValue()).isEqualTo(80000.50);
            assertThat(cards.getRow(1).getCell(19).getStringCellValue()).isEqualTo("Andressa");
            assertThat(cards.getRow(1).getCell(20).getLocalDateTimeCellValue()).isEqualTo(T0.plusHours(8));
            assertThat(cards.getRow(1).getCell(21).getNumericCellValue()).isEqualTo(2.0);
            assertThat(cards.getRow(1).getCell(22).getNumericCellValue()).isEqualTo(6.0);
            assertThat(cards.getRow(1).getCell(24).getStringCellValue()).isEqualTo("Com ressalvas");
            assertThat(cards.getRow(1).getCell(25).getStringCellValue()).isEqualTo("Ver com @Andressa");
            assertThat(planilha.getSheet("Sacados").getRow(1).getCell(3).getStringCellValue()).isEqualTo("BETA SA");
            assertThat(planilha.getSheet("Histórico").getLastRowNum()).isEqualTo(2);
            assertThat(planilha.getSheet("Comentários").getRow(1).getCell(4).getStringCellValue()).isEqualTo("Feito");
        }
    }

    @Test
    @DisplayName("exportar: aba Sacados traz praça, situação, valor aprovado, quem decidiu e quando")
    void shouldExportSacadoDecisions() throws Exception {
        UUID id = UUID.randomUUID();
        LiberacaoCardEntity card = card(id, 20L, EtapaLiberacao.FINALIZADO, ResultadoLiberacao.PARCIAL);
        when(cardRepository.findAllById(any())).thenReturn(List.of(card));
        when(empresaResolver.resolver(anyCollection())).thenReturn(Map.of(
                SACADO_A, new EmpresaResolver.Empresa("ALFA DA BASE", "Campinas", "SP", true)));
        when(sacadoRepository.findByCardIdIn(any())).thenReturn(List.of(
                // fora de ordem de propósito: a aba ordena pela ordem da lista do card
                sacado(id, SACADO_B, "GAMA SA", "10000", 2, ResultadoLiberacao.REPROVADO, null, T0.plusHours(9)),
                sacado(id, SACADO_A, null, "50000", 0, ResultadoLiberacao.APROVADO, null, T0.plusHours(7)),
                sacado(id, CPF, "Fulano de Tal", "20000", 1, ResultadoLiberacao.PARCIAL, "12000", T0.plusHours(8))));

        try (XSSFWorkbook planilha = exportar(id)) {
            Sheet sacados = planilha.getSheet("Sacados");
            assertThat(sacados.getLastRowNum()).isEqualTo(3);
            assertThat(sacados.getRow(0).getCell(6).getStringCellValue()).isEqualTo("Situação");
            assertThat(sacados.getRow(0).getCell(7).getStringCellValue()).isEqualTo("Valor aprovado");
            assertThat(sacados.getRow(0).getCell(8).getStringCellValue()).isEqualTo("Decidido por");
            assertThat(sacados.getRow(0).getCell(9).getStringCellValue()).isEqualTo("Decidido em");

            // aprovado: o valor aprovado é o valor inteiro do sacado; sem nome no card, usa o da base
            assertThat(sacados.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(20);
            assertThat(sacados.getRow(1).getCell(2).getStringCellValue()).isEqualTo("11.444.777/0001-61");
            assertThat(sacados.getRow(1).getCell(3).getStringCellValue()).isEqualTo("ALFA DA BASE");
            assertThat(sacados.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Campinas/SP");
            assertThat(sacados.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(50000.0);
            assertThat(sacados.getRow(1).getCell(6).getStringCellValue()).isEqualTo("Aprovado");
            assertThat(sacados.getRow(1).getCell(7).getNumericCellValue()).isEqualTo(50000.0);
            assertThat(sacados.getRow(1).getCell(8).getStringCellValue()).isEqualTo("Andressa");
            assertThat(sacados.getRow(1).getCell(9).getLocalDateTimeCellValue()).isEqualTo(T0.plusHours(7));

            // parcial: o valor aprovado é o que a analista informou
            assertThat(sacados.getRow(2).getCell(2).getStringCellValue()).isEqualTo("529.982.247-25");
            assertThat(sacados.getRow(2).getCell(6).getStringCellValue()).isEqualTo("Parcialmente aprovado");
            assertThat(sacados.getRow(2).getCell(5).getNumericCellValue()).isEqualTo(20000.0);
            assertThat(sacados.getRow(2).getCell(7).getNumericCellValue()).isEqualTo(12000.0);

            // reprovado: valor aprovado zero, e não vazio
            assertThat(sacados.getRow(3).getCell(6).getStringCellValue()).isEqualTo("Reprovado");
            assertThat(sacados.getRow(3).getCell(7).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sacados.getRow(3).getCell(7).getNumericCellValue()).isEqualTo(0.0);
            assertThat(sacados.getRow(3).getCell(8).getStringCellValue()).isEqualTo("Andressa");
        }
    }

    @Test
    @DisplayName("exportar: sacado ainda não decidido sai como 'A decidir', sem valor aprovado nem decisor")
    void shouldExportUndecidedSacadoAsPending() throws Exception {
        UUID id = UUID.randomUUID();
        when(cardRepository.findAllById(any())).thenReturn(List.of(card(id, 21L, EtapaLiberacao.COMITE, null)));
        when(sacadoRepository.findByCardIdIn(any())).thenReturn(List.of(
                sacado(id, SACADO_A, "ALFA SA", "50000", 0, null, null, null)));

        try (XSSFWorkbook planilha = exportar(id)) {
            Row linha = planilha.getSheet("Sacados").getRow(1);
            assertThat(linha.getCell(6).getStringCellValue()).isEqualTo("A decidir");
            assertThat(linha.getCell(7).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(linha.getCell(8).getStringCellValue()).isEmpty();
            assertThat(linha.getCell(9).getCellType()).isEqualTo(CellType.BLANK);
        }
    }

    @Test
    @DisplayName("exportar: valor aprovado do card soma aprovado inteiro + parcial + reprovado zero")
    void shouldSumApprovedValueOfCard() throws Exception {
        UUID id = UUID.randomUUID();
        when(cardRepository.findAllById(any())).thenReturn(List.of(card(id, 22L, EtapaLiberacao.FINALIZADO, ResultadoLiberacao.PARCIAL)));
        when(sacadoRepository.findByCardIdIn(any())).thenReturn(List.of(
                sacado(id, SACADO_A, "ALFA SA", "50000", 0, ResultadoLiberacao.APROVADO, null, T0),
                sacado(id, CPF, "Fulano", "20000", 1, ResultadoLiberacao.PARCIAL, "12000", T0),
                sacado(id, SACADO_B, "GAMA SA", "10000", 2, ResultadoLiberacao.REPROVADO, null, T0)));
        when(eventoRepository.findByCardIdInOrderByCriadoEm(any())).thenReturn(List.of(
                transicao(id, EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO, T0.plusHours(8))));

        try (XSSFWorkbook planilha = exportar(id)) {
            Row linha = planilha.getSheet("Cards").getRow(1);
            assertThat(linha.getCell(17).getStringCellValue()).isEqualTo("Parcialmente aprovado");
            assertThat(linha.getCell(18).getNumericCellValue()).isEqualTo(62000.0);
        }
    }

    @Test
    @DisplayName("exportar: card em andamento sai sem resultado, valor aprovado, decisor nem data")
    void shouldLeaveDecisionColumnsEmptyWhileInProgress() throws Exception {
        UUID id = UUID.randomUUID();
        when(cardRepository.findAllById(any())).thenReturn(List.of(card(id, 23L, EtapaLiberacao.COMITE, null)));
        when(sacadoRepository.findByCardIdIn(any())).thenReturn(List.of(
                sacado(id, SACADO_A, "ALFA SA", "50000", 0, null, null, null)));
        when(eventoRepository.findByCardIdInOrderByCriadoEm(any())).thenReturn(List.of(
                transicao(id, EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, T0.plusHours(2))));

        try (XSSFWorkbook planilha = exportar(id)) {
            Row linha = planilha.getSheet("Cards").getRow(1);
            assertThat(linha.getCell(4).getStringCellValue()).isEqualTo("Comitê");
            assertThat(linha.getCell(17).getStringCellValue()).isEmpty();
            assertThat(linha.getCell(18).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(linha.getCell(19).getStringCellValue()).isEmpty();
            assertThat(linha.getCell(20).getCellType()).isEqualTo(CellType.BLANK);
        }
    }

    @Test
    @DisplayName("exportar: card reaberto não mostra a decisão da rodada anterior como decisão atual")
    void shouldNotShowPreviousDecisionAfterReopening() throws Exception {
        UUID id = UUID.randomUUID();
        // reaberto: voltou ao Comitê, o resultado foi limpo e a decisão antiga ainda está no histórico
        when(cardRepository.findAllById(any())).thenReturn(List.of(card(id, 24L, EtapaLiberacao.COMITE, null)));
        when(sacadoRepository.findByCardIdIn(any())).thenReturn(List.of(
                sacado(id, SACADO_A, "ALFA SA", "50000", 0, ResultadoLiberacao.APROVADO, null, T0.plusHours(8))));
        when(eventoRepository.findByCardIdInOrderByCriadoEm(any())).thenReturn(List.of(
                transicao(id, EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, T0.plusHours(2)),
                transicao(id, EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO, T0.plusHours(8)),
                reabertura(id, EtapaLiberacao.FINALIZADO, EtapaLiberacao.COMITE, T0.plusHours(20))));

        try (XSSFWorkbook planilha = exportar(id)) {
            Row linha = planilha.getSheet("Cards").getRow(1);
            assertThat(linha.getCell(17).getStringCellValue()).isEmpty();
            assertThat(linha.getCell(19).getStringCellValue()).isEmpty();
            assertThat(linha.getCell(20).getCellType()).isEqualTo(CellType.BLANK);
            // a decisão do sacado continua gravada nele, e portanto na planilha
            assertThat(linha.getCell(18).getNumericCellValue()).isEqualTo(50000.0);
        }
    }

    @Test
    @DisplayName("exportar: a aba Histórico rotula reabertura e anexos, e mostra o resultado como 'Depois' da finalização")
    void shouldLabelHistoryEvents() throws Exception {
        UUID id = UUID.randomUUID();
        when(cardRepository.findAllById(any())).thenReturn(List.of(card(id, 25L, EtapaLiberacao.COMITE, null)));
        LiberacaoEventoEntity finalizacao = transicao(id, EtapaLiberacao.COMITE, EtapaLiberacao.FINALIZADO, T0.plusHours(8));
        finalizacao.setCampo("resultado");
        finalizacao.setValorDepois("Parcialmente aprovado");
        LiberacaoEventoEntity anexo = LiberacaoEventoEntity.builder().id(UUID.randomUUID()).cardId(id)
                .tipo(TipoEventoLiberacao.ANEXO_ADICIONADO).usuarioNome("Aline").texto("Contrato.pdf").criadoEm(T0.plusHours(9)).build();
        LiberacaoEventoEntity anexoRemovido = LiberacaoEventoEntity.builder().id(UUID.randomUUID()).cardId(id)
                .tipo(TipoEventoLiberacao.ANEXO_REMOVIDO).usuarioNome("Aline").texto("Contrato.pdf").criadoEm(T0.plusHours(10)).build();
        when(eventoRepository.findByCardIdInOrderByCriadoEm(any())).thenReturn(List.of(
                finalizacao, reabertura(id, EtapaLiberacao.FINALIZADO, EtapaLiberacao.COMITE, T0.plusHours(20)),
                anexo, anexoRemovido));

        try (XSSFWorkbook planilha = exportar(id)) {
            Sheet historico = planilha.getSheet("Histórico");
            assertThat(historico.getLastRowNum()).isEqualTo(4);
            assertThat(historico.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Movimento");
            assertThat(historico.getRow(1).getCell(6).getStringCellValue()).isEqualTo("Finalizados");
            assertThat(historico.getRow(1).getCell(7).getStringCellValue()).isEqualTo("resultado");
            assertThat(historico.getRow(1).getCell(9).getStringCellValue()).isEqualTo("Parcialmente aprovado");
            assertThat(historico.getRow(2).getCell(4).getStringCellValue()).isEqualTo("Reabertura");
            assertThat(historico.getRow(2).getCell(5).getStringCellValue()).isEqualTo("Finalizados");
            assertThat(historico.getRow(3).getCell(4).getStringCellValue()).isEqualTo("Anexo enviado");
            assertThat(historico.getRow(3).getCell(10).getStringCellValue()).isEqualTo("Contrato.pdf");
            assertThat(historico.getRow(4).getCell(4).getStringCellValue()).isEqualTo("Anexo removido");
        }
    }

    // ------------------------------------------------------------ Análise de Risco (AR) importada

    /** Cada campo com um valor diferente: coluna trocada de lugar na planilha aparece como diferença. */
    private static PropostaAr propostaDistinta(String emitidaEm) {
        return new PropostaAr("4821", emitidaEm, "GRUPO ACME",
                new BigDecimal("120500.00"), new BigDecimal("300000.00"),
                3, new BigDecimal("28.50"), new BigDecimal("21300.00"), new BigDecimal("745.25"), new BigDecimal("20554.75"),
                4, new BigDecimal("25800.00"),
                new BigDecimal("547000.00"), new BigDecimal("12000.00"), new BigDecimal("8000.00"),
                new BigDecimal("3300.00"), new BigDecimal("40000.00"), new BigDecimal("43300.00"),
                new BigDecimal("35.25"), new BigDecimal("12.50"), new BigDecimal("1.10"),
                new BigDecimal("41.75"), new BigDecimal("14.00"), new BigDecimal("1.35"));
    }

    private static final String[] CABECALHO_AR_CARDS = {"AR emitida em", "Limite individual", "Comprometimento atual (%)",
            "Comprometimento após (%)", "Concentração após (%)", "Prazo médio (dias)", "Face liberados", "Desconto", "Líquido",
            "Vencidos do cedente", "A vencer do cedente", "Liquidados do cedente", "Recomprados do cedente"};

    private static final String[] CABECALHO_AR_SACADOS = {"Títulos na proposta", "Vencidos", "A vencer", "Abertos",
            "Liquidados", "Recomprados"};

    @Test
    @DisplayName("exportar: cabeçalho da AR — Cards nas colunas 26 a 38 e Sacados nas 10 a 15, sem mexer nas antigas")
    void shouldExportArHeaders() throws Exception {
        UUID id = UUID.randomUUID();
        when(cardRepository.findAllById(any())).thenReturn(List.of(card(id, 30L, EtapaLiberacao.COMITE, null)));
        when(sacadoRepository.findByCardIdIn(any())).thenReturn(List.of(
                sacado(id, SACADO_A, "ALFA SA", "50000", 0, null, null, null)));

        try (XSSFWorkbook planilha = exportar(id)) {
            Row cards = planilha.getSheet("Cards").getRow(0);
            assertThat(cards.getLastCellNum()).as("39 colunas: 0 a 38").isEqualTo((short) 39);
            for (int i = 0; i < CABECALHO_AR_CARDS.length; i++) {
                assertThat(cards.getCell(26 + i).getStringCellValue()).as("Cards, coluna %d", 26 + i).isEqualTo(CABECALHO_AR_CARDS[i]);
            }
            // as colunas que já existiam não se mexeram
            assertThat(cards.getCell(24).getStringCellValue()).isEqualTo("Posição da origem");
            assertThat(cards.getCell(25).getStringCellValue()).isEqualTo("Parecer da origem");

            Row sacados = planilha.getSheet("Sacados").getRow(0);
            assertThat(sacados.getLastCellNum()).as("16 colunas: 0 a 15").isEqualTo((short) 16);
            for (int i = 0; i < CABECALHO_AR_SACADOS.length; i++) {
                assertThat(sacados.getCell(10 + i).getStringCellValue()).as("Sacados, coluna %d", 10 + i).isEqualTo(CABECALHO_AR_SACADOS[i]);
            }
            assertThat(sacados.getCell(9).getStringCellValue()).isEqualTo("Decidido em");
        }
    }

    @Test
    @DisplayName("exportar: o filtro automático cobre as colunas novas (Cards até AM, Sacados até P)")
    void shouldExtendAutoFilterToArColumns() throws Exception {
        UUID id = UUID.randomUUID();
        when(cardRepository.findAllById(any())).thenReturn(List.of(card(id, 30L, EtapaLiberacao.COMITE, null)));
        when(sacadoRepository.findByCardIdIn(any())).thenReturn(List.of(
                sacado(id, SACADO_A, "ALFA SA", "50000", 0, null, null, null)));

        try (XSSFWorkbook planilha = exportar(id)) {
            XSSFSheet cards = planilha.getSheet("Cards");
            XSSFSheet sacados = planilha.getSheet("Sacados");
            assertThat(cards.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:AM2");
            assertThat(sacados.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:P2");
        }
    }

    @Test
    @DisplayName("exportar: card e sacado com AR saem com cada número na coluna certa e no formato certo")
    void shouldExportArValues() throws Exception {
        UUID id = UUID.randomUUID();
        LiberacaoCardEntity card = card(id, 30L, EtapaLiberacao.COMITE, null);
        card.setProposta(propostaDistinta("2026-10-08T15:59:13"));
        when(cardRepository.findAllById(any())).thenReturn(List.of(card));
        LiberacaoSacadoEntity comAr = sacado(id, SACADO_A, "ALFA SA", "6000", 0, null, null, null);
        comAr.setCarteira(new CarteiraSacado(2, new BigDecimal("800.00"), new BigDecimal("7200.00"),
                new BigDecimal("8000.00"), new BigDecimal("120000.00"), new BigDecimal("1500.00")));
        when(sacadoRepository.findByCardIdIn(any())).thenReturn(List.of(comAr));

        try (XSSFWorkbook planilha = exportar(id)) {
            Row linha = planilha.getSheet("Cards").getRow(1);
            assertThat(linha.getCell(26).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 10, 8, 15, 59, 13));
            assertThat(linha.getCell(27).getNumericCellValue()).as("Limite individual").isEqualTo(120500.00);
            assertThat(linha.getCell(28).getNumericCellValue()).as("Comprometimento atual (%)").isEqualTo(35.25);
            assertThat(linha.getCell(29).getNumericCellValue()).as("Comprometimento após (%)").isEqualTo(41.75);
            assertThat(linha.getCell(30).getNumericCellValue()).as("Concentração após (%)").isEqualTo(1.35);
            assertThat(linha.getCell(31).getNumericCellValue()).as("Prazo médio (dias)").isEqualTo(28.5);
            assertThat(linha.getCell(32).getNumericCellValue()).as("Face liberados").isEqualTo(21300.00);
            assertThat(linha.getCell(33).getNumericCellValue()).as("Desconto").isEqualTo(745.25);
            assertThat(linha.getCell(34).getNumericCellValue()).as("Líquido").isEqualTo(20554.75);
            assertThat(linha.getCell(35).getNumericCellValue()).as("Vencidos do cedente").isEqualTo(3300.00);
            assertThat(linha.getCell(36).getNumericCellValue()).as("A vencer do cedente").isEqualTo(40000.00);
            assertThat(linha.getCell(37).getNumericCellValue()).as("Liquidados do cedente").isEqualTo(547000.00);
            assertThat(linha.getCell(38).getNumericCellValue()).as("Recomprados do cedente").isEqualTo(8000.00);

            // formatos: data/hora, moeda, percentual com 2 casas e prazo com 1 casa
            assertThat(linha.getCell(26).getCellStyle().getDataFormatString()).isEqualTo("dd/mm/yyyy hh:mm");
            assertThat(linha.getCell(27).getCellStyle().getDataFormatString()).contains("R$").contains("#,##0.00");
            assertThat(linha.getCell(28).getCellStyle().getDataFormatString()).isEqualTo("0.00");
            assertThat(linha.getCell(31).getCellStyle().getDataFormatString()).isEqualTo("0.0");
            assertThat(linha.getCell(38).getCellStyle().getDataFormatString()).contains("R$");

            Row sacado = planilha.getSheet("Sacados").getRow(1);
            assertThat(sacado.getCell(10).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sacado.getCell(10).getNumericCellValue()).as("Títulos na proposta").isEqualTo(2.0);
            assertThat(sacado.getCell(11).getNumericCellValue()).as("Vencidos").isEqualTo(800.00);
            assertThat(sacado.getCell(12).getNumericCellValue()).as("A vencer").isEqualTo(7200.00);
            assertThat(sacado.getCell(13).getNumericCellValue()).as("Abertos").isEqualTo(8000.00);
            assertThat(sacado.getCell(14).getNumericCellValue()).as("Liquidados").isEqualTo(120000.00);
            assertThat(sacado.getCell(15).getNumericCellValue()).as("Recomprados").isEqualTo(1500.00);
            assertThat(sacado.getCell(10).getCellStyle().getDataFormatString()).isEqualTo("0");
            assertThat(sacado.getCell(11).getCellStyle().getDataFormatString()).contains("R$");
        }
    }

    @Test
    @DisplayName("exportar: card e sacado sem AR deixam as colunas da AR em branco, sem afetar o que tem AR na mesma planilha")
    void shouldLeaveArColumnsBlankWhenThereIsNoAr() throws Exception {
        UUID comAr = UUID.randomUUID();
        UUID semAr = UUID.randomUUID();
        LiberacaoCardEntity cardComAr = card(comAr, 30L, EtapaLiberacao.COMITE, null);
        cardComAr.setProposta(propostaDistinta("2026-10-08T15:59:13"));
        LiberacaoCardEntity cardSemAr = card(semAr, 31L, EtapaLiberacao.COMITE, null);
        when(cardRepository.findAllById(any())).thenReturn(List.of(cardSemAr, cardComAr));
        LiberacaoSacadoEntity sacadoComAr = sacado(comAr, SACADO_A, "ALFA SA", "6000", 0, null, null, null);
        sacadoComAr.setCarteira(new CarteiraSacado(1, BigDecimal.ZERO, new BigDecimal("7200.00"), new BigDecimal("7200.00"),
                new BigDecimal("1.00"), BigDecimal.ZERO));
        when(sacadoRepository.findByCardIdIn(any())).thenReturn(List.of(
                sacado(semAr, SACADO_B, "GAMA SA", "100", 0, null, null, null), sacadoComAr));

        try (XSSFWorkbook planilha = exportar(comAr, semAr)) {
            Sheet cards = planilha.getSheet("Cards");
            assertThat(cards.getLastRowNum()).isEqualTo(2);
            assertThat(cards.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(30);
            assertThat(cards.getRow(1).getCell(27).getNumericCellValue()).isEqualTo(120500.00);
            Row vazio = cards.getRow(2);
            assertThat(vazio.getCell(0).getNumericCellValue()).isEqualTo(31);
            for (int coluna = 26; coluna <= 38; coluna++) {
                assertThat(vazio.getCell(coluna)).as("Cards, coluna %d existe (com estilo)", coluna).isNotNull();
                assertThat(vazio.getCell(coluna).getCellType()).as("Cards, coluna %d", coluna).isEqualTo(CellType.BLANK);
            }

            Sheet sacados = planilha.getSheet("Sacados");
            assertThat(sacados.getLastRowNum()).isEqualTo(2);
            assertThat(sacados.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(30);
            assertThat(sacados.getRow(1).getCell(10).getNumericCellValue()).isEqualTo(1.0);
            Row semCarteira = sacados.getRow(2);
            assertThat(semCarteira.getCell(0).getNumericCellValue()).isEqualTo(31);
            for (int coluna = 10; coluna <= 15; coluna++) {
                assertThat(semCarteira.getCell(coluna).getCellType()).as("Sacados, coluna %d", coluna).isEqualTo(CellType.BLANK);
            }
        }
    }

    @Test
    @DisplayName("exportar: AR sem data de emissão deixa só a data em branco e preenche o resto")
    void shouldLeaveOnlyIssueDateBlankWhenArHasNoDate() throws Exception {
        UUID id = UUID.randomUUID();
        LiberacaoCardEntity card = card(id, 30L, EtapaLiberacao.COMITE, null);
        card.setProposta(propostaDistinta(null));
        when(cardRepository.findAllById(any())).thenReturn(List.of(card));

        try (XSSFWorkbook planilha = exportar(id)) {
            Row linha = planilha.getSheet("Cards").getRow(1);
            assertThat(linha.getCell(26).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(linha.getCell(32).getNumericCellValue()).isEqualTo(21300.00);
        }
    }

    @Test
    @DisplayName("exportar: AR com campos nulos (relatório incompleto) deixa só essas células em branco")
    void shouldLeaveNullArFieldsBlank() throws Exception {
        UUID id = UUID.randomUUID();
        LiberacaoCardEntity card = card(id, 30L, EtapaLiberacao.COMITE, null);
        card.setProposta(new PropostaAr("4821", "2026-10-08T15:59:13", null, null, null, null, null, new BigDecimal("21300.00"),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        when(cardRepository.findAllById(any())).thenReturn(List.of(card));

        try (XSSFWorkbook planilha = exportar(id)) {
            Row linha = planilha.getSheet("Cards").getRow(1);
            assertThat(linha.getCell(26).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 10, 8, 15, 59, 13));
            assertThat(linha.getCell(32).getNumericCellValue()).isEqualTo(21300.00);
            for (int coluna : new int[]{27, 28, 29, 30, 31, 33, 34, 35, 36, 37, 38}) {
                assertThat(linha.getCell(coluna).getCellType()).as("Cards, coluna %d", coluna).isEqualTo(CellType.BLANK);
            }
        }
    }

    @Test
    @DisplayName("exportar: sem cards é recusado")
    void shouldRejectEmpty() {
        assertThatThrownBy(() -> service.exportar(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
