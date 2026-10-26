package com.portal.serasa.application.service.judit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Leitura da resposta da Judit, com os formatos vistos nos payloads reais de setembro. */
class JuditLeituraTest {

    private static final String CNPJ = "12021039000174";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode json(String texto) throws Exception {
        return JSON.readTree(texto.replace('\'', '"'));
    }

    @Test
    @DisplayName("resumo: total, réu/autor pelos polos e 'NÃO INFORMADO' juntado num valor nulo")
    void resumo() throws Exception {
        JsonNode raiz = json("""
                {'lawsuits_count': 57, 'secrecy_lawsuits_count': 2,
                 'sides': [{'count': 36, 'value': 'PASSIVE'}, {'count': 22, 'value': 'ACTIVE'}, {'count': 1, 'value': 'UNKNOWN'}],
                 'areas': [{'count': 21, 'value': 'NÃO INFORMADO'}, {'count': 26, 'value': 'DIREITO CIVIL'}, {'count': 3, 'value': ' '}],
                 'tribunals': [{'count': 6, 'value': 'TRT9'}, {'count': 31, 'value': 'TJPR'}]}
                """);

        JuditLeitura.Resumo resumo = JuditLeitura.resumo(raiz);

        assertThat(resumo.total()).isEqualTo(57);
        assertThat(resumo.emSegredo()).isEqualTo(2);
        assertThat(resumo.comoReu()).isEqualTo(36);
        assertThat(resumo.comoAutor()).isEqualTo(22);
        assertThat(resumo.naoEncontrado()).isFalse();
        assertThat(resumo.areas()).containsExactly(
                new JuditLeitura.Contagem("DIREITO CIVIL", 26),
                new JuditLeitura.Contagem(null, 24));
        assertThat(resumo.tribunais().get(0)).isEqualTo(new JuditLeitura.Contagem("TJPR", 31));
        assertThat(resumo.assuntos()).isEmpty();
    }

