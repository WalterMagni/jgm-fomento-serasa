package com.portal.serasa.api.rest.mapper;

import com.portal.serasa.api.rest.dto.response.LiberacaoCardResponse;
import com.portal.serasa.api.rest.dto.response.LiberacaoDetalheResponse;
import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.application.service.liberacao.LiberacaoAutorizacao;
import com.portal.serasa.application.service.liberacao.LiberacaoService;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEventoEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMembroEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoSacadoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMembroJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoParecerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoPendenciaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoSacadoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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

        Map<UUID, UserEntity> usuarios = usuarios(membros.values().stream()
                .flatMap(List::stream).map(LiberacaoMembroEntity::getUsuarioId).toList());
        Set<String> cadastrados = cadastrados(cards.stream().map(LiberacaoCardEntity::getCedenteCnpj).toList());
        boolean comiteVazio = liberacaoService.comiteVazio();

        return cards.stream()
                .map(card -> card(card,
                        sacados.getOrDefault(card.getId(), List.of()),
                        pareceres.getOrDefault(card.getId(), List.of()),
                        pendenciasAbertas.getOrDefault(card.getId(), 0L).intValue(),
                        membros.getOrDefault(card.getId(), List.of()),
                        usuarios, cadastrados, comiteVazio, usuario))
                .toList();
    }

    @Transactional(readOnly = true)
    public LiberacaoCardResponse card(LiberacaoCardEntity card, UserEntity usuario) {
        return cards(List.of(card), usuario).get(0);
    }

    @Transactional(readOnly = true)
    public LiberacaoDetalheResponse detalhe(LiberacaoCardEntity card, UserEntity usuario) {
        List<LiberacaoSacadoEntity> sacados = sacadoRepository.findByCardIdOrderByOrdem(card.getId());
        Set<String> cadastrados = cadastrados(sacados.stream().map(LiberacaoSacadoEntity::getCnpj).toList());

        return LiberacaoDetalheResponse.builder()
                .card(card(card, usuario))
                .sacados(sacados.stream()
                        .map(sacado -> new LiberacaoDetalheResponse.Sacado(sacado.getCnpj(), sacado.getNome(),
                                sacado.getValor(), cadastrados.contains(sacado.getCnpj())))
                        .toList())
                .pareceres(liberacaoService.pareceres(card.getId()).stream().map(this::parecer).toList())
                .pendencias(liberacaoService.pendencias(card.getId()).stream()
                        .map(pendencia -> pendencia(pendencia, usuario))
                        .toList())
                .eventos(liberacaoService.timeline(card.getId()).stream().map(this::evento).toList())
                .build();
    }

    private LiberacaoCardResponse card(LiberacaoCardEntity card, List<LiberacaoSacadoEntity> sacados,
                                       List<LiberacaoParecerEntity> pareceres, int pendenciasAbertas,
                                       List<LiberacaoMembroEntity> membros, Map<UUID, UserEntity> usuarios,
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
                .criadoPorNome(card.getCriadoPorNome())
                .criadoEm(card.getCriadoEm())
                .atualizadoPorNome(card.getAtualizadoPorNome())
                .atualizadoEm(card.getAtualizadoEm())
                .finalizadoEm(card.getFinalizadoEm())
                .version(card.getVersion())
                .sacadosQtd(sacados.size())
                .somaSacados(algumValor ? soma : null)
                .pareceres(pareceres.stream().map(this::parecer).toList())
                .pendenciasAbertas(pendenciasAbertas)
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
