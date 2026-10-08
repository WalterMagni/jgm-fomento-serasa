package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.application.service.liberacao.PropostaArParser.Leitura;
import com.portal.serasa.application.service.liberacao.PropostaArService.PropostaImportada;
import com.portal.serasa.application.service.liberacao.PropostaArService.SacadoImportado;
import com.portal.serasa.domain.model.Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.portal.serasa.application.service.liberacao.PropostaArTextos.CLIENTE;
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
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.TOTAL_SACADOS;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.ar;
import static com.portal.serasa.application.service.liberacao.PropostaArTextos.sacadosPadrao;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Conferência do que o parser leu: vínculo do cedente pelo código do cliente, sacados sem título,
 * junção de documento repetido e avisos. O texto da AR é o fixture anonimizado de
 * {@link PropostaArTextos}, lido pelo parser de verdade (ele não tem dependências).
 */
@ExtendWith(MockitoExtension.class)
class PropostaArServiceTest {

    private static final String CNPJ_CEDENTE = "11222333000181";
    private static final String CNPJ_CEDENTE_MASCARADO = "11.222.333/0001-81";

    @Mock private PropostaArParser parser;
    @Mock private ClientRepository clientRepository;
    @Mock private EmpresaResolver empresaResolver;

    @InjectMocks private PropostaArService service;

    private final PropostaArParser parserReal = new PropostaArParser();

    private Leitura ler(String texto) {
        return parserReal.lerTexto(texto);
    }

    /** AR padrão, mas com o cliente e a lista de sacados trocados. */
    private Leitura lerComSacados(List<String> sacados) {
        return ler(ar(CLIENTE, HORARIO, GRUPO_VAZIO, OPERACAO, RISCO, sacados));
    }

    private static Client cliente(String documento) {
        return Client.builder().clientCode("4821").documentNumber(documento).name("ACME").build();
    }

    private static String semEspacoInsecavel(String texto) {
        return texto.replace('\u00a0', ' ');
    }

