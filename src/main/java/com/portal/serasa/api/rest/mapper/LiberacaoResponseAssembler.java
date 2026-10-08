package com.portal.serasa.api.rest.mapper;

import com.portal.serasa.api.rest.dto.response.LiberacaoCardResponse;
import com.portal.serasa.api.rest.dto.response.LiberacaoDetalheResponse;
import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.application.service.liberacao.LiberacaoAutorizacao;
import com.portal.serasa.application.service.liberacao.LiberacaoComentarioService;
import com.portal.serasa.application.service.liberacao.MencaoParser;
import com.portal.serasa.application.service.liberacao.LiberacaoService;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoComentarioEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEventoEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMembroEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoSacadoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEtiquetaEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEtiquetaEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoCardEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoComentarioJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoEventoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMembroJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoParecerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoPendenciaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoSacadoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Monta as respostas da esteira de liberação em lote.
 *
 * <p>O quadro carrega dezenas de cards de uma vez; buscar sacados, pareceres, pendências e
 * membros card a card seria uma consulta por card por coleção. Aqui cada coleção é uma consulta
 * só, agrupada em memória.</p>
 */
@Component
@RequiredArgsConstructor
public class LiberacaoResponseAssembler {

    private final LiberacaoSacadoJpaRepository sacadoRepository;
    private final LiberacaoParecerJpaRepository parecerRepository;
    private final LiberacaoPendenciaJpaRepository pendenciaRepository;
    private final LiberacaoMembroJpaRepository membroRepository;
    private final LiberacaoComentarioJpaRepository comentarioRepository;
    private final LiberacaoComentarioService comentarioService;
    private final LiberacaoCardEtiquetaJpaRepository cardEtiquetaRepository;
    private final LiberacaoEtiquetaJpaRepository etiquetaRepository;
    private final LiberacaoEventoJpaRepository eventoRepository;
    private final UserRepository userRepository;
    private final CompanyDetailRepository companyDetailRepository;
    private final LiberacaoAutorizacao autorizacao;
    private final LiberacaoService liberacaoService;