    @Test
    @DisplayName("processos: junta instâncias pelo CNJ, polo pelo documento e valor zero vira ausente")
    void processosJuntaInstancias() throws Exception {
        JsonNode raiz = json("""
                {'lawsuits': [
                  {'code': '0001', 'instance': 2, 'name': 'BANCO X IG LABEGALINI', 'amount': 1540325.19,
                   'distribution_date': '2026-07-30T19:19:08.465Z', 'tribunal_acronym': 'TJPR', 'state': 'PR',
                   'county': '16ª CÂMARA CÍVEL', 'courts': [{'name': 'GABINETE DO DESEMBARGADOR X'}, {'name': '16ª CÂMARA CÍVEL'}],
                   'subjects': [{'code': '4960', 'name': 'CÉDULA DE CRÉDITO BANCÁRIO'}],
                   'parties': [{'main_document': '12021039000174', 'name': 'IG LABEGALINI EIRELI', 'side': 'Passive'},
                               {'main_document': '00000000000191', 'name': 'BANCO DO BRASIL S/A', 'side': 'Active'}],
                   'last_step': {'step_date': '2026-08-11T22:33:53.613Z', 'content': 'EXPEDIÇÃO DE INTIMAÇÃO'},
                   'crawler': {'updated_at': '2026-08-23T19:44:03.908Z'}, 'secrecy_level': 0},
                  {'code': '0001', 'instance': 1, 'name': 'BANCO X IG LABEGALINI', 'amount': 1540325.19,
                   'distribution_date': '2026-03-02T10:00:00.000Z', 'tribunal_acronym': 'TJPR', 'state': 'PR',
                   'classifications': [{'code': '12154', 'name': 'EXECUÇÃO DE TÍTULO EXTRAJUDICIAL'}],
                   'subjects': [{'code': '4960', 'name': 'CÉDULA DE CRÉDITO BANCÁRIO'}],
                   'county': 'CURITIBA - 3ª VARA CÍVEL',
                   'parties': [{'main_document': '12021039000174', 'name': 'I. G. LABEGALINI LTDA', 'side': 'Passive'},
                               {'main_document': '00000000000191', 'name': 'BANCO DO BRASIL S/A', 'side': 'Active'},
                               {'name': 'ITAU UNIBANCO S/A', 'side': 'Active', 'person_type': 'EXEQUENTE'},
                               {'name': 'LOUISE RAINER', 'side': 'Active', 'person_type': 'ADVOGADA (EXEQTE)'},
                               {'name': 'GUSTAVO BORGES', 'side': 'Active', 'person_type': 'PARTE',
                                'documents': [{'document': '07363780900', 'document_type': 'cpf'}, {'document': 'PR12345', 'document_type': 'oab'}]},
                               {'name': 'FULANO & ADVOGADOS', 'side': 'Active', 'person_type': 'SOC. ADVOGADOS'},
                               {'name': 'PERITO X', 'side': 'Active', 'person_type': 'PERITO'}],
                   'last_step': {'step_date': '2026-05-01T10:00:00.000Z', 'content': 'CITAÇÃO'}},
                  {'code': '0002', 'instance': 1, 'name': 'IG LABEGALINI X CLIENTE', 'amount': 0,
                   'distribution_date': '2025-01-10T10:00:00.000Z', 'area': 'NÃO INFORMADO',
                   'subjects': [{'name': 'COBRANÇA'}],
                   'parties': [{'main_document': '12021039000255', 'name': 'IG LABEGALINI FILIAL', 'side': 'Active'},
                               {'main_document': '', 'name': 'CLIENTE', 'side': 'Passive'}]}
                ]}
                """);

        JuditLeitura.Processos processos = JuditLeitura.processos(raiz, CNPJ);

        assertThat(JuditLeitura.registros(raiz)).isEqualTo(3);
        assertThat(processos.registros()).isEqualTo(3);
        assertThat(processos.quantidade()).isEqualTo(2);
        assertThat(processos.comoReu()).isEqualTo(1);
        assertThat(processos.comoAutor()).isEqualTo(1);
        assertThat(processos.valorComoReu()).isEqualByComparingTo("1540325.19");

        JuditLeitura.Processo execucao = processos.lista().get(0);
        assertThat(execucao.cnj()).isEqualTo("0001");
        assertThat(execucao.instancias()).containsExactly(1, 2);
        assertThat(execucao.polo()).isEqualTo(JuditLeitura.REU);
        assertThat(execucao.contrapartes()).as("todas as partes do outro polo, sem advogado, sociedade, perito")
                .containsExactly("BANCO DO BRASIL S/A", "ITAU UNIBANCO S/A");
        assertThat(execucao.orgaos()).as("órgãos das duas instâncias, sem repetir")
                .containsExactly("CURITIBA - 3ª VARA CÍVEL", "16ª CÂMARA CÍVEL", "GABINETE DO DESEMBARGADOR X");
        assertThat(execucao.classe()).isEqualTo("EXECUÇÃO DE TÍTULO EXTRAJUDICIAL");
        assertThat(execucao.distribuidoEm()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(execucao.ultimoAndamento().texto()).isEqualTo("EXPEDIÇÃO DE INTIMAÇÃO");
        assertThat(execucao.fonteAtualizadaEm()).isEqualTo(LocalDate.of(2026, 8, 23));

        JuditLeitura.Processo cobranca = processos.lista().get(1);
        assertThat(cobranca.polo()).as("filial casa pela raiz do CNPJ").isEqualTo(JuditLeitura.AUTOR);
        assertThat(cobranca.contrapartes()).containsExactly("CLIENTE");
        assertThat(cobranca.valor()).as("amount 0 é ausência de dado").isNull();
        assertThat(cobranca.area()).isNull();
    }

    @Test
    @DisplayName("processos: conta como ré os assuntos de título contestado (protesto, negativação)")
    void tituloContestado() throws Exception {
        JsonNode raiz = json("""
                {'lawsuits': [
                  {'code': 'A', 'subjects': [{'name': 'PROTESTO INDEVIDO DE TÍTULO'}],
                   'parties': [{'main_document': '12021039000174', 'side': 'Passive'}]},
                  {'code': 'B', 'subjects': [{'name': 'INCLUSÃO INDEVIDA EM CADASTRO DE INADIMPLENTES'}],
                   'parties': [{'main_document': '12021039000174', 'side': 'Passive'}]},
                  {'code': 'C', 'subjects': [{'name': 'PROTESTO INDEVIDO DE TÍTULO'}],
                   'parties': [{'main_document': '12021039000174', 'side': 'Active'}]},
                  {'code': 'D', 'subjects': [{'name': 'DÍVIDA ATIVA'}],
                   'parties': [{'main_document': '12021039000174', 'side': 'Passive'}]}
                ]}
                """);

        JuditLeitura.Processos processos = JuditLeitura.processos(raiz, CNPJ);

        assertThat(processos.tituloContestadoComoReu()).isEqualTo(2);
        assertThat(processos.assuntosComoReu()).extracting(JuditLeitura.Contagem::valor)
                .containsExactlyInAnyOrder("PROTESTO INDEVIDO DE TÍTULO",
                        "INCLUSÃO INDEVIDA EM CADASTRO DE INADIMPLENTES", "DÍVIDA ATIVA");
        assertThat(processos.valorComoReu()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("processos: aceita a lista em response_data, como diz a documentação")
    void responseData() throws Exception {
        JsonNode raiz = json("{'response_data': [{'code': 'X', 'parties': []}]}");

        JuditLeitura.Processos processos = JuditLeitura.processos(raiz, CNPJ);

        assertThat(processos.lista()).extracting(JuditLeitura.Processo::polo).isEqualTo(List.of(JuditLeitura.DESCONHECIDO));
    }

    @Test
    @DisplayName("mesmaPessoa: CNPJ pela raiz, CPF inteiro")
    void mesmaPessoa() {
        assertThat(JuditLeitura.mesmaPessoa(CNPJ, "12021039000255")).isTrue();
        assertThat(JuditLeitura.mesmaPessoa(CNPJ, "12021040000174")).isFalse();
        assertThat(JuditLeitura.mesmaPessoa("12345678901", "12345678901")).isTrue();
        assertThat(JuditLeitura.mesmaPessoa("12345678901", "12345678902")).isFalse();
        assertThat(JuditLeitura.mesmaPessoa(CNPJ, null)).isFalse();
    }

    @Test
    @DisplayName("semLista: mantém os totais e tira os processos")
    void semLista() throws Exception {
        JsonNode raiz = json("{'lawsuits': [{'code': 'A', 'parties': [{'main_document': '12021039000174', 'side': 'Passive'}]}]}");

        JuditLeitura.Processos completo = JuditLeitura.processos(raiz, CNPJ);
        JuditLeitura.Processos curto = completo.semLista();

        assertThat(curto.lista()).isEmpty();
        assertThat(curto.quantidade()).isEqualTo(1);
        assertThat(curto.comoReu()).isEqualTo(1);
    }

    // ── detalhe de processo ─────────────────────────────────────────────────────────────────────

    /** Trecho do resumo real (processo 1009479-30.2024.8.26.0011): markdown com HTML do site da Judit. */
    private static final String RESUMO_JUDIT = """
            ## Resumo

            O processo de número <strong className="text-primary">1009479-30.2024.8.26.0011</strong> trata de uma \
            <strong>execução de título extrajudicial</strong>.

            ---

            ## Partes

            <ul>
              <li>
                <p>
                  <strong className="text-foreground text-base">FUNDO JGM LP</strong>
                  <br />
                  <span className="text-muted-foreground text-sm">Parte autora / exequente</span>
                  <br />
                  <p className="text-foreground/70">
                    É quem entrou com o processo para <strong>cobrar judicialmente a dívida</strong>.
                  </p>
                </p>
              </li>
              <li>
                <p>
                  <strong>IG LABEGALINI LTDA</strong>
                  <br />
                  <span>Parte ré / executada</span>
                </p>
              </li>
            </ul>

            ## Movimentações

            <p className="text-foreground/70">
              Começou em <span className="text-primary">11/06/2024</span>, com citação de <strong>CARLOS <span className="font-bold">LABEGALINI</span></strong>.
            </p>
            <script>alert(1)</script>
            """;

    @Test
    @DisplayName("resumo da IA: tira o HTML, mantém negrito e títulos, item de lista numa linha e sem recuo")
    void resumoEmMarkdown() {
        String markdown = JuditLeitura.resumoEmMarkdown(RESUMO_JUDIT);

        assertThat(markdown).doesNotContain("<", ">", "className");
        assertThat(markdown).contains("## Resumo", "## Partes", "## Movimentações", "---");
        assertThat(markdown).contains("número **1009479-30.2024.8.26.0011** trata de uma **execução de título extrajudicial**.");
        assertThat(markdown).contains(
                "- **FUNDO JGM LP** *Parte autora / exequente* É quem entrou com o processo para **cobrar judicialmente a dívida**.\n"
                        + "- **IG LABEGALINI LTDA** *Parte ré / executada*");
        assertThat(markdown).contains("Começou em 11/06/2024, com citação de **CARLOS LABEGALINI**.");
        assertThat(markdown.lines()).as("sem recuo: viraria bloco de código").noneMatch(l -> l.startsWith(" "));
        assertThat(markdown).doesNotContain("\n\n\n");
        assertThat(JuditLeitura.resumoEmMarkdown("  ")).isNull();
    }

    @Test
    @DisplayName("detalhe: junta instâncias, movimentações da mais recente para a mais antiga e partes antes de advogados")
    void detalhe() throws Exception {
        JsonNode respostas = json("""
                {'page_data': [
                  {'response_type': 'summary', 'response_data': {'data': ['## Resumo\\n\\nTexto <strong>forte</strong>.']}},
                  {'response_type': 'lawsuit', 'response_data': {
                    'code': '1009479-30.2024.8.26.0011', 'instance': 1, 'name': 'JGM LP FIDC X I. G. LABEGALINI LTDA',
                    'status': 'ATIVO', 'phase': 'INICIAL', 'situation': 'MOVIMENTO', 'amount': 200485,
                    'distribution_date': '2024-06-11T20:57:37.000Z', 'tribunal_acronym': 'TJSP', 'county': '01 CIVEL DE PINHEIROS',
                    'judge': 'NÃO INFORMADO',
                    'classifications': [{'code': '12154', 'name': 'EXECUÇÃO DE TÍTULO EXTRAJUDICIAL'}],
                    'subjects': [{'name': 'CONFISSÃO/COMPOSIÇÃO DE DÍVIDA'}],
                    'parties': [
                      {'name': 'JOANY BARBI BRUMILLER', 'side': 'Active', 'person_type': 'ADVOGADO'},
                      {'name': 'I. G. LABEGALINI LTDA', 'side': 'Passive', 'person_type': 'EXECUTADO'},
                      {'name': 'JGM LP FIDC', 'side': 'Active', 'person_type': 'EXEQUENTE'}],
                    'steps': [
                      {'step_id': 'a', 'step_date': '2024-06-12T10:00:00.000Z', 'content': '2 - CITAÇÃO'},
                      {'step_id': 'b', 'step_date': '2026-09-09T16:55:41.000Z', 'content': '259 - JUNTADA DE OFÍCIO CUMPRIDO'}],
                    'attachments': [{}, {}]}},
                  {'response_type': 'lawsuit', 'response_data': {
                    'code': '1009479-30.2024.8.26.0011', 'instance': 2, 'status': 'ATIVO',
                    'parties': [{'name': 'JGM LP FIDC', 'side': 'Active', 'person_type': 'APELANTE'}],
                    'steps': [{'step_id': 'b', 'step_date': '2026-09-09T16:55:41.000Z', 'content': '259 - JUNTADA DE OFÍCIO CUMPRIDO'},
                              {'step_id': 'c', 'step_date': '2025-07-10T10:00:00.000Z', 'content': 'CARTA PRECATÓRIA'}]}}
                ]}
                """);

        JuditLeitura.Detalhe detalhe = JuditLeitura.detalhe(respostas);

        assertThat(detalhe.resumoIa()).isEqualTo("## Resumo\n\nTexto **forte**.");
        JuditLeitura.ProcessoDetalhado processo = detalhe.processo();
        assertThat(processo.status()).isEqualTo("ATIVO");
        assertThat(processo.fase()).isEqualTo("INICIAL");
        assertThat(processo.juiz()).as("NÃO INFORMADO vira nulo").isNull();
        assertThat(processo.instancias()).containsExactly(1, 2);
        assertThat(processo.valor()).isEqualByComparingTo("200485");
        assertThat(processo.movimentacoes()).as("sem repetir o mesmo passo das duas instâncias")
                .extracting(JuditLeitura.Movimentacao::texto)
                .containsExactly("259 - JUNTADA DE OFÍCIO CUMPRIDO", "CARTA PRECATÓRIA", "2 - CITAÇÃO");
        assertThat(processo.partes()).extracting(JuditLeitura.Parte::nome)
                .containsExactly("JGM LP FIDC", "I. G. LABEGALINI LTDA", "JOANY BARBI BRUMILLER");
        assertThat(processo.partes().get(2).auxiliar()).isTrue();
        assertThat(processo.anexos()).isEqualTo(2);
    }

    @Test
    @DisplayName("detalhe: sem o processo na resposta (não achado no tribunal) e sem resumo")
    void detalheVazio() throws Exception {
        JuditLeitura.Detalhe detalhe = JuditLeitura.detalhe(json("{'page_data': []}"));

        assertThat(detalhe.processo()).isNull();
        assertThat(detalhe.resumoIa()).isNull();
    }

    // ── pontos de atenção ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pontos de atenção: o maior entre as dimensões, sem confundir cláusula penal nem processo civil")
    void pontosDeAtencao() throws Exception {
        JuditLeitura.Resumo resumo = JuditLeitura.resumo(json("""
                {'lawsuits_count': 60,
                 'areas': [{'count': 2, 'value': 'DIREITO PENAL'},
                           {'count': 10, 'value': 'DIREITO PROCESSUAL CIVIL E DO TRABALHO'}],
                 'classifications': [{'count': 3, 'value': 'AÇÃO PENAL - PROCEDIMENTO ORDINÁRIO'},
                                     {'count': 4, 'value': 'EXECUÇÃO FISCAL'}],
                 'subjects': [{'count': 5, 'value': 'Cláusula Penal'},
                              {'count': 1, 'value': 'Recuperação judicial e Falência'},
                              {'count': 2, 'value': 'Protesto Indevido de Título'},
                              {'count': 2, 'value': 'Inclusão Indevida em Cadastro de Inadimplentes'}],
                 'justices': [{'count': 7, 'value': 'JUSTIÇA DO TRABALHO'}, {'count': 40, 'value': 'JUSTIÇA ESTADUAL'}]}
                """));

        assertThat(JuditLeitura.pontosDeAtencao(resumo)).containsExactly(
                new JuditLeitura.PontoDeAtencao("CRIMINAL", "Processo criminal", 3),
                new JuditLeitura.PontoDeAtencao("RECUPERACAO_FALENCIA", "Recuperação judicial ou falência", 1),
                new JuditLeitura.PontoDeAtencao("EXECUCAO_FISCAL", "Execução fiscal", 4),
                new JuditLeitura.PontoDeAtencao("TITULO_CONTESTADO", "Protesto, negativação ou duplicata contestada", 4),
                new JuditLeitura.PontoDeAtencao("TRABALHISTA", "Justiça do Trabalho", 7));
    }

    @Test
    @DisplayName("pontos de atenção: resumo limpo não acusa nada")
    void pontosDeAtencaoLimpo() throws Exception {
        JuditLeitura.Resumo resumo = JuditLeitura.resumo(json("""
                {'lawsuits_count': 9, 'areas': [{'count': 9, 'value': 'DIREITO CIVIL'}],
                 'subjects': [{'count': 9, 'value': 'Indenização por Dano Moral'}],
                 'justices': [{'count': 9, 'value': 'JUSTIÇA ESTADUAL'}]}
                """));

        assertThat(JuditLeitura.pontosDeAtencao(resumo)).isEmpty();
    }
}