    private void cedenteNaoVinculado() {
        when(clientRepository.findByClientCode("4821")).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------------------------- cedente

    @Test
    @DisplayName("montar: código vinculado (sem zeros à esquerda na busca) traz CNPJ, nome da base e 'cadastrada'")
    void shouldLinkCedenteByClientCodeWithoutLeadingZeros() {
        Leitura leitura = ler(ar().replace("CLIENTE  :4821", "CLIENTE  :0004821"));
        when(clientRepository.findByClientCode("4821")).thenReturn(Optional.of(cliente(CNPJ_CEDENTE_MASCARADO)));
        when(empresaResolver.resolver(List.of(CNPJ_CEDENTE))).thenReturn(Map.of(CNPJ_CEDENTE,
                new EmpresaResolver.Empresa("ACME EMBALAGENS LTDA DA BASE", "Bauru", "SP", true)));

        PropostaImportada importada = service.montar(leitura);

        assertThat(importada.cedenteCnpj()).isEqualTo(CNPJ_CEDENTE);
        assertThat(importada.cedenteNome()).isEqualTo("ACME EMBALAGENS LTDA DA BASE");
        assertThat(importada.cedenteCadastrado()).isTrue();
        // o que o PDF disse continua disponível, com os zeros que vieram nele
        assertThat(importada.clienteCodigo()).isEqualTo("0004821");
        assertThat(importada.clienteNome()).isEqualTo("ACME EMBALAGENS LTDA");
        assertThat(importada.avisos()).noneMatch(aviso -> aviso.contains("vincule"));
        verify(clientRepository).findByClientCode("4821");
        verify(clientRepository, never()).findByClientCode("0004821");
    }

    @Test
    @DisplayName("montar: cadastrada vem de Empresa.cadastrada(), não do fato de ter CNPJ")
    void shouldTakeCadastradaFromEmpresa() {
        when(clientRepository.findByClientCode("4821")).thenReturn(Optional.of(cliente(CNPJ_CEDENTE)));
        when(empresaResolver.resolver(List.of(CNPJ_CEDENTE))).thenReturn(Map.of(CNPJ_CEDENTE,
                new EmpresaResolver.Empresa("ACME DA BASE", null, null, false)));

        PropostaImportada importada = service.montar(ler(ar()));

        assertThat(importada.cedenteCnpj()).isEqualTo(CNPJ_CEDENTE);
        assertThat(importada.cedenteCadastrado()).isFalse();
        assertThat(importada.cedenteNome()).isEqualTo("ACME DA BASE");
    }

    @Test
    @DisplayName("montar: empresa da base sem nome ou fora da base mantém o nome do PDF")
    void shouldKeepPdfNameWhenBaseHasNoName() {
        when(clientRepository.findByClientCode("4821")).thenReturn(Optional.of(cliente(CNPJ_CEDENTE)));
        when(empresaResolver.resolver(List.of(CNPJ_CEDENTE)))
                .thenReturn(Map.of(CNPJ_CEDENTE, new EmpresaResolver.Empresa(null, "Bauru", "SP", true)))
                .thenReturn(Map.of());

        PropostaImportada semNome = service.montar(ler(ar()));
        PropostaImportada foraDaBase = service.montar(ler(ar()));

        assertThat(semNome.cedenteNome()).isEqualTo("ACME EMBALAGENS LTDA");
        assertThat(semNome.cedenteCadastrado()).isTrue();
        assertThat(semNome.cedenteCnpj()).isEqualTo(CNPJ_CEDENTE);
        assertThat(foraDaBase.cedenteNome()).isEqualTo("ACME EMBALAGENS LTDA");
        assertThat(foraDaBase.cedenteCadastrado()).isFalse();
        assertThat(foraDaBase.cedenteCnpj()).as("o vínculo existe mesmo que a empresa não esteja nas bases").isEqualTo(CNPJ_CEDENTE);
    }

    @Test
    @DisplayName("montar: código não vinculado deixa o CNPJ nulo, usa o nome do PDF e avisa para vincular")
    void shouldWarnWhenCodeIsNotLinked() {
        cedenteNaoVinculado();

        PropostaImportada importada = service.montar(ler(ar()));

        assertThat(importada.cedenteCnpj()).isNull();
        assertThat(importada.cedenteNome()).isEqualTo("ACME EMBALAGENS LTDA");
        assertThat(importada.cedenteCadastrado()).isFalse();
        assertThat(importada.avisos()).anySatisfy(aviso -> assertThat(aviso)
                .contains("4821")
                .contains("ACME EMBALAGENS LTDA")
                .contains("vincule")
                .contains("não está vinculado"));
        verifyNoInteractions(empresaResolver);
    }

    @ParameterizedTest(name = "[{index}] documento \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"52998224725", "529.982.247-25", "1122233300018", "112223330001811", "sem digitos"})
    @DisplayName("montar: cliente cujo documento não tem 14 dígitos conta como não vinculado")
    void shouldTreatClientWithoutCnpjAsUnlinked(String documento) {
        when(clientRepository.findByClientCode("4821")).thenReturn(Optional.of(cliente(documento)));

        PropostaImportada importada = service.montar(ler(ar()));

        assertThat(importada.cedenteCnpj()).isNull();
        assertThat(importada.cedenteCadastrado()).isFalse();
        assertThat(importada.cedenteNome()).isEqualTo("ACME EMBALAGENS LTDA");
        assertThat(importada.avisos()).anyMatch(aviso -> aviso.contains("vincule"));
        verifyNoInteractions(empresaResolver);
    }

    @ParameterizedTest(name = "[{index}] código \"{0}\"")
    @ValueSource(strings = {"0", "0000"})
    @DisplayName("montar: código só de zeros não consulta o repositório, mas avisa")
    void shouldNotLookUpAllZeroCode(String codigo) {
        Leitura leitura = ler(ar().replace("CLIENTE  :4821", "CLIENTE  :" + codigo));

        PropostaImportada importada = service.montar(leitura);

        assertThat(importada.cedenteCnpj()).isNull();
        assertThat(importada.avisos()).anyMatch(aviso -> aviso.contains("O código " + codigo) && aviso.contains("vincule"));
        verifyNoInteractions(clientRepository, empresaResolver);
    }

    @Test
    @DisplayName("montar: AR sem linha CLIENTE não consulta ninguém e não avisa de código")
    void shouldNotWarnAboutCodeWhenReportHasNoClientLine() {
        Leitura leitura = ler(ar(null, HORARIO, GRUPO_VAZIO, OPERACAO, RISCO, sacadosPadrao()));

        PropostaImportada importada = service.montar(leitura);

        assertThat(importada.cedenteCnpj()).isNull();
        assertThat(importada.cedenteNome()).isNull();
        assertThat(importada.clienteCodigo()).isNull();
        assertThat(importada.avisos()).noneMatch(aviso -> aviso.contains("vincule"));
        verifyNoInteractions(clientRepository, empresaResolver);
    }

    // ------------------------------------------------------------------------------------ sacados

    @Test
    @DisplayName("montar: sacado com 0 títulos vai para semTitulo, os demais para sacados, na ordem do relatório")
    void shouldSplitSacadosWithAndWithoutTitles() {
        cedenteNaoVinculado();

        PropostaImportada importada = service.montar(ler(ar()));

        assertThat(importada.sacados()).extracting(SacadoImportado::documento)
                .containsExactly("11444777000161", "45723174000110", "52998224725");
        assertThat(importada.sacados()).extracting(SacadoImportado::nome)
                .containsExactly("GAMA DISTRIBUIDORA S/A", "DELTA ATACADO LTDA", "FULANO DE TAL");
        assertThat(importada.sacados()).extracting(SacadoImportado::valor)
                .usingElementComparator(java.math.BigDecimal::compareTo)
                .containsExactly(new java.math.BigDecimal("6000"), new java.math.BigDecimal("9300"), new java.math.BigDecimal("6000"));
        assertThat(importada.sacados().get(0).carteira().vincendos()).isEqualByComparingTo("7200.00");
        assertThat(importada.sacados()).allSatisfy(sacado -> assertThat(sacado.carteira().titulos()).isPositive());

        assertThat(importada.semTitulo()).singleElement().satisfies(sacado -> {
            assertThat(sacado.documento()).isEqualTo("11222333000181");
            assertThat(sacado.nome()).isEqualTo("BETA COMERCIAL LTDA");
            assertThat(sacado.carteira().titulos()).isZero();
            assertThat(sacado.carteira().liquidados()).isEqualByComparingTo("32000.00");
        });
        assertThat(importada.proposta()).isEqualTo(leituraPadraoResumo());
    }

    private com.portal.serasa.domain.model.liberacao.PropostaAr leituraPadraoResumo() {
        return ler(ar()).resumo();
    }

    @Test
    @DisplayName("montar: um sacado sem título gera aviso no singular, com nome e documento formatado")
    void shouldWarnInSingularForOneSacadoWithoutTitle() {
        cedenteNaoVinculado();

        PropostaImportada importada = service.montar(ler(ar()));

        assertThat(importada.avisos()).contains(
                "1 sacado sem título nesta proposta ficou de fora: BETA COMERCIAL LTDA (11.222.333/0001-81).");
    }

    @Test
    @DisplayName("montar: dois ou mais sacados sem título geram aviso no plural listando todos, com CPF formatado")
    void shouldWarnInPluralForSeveralSacadosWithoutTitle() {
        cedenteNaoVinculado();
        List<String> sacados = List.of(
                DOC_BETA, LINHA_BETA,
                DOC_GAMA, LINHA_GAMA,
                DOC_FULANO, "00091 FULANO DE TAL 0 0,00 0,00 0,00 0,00 0,00 0,00",
                DOC_DELTA, LINHA_DELTA,
                "TOTAL 2 15.300,00 800,00 11.300,00 12.100,00 197.500,00 1.500,00");

        PropostaImportada importada = service.montar(lerComSacados(sacados));

        assertThat(importada.sacados()).extracting(SacadoImportado::documento).containsExactly("11444777000161", "45723174000110");
        assertThat(importada.semTitulo()).extracting(SacadoImportado::documento).containsExactly("11222333000181", "52998224725");
        assertThat(importada.avisos()).contains("2 sacados sem título nesta proposta ficaram de fora:"
                + " BETA COMERCIAL LTDA (11.222.333/0001-81), FULANO DE TAL (529.982.247-25).");
        assertThat(importada.avisos()).noneMatch(aviso -> aviso.startsWith("1 sacado sem título"));
    }

    @Test
    @DisplayName("montar: sem sacado sem título não há aviso nem lista")
    void shouldNotWarnWhenEverySacadoHasTitles() {
        cedenteNaoVinculado();
        List<String> sacados = List.of(DOC_GAMA, LINHA_GAMA, DOC_DELTA, LINHA_DELTA, DOC_FULANO, LINHA_FULANO, TOTAL_SACADOS);

        PropostaImportada importada = service.montar(lerComSacados(sacados));

        assertThat(importada.semTitulo()).isEmpty();
        assertThat(importada.sacados()).hasSize(3);
        assertThat(importada.avisos()).noneMatch(aviso -> aviso.contains("sem título"));
    }

    @Test
    @DisplayName("montar: mesmo documento em duas linhas (relatório em páginas) vira um sacado com face e títulos somados")
    void shouldMergeDuplicateDocumentsAcrossPages() {
        cedenteNaoVinculado();
        List<String> sacados = List.of(
                DOC_GAMA, LINHA_GAMA,
                DOC_DELTA, LINHA_DELTA,
                // página 2: o mesmo documento de novo, com outro código interno
                DOC_GAMA, "00047 GAMA DISTRIBUIDORA S/A (2) 2 4.000,00 0,00 0,00 0,00 0,00 0,00",
                // 3 títulos na proposta (1 + 1 + 2 = 4 linhas lidas) e 19.300,00 de face
                "TOTAL 4 19.300,00 800,00 11.300,00 12.100,00 165.500,00 1.500,00");

        PropostaImportada importada = service.montar(lerComSacados(sacados));

        assertThat(importada.sacados()).hasSize(2);
        SacadoImportado gama = importada.sacados().get(0);
        assertThat(gama.documento()).isEqualTo("11444777000161");
        assertThat(gama.nome()).as("vale o nome da primeira linha").isEqualTo("GAMA DISTRIBUIDORA S/A");
        assertThat(gama.valor()).isEqualByComparingTo("10000.00");
        assertThat(gama.carteira().titulos()).isEqualTo(3);
        assertThat(importada.sacados().get(1).documento()).isEqualTo("45723174000110");
        assertThat(importada.sacados().get(1).valor()).isEqualByComparingTo("9300.00");
        // a conferência com o TOTAL usa as linhas lidas, antes da junção: 19.300,00 e 4 títulos batem
        assertThat(importada.avisos()).noneMatch(aviso -> aviso.contains("não bate"));
    }

    @Test
    @DisplayName("montar: documento repetido soma também a carteira (vencidos, a vencer, abertos, liquidados, recomprados)")
    void shouldSumWholeCarteiraOfDuplicateDocument() {
        cedenteNaoVinculado();
        List<String> sacados = List.of(
                DOC_GAMA, LINHA_GAMA,
                DOC_GAMA, "00047 GAMA DISTRIBUIDORA S/A 1 1.000,00 500,00 100,00 600,00 2.000,00 300,00",
                "TOTAL 2 7.000,00 1.300,00 7.300,00 8.600,00 122.000,00 1.800,00");

        PropostaImportada importada = service.montar(lerComSacados(sacados));

        assertThat(importada.sacados()).singleElement().satisfies(gama -> {
            assertThat(gama.valor()).isEqualByComparingTo("7000.00");
            assertThat(gama.carteira().titulos()).isEqualTo(2);
            assertThat(gama.carteira().vencidos()).isEqualByComparingTo("1300.00");
            assertThat(gama.carteira().vincendos()).isEqualByComparingTo("7300.00");
            assertThat(gama.carteira().abertos()).isEqualByComparingTo("8600.00");
            assertThat(gama.carteira().liquidados()).isEqualByComparingTo("122000.00");
            assertThat(gama.carteira().recomprados()).isEqualByComparingTo("1800.00");
        });
    }

    @Test
    @DisplayName("montar: sacado repetido cujas linhas somam zero títulos vai para semTitulo uma vez só")
    void shouldMergeDuplicatesWithoutTitlesIntoOneEntry() {
        cedenteNaoVinculado();
        List<String> sacados = List.of(
                DOC_BETA, LINHA_BETA,
                DOC_BETA, "00069 BETA COMERCIAL LTDA 0 0,00 0,00 0,00 0,00 0,00 0,00",
                DOC_GAMA, LINHA_GAMA,
                "TOTAL 1 6.000,00 800,00 12.200,00 13.000,00 152.000,00 1.500,00");

        PropostaImportada importada = service.montar(lerComSacados(sacados));

        assertThat(importada.semTitulo()).singleElement().satisfies(sacado -> {
            assertThat(sacado.documento()).isEqualTo("11222333000181");
            assertThat(sacado.carteira().titulos()).isZero();
        });
        assertThat(importada.sacados()).extracting(SacadoImportado::documento).containsExactly("11444777000161");
    }

    // ------------------------------------------------------------------------- conferência dos totais

    @Test
    @DisplayName("montar: AR consistente não gera aviso de soma nem de total ausente")
    void shouldNotWarnAboutTotalsWhenSumsMatch() {
        cedenteNaoVinculado();

        PropostaImportada importada = service.montar(ler(ar()));

        assertThat(importada.avisos()).noneMatch(aviso -> aviso.contains("não bate"));
        assertThat(importada.avisos()).noneMatch(aviso -> aviso.contains("não trouxe a linha de total"));
    }

    @Test
    @DisplayName("montar: face dos sacados diferente do TOTAL avisa 'não bate' com os dois valores")
    void shouldWarnWhenFaceSumDiffersFromTotal() {
        cedenteNaoVinculado();
        // sem a GAMA (6.000,00) a soma cai para 15.300,00 e 2 títulos
        List<String> semGama = List.of(DOC_BETA, LINHA_BETA, DOC_DELTA, LINHA_DELTA, DOC_FULANO, LINHA_FULANO, TOTAL_SACADOS);

        PropostaImportada importada = service.montar(lerComSacados(semGama));

        assertThat(importada.avisos()).anySatisfy(aviso -> assertThat(semEspacoInsecavel(aviso))
                .contains("não bate")
                .contains("R$ 15.300,00, 2 títulos")
                .contains("R$ 21.300,00, 3 títulos")
                .contains("Confira a lista antes de salvar"));
    }

    @Test
    @DisplayName("montar: só a quantidade de títulos diferente também avisa 'não bate'")
    void shouldWarnWhenOnlyTitleCountDiffers() {
        cedenteNaoVinculado();
        // mesma face (21.300,00), mas a GAMA diz 2 títulos: 4 contra os 3 do TOTAL
        List<String> sacados = List.of(DOC_BETA, LINHA_BETA,
                DOC_GAMA, LINHA_GAMA.replace("S/A 1 6.000,00", "S/A 2 6.000,00"),
                DOC_DELTA, LINHA_DELTA, DOC_FULANO, LINHA_FULANO, TOTAL_SACADOS);

        PropostaImportada importada = service.montar(lerComSacados(sacados));

        assertThat(importada.avisos()).anySatisfy(aviso -> assertThat(semEspacoInsecavel(aviso))
                .contains("não bate")
                .contains("R$ 21.300,00, 4 títulos")
                .contains("R$ 21.300,00, 3 títulos"));
    }

    @Test
    @DisplayName("montar: sacado de linha órfã (sem documento) deixa a soma menor e o aviso 'não bate' aparece")
    void shouldWarnWhenARowWasDroppedByTheParser() {
        cedenteNaoVinculado();
        List<String> sacados = List.of(DOC_BETA, LINHA_BETA, DOC_GAMA, LINHA_GAMA, DOC_DELTA, LINHA_DELTA,
                // sem a linha do documento, o parser ignora esta linha
                LINHA_FULANO, TOTAL_SACADOS);

        PropostaImportada importada = service.montar(lerComSacados(sacados));

        assertThat(importada.sacados()).extracting(SacadoImportado::documento).doesNotContain("52998224725");
        assertThat(importada.avisos()).anyMatch(aviso -> aviso.contains("não bate"));
    }

    @Test
    @DisplayName("montar: sem a linha TOTAL dos sacados avisa que o relatório não trouxe o total")
    void shouldWarnWhenTotalLineIsMissing() {
        cedenteNaoVinculado();
        List<String> semTotal = sacadosPadrao().subList(0, sacadosPadrao().size() - 1);

        PropostaImportada importada = service.montar(lerComSacados(semTotal));

        assertThat(importada.avisos()).anySatisfy(aviso -> assertThat(aviso)
                .contains("não trouxe a linha de total")
                .contains("Confira a lista antes de salvar"));
        assertThat(importada.avisos()).noneMatch(aviso -> aviso.contains("não bate"));
        assertThat(importada.sacados()).hasSize(3);
    }

    // ----------------------------------------------------------------- títulos liberados x proposta

    @Test
    @DisplayName("montar: quantidade total diferente da liberada avisa com as duas quantidades e valores")
    void shouldWarnWhenTotalCountDiffersFromReleased() {
        cedenteNaoVinculado();

        PropostaImportada importada = service.montar(ler(ar()));

        assertThat(importada.avisos()).anySatisfy(aviso -> assertThat(semEspacoInsecavel(aviso))
                .contains("4 títulos")
                .contains("R$ 25.800,00")
                .contains("3 liberados")
                .contains("R$ 21.300,00"));
    }

    @Test
    @DisplayName("montar: quantidade total igual à liberada não gera esse aviso")
    void shouldNotWarnWhenTotalCountEqualsReleased() {
        cedenteNaoVinculado();
        String operacaoIgual = "3 28,50 21.300,00 745,25 20.554,75 3 21.300,00";

        PropostaImportada importada = service.montar(
                ler(ar(CLIENTE, HORARIO, GRUPO_VAZIO, operacaoIgual, RISCO, sacadosPadrao())));

        assertThat(importada.avisos()).noneMatch(aviso -> aviso.contains("liberados"));
        assertThat(importada.avisos()).noneMatch(aviso -> aviso.startsWith("A proposta tem"));
    }

    @Test
    @DisplayName("montar: sem a linha de operação não há como comparar quantidades e o aviso não aparece")
    void shouldNotWarnAboutCountsWithoutOperationLine() {
        cedenteNaoVinculado();

        PropostaImportada importada = service.montar(
                ler(ar(CLIENTE, HORARIO, GRUPO_VAZIO, null, RISCO, sacadosPadrao())));

        assertThat(importada.avisos()).noneMatch(aviso -> aviso.startsWith("A proposta tem"));
        assertThat(importada.proposta().qtdTotal()).isNull();
    }

    @Test
    @DisplayName("montar: a lista de avisos devolvida não pode ser alterada")
    void shouldReturnImmutableWarnings() {
        cedenteNaoVinculado();

        PropostaImportada importada = service.montar(ler(ar()));

        assertThat(importada.avisos()).isNotEmpty();
        assertThatThrownBy(() -> importada.avisos().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    // -------------------------------------------------------------------------------------- importar

    @ParameterizedTest(name = "[{index}]")
    @NullAndEmptySource
    @DisplayName("importar: nulo ou vazio é 'Arquivo vazio.' e o parser nem é chamado")
    void shouldRejectNullOrEmptyFile(byte[] pdf) {
        assertThatThrownBy(() -> service.importar(pdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Arquivo vazio.");
        verifyNoInteractions(parser);
    }

    @Test
    @DisplayName("importar: mais de 10 MB é recusado antes de olhar o conteúdo")
    void shouldRejectFileAboveTenMegabytes() {
        byte[] grande = new byte[PropostaArService.LIMITE_BYTES + 1];
        grande[0] = '%';
        grande[1] = 'P';
        grande[2] = 'D';
        grande[3] = 'F';

        assertThatThrownBy(() -> service.importar(grande))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PDF acima de 10 MB.");
        verifyNoInteractions(parser);
    }

    @Test
    @DisplayName("importar: exatamente 10 MB passa pelo limite de tamanho (e cai na checagem de PDF)")
    void shouldAcceptExactlyTenMegabytesAtTheSizeCheck() {
        byte[] exato = new byte[PropostaArService.LIMITE_BYTES];

        assertThatThrownBy(() -> service.importar(exato))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O arquivo não é um PDF.");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"PK\u0003\u0004 isto é um zip", "<html></html>", "%PDF", "%PD", "x", " %PDF-1.7", "%pdf-1.7"})
    @DisplayName("importar: arquivo que não começa com %PDF é 'O arquivo não é um PDF.'")
    void shouldRejectFileThatIsNotPdf(String conteudo) {
        byte[] bytes = conteudo.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> service.importar(bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O arquivo não é um PDF.");
        verifyNoInteractions(parser);
    }

    @Test
    @DisplayName("importar: PDF válido passa pelo parser e o resultado é montado (vínculo, sacados, avisos)")
    void shouldParseAndAssembleValidPdf() {
        byte[] pdf = "%PDF-1.7 corpo qualquer".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        when(parser.ler(pdf)).thenReturn(ler(ar()));
        when(clientRepository.findByClientCode("4821")).thenReturn(Optional.of(cliente(CNPJ_CEDENTE_MASCARADO)));
        when(empresaResolver.resolver(List.of(CNPJ_CEDENTE))).thenReturn(Map.of(CNPJ_CEDENTE,
                new EmpresaResolver.Empresa("ACME DA BASE", "Bauru", "SP", true)));

        PropostaImportada importada = service.importar(pdf);

        assertThat(importada.cedenteCnpj()).isEqualTo(CNPJ_CEDENTE);
        assertThat(importada.cedenteNome()).isEqualTo("ACME DA BASE");
        assertThat(importada.sacados()).hasSize(3);
        assertThat(importada.semTitulo()).hasSize(1);
        verify(parser).ler(pdf);
    }

    @Test
    @DisplayName("importar: erro do parser (PDF ilegível ou que não é uma AR) sobe como está, sem consultar clientes")
    void shouldPropagateParserErrors() {
        byte[] pdf = "%PDF-1.7 corpo qualquer".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        when(parser.ler(any())).thenThrow(new IllegalArgumentException("Este PDF não parece uma Análise de Risco (AR)."));

        assertThatThrownBy(() -> service.importar(pdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Análise de Risco");
        verifyNoInteractions(clientRepository, empresaResolver);
    }

    @Test
    @DisplayName("importar: de ponta a ponta com o parser real e um PDF gerado em duas páginas")
    void shouldImportRealPdfEndToEnd() {
        PropostaArService completo = new PropostaArService(new PropostaArParser(), clientRepository, empresaResolver);
        when(clientRepository.findByClientCode("4821")).thenReturn(Optional.of(cliente(CNPJ_CEDENTE_MASCARADO)));
        when(empresaResolver.resolver(List.of(CNPJ_CEDENTE))).thenReturn(Map.of(CNPJ_CEDENTE,
                new EmpresaResolver.Empresa("ACME DA BASE", "Bauru", "SP", true)));

        PropostaImportada importada = completo.importar(PropostaArTextos.pdf(ar(), 25));

        assertThat(importada.cedenteCnpj()).isEqualTo(CNPJ_CEDENTE);
        assertThat(importada.cedenteCadastrado()).isTrue();
        assertThat(importada.proposta().emitidaEm()).isEqualTo("2026-10-08T15:59:13");
        assertThat(importada.proposta().faceLiberados()).isEqualByComparingTo("21300.00");
        assertThat(importada.sacados()).extracting(SacadoImportado::documento)
                .containsExactly("11444777000161", "45723174000110", "52998224725");
        assertThat(importada.semTitulo()).extracting(SacadoImportado::documento).containsExactly("11222333000181");
        assertThat(importada.avisos()).noneMatch(aviso -> aviso.contains("não bate"));
    }
}
