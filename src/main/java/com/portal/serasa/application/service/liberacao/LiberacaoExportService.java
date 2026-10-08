package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.TipoEventoLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoComentarioEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEtiquetaEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEventoEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMembroEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoPendenciaEntity;
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
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Relatório .xlsx da esteira de liberação.
 *
 * <p>A prospecção exporta CSV para não depender de biblioteca. Aqui o pedido foi um relatório
 * final completo, e isso pede várias abas, moeda e data formatadas, cabeçalho congelado e
 * filtro — coisas que CSV não carrega. Daí o Apache POI.</p>
 *
 * <p>Exporta exatamente os cards que a tela mostra: quem chama manda os ids depois de aplicar os
 * filtros, e a planilha não tem como divergir do que a pessoa estava vendo.</p>
 */
@Service
@RequiredArgsConstructor
public class LiberacaoExportService {

    static final int LIMITE_CARDS = 5000;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Bordô da marca no cabeçalho. */
    private static final byte[] BORDO = {(byte) 0x61, (byte) 0x20, (byte) 0x35};

    private final LiberacaoCardJpaRepository cardRepository;
    private final LiberacaoSacadoJpaRepository sacadoRepository;
    private final LiberacaoParecerJpaRepository parecerRepository;
    private final LiberacaoPendenciaJpaRepository pendenciaRepository;
    private final LiberacaoEventoJpaRepository eventoRepository;
    private final LiberacaoComentarioJpaRepository comentarioRepository;
    private final LiberacaoMembroJpaRepository membroRepository;
    private final LiberacaoCardEtiquetaJpaRepository cardEtiquetaRepository;
    private final LiberacaoEtiquetaJpaRepository etiquetaRepository;
    private final UserRepository userRepository;
    private final EmpresaResolver empresaResolver;

    @Transactional(readOnly = true)
    public byte[] exportar(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Nenhum card para exportar.");
        }
        if (ids.size() > LIMITE_CARDS) {
            throw new IllegalArgumentException("Exporte até " + LIMITE_CARDS + " cards por vez.");
        }
        List<LiberacaoCardEntity> cards = cardRepository.findAllById(ids).stream()
                .filter(card -> card.getExcluidoEm() == null)
                .sorted(Comparator.comparing(LiberacaoCardEntity::getNumero))
                .toList();
        List<UUID> idsValidos = cards.stream().map(LiberacaoCardEntity::getId).toList();
        Dados dados = carregar(idsValidos);

