package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.service.liberacao.PropostaArParser.Leitura;
import com.portal.serasa.application.service.liberacao.PropostaArParser.SacadoLido;
import com.portal.serasa.domain.model.liberacao.CarteiraSacado;
import com.portal.serasa.domain.model.liberacao.PropostaAr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.portal.serasa.application.service.liberacao.PropostaArTextos.CLIENTE;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.COLUNAS_SACADOS;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.DOC_BETA;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.DOC_DELTA;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.DOC_FULANO;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.DOC_GAMA;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.GRUPO_VAZIO;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.HORARIO;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.LINHA_BETA;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.LINHA_DELTA;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.LINHA_FULANO;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.LINHA_GAMA;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.OPERACAO;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.RISCO;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.RODAPE;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.TITULO;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.TOTAL_SACADOS;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.ar;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.sacadosPadrao;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Leitura do texto do PDF "ANÁLISE DE RISCO - AR". O texto é anonimizado e tem o layout real; ver
 * {@link PropostaArTextos}.
 */
class PropostaArParserTest {

    private final PropostaArParser parser = new PropostaArParser();

    private static BigDecimal n(String valor) {
        return new BigDecimal(valor);
    }

    private static CarteiraSacado carteira(int titulos, String vencidos, String vincendos, String abertos,
                                           String liquidados, String recomprados) {
        return new CarteiraSacado(titulos, n(vencidos), n(vincendos), n(abertos), n(liquidados), n(recomprados));
    }

