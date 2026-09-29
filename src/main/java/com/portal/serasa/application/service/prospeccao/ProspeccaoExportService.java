package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEventoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoEventoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exportação da esteira em CSV.
 *
 * <p>CSV, e não .xlsx, porque o Excel em português abre um arquivo com separador ponto e vírgula
 * e marca de ordem de bytes já com as colunas separadas e os acentos certos — e isso não custa
 * nenhuma dependência nova no projeto. O preço é não ter formatação nem várias abas: por isso a
 * exportação é feita por assunto, um arquivo de cada vez.</p>
 */
@Service
@RequiredArgsConstructor
public class ProspeccaoExportService {

    /**
     * O Excel só reconhece o arquivo como UTF-8 se ele começar com a marca de ordem de bytes.
     * Sem ela, "Análise" vira "AnÃ¡lise" na planilha do time.
     */
    public static final String BOM = "﻿";

    /** O Excel em português usa a vírgula como separador decimal, então a coluna separa por ponto e vírgula. */
    private static final char SEPARADOR = ';';

    /** Fim de linha do Windows: é onde a planilha vai ser aberta. */
    private static final String QUEBRA = "\r\n";

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ProspeccaoService prospeccaoService;
    private final ProspeccaoDocumentoService documentoService;
    private final ProspeccaoEventoJpaRepository eventoRepository;
    private final UserRepository userRepository;

    public enum Conteudo { CARDS, DOCUMENTOS, EVENTOS }

    @Transactional(readOnly = true)
    public String exportar(Conteudo conteudo, List<ProspeccaoEntity> cards) {
        return switch (conteudo) {
            case CARDS -> cards(cards);
            case DOCUMENTOS -> documentos(cards);
            case EVENTOS -> eventos(cards);
        };
    }

    public String nomeDoArquivo(Conteudo conteudo) {
        String assunto = switch (conteudo) {
            case CARDS -> "esteira";
            case DOCUMENTOS -> "esteira-documentos";
            case EVENTOS -> "esteira-historico";
        };
        return assunto + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv";
    }

    // ------------------------------------------------------------------ cards

    private String cards(List<ProspeccaoEntity> cards) {
        Map<UUID, String> nomes = nomesDeUsuario(cards);
        Map<UUID, List<ProspeccaoDocumentoEntity>> docs = documentoService.listarPorCards(ids(cards));

        StringBuilder csv = new StringBuilder(BOM);
        linha(csv, "Empresa", "CNPJ", "Estágio", "Dias no estágio", "Prazo (dias úteis)", "Situação",
                "Comercial", "Analista", "Origem", "Motivo da recusa", "Observação", "Reaberturas",
                "Documentos resolvidos", "Documentos no checklist", "Última cobrança", "Aberto em", "Fechado em");

        for (ProspeccaoEntity card : cards) {
            List<ProspeccaoDocumentoEntity> itens = docs.getOrDefault(card.getId(), List.of());
            long total = itens.stream()
                    .filter(item -> !Boolean.TRUE.equals(item.getInformativoSnapshot()))
                    .filter(item -> Boolean.TRUE.equals(item.getSocioAtivo()))
                    .count();
            long resolvidos = itens.stream()
                    .filter(item -> !Boolean.TRUE.equals(item.getInformativoSnapshot()))
                    .filter(item -> Boolean.TRUE.equals(item.getSocioAtivo()))
                    .filter(item -> item.getStatus().resolvido())
                    .count();

            linha(csv,
                    card.getRazaoSocial(),
                    cnpjFormatado(card.getCnpj()),
                    rotulo(card.getEstagio()),
                    String.valueOf(prospeccaoService.diasNoEstagio(card)),
                    String.valueOf(card.getPrazoEstagioDias()),
                    situacao(card),
                    card.getComercialNome(),
                    nomes.get(card.getAnalistaId()),
                    card.getOrigem() == null ? "" : card.getOrigem().name(),
                    card.getMotivoRecusa() == null ? "" : card.getMotivoRecusa().name(),
                    card.getObservacao(),
                    String.valueOf(card.getReaberturas()),
                    String.valueOf(resolvidos),
                    String.valueOf(total),
                    data(card.getUltimoContatoEm()),
                    data(card.getCreatedAt()),
                    data(card.getClosedAt()));
        }
        return csv.toString();
    }

    /** Situação em uma palavra, que é o que a planilha precisa para filtrar. */
    private String situacao(ProspeccaoEntity card) {
        // Card encerrado não tem prazo correndo: dizer "no prazo" sobre um reprovado seria ruído
        // na coluna que o time vai usar para filtrar.
        if (card.getClosedAt() != null) {
            return "Encerrado";
        }
        if (prospeccaoService.silencioProlongado(card)) {
            return "Sem resposta";
        }
        if (prospeccaoService.slaEstourado(card)) {
            return "Atrasado";
        }
        if (prospeccaoService.slaEmAtencao(card)) {
            return "Perto do prazo";
        }
        return "No prazo";
    }

    // ------------------------------------------------------------- documentos

