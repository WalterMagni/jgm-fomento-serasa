package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.domain.exception.ConflitoEdicaoException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.OrigemMembro;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import com.portal.serasa.domain.model.liberacao.TipoEventoLiberacao;
import com.portal.serasa.domain.model.liberacao.TipoOperacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEventoEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMembroEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoSacadoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoCardJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoEventoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMembroJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoParecerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoPendenciaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoSacadoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Regras da esteira de liberação de operações.
 *
 * <p>Toda escrita passa por aqui e grava evento na mesma transação. Edição grava um evento por
 * campo, com o valor antes e depois: é o que responde "quem mudou o valor de 50 para 80 mil"
 * sem depender da memória de ninguém.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiberacaoService {

    /** Quanto tempo um finalizado continua no quadro. Mais antigo só no relatório. */
    public static final int DIAS_FINALIZADOS_NO_QUADRO = 30;

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final LiberacaoCardJpaRepository cardRepository;
    private final LiberacaoSacadoJpaRepository sacadoRepository;
    private final LiberacaoMembroJpaRepository membroRepository;
    private final LiberacaoParecerJpaRepository parecerRepository;
    private final LiberacaoPendenciaJpaRepository pendenciaRepository;
    private final LiberacaoEventoJpaRepository eventoRepository;
    private final UserRepository userRepository;
    private final CompanyDetailRepository companyDetailRepository;
    private final LiberacaoAutorizacao autorizacao;
    private final ApplicationEventPublisher eventos;

    /** Dados editáveis do card. Os mesmos na criação e na edição. */
    public record DadosCard(String cedenteCnpj, String cedenteNome, String tipoOperacao,
                            BigDecimal valor, LocalDateTime prazo, String parecerOrigem,
                            List<DadosSacado> sacados) {
    }

    public record DadosSacado(String documento, String nome, BigDecimal valor) {
    }

    public record NovaPendencia(UUID destinatarioId, String texto) {
    }

    /** Resultado de registrar parecer: o card e se este foi o último que faltava. */
    public record ParecerRegistrado(LiberacaoCardEntity card, LiberacaoParecerEntity parecer, boolean ultimo) {
    }

    // ---------------------------------------------------------------- leitura

    @Transactional(readOnly = true)
    public List<LiberacaoCardEntity> listarQuadro(LocalDateTime finalizadosDesde) {
        LocalDateTime corte = finalizadosDesde != null
                ? finalizadosDesde
                : LocalDateTime.now().minusDays(DIAS_FINALIZADOS_NO_QUADRO);
        return cardRepository.listarQuadro(corte);
    }

    @Transactional(readOnly = true)
    public LiberacaoCardEntity buscar(UUID id) {
        return cardRepository.findByIdAndExcluidoEmIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Card não encontrado"));
    }

    @Transactional(readOnly = true)
    public List<LiberacaoEventoEntity> timeline(UUID cardId) {
        return eventoRepository.findByCardIdOrderByCriadoEmDesc(cardId);
    }

    /**
     * O que pede atenção, para o contador do menu e a faixa do quadro.
     *
     * <p>{@code total} é o que espera por esta pessoa: parecer devido e pendência destinada a
     * ela. {@code atrasados} são os cards em aberto com prazo vencido, iguais para todo mundo,
     * como os atrasados da prospecção. {@code precisamAtencao} junta os dois sem contar o mesmo
     * card duas vezes — é o número vermelho do menu.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Long> resumo(UserEntity usuario) {
        long pareceres = parecerRepository.contarAguardando(usuario.getId());
        long pendencias = pendenciaRepository.contarAbertasPara(usuario.getId());
        List<UUID> atrasados = cardRepository.idsAtrasados(LocalDateTime.now());

        Set<UUID> atencao = new java.util.HashSet<>(atrasados);
        atencao.addAll(parecerRepository.cardsAguardando(usuario.getId()));
        atencao.addAll(pendenciaRepository.cardsComPendenciaPara(usuario.getId()));

        Map<String, Long> resumo = new LinkedHashMap<>();
        resumo.put("pareceresAguardando", pareceres);
        resumo.put("pendenciasParaMim", pendencias);
        resumo.put("total", pareceres + pendencias);
        resumo.put("atrasados", (long) atrasados.size());
        resumo.put("precisamAtencao", (long) atencao.size());
        return resumo;
    }

    /** Nomes de quem ainda deve parecer na rodada vigente. É o que trava a saída do Comitê. */
    @Transactional(readOnly = true)
    public List<String> aguardandoParecer(LiberacaoCardEntity card) {
        return aguardando(parecerRepository.findByCardIdAndRodadaOrderByCriadoEm(card.getId(), card.getRodada()));
    }

    public static List<String> aguardando(List<LiberacaoParecerEntity> rodada) {
        // usuario_id nulo é parecer de quem foi removido do portal: não pode travar o card.
        return rodada.stream()
                .filter(parecer -> !parecer.registrado() && parecer.getUsuarioId() != null)
                .map(LiberacaoParecerEntity::getUsuarioNome)
                .toList();
    }

    /** Tipos para o formulário e o filtro: os padrões primeiro, depois os criados pelo time. */
    @Transactional(readOnly = true)
    public List<String> tiposDeOperacao() {
        List<String> tipos = new ArrayList<>(TipoOperacao.PADRAO);
        cardRepository.tiposUsados().stream()
                .filter(tipo -> TipoOperacao.PADRAO.stream().noneMatch(padrao -> padrao.equalsIgnoreCase(tipo)))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(tipos::add);
        return tipos;
    }

    @Transactional(readOnly = true)
    public boolean comiteVazio() {
        return userRepository.findByComiteTrueOrderByNameAsc().isEmpty();
    }

    // ---------------------------------------------------------------- criação

    @Transactional
    public LiberacaoCardEntity criar(DadosCard dados, UserEntity autor) {
        autorizacao.exigirAutenticado(autor);
        String cnpj = normalizarCnpj(dados.cedenteCnpj());
        LocalDateTime agora = LocalDateTime.now();

        LiberacaoCardEntity card = cardRepository.saveAndFlush(LiberacaoCardEntity.builder()
                .etapa(EtapaLiberacao.ORIGEM)
                .etapaDesde(agora)
                .rodada(1)
                .cedenteCnpj(cnpj)
                .cedenteNome(resolverNomeCedente(cnpj, dados.cedenteNome()))
                .tipoOperacao(TipoOperacao.resolver(dados.tipoOperacao(), cardRepository.tiposUsados()))
                .valor(dados.valor())
                .prazo(dados.prazo())
                .parecerOrigem(textoOuNulo(dados.parecerOrigem()))
                .criadoPorId(autor.getId())
                .criadoPorNome(autor.getName())
                .criadoEm(agora)
                .atualizadoPorId(autor.getId())
                .atualizadoPorNome(autor.getName())
                .atualizadoEm(agora)
                .build());

        gravarSacados(card.getId(), normalizarSacados(dados.sacados()));
        adicionarMembro(card.getId(), autor.getId(), OrigemMembro.CRIADOR);
        registrar(card, TipoEventoLiberacao.CRIACAO, null, null, null, null, null, "Card criado", autor);
        Set<UUID> mencionados = acompanharMencionados(card.getId(), MencaoParser.usuarios(card.getParecerOrigem()));
        eventos.publishEvent(new LiberacaoEvento.Criado(card, autor, mencionados, trecho(card.getParecerOrigem())));
        return card;
    }

    // ---------------------------------------------------------------- edição

    /**
     * Edita os campos do card.
     *
     * <p>A versão vem da tela, que a recebeu quando abriu o card. Se alguém salvou no meio, a
     * gravação é recusada com o nome de quem salvou — sobrescrever calado apagaria o trabalho da
     * colega sem que nenhuma das duas soubesse.</p>
     */
    @Transactional
    public LiberacaoCardEntity editar(UUID id, Long versao, DadosCard dados, UserEntity autor) {
        LiberacaoCardEntity card = buscar(id);
        autorizacao.exigirEdicao(card, autor);
        if (versao == null || !versao.equals(card.getVersion())) {
            throw new ConflitoEdicaoException("O card foi alterado por " + card.getAtualizadoPorNome()
                    + " em " + card.getAtualizadoEm().format(HORA) + ". Recarregue para ver a versão atual.");
        }

        String cnpj = normalizarCnpj(dados.cedenteCnpj());
        List<String[]> mudancas = new ArrayList<>();

        if (!cnpj.equals(card.getCedenteCnpj())) {
            String nome = resolverNomeCedente(cnpj, dados.cedenteNome());
            mudancas.add(new String[]{"cedente", titulo(card.getCedenteNome(), card.getCedenteCnpj()), titulo(nome, cnpj)});
            card.setCedenteCnpj(cnpj);
            card.setCedenteNome(nome);
        } else if (dados.cedenteNome() != null && !dados.cedenteNome().isBlank()
                && !dados.cedenteNome().trim().equals(card.getCedenteNome())) {
            mudancas.add(new String[]{"cedente", card.getCedenteNome(), dados.cedenteNome().trim()});
            card.setCedenteNome(dados.cedenteNome().trim());
        }
        String tipo = TipoOperacao.resolver(dados.tipoOperacao(), cardRepository.tiposUsados());
        if (!Objects.equals(tipo, card.getTipoOperacao())) {
            mudancas.add(new String[]{"tipoOperacao", rotulo(card.getTipoOperacao()), rotulo(tipo)});
            card.setTipoOperacao(tipo);
        }
        if (!mesmoValor(dados.valor(), card.getValor())) {
            mudancas.add(new String[]{"valor", moeda(card.getValor()), moeda(dados.valor())});
            card.setValor(dados.valor());
        }
        if (!Objects.equals(dados.prazo(), card.getPrazo())) {
            mudancas.add(new String[]{"prazo", dataHora(card.getPrazo()), dataHora(dados.prazo())});
            card.setPrazo(dados.prazo());
        }
        String parecer = textoOuNulo(dados.parecerOrigem());
        String parecerAnterior = card.getParecerOrigem();
        if (!Objects.equals(parecer, card.getParecerOrigem())) {
            mudancas.add(new String[]{"parecerOrigem", card.getParecerOrigem(), parecer});
            card.setParecerOrigem(parecer);
        }

        List<DadosSacado> novos = normalizarSacados(dados.sacados());
        String diferencaSacados = diferencaSacados(sacadoRepository.findByCardIdOrderByOrdem(id), novos);
        if (diferencaSacados != null) {
            gravarSacados(id, novos);
        }

        if (mudancas.isEmpty() && diferencaSacados == null) {
            return card;
        }

        tocar(card, autor);
        LiberacaoCardEntity salvo = cardRepository.saveAndFlush(card);
        for (String[] mudanca : mudancas) {
            registrar(salvo, TipoEventoLiberacao.EDICAO, null, null, mudanca[0], mudanca[1], mudanca[2], null, autor);
        }
        if (diferencaSacados != null) {
            registrar(salvo, TipoEventoLiberacao.EDICAO, null, null, "sacados", null, null, diferencaSacados, autor);
        }
        Set<UUID> mencionados = acompanharMencionados(id, MencaoParser.novosUsuarios(parecerAnterior, parecer));
        eventos.publishEvent(new LiberacaoEvento.Editado(salvo, autor, mencionados, trecho(parecer)));
        return salvo;
    }

    // ------------------------------------------------------------- transições

    /**
     * Move o card.
     *
     * <p>{@code de} é a etapa em que a tela viu o card. Se outra pessoa já o moveu, a recusa diz
     * para onde e quem — arrastar um card desatualizado não pode desfazer a decisão de outra
     * analista.</p>
     */
    @Transactional
    public LiberacaoCardEntity transicionar(UUID id, EtapaLiberacao de, EtapaLiberacao para,
                                            List<NovaPendencia> pendencias, String observacao,
                                            UserEntity autor) {
        LiberacaoCardEntity card = buscar(id);
        if (card.getEtapa() != de) {
            throw new ConflitoEdicaoException("O card já está em " + LiberacaoAutorizacao.rotulo(card.getEtapa())
                    + ", movido por " + card.getAtualizadoPorNome() + ". Recarregue o quadro.");
        }

        List<UserEntity> comite = para == EtapaLiberacao.COMITE
                ? userRepository.findByComiteTrueOrderByNameAsc()
                : List.of();
        autorizacao.exigirTransicao(card, para, autor, aguardandoParecer(card),
                para == EtapaLiberacao.COMITE && comite.isEmpty());

        List<NovaPendencia> novasPendencias = pendencias == null ? List.of() : pendencias;
        if (para != EtapaLiberacao.PENDENCIA && !novasPendencias.isEmpty()) {
            throw new IllegalArgumentException("Pendência só é aberta ao mover para Pendência.");
        }
        if (para == EtapaLiberacao.PENDENCIA && novasPendencias.isEmpty()
                && pendenciaRepository.countByCardIdAndRespondidaEmIsNull(id) == 0) {
            throw new TransicaoInvalidaException("Informe para quem é a pendência e o que falta.");
        }

        boolean reabertura = de.terminal();
        if (de == EtapaLiberacao.COMITE && para == EtapaLiberacao.ORIGEM) {
            // Devolução: o que ninguém registrou nesta rodada some; o registrado fica como histórico.
            parecerRepository.apagarAguardando(id, card.getRodada());
            card.setRodada(card.getRodada() + 1);
        }
        if (reabertura) {
            card.setRodada(card.getRodada() + 1);
        }

        LocalDateTime agora = LocalDateTime.now();
        card.setEtapa(para);
        card.setEtapaDesde(agora);
        card.setFinalizadoEm(para.terminal() ? agora : null);
        tocar(card, autor);
        LiberacaoCardEntity salvo = cardRepository.saveAndFlush(card);

        // Da Pendência de volta ao Comitê os pareceres da rodada continuam valendo; só Origem e
        // reabertura chegam aqui com rodada sem ninguém convocado.
        if (para == EtapaLiberacao.COMITE && de != EtapaLiberacao.PENDENCIA) {
            convocarComite(salvo, comite);
        }

        registrar(salvo, reabertura ? TipoEventoLiberacao.REABERTURA : TipoEventoLiberacao.TRANSICAO,
                de, para, null, null, null, textoOuNulo(observacao), autor);
        Set<UUID> mencionados = acompanharMencionados(id, MencaoParser.usuarios(observacao));
        eventos.publishEvent(new LiberacaoEvento.Movido(salvo, de, para, autor, mencionados, trecho(observacao)));

        for (NovaPendencia pendencia : novasPendencias) {
            abrirPendencia(salvo, pendencia, autor);
        }
        return salvo;
    }

    // --------------------------------------------------------------- pareceres

    @Transactional
    public ParecerRegistrado registrarParecer(UUID id, PosicaoParecer posicao, String texto, UserEntity autor) {
        autorizacao.exigirAutenticado(autor);
        LiberacaoCardEntity card = buscar(id);
        if (card.getEtapa() != EtapaLiberacao.COMITE) {
            throw new TransicaoInvalidaException("Parecer só é registrado com o card no Comitê.");
        }
        if (posicao == null) {
            throw new IllegalArgumentException("Escolha a posição do parecer.");
        }
        LiberacaoParecerEntity parecer = parecerRepository
                .findByCardIdAndRodadaAndUsuarioId(id, card.getRodada(), autor.getId())
                .orElseThrow(() -> new com.portal.serasa.domain.exception.AcessoNegadoException(
                        "Seu parecer não é esperado neste card."));

        boolean revisao = parecer.registrado();
        String textoAnterior = parecer.getTexto();
        parecer.setPosicao(posicao);
        parecer.setTexto(textoOuNulo(texto));
        parecer.setRegistradoEm(LocalDateTime.now());
        parecer.setUsuarioNome(autor.getName());
        LiberacaoParecerEntity salvo = parecerRepository.save(parecer);

        registrar(card, TipoEventoLiberacao.PARECER, null, null, null, null, rotulo(posicao),
                revisao ? "Parecer revisto" : "Parecer registrado", autor);

        boolean ultimo = !revisao && aguardandoParecer(card).isEmpty();
        Set<UUID> mencionados = acompanharMencionados(id, MencaoParser.novosUsuarios(textoAnterior, salvo.getTexto()));
        eventos.publishEvent(new LiberacaoEvento.ParecerDado(card, salvo, ultimo, revisao, autor, mencionados, trecho(salvo.getTexto())));
        return new ParecerRegistrado(card, salvo, ultimo);
    }

    @Transactional(readOnly = true)
    public List<LiberacaoParecerEntity> pareceres(UUID cardId) {
        return parecerRepository.findByCardIdOrderByRodadaDescCriadoEm(cardId);
    }

    // --------------------------------------------------------------- pendências

    @Transactional
    public LiberacaoPendenciaEntity novaPendencia(UUID id, NovaPendencia pendencia, UserEntity autor) {
        LiberacaoCardEntity card = buscar(id);
        if (card.getEtapa() != EtapaLiberacao.PENDENCIA) {
            throw new TransicaoInvalidaException("Pendência nova só com o card em Pendência.");
        }
        return abrirPendencia(card, pendencia, autor);
    }

    @Transactional
    public LiberacaoPendenciaEntity responderPendencia(UUID cardId, UUID pendenciaId, String resposta, UserEntity autor) {
        LiberacaoCardEntity card = buscar(cardId);
        LiberacaoPendenciaEntity pendencia = pendenciaRepository.findById(pendenciaId)
                .filter(encontrada -> encontrada.getCardId().equals(cardId))
                .orElseThrow(() -> new EntityNotFoundException("Pendência não encontrada"));
        autorizacao.exigirResposta(pendencia, autor);
        if (!pendencia.aberta()) {
            throw new TransicaoInvalidaException("Pendência já respondida por " + pendencia.getRespondidaPorNome() + ".");
        }
        String texto = textoOuNulo(resposta);
        if (texto == null) {
            throw new IllegalArgumentException("Escreva a resposta da pendência.");
        }
        pendencia.setResposta(texto);
        pendencia.setRespondidaEm(LocalDateTime.now());
        pendencia.setRespondidaPorNome(autor.getName());
        LiberacaoPendenciaEntity salva = pendenciaRepository.save(pendencia);
        registrar(card, TipoEventoLiberacao.PENDENCIA_RESPONDIDA, null, null, null, null, null,
                "Respondeu a pendência de " + pendencia.getAbertaPorNome(), autor);
        Set<UUID> mencionados = acompanharMencionados(cardId, MencaoParser.usuarios(texto));
        eventos.publishEvent(new LiberacaoEvento.PendenciaRespondida(card, salva, autor, mencionados, trecho(texto)));
        return salva;
    }

    @Transactional(readOnly = true)
    public List<LiberacaoPendenciaEntity> pendencias(UUID cardId) {
        return pendenciaRepository.findByCardIdOrderByAbertaEm(cardId);
    }

    // ---------------------------------------------------------------- exclusão

    /**
     * Exclusão lógica. O card some do quadro e fica no banco com quem apagou e quando: é liberação
     * de dinheiro, e "quem sumiu com aquele card" precisa ter resposta.
     */
    @Transactional
    public void excluir(UUID id, UserEntity autor) {
        LiberacaoCardEntity card = buscar(id);
        autorizacao.exigirEdicao(card, autor);
        LocalDateTime agora = LocalDateTime.now();
        card.setExcluidoEm(agora);
        card.setExcluidoPorId(autor.getId());
        card.setExcluidoPorNome(autor.getName());
        cardRepository.save(card);
        registrar(card, TipoEventoLiberacao.EXCLUSAO, card.getEtapa(), null, null, null, null, "Card apagado", autor);
        eventos.publishEvent(new LiberacaoEvento.Excluido(card, autor));
        log.warn("Card de liberação #{} ({} · {}) apagado por {} em {}",
                card.getNumero(), card.getCedenteNome(), card.getCedenteCnpj(), autor.getEmail(), card.getEtapa());
    }

    // ---------------------------------------------------------------- internos

    private void convocarComite(LiberacaoCardEntity card, List<UserEntity> comite) {
        LocalDateTime agora = LocalDateTime.now();
        for (UserEntity membro : comite) {
            if (parecerRepository.findByCardIdAndRodadaAndUsuarioId(card.getId(), card.getRodada(), membro.getId()).isEmpty()) {
                parecerRepository.save(LiberacaoParecerEntity.builder()
                        .cardId(card.getId())
                        .rodada(card.getRodada())
                        .usuarioId(membro.getId())
                        .usuarioNome(membro.getName())
                        .criadoEm(agora)
                        .build());
            }
            adicionarMembro(card.getId(), membro.getId(), OrigemMembro.COMITE);
        }
    }

    private LiberacaoPendenciaEntity abrirPendencia(LiberacaoCardEntity card, NovaPendencia pendencia, UserEntity autor) {
        autorizacao.exigirAnalista(autor, "abrir pendência");
        String texto = textoOuNulo(pendencia.texto());
        if (pendencia.destinatarioId() == null || texto == null) {
            throw new IllegalArgumentException("Pendência precisa de destinatário e texto.");
        }
        UserEntity destinatario = userRepository.findById(pendencia.destinatarioId())
                .orElseThrow(() -> new EntityNotFoundException("Destinatário da pendência não encontrado"));

        LiberacaoPendenciaEntity salva = pendenciaRepository.save(LiberacaoPendenciaEntity.builder()
                .cardId(card.getId())
                .abertaPorId(autor.getId())
                .abertaPorNome(autor.getName())
                .destinatarioId(destinatario.getId())
                .destinatarioNome(destinatario.getName())
                .texto(texto)
                .abertaEm(LocalDateTime.now())
                .build());
        adicionarMembro(card.getId(), destinatario.getId(), OrigemMembro.PENDENCIA);
        registrar(card, TipoEventoLiberacao.PENDENCIA_ABERTA, null, null, null, null, null,
                "Pendência para " + destinatario.getName() + ": " + texto, autor);
        Set<UUID> mencionados = acompanharMencionados(card.getId(), MencaoParser.usuarios(texto));
        // O destinatário já recebe "abriu uma pendência para você"; marcado no mesmo texto seria aviso dobrado.
        mencionados.remove(destinatario.getId());
        eventos.publishEvent(new LiberacaoEvento.PendenciaAberta(card, salva, autor, mencionados, trecho(texto)));
        return salva;
    }

    /**
     * Quem foi marcado passa a acompanhar o card, como no GitHub. Id que não é usuário (marcação
     * digitada à mão) é descartado aqui, antes de virar chave estrangeira inválida.
     */
    public Set<UUID> acompanharMencionados(UUID cardId, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return new java.util.LinkedHashSet<>();
        }
        Set<UUID> existentes = userRepository.findAllById(ids).stream()
                .map(UserEntity::getId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        existentes.forEach(usuarioId -> adicionarMembro(cardId, usuarioId, OrigemMembro.MENCAO));
        return existentes;
    }

    /** Para o comentário fazer quem comentou acompanhar o card. */
    public void acompanhar(UUID cardId, UUID usuarioId, OrigemMembro origem) {
        adicionarMembro(cardId, usuarioId, origem);
    }

    /** Publica para quem está com o quadro aberto ver a mudança. Sem menção, sem notificação. */
    public void avisarEdicao(LiberacaoCardEntity card, UserEntity autor) {
        eventos.publishEvent(new LiberacaoEvento.Editado(card, autor, new java.util.LinkedHashSet<>(), null));
    }

    public static String trecho(String texto) {
        String plano = MencaoParser.textoPlano(texto);
        return plano == null || plano.isBlank() ? null : plano;
    }

    private void adicionarMembro(UUID cardId, UUID usuarioId, OrigemMembro origem) {
        if (usuarioId == null || membroRepository.existsByCardIdAndUsuarioId(cardId, usuarioId)) {
            return;
        }
        membroRepository.save(LiberacaoMembroEntity.builder()
                .cardId(cardId)
                .usuarioId(usuarioId)
                .origem(origem)
                .adicionadoEm(LocalDateTime.now())
                .build());
    }

    private void gravarSacados(UUID cardId, List<DadosSacado> sacados) {
        sacadoRepository.apagarDoCard(cardId);
        List<LiberacaoSacadoEntity> linhas = new ArrayList<>();
        for (int i = 0; i < sacados.size(); i++) {
            DadosSacado sacado = sacados.get(i);
            linhas.add(LiberacaoSacadoEntity.builder()
                    .cardId(cardId)
                    .cnpj(sacado.documento())
                    .nome(sacado.nome())
                    .valor(sacado.valor())
                    .ordem(i)
                    .build());
        }
        sacadoRepository.saveAll(linhas);
    }

    /**
     * Normaliza documento, descarta linha vazia, junta CNPJ repetido e completa o nome pela base.
     *
     * <p>Sacado aceita CPF: pessoa física sacada é comum em duplicata. Cedente não — a página da
     * empresa, para onde o card aponta, é por CNPJ.</p>
     */
    private List<DadosSacado> normalizarSacados(List<DadosSacado> entrada) {
        if (entrada == null) {
            return List.of();
        }
        Map<String, DadosSacado> porDocumento = new LinkedHashMap<>();
        for (DadosSacado sacado : entrada) {
            if (sacado == null || sacado.documento() == null || sacado.documento().isBlank()) {
                continue;
            }
            String documento = sacado.documento().replaceAll("\\D", "");
            if (documento.length() != 14 && documento.length() != 11) {
                throw new IllegalArgumentException("Documento de sacado inválido: " + sacado.documento());
            }
            if (porDocumento.containsKey(documento)) {
                throw new IllegalArgumentException("Sacado repetido: " + formatarDocumento(documento));
            }
            porDocumento.put(documento, new DadosSacado(documento, textoOuNulo(sacado.nome()), sacado.valor()));
        }
        if (porDocumento.isEmpty()) {
            return List.of();
        }

        Map<String, String> nomesDaBase = new LinkedHashMap<>();
        List<String> semNome = porDocumento.values().stream()
                .filter(sacado -> sacado.nome() == null && sacado.documento().length() == 14)
                .map(DadosSacado::documento)
                .toList();
        if (!semNome.isEmpty()) {
            for (CompanyDetail empresa : companyDetailRepository.findByDocumentNumberIn(semNome)) {
                nomesDaBase.put(empresa.getDocumentNumber(), empresa.getCompanyName());
            }
        }
        return porDocumento.values().stream()
                .map(sacado -> sacado.nome() != null ? sacado
                        : new DadosSacado(sacado.documento(), nomesDaBase.get(sacado.documento()), sacado.valor()))
                .toList();
    }

    /** Resumo legível do que mudou na lista de sacados, ou nulo se nada mudou. */
    static String diferencaSacados(List<LiberacaoSacadoEntity> antes, List<DadosSacado> depois) {
        Map<String, LiberacaoSacadoEntity> anteriores = new LinkedHashMap<>();
        antes.forEach(sacado -> anteriores.put(sacado.getCnpj(), sacado));
        Map<String, DadosSacado> novos = new LinkedHashMap<>();
        depois.forEach(sacado -> novos.put(sacado.documento(), sacado));

        List<String> partes = new ArrayList<>();
        novos.values().stream()
                .filter(sacado -> !anteriores.containsKey(sacado.documento()))
                .forEach(sacado -> partes.add("+ " + rotuloSacado(sacado.documento(), sacado.nome())));
        anteriores.values().stream()
                .filter(sacado -> !novos.containsKey(sacado.getCnpj()))
                .forEach(sacado -> partes.add("− " + rotuloSacado(sacado.getCnpj(), sacado.getNome())));
        novos.values().stream()
                .filter(sacado -> anteriores.containsKey(sacado.documento()))
                .filter(sacado -> !mesmoValor(sacado.valor(), anteriores.get(sacado.documento()).getValor()))
                .forEach(sacado -> partes.add(rotuloSacado(sacado.documento(), sacado.nome()) + ": "
                        + moeda(anteriores.get(sacado.documento()).getValor()) + " → " + moeda(sacado.valor())));

        boolean reordenado = partes.isEmpty()
                && !new ArrayList<>(anteriores.keySet()).equals(new ArrayList<>(novos.keySet()));
        if (reordenado) {
            return "Ordem dos sacados alterada";
        }
        return partes.isEmpty() ? null : String.join("; ", partes);
    }

    private String resolverNomeCedente(String cnpj, String informado) {
        Optional<String> daBase = companyDetailRepository.findByDocumentNumber(cnpj)
                .map(CompanyDetail::getCompanyName)
                .filter(nome -> !nome.isBlank());
        if (daBase.isPresent()) {
            return daBase.get();
        }
        String nome = textoOuNulo(informado);
        if (nome == null) {
            throw new IllegalArgumentException("Empresa não cadastrada: informe a razão social do cedente.");
        }
        return nome;
    }

    private void tocar(LiberacaoCardEntity card, UserEntity autor) {
        card.setAtualizadoEm(LocalDateTime.now());
        card.setAtualizadoPorId(autor.getId());
        card.setAtualizadoPorNome(autor.getName());
    }

    /** Para edições feitas fora deste serviço (cor, etiquetas) entrarem na mesma timeline. */
    public void registrarEdicao(LiberacaoCardEntity card, String campo, String antes, String depois, UserEntity autor) {
        registrar(card, TipoEventoLiberacao.EDICAO, null, null, campo, antes, depois, null, autor);
    }

    private void registrar(LiberacaoCardEntity card, TipoEventoLiberacao tipo, EtapaLiberacao de,
                           EtapaLiberacao para, String campo, String antes, String depois,
                           String texto, UserEntity autor) {
        eventoRepository.save(LiberacaoEventoEntity.builder()
                .cardId(card.getId())
                .tipo(tipo)
                .etapaDe(de)
                .etapaPara(para)
                .campo(campo)
                .valorAntes(antes)
                .valorDepois(depois)
                .texto(texto)
                .usuarioId(autor != null ? autor.getId() : null)
                .usuarioNome(autor != null ? autor.getName() : "Sistema")
                .criadoEm(LocalDateTime.now())
                .build());
    }

    static String normalizarCnpj(String cnpj) {
        String digitos = cnpj == null ? "" : cnpj.replaceAll("\\D", "");
        if (digitos.length() != 14) {
            throw new IllegalArgumentException("CNPJ do cedente inválido: informe os 14 dígitos.");
        }
        return digitos;
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static boolean mesmoValor(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    static String moeda(BigDecimal valor) {
        return valor == null ? "—" : NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor);
    }

    private static String dataHora(LocalDateTime valor) {
        return valor == null ? "—" : valor.format(DATA_HORA);
    }

    private static String titulo(String nome, String cnpj) {
        return nome + " · " + formatarDocumento(cnpj);
    }

    private static String rotuloSacado(String documento, String nome) {
        return nome == null ? formatarDocumento(documento) : nome + " (" + formatarDocumento(documento) + ")";
    }

    static String formatarDocumento(String documento) {
        if (documento == null) {
            return "";
        }
        if (documento.length() == 14) {
            return documento.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        }
        if (documento.length() == 11) {
            return documento.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        }
        return documento;
    }

    private static String rotulo(String tipo) {
        return tipo == null ? "—" : tipo;
    }

    private static String rotulo(PosicaoParecer posicao) {
        return switch (posicao) {
            case FAVORAVEL -> "Favorável";
            case COM_RESSALVAS -> "Com ressalvas";
            case DESFAVORAVEL -> "Desfavorável";
        };
    }
}