    /** BigDecimal 0,00 == 0 para o teste: o que importa é o valor, não a escala. */
    private static org.assertj.core.api.RecursiveComparisonAssert<?> assertThatRecursivo(Object atual) {
        return assertThat(atual).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class);
    }

    // ------------------------------------------------------------ cabeçalho, operação, carteira, risco

    @Test
    @DisplayName("lerTexto: cliente, data/hora, grupo vazio e limites")
    void shouldReadHeader() {
        Leitura leitura = parser.lerTexto(ar());

        assertThat(leitura.clienteCodigo()).isEqualTo("4821");
        assertThat(leitura.clienteNome()).isEqualTo("ACME EMBALAGENS LTDA");
        PropostaAr resumo = leitura.resumo();
        assertThat(resumo.clienteCodigo()).isEqualTo("4821");
        assertThat(resumo.emitidaEm()).isEqualTo("2026-10-08T15:59:13");
        assertThat(resumo.grupo()).as("a linha de traços é 'sem grupo'").isNull();
        assertThat(resumo.limiteIndividual()).isEqualByComparingTo("120500.00");
        assertThat(resumo.limiteGrupo()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("lerTexto: todos os campos da proposta saem da coluna certa")
    void shouldReadEveryColumnOfTheProposal() {
        PropostaAr esperado = new PropostaAr(
                "4821", "2026-10-08T15:59:13", null, n("120500.00"), n("0.00"),
                // operação: qtd liberados, P.M., face, desconto, líquido, qtd total, valor total
                3, n("28.50"), n("21300.00"), n("745.25"), n("20554.75"), 4, n("25800.00"),
                // carteira: liquidados, liquidados em atraso, recomprados, vencidos, a vencer, em aberto
                n("547000.00"), n("12000.00"), n("8000.00"), n("3300.00"), n("40000.00"), n("43300.00"),
                // risco: atual (indiv., grupo, concentração) e após a compra (indiv., grupo, concentração)
                n("35.2500"), n("0.0000"), n("1.1000"), n("41.7500"), n("0.0000"), n("1.3500"));

        assertThatRecursivo(parser.lerTexto(ar()).resumo()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("lerTexto: os TOTAL de liquidados, recomprados, vencidos e a vencer não se confundem")
    void shouldKeepTotalsOfEachSectionApart() {
        PropostaAr resumo = parser.lerTexto(ar()).resumo();

        assertThat(resumo.liquidados()).isEqualByComparingTo("547000.00");
        assertThat(resumo.recomprados()).isEqualByComparingTo("8000.00");
        assertThat(resumo.vencidos()).isEqualByComparingTo("3300.00");
        assertThat(resumo.vincendos()).isEqualByComparingTo("40000.00");
        assertThat(resumo.emAberto()).isEqualByComparingTo("43300.00");
        // "TOTAL LIQUIDADO" (liquidados + recomprados) e "TOTAL EM ABERTO" não sobrescrevem o TOTAL da seção
        assertThat(resumo.liquidados()).isNotEqualByComparingTo("555000.00");
        assertThat(resumo.vincendos()).isNotEqualByComparingTo(resumo.emAberto());
    }

    @Test
    @DisplayName("lerTexto: 'EM ATRASO' só vale na seção de liquidados")
    void shouldReadOverdueSettledOnlyInSettledSection() {
        String texto = ar().replace("ATÉ 10 DIAS 100,00 200,00 0,00 300,00",
                "EM ATRASO 9.999,00 9.999,00 9.999,00 9.999,00");

        PropostaAr resumo = parser.lerTexto(texto).resumo();

        assertThat(resumo.liquidadosEmAtraso()).isEqualByComparingTo("12000.00");
    }

    @Test
    @DisplayName("lerTexto: a ordem das seções não importa — cada TOTAL vai para a seção em que está")
    void shouldAttributeTotalToTheSectionItBelongsTo() {
        String texto = String.join("\n",
                "ANÁLISE DE RISCO - AR",
                CLIENTE,
                "A VENCER A CONFIRMAR CONFIRMADOS NÃO CONFIRMADOS TOTAL",
                "TOTAL 1,00 2,00 3,00 4,00",
                "VENCIDOS A CONFIRMAR CONFIRMADOS NÃO CONFIRMADOS TOTAL",
                "TOTAL 5,00 6,00 7,00 8,00",
                "RECOMPRADOS A CONFIRMAR CONFIRMADOS NÃO CONFIRMADOS TOTAL",
                "TOTAL 9,00 10,00 11,00 12,00",
                "LIQUIDADOS A CONFIRMAR CONFIRMADOS NÃO CONFIRMADOS TOTAL",
                "TOTAL 13,00 14,00 15,00 16,00");

        PropostaAr resumo = parser.lerTexto(texto).resumo();

        assertThat(resumo.vincendos()).isEqualByComparingTo("4.00");
        assertThat(resumo.vencidos()).isEqualByComparingTo("8.00");
        assertThat(resumo.recomprados()).isEqualByComparingTo("12.00");
        assertThat(resumo.liquidados()).isEqualByComparingTo("16.00");
    }

    @Test
    @DisplayName("lerTexto: sem seção de risco, os seis percentuais ficam nulos")
    void shouldLeavePercentagesNullWithoutRiskSection() {
        PropostaAr resumo = parser.lerTexto(ar(CLIENTE, HORARIO, GRUPO_VAZIO, OPERACAO, null, sacadosPadrao())).resumo();

        assertThat(resumo.comprometimentoAtual()).isNull();
        assertThat(resumo.comprometimentoGrupoAtual()).isNull();
        assertThat(resumo.concentracaoAtual()).isNull();
        assertThat(resumo.comprometimentoApos()).isNull();
        assertThat(resumo.comprometimentoGrupoApos()).isNull();
        assertThat(resumo.concentracaoApos()).isNull();
        assertThat(resumo.faceLiberados()).as("o resto continua lido").isEqualByComparingTo("21300.00");
    }

    @Test
    @DisplayName("lerTexto: sem a linha de operação, quantidades e valores da operação ficam nulos")
    void shouldLeaveOperationNullWhenMissing() {
        PropostaAr resumo = parser.lerTexto(ar(CLIENTE, HORARIO, GRUPO_VAZIO, null, RISCO, sacadosPadrao())).resumo();

        assertThat(resumo.qtdLiberados()).isNull();
        assertThat(resumo.prazoMedio()).isNull();
        assertThat(resumo.faceLiberados()).isNull();
        assertThat(resumo.desconto()).isNull();
        assertThat(resumo.liquido()).isNull();
        assertThat(resumo.qtdTotal()).isNull();
        assertThat(resumo.valorTotal()).isNull();
        assertThat(resumo.comprometimentoApos()).isEqualByComparingTo("41.75");
    }

    // ------------------------------------------------------------------------------ grupo e horário

    @ParameterizedTest(name = "[{index}] \"{0}\" -> grupo \"{1}\", individual {2}, grupo {3}")
    @CsvSource(delimiter = '|', value = {
            "GRUPO    : GRUPO ACME 300.000,00 500.000,00|GRUPO ACME|300000.00|500000.00",
            "GRUPO    : IRMAOS FICTICIOS & CIA - SUL 1.250,50 2.000.000,00|IRMAOS FICTICIOS & CIA - SUL|1250.50|2000000.00",
            "GRUPO    : GRUPO 2000 ACME 10,00 20,00|GRUPO 2000 ACME|10.00|20.00",
            "GRUPO : ACME 0,00 0,00|ACME|0.00|0.00"})
    @DisplayName("lerTexto: grupo com nome de verdade traz o nome e os dois limites")
    void shouldReadNamedGroupAndLimits(String linha, String grupo, String individual, String limiteGrupo) {
        PropostaAr resumo = parser.lerTexto(ar(CLIENTE, HORARIO, linha, OPERACAO, RISCO, sacadosPadrao())).resumo();

        assertThat(resumo.grupo()).isEqualTo(grupo);
        assertThat(resumo.limiteIndividual()).isEqualByComparingTo(individual);
        assertThat(resumo.limiteGrupo()).isEqualByComparingTo(limiteGrupo);
    }

    @Test
    @DisplayName("lerTexto: grupo nomeado lê também os percentuais de grupo do risco")
    void shouldReadGroupPercentages() {
        String risco = "35,2500 12,5000 1,1000 41,7500 14,0000 1,3500";

        PropostaAr resumo = parser.lerTexto(ar(CLIENTE, HORARIO, "GRUPO    : GRUPO ACME 300.000,00 500.000,00",
                OPERACAO, risco, sacadosPadrao())).resumo();

        assertThat(resumo.comprometimentoGrupoAtual()).isEqualByComparingTo("12.5");
        assertThat(resumo.comprometimentoGrupoApos()).isEqualByComparingTo("14.0");
    }

    @Test
    @DisplayName("lerTexto: HORARIO só com HH:mm completa com ':00'")
    void shouldAppendZeroSecondsWhenTimeHasNoSeconds() {
        PropostaAr resumo = parser.lerTexto(ar(CLIENTE, "HORARIO  : 09:05 INDIVIDUAL GRUPO", GRUPO_VAZIO, OPERACAO,
                RISCO, sacadosPadrao())).resumo();

        assertThat(resumo.emitidaEm()).isEqualTo("2026-10-08T09:05:00");
    }

    @Test
    @DisplayName("lerTexto: sem HORARIO a data fica à meia-noite; sem DATA não há emitidaEm")
    void shouldHandleMissingTimeAndDate() {
        assertThat(parser.lerTexto(ar(CLIENTE, null, GRUPO_VAZIO, OPERACAO, RISCO, sacadosPadrao())).resumo().emitidaEm())
                .isEqualTo("2026-10-08T00:00:00");

        String semData = ar().replace("DATA     : 08/10/2026 LIMITES\n", "");
        assertThat(parser.lerTexto(semData).resumo().emitidaEm()).isNull();
    }

    // --------------------------------------------------------------------------------------- sacados

    @Test
    @DisplayName("lerTexto: sacados com e sem código interno, CNPJ e CPF; documento só com dígitos")
    void shouldReadSacados() {
        Leitura leitura = parser.lerTexto(ar());

        List<SacadoLido> esperados = List.of(
                new SacadoLido("11222333000181", "BETA COMERCIAL LTDA", n("0.00"),
                        carteira(0, "0.00", "5000.00", "5000.00", "32000.00", "0.00")),
                new SacadoLido("11444777000161", "GAMA DISTRIBUIDORA S/A", n("6000.00"),
                        carteira(1, "800.00", "7200.00", "8000.00", "120000.00", "1500.00")),
                // sem código interno: o nome inteiro é o nome
                new SacadoLido("45723174000110", "DELTA ATACADO LTDA", n("9300.00"),
                        carteira(1, "0.00", "4100.00", "4100.00", "45500.00", "0.00")),
                // CPF: 11 dígitos
                new SacadoLido("52998224725", "FULANO DE TAL", n("6000.00"),
                        carteira(1, "250.00", "0.00", "250.00", "3000.00", "600.00")));
        assertThatRecursivo(leitura.sacados()).isEqualTo(esperados);
    }

    @Test
    @DisplayName("lerTexto: linha TOTAL dos sacados vira totalTitulos e totalFace, e não entra como sacado")
    void shouldReadSacadosTotalLine() {
        Leitura leitura = parser.lerTexto(ar());

        assertThat(leitura.totalTitulos()).isEqualTo(3);
        assertThat(leitura.totalFace()).isEqualByComparingTo("21300.00");
        assertThat(leitura.sacados()).extracting(SacadoLido::nome).doesNotContain("TOTAL");
        assertThat(leitura.sacados()).hasSize(4);
    }

    @Test
    @DisplayName("lerTexto: sem a linha TOTAL, totalTitulos e totalFace ficam nulos")
    void shouldLeaveTotalNullWhenMissing() {
        List<String> semTotal = sacadosPadrao().subList(0, sacadosPadrao().size() - 1);

        Leitura leitura = parser.lerTexto(ar(CLIENTE, HORARIO, GRUPO_VAZIO, OPERACAO, RISCO, semTotal));

        assertThat(leitura.totalTitulos()).isNull();
        assertThat(leitura.totalFace()).isNull();
        assertThat(leitura.sacados()).hasSize(4);
    }

    @Test
    @DisplayName("lerTexto: nome com números e barras e com código interno de 3 a 6 dígitos")
    void shouldReadSacadoNamesWithDigitsAndSlashes() {
        List<String> sacados = List.of(
                DOC_BETA, "046 3M FICTICIA 2 FILIAL 10 1 100,00 0,00 0,00 0,00 0,00 0,00",
                DOC_GAMA, "123456 COMERCIO 123 S/A - ME 12 2.500,00 1,00 2,00 3,00 4,00 5,00");

        Leitura leitura = parser.lerTexto(ar(CLIENTE, HORARIO, GRUPO_VAZIO, OPERACAO, RISCO, sacados));

        assertThat(leitura.sacados()).extracting(SacadoLido::nome)
                .containsExactly("3M FICTICIA 2 FILIAL 10", "COMERCIO 123 S/A - ME");
        // o número que fecha o nome não é confundido com a quantidade de títulos
        assertThat(leitura.sacados()).extracting(sacado -> sacado.carteira().titulos()).containsExactly(1, 12);
        assertThat(leitura.sacados().get(0).face()).isEqualByComparingTo("100.00");
        assertThat(leitura.sacados().get(1).face()).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("lerTexto: sacado repetido no relatório sai uma vez por linha lida (a junção é do service)")
    void shouldKeepRepeatedDocumentAsSeparateRows() {
        List<String> sacados = List.of(DOC_GAMA, LINHA_GAMA, DOC_GAMA, "00047 GAMA DISTRIBUIDORA S/A 2 4.000,00 0,00 0,00 0,00 0,00 0,00");

        Leitura leitura = parser.lerTexto(ar(CLIENTE, HORARIO, GRUPO_VAZIO, OPERACAO, RISCO, sacados));

        assertThat(leitura.sacados()).extracting(SacadoLido::documento).containsExactly("11444777000161", "11444777000161");
    }

    @Test
    @DisplayName("lerTexto: linha de sacado sem a linha do documento antes é ignorada")
    void shouldIgnoreSacadoRowWithoutPrecedingDocument() {
        // 1) logo depois do título da seção; 2) depois de uma linha já consumida
        List<String> sacados = List.of(
                LINHA_BETA,
                DOC_GAMA, LINHA_GAMA,
                "00099 ORFAO SEM DOCUMENTO LTDA 1 777,00 0,00 0,00 0,00 0,00 0,00",
                DOC_DELTA, LINHA_DELTA);

        Leitura leitura = parser.lerTexto(ar(CLIENTE, HORARIO, GRUPO_VAZIO, OPERACAO, RISCO, sacados));

        assertThat(leitura.sacados()).extracting(SacadoLido::documento).containsExactly("11444777000161", "45723174000110");
        assertThat(leitura.sacados()).extracting(SacadoLido::nome).doesNotContain("ORFAO SEM DOCUMENTO LTDA", "BETA COMERCIAL LTDA");
    }

    @Test
    @DisplayName("lerTexto: documento sem a linha do sacado depois é substituído pelo documento seguinte")
    void shouldDropDocumentWithoutRow() {
        List<String> sacados = List.of(DOC_BETA, DOC_GAMA, LINHA_GAMA);

        Leitura leitura = parser.lerTexto(ar(CLIENTE, HORARIO, GRUPO_VAZIO, OPERACAO, RISCO, sacados));

        assertThat(leitura.sacados()).singleElement().satisfies(sacado -> {
            assertThat(sacado.documento()).isEqualTo("11444777000161");
            assertThat(sacado.nome()).isEqualTo("GAMA DISTRIBUIDORA S/A");
        });
    }

    @Test
    @DisplayName("lerTexto: tabela de sacados vazia lê o cliente e devolve lista vazia")
    void shouldReadClientWithEmptySacadoTable() {
        Leitura leitura = parser.lerTexto(ar(CLIENTE, HORARIO, GRUPO_VAZIO, OPERACAO, RISCO, List.of()));

        assertThat(leitura.clienteCodigo()).isEqualTo("4821");
        assertThat(leitura.sacados()).isEmpty();
        assertThat(leitura.totalFace()).isNull();
    }

    // --------------------------------------------------------------------------------------- páginas

    @Test
    @DisplayName("lerTexto: relatório em duas páginas — cabeçalho da página, título das colunas e rodapé no meio da tabela")
    void shouldReadSacadosAcrossPages() {
        List<String> sacados = List.of(
                DOC_BETA, LINHA_BETA,
                DOC_GAMA, LINHA_GAMA,
                // fim da página 1
                RODAPE,
                // início da página 2: tudo o que o relatório repete
                "JGM LP FUNDO DE INVESTIMENTO EM DIREITOS CREDITORIOS MULTISSETORIAL    Página 002/002",
                "ANÁLISE DE RISCO - AR",
                "ANÁLISE DO CLIENTE",
                "CLIENTE  :4821 - ACME EMBALAGENS LTDA",
                "DATA     : 09/12/2030 LIMITES",
                "HORARIO  : 01:02:03 INDIVIDUAL GRUPO",
                COLUNAS_SACADOS,
                DOC_DELTA, LINHA_DELTA,
                DOC_FULANO, LINHA_FULANO,
                TOTAL_SACADOS);

        Leitura leitura = parser.lerTexto(ar(CLIENTE, HORARIO, GRUPO_VAZIO, OPERACAO, RISCO, sacados));

        assertThat(leitura.sacados()).extracting(SacadoLido::documento)
                .containsExactly("11222333000181", "11444777000161", "45723174000110", "52998224725");
        assertThat(leitura.totalTitulos()).isEqualTo(3);
        assertThat(leitura.totalFace()).isEqualByComparingTo("21300.00");
        // nada do cabeçalho repetido na página 2 sobrescreve o cabeçalho da página 1
        assertThat(leitura.clienteCodigo()).isEqualTo("4821");
        assertThat(leitura.resumo().emitidaEm()).isEqualTo("2026-10-08T15:59:13");
    }

    @Test
    @DisplayName("lerTexto: TOTAL de cada seção e rodapé não viram sacado (nada espúrio)")
    void shouldNotInventSacadosFromOtherLines() {
        Leitura leitura = parser.lerTexto(ar());

        assertThat(leitura.sacados()).extracting(SacadoLido::documento)
                .containsExactly("11222333000181", "11444777000161", "45723174000110", "52998224725");
    }

    @Test
    @DisplayName("lerTexto: aceita quebra de linha do Windows, linhas em branco, recuo e título sem acento")
    void shouldTolerateFormattingNoise() {
        String texto = ar().replace("ANÁLISE DE RISCO - AR", "ANALISE DE RISCO - AR")
                .replace("ANÁLISE DOS SACADOS", "analise dos sacados")
                .replace("\n", "\r\n\r\n   ");

        Leitura leitura = parser.lerTexto(texto);

        assertThat(leitura.clienteNome()).isEqualTo("ACME EMBALAGENS LTDA");
        assertThat(leitura.sacados()).hasSize(4);
        assertThat(leitura.resumo().liquidados()).isEqualByComparingTo("547000.00");
        assertThat(leitura.resumo().concentracaoApos()).isEqualByComparingTo("1.3500");
    }

    // ------------------------------------------------------------------------------ não é uma AR

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "",
            "   \n  ",
            "Nota fiscal 123\nTotal 1.000,00",
            "RELATÓRIO QUALQUER\nCLIENTE  :4821 - ACME EMBALAGENS LTDA\nANÁLISE DE RISCO",
    })
    @DisplayName("lerTexto: texto que não é uma AR é recusado com mensagem para o usuário")
    void shouldRejectTextThatIsNotAnAr(String texto) {
        assertThatThrownBy(() -> parser.lerTexto(texto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Análise de Risco");
    }

    @Test
    @DisplayName("lerTexto: com o título da AR mas sem cliente nem sacados também é recusado")
    void shouldRejectArTitleWithoutClientNorSacados() {
        String soTitulo = String.join("\n", TITULO, "ANÁLISE DE RISCO - AR", "ANÁLISE DO CLIENTE", RODAPE);

        assertThatThrownBy(() -> parser.lerTexto(soTitulo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Análise de Risco");
    }

    @Test
    @DisplayName("lerTexto: sem o título 'ANÁLISE DE RISCO - AR' é recusado mesmo com cliente e sacados")
    void shouldRejectTextWithoutArTitle() {
        String semTitulo = ar().replace("ANÁLISE DE RISCO - AR\n", "");

        assertThatThrownBy(() -> parser.lerTexto(semTitulo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Análise de Risco");
    }

    @Test
    @DisplayName("lerTexto: só sacados, sem linha CLIENTE, ainda é lido (código nulo)")
    void shouldAcceptSacadosWithoutClientLine() {
        Leitura leitura = parser.lerTexto(ar(null, HORARIO, GRUPO_VAZIO, OPERACAO, RISCO, sacadosPadrao()));

        assertThat(leitura.clienteCodigo()).isNull();
        assertThat(leitura.clienteNome()).isNull();
        assertThat(leitura.sacados()).hasSize(4);
    }

    // ----------------------------------------------------------------------------------------- numero

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "1.234.567,89|1234567.89",
            "0,00|0.00",
            "150.030,00|150030.00",
            "239,4184|239.4184",
            "-1.234,50|-1234.50",
            "867,34|867.34",
            "7|7"})
    @DisplayName("numero: formato pt-BR (ponto de milhar, vírgula decimal) vira BigDecimal")
    void shouldParseBrazilianNumbers(String texto, String esperado) {
        // "7" não tem vírgula e nunca chega aqui pelos padrões, mas o método não pode quebrar com ele
        assertThat(PropostaArParser.numero(texto)).isEqualByComparingTo(esperado);
    }

    @Test
    @DisplayName("numero: preserva a escala digitada (percentual com 4 casas)")
    void shouldKeepScaleOfTheText() {
        assertThat(PropostaArParser.numero("239,4184").scale()).isEqualTo(4);
        assertThat(PropostaArParser.numero("0,00").scale()).isEqualTo(2);
    }

    // --------------------------------------------------------------------------------- ler(byte[])

    @Test
    @DisplayName("ler: PDF de verdade em várias páginas dá a mesma leitura que o texto")
    void shouldReadRealPdfAcrossPages() {
        String texto = ar();

        // 25 linhas por página parte a tabela de sacados entre duas páginas
        Leitura doPdf = parser.ler(PropostaArTextos.pdf(texto, 25));

        assertThatRecursivo(doPdf).isEqualTo(parser.lerTexto(texto));
        assertThat(doPdf.sacados()).hasSize(4);
        assertThat(doPdf.resumo().emitidaEm()).isEqualTo("2026-10-08T15:59:13");
    }

    @Test
    @DisplayName("ler: bytes que não são PDF viram IllegalArgumentException com mensagem legível")
    void shouldTurnGarbageIntoIllegalArgument() {
        byte[] lixo = "isto não é um pdf, só texto qualquer".getBytes(StandardCharsets.UTF_8);
        byte[] cabecalhoSemCorpo = "%PDF-1.7\nlixo sem objetos".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.ler(lixo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Não foi possível ler o PDF")
                .hasCauseInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> parser.ler(cabecalhoSemCorpo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Não foi possível ler o PDF");
        assertThatThrownBy(() -> parser.ler(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ler: PDF válido de outro assunto é recusado como 'não parece uma Análise de Risco'")
    void shouldRejectValidPdfThatIsNotAnAr() {
        byte[] outro = PropostaArTextos.pdf("NOTA FISCAL 123\nTotal 1.000,00", 50);

        assertThatThrownBy(() -> parser.ler(outro))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Análise de Risco");
    }
}