    private String documentos(List<ProspeccaoEntity> cards) {
        Map<UUID, ProspeccaoEntity> porId = cards.stream()
                .collect(Collectors.toMap(ProspeccaoEntity::getId, Function.identity()));
        Map<UUID, String> nomes = nomesDeUsuario(cards);

        StringBuilder csv = new StringBuilder(BOM);
        linha(csv, "Empresa", "CNPJ", "Estágio", "Escopo", "Sócio", "Sócio ativo", "Motivo do sócio inativo",
                "Documento", "Obrigatório", "Informativo", "Status", "Observação", "Motivo",
                "Recebido em", "Validado em", "Validado por", "Arquivos");

        documentoService.listarPorCards(ids(cards)).forEach((cardId, itens) -> {
            ProspeccaoEntity card = porId.get(cardId);
            if (card == null) {
                return;
            }
            for (ProspeccaoDocumentoEntity item : itens) {
                linha(csv,
                        card.getRazaoSocial(),
                        cnpjFormatado(card.getCnpj()),
                        rotulo(card.getEstagio()),
                        item.getEscopo().name(),
                        item.getSocioNome(),
                        Boolean.TRUE.equals(item.getSocioAtivo()) ? "Sim" : "Não",
                        item.getSocioInativoMotivo(),
                        item.getNomeSnapshot(),
                        Boolean.TRUE.equals(item.getObrigatorioSnapshot()) ? "Sim" : "Não",
                        Boolean.TRUE.equals(item.getInformativoSnapshot()) ? "Sim" : "Não",
                        item.getStatus().name(),
                        item.getObservacao(),
                        item.getMotivo(),
                        data(item.getRecebidoEm()),
                        data(item.getValidadoEm()),
                        nomes.get(item.getValidadoPor()),
                        "");
            }
        });
        return csv.toString();
    }

    // ----------------------------------------------------------------- eventos

    private String eventos(List<ProspeccaoEntity> cards) {
        Map<UUID, ProspeccaoEntity> porId = cards.stream()
                .collect(Collectors.toMap(ProspeccaoEntity::getId, Function.identity()));

        StringBuilder csv = new StringBuilder(BOM);
        linha(csv, "Empresa", "CNPJ", "Data", "Tipo", "Canal", "De", "Para", "Autor", "Texto");

        for (ProspeccaoEntity card : cards) {
            for (ProspeccaoEventoEntity evento : eventoRepository.findByProspeccaoIdOrderByCriadoEmDesc(card.getId())) {
                ProspeccaoEntity dono = porId.getOrDefault(card.getId(), card);
                linha(csv,
                        dono.getRazaoSocial(),
                        cnpjFormatado(dono.getCnpj()),
                        dataHora(evento.getCriadoEm()),
                        evento.getTipo().name(),
                        evento.getCanal() == null ? "" : evento.getCanal().name(),
                        evento.getEstagioDe() == null ? "" : rotulo(evento.getEstagioDe()),
                        evento.getEstagioPara() == null ? "" : rotulo(evento.getEstagioPara()),
                        evento.getUsuarioNome(),
                        evento.getTexto());
            }
        }
        return csv.toString();
    }

    // ---------------------------------------------------------------- helpers

    private List<UUID> ids(List<ProspeccaoEntity> cards) {
        return cards.stream().map(ProspeccaoEntity::getId).toList();
    }

    private Map<UUID, String> nomesDeUsuario(List<ProspeccaoEntity> cards) {
        List<UUID> ids = cards.stream()
                .map(ProspeccaoEntity::getAnalistaId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));
    }

    static String rotulo(EstagioProspeccao estagio) {
        return switch (estagio) {
            case TRIAGEM -> "Triagem";
            case EM_ANALISE -> "Em análise";
            case APROVADO -> "Aprovado";
            case DOCS_PENDENTES -> "Documentos pendentes";
            case DOCS_COMPLETOS -> "Documentos completos";
            case PRONTO_HABILITACAO -> "Pronto p/ habilitação";
            case REPROVADO -> "Reprovado";
            case REMOVIDO_RADAR -> "Removido do radar";
        };
    }

    static String cnpjFormatado(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) {
            return cnpj == null ? "" : cnpj;
        }
        return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8)
                + "/" + cnpj.substring(8, 12) + "-" + cnpj.substring(12);
    }

    private String data(LocalDateTime valor) {
        return valor == null ? "" : valor.format(DATA);
    }

    private String dataHora(LocalDateTime valor) {
        return valor == null ? "" : valor.format(DATA_HORA);
    }

    private void linha(StringBuilder csv, String... colunas) {
        for (int i = 0; i < colunas.length; i++) {
            if (i > 0) {
                csv.append(SEPARADOR);
            }
            csv.append(escapar(colunas[i]));
        }
        csv.append(QUEBRA);
    }

    /**
     * Escapa um campo conforme o RFC 4180.
     *
     * <p>É a parte que mais quebra planilha na prática: observação de documento é texto livre e
     * costuma trazer ponto e vírgula, aspas e quebra de linha — "OK - Sofisa, Bradesco, Itaú" é
     * um caso real da planilha de origem.</p>
     */
    static String escapar(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        boolean precisaAspas = valor.indexOf(SEPARADOR) >= 0
                || valor.indexOf('"') >= 0
                || valor.indexOf('\n') >= 0
                || valor.indexOf('\r') >= 0;
        if (!precisaAspas) {
            return valor;
        }
        return '"' + valor.replace("\"", "\"\"") + '"';
    }
}