    @Transactional(readOnly = true)
    public List<LiberacaoCardResponse> cards(List<LiberacaoCardEntity> cards, UserEntity usuario) {
        if (cards.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = cards.stream().map(LiberacaoCardEntity::getId).toList();

        Map<UUID, List<LiberacaoSacadoEntity>> sacados = sacadoRepository.findByCardIdIn(ids).stream()
                .collect(Collectors.groupingBy(LiberacaoSacadoEntity::getCardId));
        Map<UUID, List<LiberacaoParecerEntity>> pareceres = parecerRepository.rodadaVigente(ids).stream()
                .collect(Collectors.groupingBy(LiberacaoParecerEntity::getCardId));
        Map<UUID, Long> pendenciasAbertas = pendenciaRepository.findByCardIdInAndRespondidaEmIsNull(ids).stream()
                .collect(Collectors.groupingBy(LiberacaoPendenciaEntity::getCardId, Collectors.counting()));
        Map<UUID, List<LiberacaoMembroEntity>> membros = membroRepository.findByCardIdIn(ids).stream()
                .collect(Collectors.groupingBy(LiberacaoMembroEntity::getCardId));
        Map<UUID, Long> comentarios = comentarioRepository.contarPorCard(ids).stream()
                .collect(Collectors.toMap(linha -> (UUID) linha[0], linha -> (Long) linha[1]));
        Map<UUID, LocalDateTime> ultimoEvento = maximos(eventoRepository.ultimoPorCard(ids));
        Map<UUID, LocalDateTime> ultimoComentario = maximos(comentarioRepository.ultimoPorCard(ids));
        Map<UUID, List<LiberacaoCardEtiquetaEntity>> vinculos = cardEtiquetaRepository.findByCardIdIn(ids).stream()
                .collect(Collectors.groupingBy(LiberacaoCardEtiquetaEntity::getCardId));
        Map<UUID, LiberacaoEtiquetaEntity> etiquetas = vinculos.isEmpty() ? Map.of()
                : etiquetaRepository.findAllById(vinculos.values().stream().flatMap(List::stream)
                        .map(LiberacaoCardEtiquetaEntity::getEtiquetaId).collect(Collectors.toSet())).stream()
                        .collect(Collectors.toMap(LiberacaoEtiquetaEntity::getId, Function.identity()));

        Map<UUID, UserEntity> usuarios = usuarios(membros.values().stream()
                .flatMap(List::stream).map(LiberacaoMembroEntity::getUsuarioId).toList());
        Set<String> cadastrados = cadastrados(cards.stream().map(LiberacaoCardEntity::getCedenteCnpj).toList());
        boolean comiteVazio = liberacaoService.comiteVazio();

        return cards.stream()
                .map(card -> card(card,
                        sacados.getOrDefault(card.getId(), List.of()),
                        pareceres.getOrDefault(card.getId(), List.of()),
                        pendenciasAbertas.getOrDefault(card.getId(), 0L).intValue(),
                        comentarios.getOrDefault(card.getId(), 0L).intValue(),
                        membros.getOrDefault(card.getId(), List.of()),
                        usuarios, cadastrados, comiteVazio, usuario))
                .map(resposta -> completar(resposta,
                        vinculos.getOrDefault(resposta.id(), List.of()).stream()
                                .map(vinculo -> etiquetas.get(vinculo.getEtiquetaId()))
                                .filter(Objects::nonNull)
                                .sorted(Comparator.comparing(LiberacaoEtiquetaEntity::getNome))
                                .map(etiqueta -> new LiberacaoCardResponse.Etiqueta(etiqueta.getId(), etiqueta.getNome(), etiqueta.getCor()))
                                .toList(),
                        mais(resposta.atualizadoEm(), ultimoEvento.get(resposta.id()), ultimoComentario.get(resposta.id()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public LiberacaoCardResponse card(LiberacaoCardEntity card, UserEntity usuario) {
        return cards(List.of(card), usuario).get(0);
    }

    @Transactional(readOnly = true)
    public LiberacaoDetalheResponse detalhe(LiberacaoCardEntity card, UserEntity usuario) {
        List<LiberacaoSacadoEntity> sacados = sacadoRepository.findByCardIdOrderByOrdem(card.getId());
        List<LiberacaoParecerEntity> pareceres = liberacaoService.pareceres(card.getId());
        List<LiberacaoPendenciaEntity> pendencias = liberacaoService.pendencias(card.getId());
        List<LiberacaoEventoEntity> eventos = liberacaoService.timeline(card.getId());
        List<LiberacaoComentarioEntity> comentarios = comentarioService.listar(card.getId());

        // Toda empresa citada em qualquer texto do card, numa consulta só.
        Set<String> mencionadas = new LinkedHashSet<>(MencaoParser.cnpjs(card.getParecerOrigem()));
        pareceres.forEach(parecer -> mencionadas.addAll(MencaoParser.cnpjs(parecer.getTexto())));
        pendencias.forEach(pendencia -> {
            mencionadas.addAll(MencaoParser.cnpjs(pendencia.getTexto()));
            mencionadas.addAll(MencaoParser.cnpjs(pendencia.getResposta()));
        });
        eventos.forEach(evento -> mencionadas.addAll(MencaoParser.cnpjs(evento.getTexto())));
        comentarios.forEach(comentario -> mencionadas.addAll(MencaoParser.cnpjs(comentario.getTexto())));

        Set<String> cadastrados = cadastrados(Stream.concat(
                sacados.stream().map(LiberacaoSacadoEntity::getCnpj), mencionadas.stream()).toList());
        Map<String, Boolean> empresas = new LinkedHashMap<>();
        mencionadas.forEach(cnpj -> empresas.put(cnpj, cadastrados.contains(cnpj)));

        return LiberacaoDetalheResponse.builder()
                .card(card(card, usuario))
                .sacados(sacados.stream()
                        .map(sacado -> new LiberacaoDetalheResponse.Sacado(sacado.getCnpj(), sacado.getNome(),
                                sacado.getValor(), cadastrados.contains(sacado.getCnpj())))
                        .toList())
                .pareceres(pareceres.stream().map(this::parecer).toList())
                .pendencias(pendencias.stream().map(pendencia -> pendencia(pendencia, usuario)).toList())
                .eventos(eventos.stream().map(this::evento).toList())
                .comentarios(comentarios.stream()
                        .map(comentario -> new LiberacaoDetalheResponse.Comentario(comentario.getId(),
                                comentario.getAutorId(), comentario.getAutorNome(), iniciais(comentario.getAutorNome()),
                                comentario.getTexto(), comentario.getCriadoEm(), comentario.getEditadoEm()))
                        .toList())
                .empresas(empresas)
                .build();
    }

    private LiberacaoCardResponse card(LiberacaoCardEntity card, List<LiberacaoSacadoEntity> sacados,
                                       List<LiberacaoParecerEntity> pareceres, int pendenciasAbertas,
                                       int comentarios, List<LiberacaoMembroEntity> membros, Map<UUID, UserEntity> usuarios,
                                       Set<String> cadastrados, boolean comiteVazio, UserEntity usuario) {
        List<String> aguardando = LiberacaoService.aguardando(pareceres);
        BigDecimal soma = sacados.stream()
                .map(LiberacaoSacadoEntity::getValor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean algumValor = sacados.stream().anyMatch(sacado -> sacado.getValor() != null);

        return LiberacaoCardResponse.builder()
                .id(card.getId())
                .numero(card.getNumero())
                .etapa(card.getEtapa())
                .etapaDesde(card.getEtapaDesde())
                .rodada(card.getRodada())
                .cedenteCnpj(card.getCedenteCnpj())
                .cedenteNome(card.getCedenteNome())
                .cedenteCadastrado(cadastrados.contains(card.getCedenteCnpj()))
                .tipoOperacao(card.getTipoOperacao())
                .valor(card.getValor())
                .prazo(card.getPrazo())
                .parecerOrigem(card.getParecerOrigem())
                .cor(card.getCor())
                .criadoPorId(card.getCriadoPorId())
                .criadoPorNome(card.getCriadoPorNome())
                .criadoEm(card.getCriadoEm())
                .atualizadoPorNome(card.getAtualizadoPorNome())
                .atualizadoEm(card.getAtualizadoEm())
                .finalizadoEm(card.getFinalizadoEm())
                .version(card.getVersion())
                .sacadosQtd(sacados.size())
                .sacados(sacados.stream()
                        .sorted(Comparator.comparing(LiberacaoSacadoEntity::getOrdem))
                        .map(sacado -> new LiberacaoCardResponse.SacadoCurto(sacado.getCnpj(), sacado.getNome()))
                        .toList())
                .somaSacados(algumValor ? soma : null)
                .pareceres(pareceres.stream().map(this::parecer).toList())
                .pendenciasAbertas(pendenciasAbertas)
                .comentarios(comentarios)
                .membros(membros.stream()
                        .sorted(Comparator.comparing(LiberacaoMembroEntity::getAdicionadoEm))
                        .map(membro -> usuarios.get(membro.getUsuarioId()))
                        .filter(Objects::nonNull)
                        .map(user -> new LiberacaoCardResponse.Pessoa(user.getId(), user.getName(), iniciais(user.getName())))
                        .toList())
                .podeEditar(autorizacao.podeEditar(card, usuario))
                // Avançar primeiro, devolver por último: é a ordem em que a tela lista os botões.
                .destinos(card.getEtapa().destinos().stream()
                        .sorted(Comparator.comparing((EtapaLiberacao etapa) -> etapa == EtapaLiberacao.ORIGEM)
                                .thenComparing(EtapaLiberacao::ordinal))
                        .map(destino -> {
                            var motivo = autorizacao.motivoBloqueio(card, destino, usuario, aguardando, comiteVazio);
                            return new LiberacaoCardResponse.Destino(destino, motivo.isEmpty(), motivo.orElse(null));
                        })
                        .toList())
                .build();
    }

    /** Etiquetas e última atividade vêm de consultas em lote feitas depois; entram aqui. */
    private static LiberacaoCardResponse completar(LiberacaoCardResponse base, List<LiberacaoCardResponse.Etiqueta> etiquetas,
                                                   LocalDateTime ultimaAtividade) {
        return new LiberacaoCardResponse(base.id(), base.numero(), base.etapa(), base.etapaDesde(), base.rodada(),
                base.cedenteCnpj(), base.cedenteNome(), base.cedenteCadastrado(), base.tipoOperacao(), base.valor(),
                base.prazo(), base.parecerOrigem(), base.cor(), etiquetas, base.criadoPorId(), base.criadoPorNome(),
                base.criadoEm(), base.atualizadoPorNome(), base.atualizadoEm(), base.finalizadoEm(), ultimaAtividade,
                base.version(), base.sacadosQtd(), base.sacados(), base.somaSacados(), base.pareceres(),
                base.pendenciasAbertas(), base.comentarios(), base.membros(), base.podeEditar(), base.destinos());
    }

    private static Map<UUID, LocalDateTime> maximos(List<Object[]> linhas) {
        return linhas.stream().collect(Collectors.toMap(linha -> (UUID) linha[0], linha -> (LocalDateTime) linha[1]));
    }

    private static LocalDateTime mais(LocalDateTime... datas) {
        LocalDateTime maior = null;
        for (LocalDateTime data : datas) {
            if (data != null && (maior == null || data.isAfter(maior))) {
                maior = data;
            }
        }
        return maior;
    }

    private LiberacaoCardResponse.Parecer parecer(LiberacaoParecerEntity parecer) {
        return new LiberacaoCardResponse.Parecer(parecer.getId(), parecer.getRodada(), parecer.getUsuarioId(),
                parecer.getUsuarioNome(), iniciais(parecer.getUsuarioNome()), parecer.getPosicao(),
                parecer.getTexto(), parecer.getRegistradoEm());
    }

    private LiberacaoDetalheResponse.Pendencia pendencia(LiberacaoPendenciaEntity pendencia, UserEntity usuario) {
        boolean podeResponder = pendencia.aberta()
                && (usuario.getId().equals(pendencia.getDestinatarioId()) || usuario.isAnalista());
        return new LiberacaoDetalheResponse.Pendencia(pendencia.getId(), pendencia.getAbertaPorNome(),
                pendencia.getDestinatarioId(), pendencia.getDestinatarioNome(), pendencia.getTexto(),
                pendencia.getResposta(), pendencia.getAbertaEm(), pendencia.getRespondidaEm(),
                pendencia.getRespondidaPorNome(), podeResponder);
    }

    private LiberacaoDetalheResponse.Evento evento(LiberacaoEventoEntity evento) {
        return new LiberacaoDetalheResponse.Evento(evento.getId(), evento.getTipo(), evento.getEtapaDe(),
                evento.getEtapaPara(), evento.getCampo(), evento.getValorAntes(), evento.getValorDepois(),
                evento.getTexto(), evento.getUsuarioNome(), evento.getCriadoEm());
    }

    private Map<UUID, UserEntity> usuarios(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    /** Quais destes documentos têm página de empresa. CPF nunca tem. */
    private Set<String> cadastrados(Collection<String> documentos) {
        List<String> cnpjs = documentos.stream()
                .filter(documento -> documento != null && documento.length() == 14)
                .distinct()
                .toList();
        if (cnpjs.isEmpty()) {
            return Set.of();
        }
        return companyDetailRepository.findByDocumentNumberIn(cnpjs).stream()
                .map(CompanyDetail::getDocumentNumber)
                .collect(Collectors.toSet());
    }

    /** Conectivos de nome que não viram inicial: "Andressa da Silva" é AS, não AD. */
    private static final Set<String> CONECTIVOS = Set.of("da", "de", "do", "das", "dos", "e");

    /** "Andressa Lima Souza" → "AS". Uma palavra só → as duas primeiras letras. */
    public static String iniciais(String nome) {
        if (nome == null || nome.isBlank()) {
            return "?";
        }
        String[] partes = Stream.of(nome.trim().split("\\s+"))
                .filter(parte -> !CONECTIVOS.contains(parte.toLowerCase()))
                .toArray(String[]::new);
        if (partes.length == 0) {
            partes = nome.trim().split("\\s+");
        }
        if (partes.length == 1) {
            String unica = partes[0];
            return unica.substring(0, Math.min(2, unica.length())).toUpperCase();
        }
        return (partes[0].substring(0, 1) + partes[partes.length - 1].substring(0, 1)).toUpperCase();
    }
}