        try (XSSFWorkbook planilha = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(planilha);
            abaCards(planilha, estilos, cards, dados);
            abaSacados(planilha, estilos, cards, dados);
            abaPareceres(planilha, estilos, cards, dados);
            abaPendencias(planilha, estilos, cards, dados);
            abaHistorico(planilha, estilos, cards, dados);
            abaComentarios(planilha, estilos, cards, dados);
            planilha.write(saida);
            return saida.toByteArray();
        } catch (IOException erro) {
            throw new UncheckedIOException("Falha ao montar a planilha", erro);
        }
    }

    // ------------------------------------------------------------------ dados

    private record Dados(Map<String, EmpresaResolver.Empresa> empresas,
                         Map<UUID, List<LiberacaoSacadoEntity>> sacados,
                         Map<UUID, List<LiberacaoParecerEntity>> pareceres,
                         Map<UUID, List<LiberacaoPendenciaEntity>> pendencias,
                         Map<UUID, List<LiberacaoEventoEntity>> eventos,
                         Map<UUID, List<LiberacaoComentarioEntity>> comentarios,
                         Map<UUID, List<String>> membros,
                         Map<UUID, List<String>> etiquetas) {
    }

    private Dados carregar(List<UUID> ids) {
        if (ids.isEmpty()) {
            return new Dados(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<UUID, String> nomesUsuarios = new java.util.HashMap<>();
        List<LiberacaoMembroEntity> membros = membroRepository.findByCardIdIn(ids);
        userRepository.findAllById(membros.stream().map(LiberacaoMembroEntity::getUsuarioId).collect(Collectors.toSet()))
                .forEach(user -> nomesUsuarios.put(user.getId(), user.getName()));
        Map<UUID, String> nomesEtiquetas = etiquetaRepository.findAll().stream()
                .collect(Collectors.toMap(LiberacaoEtiquetaEntity::getId, LiberacaoEtiquetaEntity::getNome));

        List<LiberacaoSacadoEntity> sacados = sacadoRepository.findByCardIdIn(ids);
        List<String> documentos = new java.util.ArrayList<>(sacados.stream().map(LiberacaoSacadoEntity::getCnpj).toList());
        cardRepository.findAllById(ids).forEach(card -> documentos.add(card.getCedenteCnpj()));
        return new Dados(
                empresaResolver.resolver(documentos),
                agrupar(sacados, LiberacaoSacadoEntity::getCardId),
                agrupar(ids.stream().flatMap(id -> parecerRepository.findByCardIdOrderByRodadaDescCriadoEm(id).stream()).toList(),
                        LiberacaoParecerEntity::getCardId),
                agrupar(ids.stream().flatMap(id -> pendenciaRepository.findByCardIdOrderByAbertaEm(id).stream()).toList(),
                        LiberacaoPendenciaEntity::getCardId),
                agrupar(eventoRepository.findByCardIdInOrderByCriadoEm(ids), LiberacaoEventoEntity::getCardId),
                agrupar(comentarioRepository.findByCardIdInAndExcluidoEmIsNullOrderByCriadoEm(ids), LiberacaoComentarioEntity::getCardId),
                membros.stream().collect(Collectors.groupingBy(LiberacaoMembroEntity::getCardId,
                        Collectors.mapping(membro -> nomesUsuarios.getOrDefault(membro.getUsuarioId(), "?"), Collectors.toList()))),
                cardEtiquetaRepository.findByCardIdIn(ids).stream().collect(Collectors.groupingBy(
                        vinculo -> vinculo.getCardId(),
                        Collectors.mapping(vinculo -> nomesEtiquetas.getOrDefault(vinculo.getEtiquetaId(), "?"), Collectors.toList()))));
    }

    private static <T> Map<UUID, List<T>> agrupar(List<T> itens, Function<T, UUID> chave) {
        return itens.stream().collect(Collectors.groupingBy(chave));
    }

    // ------------------------------------------------------------------- abas

    private void abaCards(XSSFWorkbook planilha, Estilos estilos, List<LiberacaoCardEntity> cards, Dados dados) {
        Sheet aba = cabecalho(planilha, estilos, "Cards", new String[]{
                "Nº", "Cedente", "CNPJ", "Praça do cedente", "Etapa", "Tipo", "Valor", "Prazo", "Etiquetas", "Criado por", "Criado em",
                "Última alteração por", "Última alteração em", "Membros", "Pareceres (rodada vigente)",
                "Pendências abertas", "Comentários", "Resultado", "Valor aprovado", "Decidido por", "Decidido em",
                "Horas na Origem", "Horas no Comitê", "Horas em Pendência", "Posição da origem", "Parecer da origem"},
                new int[]{6, 38, 20, 22, 12, 13, 16, 17, 22, 22, 17, 22, 17, 30, 46, 11, 11, 20, 16, 22, 17, 11, 11, 11, 15, 60});
        int linha = 1;
        for (LiberacaoCardEntity card : cards) {
            Row row = aba.createRow(linha++);
            List<LiberacaoEventoEntity> eventos = dados.eventos().getOrDefault(card.getId(), List.of());
            LiberacaoEventoEntity decisao = eventos.stream()
                    .filter(evento -> evento.getTipo() == TipoEventoLiberacao.TRANSICAO && evento.getEtapaPara() != null
                            && evento.getEtapaPara().terminal())
                    .reduce((primeiro, segundo) -> segundo)
                    .filter(evento -> card.getEtapa().terminal())
                    .orElse(null);
            Map<EtapaLiberacao, Double> horas = horasPorEtapa(card, eventos, LocalDateTime.now());
            String pareceres = dados.pareceres().getOrDefault(card.getId(), List.of()).stream()
                    .filter(parecer -> Objects.equals(parecer.getRodada(), card.getRodada()))
                    .map(parecer -> parecer.getUsuarioNome() + ": " + (parecer.getPosicao() == null ? "aguardando"
                            : rotulo(parecer) + " (" + data(parecer.getRegistradoEm()) + ")"))
                    .collect(Collectors.joining("; "));
            long pendenciasAbertas = dados.pendencias().getOrDefault(card.getId(), List.of()).stream()
                    .filter(LiberacaoPendenciaEntity::aberta).count();

            int c = 0;
            numero(row, c++, card.getNumero(), estilos.inteiro);
            texto(row, c++, card.getCedenteNome(), estilos.texto);
            texto(row, c++, LiberacaoService.formatarDocumento(card.getCedenteCnpj()), estilos.texto);
            texto(row, c++, praca(dados, card.getCedenteCnpj()), estilos.texto);
            texto(row, c++, LiberacaoAutorizacao.rotulo(card.getEtapa()), estilos.texto);
            texto(row, c++, card.getTipoOperacao(), estilos.texto);
            moeda(row, c++, card.getValor(), estilos);
            dataHora(row, c++, card.getPrazo(), estilos);
            texto(row, c++, String.join(", ", dados.etiquetas().getOrDefault(card.getId(), List.of())), estilos.texto);
            texto(row, c++, card.getCriadoPorNome(), estilos.texto);
            dataHora(row, c++, card.getCriadoEm(), estilos);
            texto(row, c++, card.getAtualizadoPorNome(), estilos.texto);
            dataHora(row, c++, card.getAtualizadoEm(), estilos);
            texto(row, c++, String.join(", ", dados.membros().getOrDefault(card.getId(), List.of())), estilos.texto);
            texto(row, c++, pareceres, estilos.quebra);
            numero(row, c++, pendenciasAbertas, estilos.inteiro);
            numero(row, c++, dados.comentarios().getOrDefault(card.getId(), List.of()).size(), estilos.inteiro);
            texto(row, c++, card.getResultado() == null ? "" : card.getResultado().rotulo(), estilos.texto);
            moeda(row, c++, LiberacaoService.valorAprovado(
                    dados.sacados().getOrDefault(card.getId(), List.of())), estilos);
            texto(row, c++, decisao == null ? "" : decisao.getUsuarioNome(), estilos.texto);
            dataHora(row, c++, decisao == null ? null : decisao.getCriadoEm(), estilos);
            horas(row, c++, horas.get(EtapaLiberacao.ORIGEM), estilos);
            horas(row, c++, horas.get(EtapaLiberacao.COMITE), estilos);
            horas(row, c++, horas.get(EtapaLiberacao.PENDENCIA), estilos);
            texto(row, c++, card.getPosicaoOrigem() == null ? "" : LiberacaoService.rotulo(card.getPosicaoOrigem()), estilos.texto);
            texto(row, c, MencaoParser.textoPlano(card.getParecerOrigem()), estilos.quebra);
        }
        fechar(aba, linha, 25);
    }

    private void abaSacados(XSSFWorkbook planilha, Estilos estilos, List<LiberacaoCardEntity> cards, Dados dados) {
        Sheet aba = cabecalho(planilha, estilos, "Sacados", new String[]{"Nº card", "Cedente", "Documento", "Sacado", "Praça", "Valor",
                        "Situação", "Valor aprovado", "Decidido por", "Decidido em"},
                new int[]{9, 38, 20, 38, 22, 16, 20, 16, 22, 17});
        int linha = 1;
        for (LiberacaoCardEntity card : cards) {
            for (LiberacaoSacadoEntity sacado : ordenar(dados.sacados().get(card.getId()), Comparator.comparing(LiberacaoSacadoEntity::getOrdem))) {
                Row row = aba.createRow(linha++);
                numero(row, 0, card.getNumero(), estilos.inteiro);
                texto(row, 1, card.getCedenteNome(), estilos.texto);
                texto(row, 2, LiberacaoService.formatarDocumento(sacado.getCnpj()), estilos.texto);
                EmpresaResolver.Empresa empresa = dados.empresas().get(sacado.getCnpj());
                texto(row, 3, sacado.getNome() != null ? sacado.getNome() : empresa == null ? null : empresa.nome(), estilos.texto);
                texto(row, 4, praca(dados, sacado.getCnpj()), estilos.texto);
                moeda(row, 5, sacado.getValor(), estilos);
                texto(row, 6, sacado.getSituacao() == null ? "A decidir" : sacado.getSituacao().rotulo(), estilos.texto);
                moeda(row, 7, sacado.getSituacao() == null ? null
                        : switch (sacado.getSituacao()) {
                            case APROVADO -> sacado.getValor();
                            case PARCIAL -> sacado.getValorAprovado();
                            case REPROVADO -> java.math.BigDecimal.ZERO;
                        }, estilos);
                texto(row, 8, sacado.getSituacaoPorNome(), estilos.texto);
                dataHora(row, 9, sacado.getSituacaoEm(), estilos);
            }
        }
        fechar(aba, linha, 9);
    }

    private void abaPareceres(XSSFWorkbook planilha, Estilos estilos, List<LiberacaoCardEntity> cards, Dados dados) {
        Sheet aba = cabecalho(planilha, estilos, "Pareceres",
                new String[]{"Nº card", "Cedente", "Rodada", "Analista", "Posição", "Parecer", "Registrado em"},
                new int[]{9, 38, 8, 24, 15, 70, 17});
        int linha = 1;
        for (LiberacaoCardEntity card : cards) {
            for (LiberacaoParecerEntity parecer : ordenar(dados.pareceres().get(card.getId()),
                    Comparator.comparing(LiberacaoParecerEntity::getRodada).thenComparing(LiberacaoParecerEntity::getCriadoEm))) {
                Row row = aba.createRow(linha++);
                numero(row, 0, card.getNumero(), estilos.inteiro);
                texto(row, 1, card.getCedenteNome(), estilos.texto);
                numero(row, 2, parecer.getRodada(), estilos.inteiro);
                texto(row, 3, parecer.getUsuarioNome(), estilos.texto);
                texto(row, 4, parecer.getPosicao() == null ? "Aguardando" : rotulo(parecer), estilos.texto);
                texto(row, 5, MencaoParser.textoPlano(parecer.getTexto()), estilos.quebra);
                dataHora(row, 6, parecer.getRegistradoEm(), estilos);
            }
        }
        fechar(aba, linha, 6);
    }

    private void abaPendencias(XSSFWorkbook planilha, Estilos estilos, List<LiberacaoCardEntity> cards, Dados dados) {
        Sheet aba = cabecalho(planilha, estilos, "Pendências", new String[]{"Nº card", "Cedente", "Aberta por", "Para",
                        "Pendência", "Aberta em", "Situação", "Resposta", "Respondida por", "Respondida em"},
                new int[]{9, 38, 22, 22, 60, 17, 12, 60, 22, 17});
        int linha = 1;
        for (LiberacaoCardEntity card : cards) {
            for (LiberacaoPendenciaEntity pendencia : ordenar(dados.pendencias().get(card.getId()),
                    Comparator.comparing(LiberacaoPendenciaEntity::getAbertaEm))) {
                Row row = aba.createRow(linha++);
                numero(row, 0, card.getNumero(), estilos.inteiro);
                texto(row, 1, card.getCedenteNome(), estilos.texto);
                texto(row, 2, pendencia.getAbertaPorNome(), estilos.texto);
                texto(row, 3, pendencia.getDestinatarioNome(), estilos.texto);
                texto(row, 4, MencaoParser.textoPlano(pendencia.getTexto()), estilos.quebra);
                dataHora(row, 5, pendencia.getAbertaEm(), estilos);
                texto(row, 6, pendencia.aberta() ? "Aberta" : "Respondida", estilos.texto);
                texto(row, 7, MencaoParser.textoPlano(pendencia.getResposta()), estilos.quebra);
                texto(row, 8, pendencia.getRespondidaPorNome(), estilos.texto);
                dataHora(row, 9, pendencia.getRespondidaEm(), estilos);
            }
        }
        fechar(aba, linha, 9);
    }

    private void abaHistorico(XSSFWorkbook planilha, Estilos estilos, List<LiberacaoCardEntity> cards, Dados dados) {
        Sheet aba = cabecalho(planilha, estilos, "Histórico", new String[]{"Nº card", "Cedente", "Data e hora", "Quem",
                        "Evento", "De", "Para", "Campo", "Antes", "Depois", "Detalhe"},
                new int[]{9, 38, 17, 22, 20, 12, 12, 16, 30, 30, 60});
        int linha = 1;
        for (LiberacaoCardEntity card : cards) {
            for (LiberacaoEventoEntity evento : dados.eventos().getOrDefault(card.getId(), List.of())) {
                Row row = aba.createRow(linha++);
                numero(row, 0, card.getNumero(), estilos.inteiro);
                texto(row, 1, card.getCedenteNome(), estilos.texto);
                dataHora(row, 2, evento.getCriadoEm(), estilos);
                texto(row, 3, evento.getUsuarioNome(), estilos.texto);
                texto(row, 4, rotulo(evento.getTipo()), estilos.texto);
                texto(row, 5, evento.getEtapaDe() == null ? "" : LiberacaoAutorizacao.rotulo(evento.getEtapaDe()), estilos.texto);
                texto(row, 6, evento.getEtapaPara() == null ? "" : LiberacaoAutorizacao.rotulo(evento.getEtapaPara()), estilos.texto);
                texto(row, 7, evento.getCampo(), estilos.texto);
                texto(row, 8, MencaoParser.textoPlano(evento.getValorAntes()), estilos.quebra);
                texto(row, 9, MencaoParser.textoPlano(evento.getValorDepois()), estilos.quebra);
                texto(row, 10, MencaoParser.textoPlano(evento.getTexto()), estilos.quebra);
            }
        }
        fechar(aba, linha, 10);
    }

    private void abaComentarios(XSSFWorkbook planilha, Estilos estilos, List<LiberacaoCardEntity> cards, Dados dados) {
        Sheet aba = cabecalho(planilha, estilos, "Comentários",
                new String[]{"Nº card", "Cedente", "Data e hora", "Autor", "Comentário", "Editado em"},
                new int[]{9, 38, 17, 22, 80, 17});
        int linha = 1;
        for (LiberacaoCardEntity card : cards) {
            for (LiberacaoComentarioEntity comentario : dados.comentarios().getOrDefault(card.getId(), List.of())) {
                Row row = aba.createRow(linha++);
                numero(row, 0, card.getNumero(), estilos.inteiro);
                texto(row, 1, card.getCedenteNome(), estilos.texto);
                dataHora(row, 2, comentario.getCriadoEm(), estilos);
                texto(row, 3, comentario.getAutorNome(), estilos.texto);
                texto(row, 4, MencaoParser.textoPlano(comentario.getTexto()), estilos.quebra);
                dataHora(row, 5, comentario.getEditadoEm(), estilos);
            }
        }
        fechar(aba, linha, 5);
    }

    /**
     * Horas corridas que o card passou em cada etapa, somando idas e voltas.
     *
     * <p>Começa na Origem na criação e troca de etapa a cada transição. Tempo depois da decisão
     * não conta: card aprovado há um mês não ficou um mês "em" algum lugar.</p>
     */
    static Map<EtapaLiberacao, Double> horasPorEtapa(LiberacaoCardEntity card, List<LiberacaoEventoEntity> eventos, LocalDateTime agora) {
        Map<EtapaLiberacao, Duration> soma = new EnumMap<>(EtapaLiberacao.class);
        EtapaLiberacao atual = EtapaLiberacao.ORIGEM;
        LocalDateTime desde = card.getCriadoEm();
        List<LiberacaoEventoEntity> transicoes = eventos.stream()
                .filter(evento -> (evento.getTipo() == TipoEventoLiberacao.TRANSICAO || evento.getTipo() == TipoEventoLiberacao.REABERTURA)
                        && evento.getEtapaPara() != null)
                .sorted(Comparator.comparing(LiberacaoEventoEntity::getCriadoEm))
                .toList();
        for (LiberacaoEventoEntity transicao : transicoes) {
            if (!atual.terminal() && desde != null) {
                soma.merge(atual, Duration.between(desde, transicao.getCriadoEm()), Duration::plus);
            }
            atual = transicao.getEtapaPara();
            desde = transicao.getCriadoEm();
        }
        if (!atual.terminal() && desde != null) {
            soma.merge(atual, Duration.between(desde, agora), Duration::plus);
        }
        Map<EtapaLiberacao, Double> horas = new EnumMap<>(EtapaLiberacao.class);
        soma.forEach((etapa, duracao) -> horas.put(etapa, Math.round(duracao.toMinutes() / 6.0) / 10.0));
        return horas;
    }

    // ------------------------------------------------------------------ células

    /** Estilos criados uma vez por planilha: o Excel tem limite de estilos por arquivo. */
    private static final class Estilos {
        final CellStyle cabecalho;
        final CellStyle texto;
        final CellStyle quebra;
        final CellStyle moeda;
        final CellStyle dataHora;
        final CellStyle inteiro;
        final CellStyle decimal;

        Estilos(XSSFWorkbook planilha) {
            Font negrito = planilha.createFont();
            negrito.setBold(true);
            negrito.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle topo = planilha.createCellStyle();
            topo.setFont(negrito);
            topo.setFillForegroundColor(new XSSFColor(BORDO, null));
            topo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            topo.setVerticalAlignment(VerticalAlignment.CENTER);
            topo.setWrapText(true);
            topo.setBorderBottom(BorderStyle.THIN);
            cabecalho = topo;

            texto = planilha.createCellStyle();
            texto.setVerticalAlignment(VerticalAlignment.TOP);
            quebra = planilha.createCellStyle();
            quebra.setVerticalAlignment(VerticalAlignment.TOP);
            quebra.setWrapText(true);

            var formatos = planilha.createDataFormat();
            moeda = planilha.createCellStyle();
            moeda.setDataFormat(formatos.getFormat("\"R$\" #,##0.00"));
            moeda.setVerticalAlignment(VerticalAlignment.TOP);
            dataHora = planilha.createCellStyle();
            dataHora.setDataFormat(formatos.getFormat("dd/mm/yyyy hh:mm"));
            dataHora.setVerticalAlignment(VerticalAlignment.TOP);
            inteiro = planilha.createCellStyle();
            inteiro.setDataFormat(formatos.getFormat("0"));
            inteiro.setVerticalAlignment(VerticalAlignment.TOP);
            decimal = planilha.createCellStyle();
            decimal.setDataFormat(formatos.getFormat("0.0"));
            decimal.setVerticalAlignment(VerticalAlignment.TOP);
        }
    }

    private static Sheet cabecalho(XSSFWorkbook planilha, Estilos estilos, String nome, String[] colunas, int[] larguras) {
        Sheet aba = planilha.createSheet(nome);
        Row row = aba.createRow(0);
        row.setHeightInPoints(30);
        for (int i = 0; i < colunas.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(colunas[i]);
            cell.setCellStyle(estilos.cabecalho);
            aba.setColumnWidth(i, larguras[i] * 256);
        }
        aba.createFreezePane(0, 1);
        return aba;
    }

    /** Filtro automático no cabeçalho. Só faz sentido com pelo menos o cabeçalho. */
    private static void fechar(Sheet aba, int ultimaLinha, int ultimaColuna) {
        aba.setAutoFilter(new CellRangeAddress(0, Math.max(0, ultimaLinha - 1), 0, ultimaColuna));
    }

    private static void texto(Row row, int coluna, String valor, CellStyle estilo) {
        Cell cell = row.createCell(coluna);
        if (valor != null && !valor.isBlank()) {
            // O Excel corta célula em 32.767 caracteres; parecer gigante não pode quebrar o arquivo.
            cell.setCellValue(valor.length() > 32000 ? valor.substring(0, 32000) + "…" : valor);
        }
        cell.setCellStyle(estilo);
    }

    private static void numero(Row row, int coluna, Number valor, CellStyle estilo) {
        Cell cell = row.createCell(coluna);
        if (valor != null) {
            cell.setCellValue(valor.doubleValue());
        }
        cell.setCellStyle(estilo);
    }

    private static void moeda(Row row, int coluna, BigDecimal valor, Estilos estilos) {
        numero(row, coluna, valor, estilos.moeda);
    }

    private static void horas(Row row, int coluna, Double valor, Estilos estilos) {
        numero(row, coluna, valor, estilos.decimal);
    }

    private static void dataHora(Row row, int coluna, LocalDateTime valor, Estilos estilos) {
        Cell cell = row.createCell(coluna);
        if (valor != null) {
            cell.setCellValue(valor);
        }
        cell.setCellStyle(estilos.dataHora);
    }

    private static String praca(Dados dados, String documento) {
        EmpresaResolver.Empresa empresa = dados.empresas().get(documento);
        return empresa == null ? null : empresa.praca();
    }

    private static <T> List<T> ordenar(List<T> itens, Comparator<T> ordem) {
        if (itens == null) {
            return List.of();
        }
        List<T> copia = new ArrayList<>(itens);
        copia.sort(ordem);
        return copia;
    }

    private static String data(LocalDateTime valor) {
        return valor == null ? "" : valor.format(DATA_HORA);
    }

    private static String rotulo(LiberacaoParecerEntity parecer) {
        return switch (parecer.getPosicao()) {
            case FAVORAVEL -> "Favorável";
            case COM_RESSALVAS -> "Com ressalvas";
            case DESFAVORAVEL -> "Desfavorável";
        };
    }

    private static String rotulo(TipoEventoLiberacao tipo) {
        return switch (tipo) {
            case CRIACAO -> "Criação";
            case EDICAO -> "Edição";
            case TRANSICAO -> "Movimento";
            case REABERTURA -> "Reabertura";
            case PARECER -> "Parecer";
            case PENDENCIA_ABERTA -> "Pendência aberta";
            case PENDENCIA_RESPONDIDA -> "Pendência respondida";
            case ANEXO_ADICIONADO -> "Anexo enviado";
            case ANEXO_REMOVIDO -> "Anexo removido";
            case EXCLUSAO -> "Exclusão";
        };
    }
}
