package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.exception.ConflitoEdicaoException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.liberacao.CarteiraSacado;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.OrigemMembro;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import com.portal.serasa.domain.model.liberacao.PropostaAr;
import com.portal.serasa.domain.model.liberacao.QuadroOpiniao;
import com.portal.serasa.domain.model.liberacao.ResultadoLiberacao;
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
import java.util.HashMap;
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

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final LiberacaoCardJpaRepository cardRepository;
    private final LiberacaoSacadoJpaRepository sacadoRepository;
    private final LiberacaoMembroJpaRepository membroRepository;
    private final LiberacaoParecerJpaRepository parecerRepository;
    private final LiberacaoPendenciaJpaRepository pendenciaRepository;
    private final LiberacaoEventoJpaRepository eventoRepository;
    private final UserRepository userRepository;
    private final EmpresaResolver empresaResolver;
    private final LiberacaoAutorizacao autorizacao;
    private final ApplicationEventPublisher eventos;

    /**
     * Dados editáveis do card. Os mesmos na criação e na edição.
     *
     * @param proposta números da AR importada do PDF; na edição, nulo mantém a que o card já tem
     * @param taxa     taxa da operação, em % (2,5 = 2,5%)
     * @param tac      tarifa de abertura de crédito, em R$
     * @param tranche  em aberto não confirmado do cedente, em R$; vem da AR, mas é editável
     */
    public record DadosCard(String cedenteCnpj, String cedenteNome, String tipoOperacao,
                            BigDecimal valor, LocalDateTime prazo, String parecerOrigem,
                            PosicaoParecer posicaoOrigem, List<DadosSacado> sacados, PropostaAr proposta,
                            BigDecimal taxa, BigDecimal tac, BigDecimal tranche) {

        public DadosCard(String cedenteCnpj, String cedenteNome, String tipoOperacao, BigDecimal valor,
                         LocalDateTime prazo, String parecerOrigem, PosicaoParecer posicaoOrigem, List<DadosSacado> sacados,
                         PropostaAr proposta, BigDecimal taxa, BigDecimal tac) {
            this(cedenteCnpj, cedenteNome, tipoOperacao, valor, prazo, parecerOrigem, posicaoOrigem, sacados, proposta, taxa, tac, null);
        }

        public DadosCard(String cedenteCnpj, String cedenteNome, String tipoOperacao, BigDecimal valor,
                         LocalDateTime prazo, String parecerOrigem, PosicaoParecer posicaoOrigem, List<DadosSacado> sacados,
                         PropostaAr proposta) {
            this(cedenteCnpj, cedenteNome, tipoOperacao, valor, prazo, parecerOrigem, posicaoOrigem, sacados, proposta, null, null, null);
        }

        public DadosCard(String cedenteCnpj, String cedenteNome, String tipoOperacao, BigDecimal valor,
                         LocalDateTime prazo, String parecerOrigem, PosicaoParecer posicaoOrigem, List<DadosSacado> sacados) {
            this(cedenteCnpj, cedenteNome, tipoOperacao, valor, prazo, parecerOrigem, posicaoOrigem, sacados, null);
        }
    }

    /** @param carteira linha do sacado na AR; na edição, nulo mantém a que o sacado já tem */
    public record DadosSacado(String documento, String nome, BigDecimal valor, CarteiraSacado carteira) {

        public DadosSacado(String documento, String nome, BigDecimal valor) {
            this(documento, nome, valor, null);
        }
    }

    public record NovaPendencia(UUID destinatarioId, String texto) {
    }

    /** Decisão sobre um sacado. Valor aprovado só no parcial. */
    public record DecisaoSacado(String documento, ResultadoLiberacao situacao, BigDecimal valorAprovado) {
    }

    /** Decisão anterior sobre um documento, em outro card. */
    public record DecisaoAnterior(String documento, UUID cardId, Long numero, String cedenteNome,
                                  ResultadoLiberacao situacao, BigDecimal valorAprovado,
                                  String decididoPor, LocalDateTime decididoEm) {
    }

    /** Resultado de registrar parecer: o card e se este foi o último que faltava. */
    public record ParecerRegistrado(LiberacaoCardEntity card, LiberacaoParecerEntity parecer, boolean ultimo) {
    }

    // ---------------------------------------------------------------- leitura

    /**
     * Cards do quadro. Finalizado fica na coluna até alguém arquivar (pedido do time em
     * 2026-10-12, no lugar do corte automático de 30 dias).
     */
    @Transactional(readOnly = true)
    public List<LiberacaoCardEntity> listarQuadro(boolean incluirArquivados) {
        return cardRepository.listarQuadro(incluirArquivados);
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

    @Transactional(readOnly = true)
    public boolean diretoriaVazia() {
        return userRepository.findByDiretoriaTrueOrderByNameAsc().isEmpty();
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
                .taxa(dados.taxa())
                .tac(dados.tac())
                .tranche(dados.tranche())
                .prazo(dados.prazo())
                .parecerOrigem(textoOuNulo(dados.parecerOrigem()))
                .posicaoOrigem(dados.posicaoOrigem())
                .proposta(dados.proposta())
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
        if (!mesmoValor(dados.taxa(), card.getTaxa())) {
            mudancas.add(new String[]{"taxa", percentual(card.getTaxa()), percentual(dados.taxa())});
            card.setTaxa(dados.taxa());
        }
        if (!mesmoValor(dados.tac(), card.getTac())) {
            mudancas.add(new String[]{"tac", moeda(card.getTac()), moeda(dados.tac())});
            card.setTac(dados.tac());
        }
        if (!mesmoValor(dados.tranche(), card.getTranche())) {
            mudancas.add(new String[]{"tranche", moeda(card.getTranche()), moeda(dados.tranche())});
            card.setTranche(dados.tranche());
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

        if (dados.posicaoOrigem() != card.getPosicaoOrigem()) {
            mudancas.add(new String[]{"posicaoOrigem", rotuloPosicao(card.getPosicaoOrigem()), rotuloPosicao(dados.posicaoOrigem())});
            card.setPosicaoOrigem(dados.posicaoOrigem());
        }

        if (dados.proposta() != null && !dados.proposta().equals(card.getProposta())) {
            mudancas.add(new String[]{"proposta", rotuloProposta(card.getProposta()), rotuloProposta(dados.proposta())});
            card.setProposta(dados.proposta());
        }

        List<DadosSacado> novos = normalizarSacados(dados.sacados());
        List<LiberacaoSacadoEntity> atuais = sacadoRepository.findByCardIdOrderByOrdem(id);
        String diferencaSacados = diferencaSacados(atuais, novos);
        if (diferencaSacados != null) {
            if (card.getEtapa().decidida()) {
                throw new TransicaoInvalidaException("Card já decidido: reabra no Comitê para mudar os sacados.");
            }
            gravarSacados(id, novos);
        }
        // AR reimportada com os mesmos sacados: só os números da carteira mudam, sem evento próprio
        // (o evento "proposta" já registra a reimportação).
        boolean carteiraAtualizada = diferencaSacados == null && carteiraMudou(atuais, novos);
        if (carteiraAtualizada) {
            gravarSacados(id, novos);
        }

        if (mudancas.isEmpty() && diferencaSacados == null && !carteiraAtualizada) {
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
        return transicionar(id, de, para, pendencias, observacao, List.of(), autor);
    }

    /**
     * Move o card, com as decisões dos sacados quando o destino é Aprovado.
     *
     * @param decisoes situação de cada sacado, aplicada antes de calcular o resultado
     */
    @Transactional
    public LiberacaoCardEntity transicionar(UUID id, EtapaLiberacao de, EtapaLiberacao para,
                                            List<NovaPendencia> pendencias, String observacao,
                                            List<DecisaoSacado> decisoes, UserEntity autor) {
        return transicionar(id, de, para, pendencias, observacao, decisoes, null, autor);
    }

    /**
     * Move o card; indo para Aprovado, pode levar a ressalva de quem aprova.
     *
     * @param ressalva texto da aprovação com ressalva; o card continua Aprovado e ganha a etiqueta
     */
    @Transactional
    public LiberacaoCardEntity transicionar(UUID id, EtapaLiberacao de, EtapaLiberacao para,
                                            List<NovaPendencia> pendencias, String observacao,
                                            List<DecisaoSacado> decisoes, String ressalva, UserEntity autor) {
        String ressalvaLimpa = textoOuNulo(ressalva);
        if (ressalvaLimpa != null && para != EtapaLiberacao.APROVADO) {
            throw new IllegalArgumentException("Ressalva só ao aprovar.");
        }
        LiberacaoCardEntity card = buscar(id);
        if (card.getEtapa() != de) {
            throw new ConflitoEdicaoException("O card já está em " + LiberacaoAutorizacao.rotulo(card.getEtapa())
                    + ", movido por " + card.getAtualizadoPorNome() + ". Recarregue o quadro.");
        }

        List<UserEntity> comite = para == EtapaLiberacao.COMITE
                ? userRepository.findByComiteTrueOrderByNameAsc()
                : List.of();
        List<UserEntity> diretoria = para == EtapaLiberacao.DIRETORIA
                ? userRepository.findByDiretoriaTrueOrderByNameAsc()
                : List.of();
        List<LiberacaoParecerEntity> rodada = parecerRepository.findByCardIdAndRodadaOrderByCriadoEm(id, card.getRodada());
        int registrados = (int) rodada.stream().filter(LiberacaoParecerEntity::registrado).count();
        List<String> faltando = aguardando(rodada);
        autorizacao.exigirTransicao(card, para, autor, registrados,
                para == EtapaLiberacao.COMITE && comite.isEmpty(),
                para == EtapaLiberacao.DIRETORIA && diretoria.isEmpty());

        // Ir para a Diretoria é escolha do Comitê, em qualquer caso: o motivo contextualiza a decisão.
        String motivoDiretoria = para == EtapaLiberacao.DIRETORIA ? textoOuNulo(observacao) : null;
        if (para == EtapaLiberacao.DIRETORIA && motivoDiretoria == null) {
            throw new IllegalArgumentException("Conte por que o card vai para a Diretoria.");
        }

        List<DecisaoSacado> decisoesSacados = decisoes == null ? List.of() : decisoes;
        if (para != EtapaLiberacao.APROVADO && !decisoesSacados.isEmpty()) {
            throw new IllegalArgumentException("Decisão de sacado só ao aprovar ou pela própria tela do sacado.");
        }
        // A Diretoria dá sim ou não à proposta do Comitê: não mexe nos sacados nem põe ressalva.
        if (de == EtapaLiberacao.DIRETORIA && !decisoesSacados.isEmpty()) {
            throw new IllegalArgumentException("A Diretoria aprova ou reprova; a decisão de cada sacado é a do Comitê.");
        }
        if (de == EtapaLiberacao.DIRETORIA && ressalvaLimpa != null) {
            throw new IllegalArgumentException("A Diretoria aprova sem ressalva.");
        }
        if (para == EtapaLiberacao.DIRETORIA) {
            exigirPropostaDoComite(card);
        }
        // Calculado na decisão; Assinatura e Finalizados herdam; voltar ao Comitê desfaz.
        ResultadoLiberacao resultado = para.decisao() ? decidir(card, de, para, decisoesSacados, autor)
                : para.decidida() ? card.getResultado()
                : null;

        List<NovaPendencia> novasPendencias = pendencias == null ? List.of() : pendencias;
        if (para != EtapaLiberacao.PENDENCIA && !novasPendencias.isEmpty()) {
            throw new IllegalArgumentException("Pendência só é aberta ao mover para Pendência.");
        }
        if (para == EtapaLiberacao.PENDENCIA && novasPendencias.isEmpty()
                && pendenciaRepository.countByCardIdAndRespondidaEmIsNull(id) == 0) {
            throw new TransicaoInvalidaException("Informe para quem é a pendência e o que falta.");
        }

        boolean reabertura = EtapaLiberacao.reabertura(de, para);
        if (de == EtapaLiberacao.COMITE && para == EtapaLiberacao.ORIGEM) {
            // Devolução: o que ninguém registrou nesta rodada some; o registrado fica como histórico.
            parecerRepository.apagarAguardando(id, card.getRodada());
            card.setRodada(card.getRodada() + 1);
        }
        if (reabertura) {
            card.setRodada(card.getRodada() + 1);
            // De volta ao Comitê: a Diretoria não decidiu esta rodada. O histórico guarda a anterior.
            limparDiretoria(card);
        }
        if (para == EtapaLiberacao.DIRETORIA) {
            card.setMotivoDiretoria(motivoDiretoria);
            card.setEnviadoDiretoriaPorNome(autor.getName());
            card.setEnviadoDiretoriaEm(LocalDateTime.now());
        }

        LocalDateTime agora = LocalDateTime.now();
        card.setEtapa(para);
        card.setEtapaDesde(agora);
        // finalizadoEm marca a decisão (o prazo para de correr ali) e vale até reabrir.
        if (para.decisao()) {
            card.setFinalizadoEm(agora);
        } else if (!para.decidida()) {
            card.setFinalizadoEm(null);
        }
        card.setResultado(resultado);
        // A ressalva nasce na decisão e acompanha Assinatura e Finalizados; reprovar ou voltar
        // para antes da decisão a desfaz, como o resultado.
        if (para.decisao()) {
            card.setRessalva(resultado == ResultadoLiberacao.REPROVADO ? null : ressalvaLimpa);
        } else if (!para.decidida()) {
            card.setRessalva(null);
        }
        card.setArquivadoEm(null);
        card.setArquivadoPorNome(null);
        tocar(card, autor);
        LiberacaoCardEntity salvo = cardRepository.saveAndFlush(card);

        // Da Pendência de volta ao Comitê os pareceres da rodada continuam valendo; só Origem e
        // reabertura chegam aqui com rodada sem ninguém convocado.
        if (para == EtapaLiberacao.COMITE && de != EtapaLiberacao.PENDENCIA) {
            convocarComite(salvo, comite);
        }
        // A Diretoria passa a acompanhar: o aviso de que a decisão é dela, e o que vier depois.
        diretoria.forEach(diretor -> adicionarMembro(salvo.getId(), diretor.getId(), OrigemMembro.DIRETORIA));

        // Saiu do Comitê com parecer faltando: fica dito no histórico, junto da observação.
        String texto = textoOuNulo(observacao);
        if (para.decisao() && salvo.getRessalva() != null) {
            String comRessalva = "Aprovado com ressalva: " + salvo.getRessalva();
            texto = texto == null ? comRessalva : comRessalva + "\n\n" + texto;
        }
        if (EtapaLiberacao.decisaoDoComite(de, para) && !faltando.isEmpty()) {
            String aviso = "Movido sem o parecer de " + LiberacaoAutorizacao.juntar(faltando) + ".";
            texto = texto == null ? aviso : texto + "\n\n" + aviso;
        }
        boolean decidiu = para.decisao();
        registrar(salvo, reabertura ? TipoEventoLiberacao.REABERTURA : TipoEventoLiberacao.TRANSICAO,
                de, para, decidiu ? "resultado" : null, null, decidiu ? resultado.rotulo() : null,
                texto, autor);
        Set<UUID> mencionados = acompanharMencionados(id, MencaoParser.usuarios(observacao));
        eventos.publishEvent(new LiberacaoEvento.Movido(salvo, de, para, autor, mencionados, trecho(observacao)));

        for (NovaPendencia pendencia : novasPendencias) {
            abrirPendencia(salvo, pendencia, autor);
        }
        return salvo;
    }

    /**
     * A decisão: a coluna escolhida e as decisões dos sacados precisam concordar.
     *
     * <p>Quem decide é o Comitê (quadrado C) ou, com o card na Diretoria, a Diretoria (quadrado
     * D). A decisão de cada sacado vai para a situação, que é a decisão final.</p>
     *
     * <p>Reprovar reprova todos os sacados. Aprovar pede todo sacado decidido — card aprovado com
     * sacado "a decidir" deixaria o resultado mentindo — e pelo menos um aprovado ou parcial.
     * Card sem sacados fica com o resultado da coluna.</p>
     */
    private ResultadoLiberacao decidir(LiberacaoCardEntity card, EtapaLiberacao de, EtapaLiberacao para,
                                       List<DecisaoSacado> decisoes, UserEntity autor) {
        QuadroOpiniao quadro = de == EtapaLiberacao.DIRETORIA ? QuadroOpiniao.DIRETORIA : QuadroOpiniao.COMITE;
        List<LiberacaoSacadoEntity> sacados = sacadoRepository.findByCardIdOrderByOrdem(card.getId());
        if (quadro == QuadroOpiniao.DIRETORIA && para == EtapaLiberacao.APROVADO) {
            // Sim da Diretoria: o D de cada sacado repete o C, assinado por quem aprovou.
            for (LiberacaoSacadoEntity sacado : List.copyOf(sacados)) {
                aplicarDecisao(card, sacados, quadro,
                        new DecisaoSacado(sacado.getCnpj(), sacado.getOpiniaoComite(), sacado.getValorAprovadoComite()), autor);
            }
        }
        if (para == EtapaLiberacao.REPROVADO) {
            for (LiberacaoSacadoEntity sacado : List.copyOf(sacados)) {
                aplicarDecisao(card, sacados, quadro, new DecisaoSacado(sacado.getCnpj(), ResultadoLiberacao.REPROVADO, null), autor);
            }
            finalizarSacados(sacados, quadro);
            return ResultadoLiberacao.REPROVADO;
        }
        for (DecisaoSacado decisao : decisoes) {
            aplicarDecisao(card, sacados, quadro, decisao, autor);
        }
        if (sacados.isEmpty()) {
            return ResultadoLiberacao.APROVADO;
        }
        List<String> aDecidir = sacados.stream()
                .filter(sacado -> decisaoDo(sacado, quadro) == null)
                .map(sacado -> rotuloSacado(sacado.getCnpj(), sacado.getNome()))
                .toList();
        if (!aDecidir.isEmpty()) {
            throw new TransicaoInvalidaException("Decida todos os sacados antes de aprovar. Falta: " + String.join(", ", aDecidir) + ".");
        }
        ResultadoLiberacao resultado = ResultadoLiberacao.doCard(sacados.stream().map(sacado -> decisaoDo(sacado, quadro)).toList());
        if (resultado == ResultadoLiberacao.REPROVADO) {
            throw new TransicaoInvalidaException("Todos os sacados estão reprovados: mova o card para Reprovado.");
        }
        finalizarSacados(sacados, quadro);
        return resultado;
    }

    /**
     * A Diretoria responde à proposta do Comitê: cada sacado precisa do C, e com todos reprovados
     * não há o que aprovar — o próprio Comitê reprova.
     */
    private void exigirPropostaDoComite(LiberacaoCardEntity card) {
        List<LiberacaoSacadoEntity> sacados = sacadoRepository.findByCardIdOrderByOrdem(card.getId());
        List<String> semC = sacados.stream()
                .filter(sacado -> sacado.getOpiniaoComite() == null)
                .map(sacado -> rotuloSacado(sacado.getCnpj(), sacado.getNome()))
                .toList();
        if (!semC.isEmpty()) {
            throw new TransicaoInvalidaException("Decida o C de todos os sacados antes de mandar para a Diretoria. Falta: "
                    + String.join(", ", semC) + ".");
        }
        if (!sacados.isEmpty() && sacados.stream().allMatch(sacado -> sacado.getOpiniaoComite() == ResultadoLiberacao.REPROVADO)) {
            throw new TransicaoInvalidaException("Todos os sacados estão reprovados no C: o Comitê mesmo reprova o card.");
        }
    }

    /** A decisão de quem decidiu o card (C ou D) vira a situação: é ela que conta daqui em diante. */
    private void finalizarSacados(List<LiberacaoSacadoEntity> sacados, QuadroOpiniao quadro) {
        for (LiberacaoSacadoEntity sacado : sacados) {
            boolean diretoria = quadro == QuadroOpiniao.DIRETORIA;
            sacado.setSituacao(decisaoDo(sacado, quadro));
            sacado.setValorAprovado(diretoria ? sacado.getValorAprovadoDiretoria() : sacado.getValorAprovadoComite());
            sacado.setSituacaoPorNome(diretoria ? sacado.getDecisaoDiretoriaPorNome() : sacado.getOpiniaoComitePorNome());
            sacado.setSituacaoEm(diretoria ? sacado.getDecisaoDiretoriaEm() : sacado.getOpiniaoComiteEm());
        }
        sacadoRepository.saveAll(sacados);
    }

    private static ResultadoLiberacao decisaoDo(LiberacaoSacadoEntity sacado, QuadroOpiniao quadro) {
        return quadro == QuadroOpiniao.DIRETORIA ? sacado.getDecisaoDiretoria() : sacado.getOpiniaoComite();
    }

    /** Reabertura: some a decisão da Diretoria e o motivo do envio. */
    private void limparDiretoria(LiberacaoCardEntity card) {
        card.setMotivoDiretoria(null);
        card.setEnviadoDiretoriaPorNome(null);
        card.setEnviadoDiretoriaEm(null);
        List<LiberacaoSacadoEntity> sacados = sacadoRepository.findByCardIdOrderByOrdem(card.getId());
        sacados.forEach(sacado -> {
            sacado.setDecisaoDiretoria(null);
            sacado.setValorAprovadoDiretoria(null);
            sacado.setDecisaoDiretoriaPorNome(null);
            sacado.setDecisaoDiretoriaEm(null);
        });
        sacadoRepository.saveAll(sacados);
    }

    // ------------------------------------------------------------- arquivamento

    /**
     * Tira o card finalizado do quadro, ou devolve. Só analista, como toda mudança depois da
     * Origem. Não apaga nada: arquivado continua no relatório e aparece com o filtro.
     */
    @Transactional
    public LiberacaoCardEntity arquivar(UUID id, boolean arquivar, UserEntity autor) {
        autorizacao.exigirAnalista(autor, arquivar ? "arquivar card" : "desarquivar card");
        LiberacaoCardEntity card = buscar(id);
        if (card.getEtapa() != EtapaLiberacao.FINALIZADO) {
            throw new TransicaoInvalidaException("Só card finalizado é arquivado.");
        }
        if (arquivar == (card.getArquivadoEm() != null)) {
            return card;
        }
        return marcarArquivado(card, arquivar, autor);
    }

    /** "Arquivar todos" da coluna Finalizados. Devolve quantos foram arquivados. */
    @Transactional
    public int arquivarFinalizados(UserEntity autor) {
        autorizacao.exigirAnalista(autor, "arquivar cards");
        List<LiberacaoCardEntity> finalizados = cardRepository.finalizadosNaoArquivados();
        finalizados.forEach(card -> marcarArquivado(card, true, autor));
        return finalizados.size();
    }

    private LiberacaoCardEntity marcarArquivado(LiberacaoCardEntity card, boolean arquivar, UserEntity autor) {
        card.setArquivadoEm(arquivar ? LocalDateTime.now() : null);
        card.setArquivadoPorNome(arquivar ? autor.getName() : null);
        tocar(card, autor);
        LiberacaoCardEntity salvo = cardRepository.saveAndFlush(card);
        registrar(salvo, TipoEventoLiberacao.EDICAO, null, null, "arquivado", null,
                arquivar ? "Arquivado" : "Desarquivado", null, autor);
        eventos.publishEvent(new LiberacaoEvento.Editado(salvo, autor, Set.of(), null));
        return salvo;
    }

    /**
     * Marca o quadrado O de um sacado: qualquer pessoa, com o card na Origem. Situação nula limpa.
     * Desde a V88 a tela não mostra o O; C e D são decisões e passam por {@link #decidirSacado}.
     */
    @Transactional
    public LiberacaoCardEntity opinarSacado(UUID cardId, String documento, QuadroOpiniao quadro,
                                            ResultadoLiberacao situacao, UserEntity autor) {
        if (quadro != QuadroOpiniao.ORIGEM) {
            throw new IllegalArgumentException("O quadrado " + quadro.letra() + " é decisão: marque pela decisão do sacado.");
        }
        LiberacaoCardEntity card = buscar(cardId);
        exigirQuadro(card, quadro, autor);
        LiberacaoSacadoEntity sacado = sacadoDoCard(cardId, documento);
        ResultadoLiberacao atual = quadro == QuadroOpiniao.ORIGEM ? sacado.getOpiniaoOrigem() : sacado.getOpiniaoComite();
        if (atual == situacao) {
            return card;
        }
        LocalDateTime agora = LocalDateTime.now();
        String nome = situacao == null ? null : autor.getName();
        LocalDateTime quando = situacao == null ? null : agora;
        if (quadro == QuadroOpiniao.ORIGEM) {
            sacado.setOpiniaoOrigem(situacao);
            sacado.setOpiniaoOrigemPorNome(nome);
            sacado.setOpiniaoOrigemEm(quando);
        } else {
            sacado.setOpiniaoComite(situacao);
            sacado.setOpiniaoComitePorNome(nome);
            sacado.setOpiniaoComiteEm(quando);
        }
        sacadoRepository.save(sacado);
        registrar(card, TipoEventoLiberacao.EDICAO, null, null,
                quadro == QuadroOpiniao.ORIGEM ? "opiniaoOrigem" : "opiniaoComite",
                rotuloOpiniao(atual), rotuloOpiniao(situacao), rotuloSacado(sacado.getCnpj(), sacado.getNome()), autor);
        cardRepository.tocarSemVersao(cardId, agora, autor.getId(), autor.getName());
        LiberacaoCardEntity atualizado = buscar(cardId);
        avisarEdicao(atualizado, autor);
        return atualizado;
    }

    /**
     * Parecer em texto de um quadrado do sacado (O, C ou D). Quem escreve é quem pode marcar o
     * quadrado. Texto vazio apaga. Quem for marcado com @ passa a acompanhar o card e é avisado.
     */
    @Transactional
    public LiberacaoCardEntity parecerSacado(UUID cardId, String documento, QuadroOpiniao quadro, String texto, UserEntity autor) {
        LiberacaoCardEntity card = buscar(cardId);
        exigirQuadro(card, quadro, autor);
        LiberacaoSacadoEntity sacado = sacadoDoCard(cardId, documento);
        String novo = textoOuNulo(texto);
        String anterior = switch (quadro) {
            case ORIGEM -> sacado.getParecerOrigem();
            case COMITE -> sacado.getParecerComite();
            case DIRETORIA -> sacado.getParecerDiretoria();
        };
        if (Objects.equals(anterior, novo)) {
            return card;
        }
        switch (quadro) {
            case ORIGEM -> sacado.setParecerOrigem(novo);
            case COMITE -> sacado.setParecerComite(novo);
            case DIRETORIA -> sacado.setParecerDiretoria(novo);
        }
        sacadoRepository.save(sacado);
        registrar(card, TipoEventoLiberacao.EDICAO, null, null, "parecerSacado", null, quadro.letra(),
                rotuloSacado(sacado.getCnpj(), sacado.getNome()), autor);
        cardRepository.tocarSemVersao(cardId, LocalDateTime.now(), autor.getId(), autor.getName());
        LiberacaoCardEntity atualizado = buscar(cardId);
        Set<UUID> mencionados = acompanharMencionados(cardId, MencaoParser.novosUsuarios(anterior, novo));
        eventos.publishEvent(new LiberacaoEvento.Editado(atualizado, autor, mencionados, trecho(novo)));
        return atualizado;
    }

    /**
     * O: qualquer pessoa, card na Origem. C: analista, card no Comitê ou em Pendência. D: quem tem
     * o perfil Diretoria, card na Diretoria.
     */
    private void exigirQuadro(LiberacaoCardEntity card, QuadroOpiniao quadro, UserEntity autor) {
        autorizacao.exigirAutenticado(autor);
        switch (quadro) {
            case ORIGEM -> {
                // O: parecer dos operadores (pedido do time em 2026-10-15). Qualquer analista,
                // enquanto o card não foi decidido nem mandado para a Diretoria.
                autorizacao.exigirAnalista(autor, "marcar o quadrado O");
                EtapaLiberacao etapa = card.getEtapa();
                if (etapa != EtapaLiberacao.ORIGEM && etapa != EtapaLiberacao.COMITE && etapa != EtapaLiberacao.PENDENCIA) {
                    throw new TransicaoInvalidaException(etapa.decidida()
                            ? "Card já decidido: reabra no Comitê para mudar o parecer do operador."
                            : "O quadrado O é marcado antes da decisão: na Origem, no Comitê ou em Pendência.");
                }
            }
            case COMITE -> {
                autorizacao.exigirAnalista(autor, "marcar o quadrado C");
                if (card.getEtapa() != EtapaLiberacao.COMITE && card.getEtapa() != EtapaLiberacao.PENDENCIA) {
                    throw new TransicaoInvalidaException(card.getEtapa().decidida()
                            ? "Card já decidido: reabra no Comitê para mudar a decisão."
                            : "O quadrado C é marcado com o card no Comitê ou em Pendência.");
                }
            }
            case DIRETORIA -> {
                autorizacao.exigirDiretoria(autor, "marcar o quadrado D");
                if (card.getEtapa() != EtapaLiberacao.DIRETORIA) {
                    throw new TransicaoInvalidaException("O quadrado D é marcado com o card na Diretoria.");
                }
            }
        }
    }

    private LiberacaoSacadoEntity sacadoDoCard(UUID cardId, String documento) {
        String digitos = documento == null ? "" : documento.replaceAll("\\D", "");
        return sacadoRepository.findByCardIdOrderByOrdem(cardId).stream()
                .filter(item -> item.getCnpj().equals(digitos))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Sacado " + formatarDocumento(digitos) + " não está neste card."));
    }

    /**
     * Decide o C de um sacado pela lista, com o card no Comitê ou em Pendência. A situação só muda
     * quando o card é decidido. O D não se marca aqui: a Diretoria só dá sim ou não ao card.
     */
    @Transactional
    public LiberacaoCardEntity decidirSacado(UUID cardId, QuadroOpiniao quadro, DecisaoSacado decisao, UserEntity autor) {
        if (quadro == QuadroOpiniao.ORIGEM) {
            throw new IllegalArgumentException("A Origem não decide sacado.");
        }
        if (quadro == QuadroOpiniao.DIRETORIA) {
            throw new IllegalArgumentException("O D é preenchido quando a Diretoria aprova ou reprova o card.");
        }
        LiberacaoCardEntity card = buscar(cardId);
        exigirQuadro(card, quadro, autor);
        if (!aplicarDecisao(card, sacadoRepository.findByCardIdOrderByOrdem(cardId), quadro, decisao, autor)) {
            return card;
        }
        cardRepository.tocarSemVersao(cardId, LocalDateTime.now(), autor.getId(), autor.getName());
        LiberacaoCardEntity atualizado = buscar(cardId);
        avisarEdicao(atualizado, autor);
        return atualizado;
    }

    /**
     * Grava a decisão no quadrado C ou D.
     *
     * @return false quando a decisão já era essa (clique repetido): nada gravado.
     */
    private boolean aplicarDecisao(LiberacaoCardEntity card, List<LiberacaoSacadoEntity> sacados, QuadroOpiniao quadro,
                                   DecisaoSacado decisao, UserEntity autor) {
        String documento = decisao.documento() == null ? "" : decisao.documento().replaceAll("\\D", "");
        LiberacaoSacadoEntity sacado = sacados.stream()
                .filter(item -> item.getCnpj().equals(documento))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Sacado " + formatarDocumento(documento) + " não está neste card."));
        BigDecimal valorAprovado = null;
        if (decisao.situacao() == ResultadoLiberacao.PARCIAL) {
            valorAprovado = decisao.valorAprovado();
            if (valorAprovado == null || valorAprovado.signum() <= 0) {
                throw new IllegalArgumentException("Parcial: informe o valor aprovado de " + rotuloSacado(sacado.getCnpj(), sacado.getNome()) + ".");
            }
            if (sacado.getValor() != null && valorAprovado.compareTo(sacado.getValor()) >= 0) {
                throw new IllegalArgumentException("Parcial: o valor aprovado precisa ser menor que " + moeda(sacado.getValor()) + ".");
            }
        }
        boolean diretoria = quadro == QuadroOpiniao.DIRETORIA;
        ResultadoLiberacao atual = decisaoDo(sacado, quadro);
        BigDecimal valorAtual = diretoria ? sacado.getValorAprovadoDiretoria() : sacado.getValorAprovadoComite();
        if (atual == decisao.situacao() && mesmoValor(valorAtual, valorAprovado)) {
            return false;
        }
        String antes = rotuloDecisao(atual, valorAtual);
        String nome = decisao.situacao() == null ? null : autor.getName();
        LocalDateTime quando = decisao.situacao() == null ? null : LocalDateTime.now();
        if (diretoria) {
            sacado.setDecisaoDiretoria(decisao.situacao());
            sacado.setValorAprovadoDiretoria(valorAprovado);
            sacado.setDecisaoDiretoriaPorNome(nome);
            sacado.setDecisaoDiretoriaEm(quando);
        } else {
            sacado.setOpiniaoComite(decisao.situacao());
            sacado.setValorAprovadoComite(valorAprovado);
            sacado.setOpiniaoComitePorNome(nome);
            sacado.setOpiniaoComiteEm(quando);
        }
        sacadoRepository.save(sacado);
        registrar(card, TipoEventoLiberacao.EDICAO, null, null, diretoria ? "decisaoDiretoria" : "decisaoComite", antes,
                rotuloDecisao(decisao.situacao(), valorAprovado), rotuloSacado(sacado.getCnpj(), sacado.getNome()), autor);
        return true;
    }

    /** Opinião O ou C: sem valor, então "Parcial" puro. */
    private static String rotuloOpiniao(ResultadoLiberacao situacao) {
        if (situacao == null) {
            return "sem opinião";
        }
        return situacao == ResultadoLiberacao.PARCIAL ? "Parcial" : situacao.rotulo();
    }

    private static String rotuloDecisao(ResultadoLiberacao situacao, BigDecimal valorAprovado) {
        if (situacao == null) {
            return "a decidir";
        }
        return situacao == ResultadoLiberacao.PARCIAL ? "Parcial (" + moeda(valorAprovado) + ")" : situacao.rotulo();
    }

    /**
     * Quanto da operação foi aprovado, pelas decisões dos sacados: o valor inteiro dos aprovados
     * mais o valor aprovado dos parciais. Nulo enquanto ninguém foi decidido.
     */
    public static BigDecimal valorAprovado(List<LiberacaoSacadoEntity> sacados) {
        if (sacados.stream().noneMatch(sacado -> sacado.getSituacao() != null)) {
            return null;
        }
        return sacados.stream()
                .map(sacado -> switch (sacado.getSituacao() == null ? ResultadoLiberacao.REPROVADO : sacado.getSituacao()) {
                    case APROVADO -> sacado.getValor() == null ? BigDecimal.ZERO : sacado.getValor();
                    case PARCIAL -> sacado.getValorAprovado() == null ? BigDecimal.ZERO : sacado.getValorAprovado();
                    case REPROVADO -> BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Decisões em outros cards sobre estes documentos, da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public List<DecisaoAnterior> decisoesAnteriores(java.util.Collection<String> documentos) {
        if (documentos.isEmpty()) {
            return List.of();
        }
        return sacadoRepository.decisoesAnteriores(documentos).stream()
                .map(linha -> {
                    LiberacaoSacadoEntity sacado = (LiberacaoSacadoEntity) linha[0];
                    LiberacaoCardEntity card = (LiberacaoCardEntity) linha[1];
                    return new DecisaoAnterior(sacado.getCnpj(), card.getId(), card.getNumero(), card.getCedenteNome(),
                            sacado.getSituacao(), sacado.getValorAprovado(), sacado.getSituacaoPorNome(), sacado.getSituacaoEm());
                })
                .toList();
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
        // Regrava a lista inteira, mas a decisão de quem continua nela não pode se perder na edição.
        Map<String, LiberacaoSacadoEntity> anteriores = new LinkedHashMap<>();
        sacadoRepository.findByCardIdOrderByOrdem(cardId).forEach(sacado -> anteriores.put(sacado.getCnpj(), sacado));
        sacadoRepository.apagarDoCard(cardId);
        List<LiberacaoSacadoEntity> linhas = new ArrayList<>();
        for (int i = 0; i < sacados.size(); i++) {
            DadosSacado sacado = sacados.get(i);
            LiberacaoSacadoEntity mesmo = anteriores.get(sacado.documento());
            LiberacaoSacadoEntity anterior = mesmo != null
                    && decisaoCabe(mesmo.getSituacao(), mesmo.getValorAprovado(), sacado.valor()) ? mesmo : null;
            LiberacaoSacadoEntity comite = mesmo != null
                    && decisaoCabe(mesmo.getOpiniaoComite(), mesmo.getValorAprovadoComite(), sacado.valor()) ? mesmo : null;
            LiberacaoSacadoEntity diretoria = mesmo != null
                    && decisaoCabe(mesmo.getDecisaoDiretoria(), mesmo.getValorAprovadoDiretoria(), sacado.valor()) ? mesmo : null;
            linhas.add(LiberacaoSacadoEntity.builder()
                    .cardId(cardId)
                    .cnpj(sacado.documento())
                    .nome(sacado.nome())
                    .valor(sacado.valor())
                    .carteira(sacado.carteira() != null ? sacado.carteira() : mesmo == null ? null : mesmo.getCarteira())
                    .ordem(i)
                    .situacao(anterior == null ? null : anterior.getSituacao())
                    .valorAprovado(anterior == null ? null : anterior.getValorAprovado())
                    .situacaoPorNome(anterior == null ? null : anterior.getSituacaoPorNome())
                    .situacaoEm(anterior == null ? null : anterior.getSituacaoEm())
                    // A opinião O não depende do valor: fica enquanto o sacado continua no card.
                    .opiniaoOrigem(mesmo == null ? null : mesmo.getOpiniaoOrigem())
                    .opiniaoOrigemPorNome(mesmo == null ? null : mesmo.getOpiniaoOrigemPorNome())
                    .opiniaoOrigemEm(mesmo == null ? null : mesmo.getOpiniaoOrigemEm())
                    // C e D são decisões: o parcial cai se o valor do sacado baixou até ele.
                    .opiniaoComite(comite == null ? null : comite.getOpiniaoComite())
                    .valorAprovadoComite(comite == null ? null : comite.getValorAprovadoComite())
                    .opiniaoComitePorNome(comite == null ? null : comite.getOpiniaoComitePorNome())
                    .opiniaoComiteEm(comite == null ? null : comite.getOpiniaoComiteEm())
                    .decisaoDiretoria(diretoria == null ? null : diretoria.getDecisaoDiretoria())
                    .valorAprovadoDiretoria(diretoria == null ? null : diretoria.getValorAprovadoDiretoria())
                    .decisaoDiretoriaPorNome(diretoria == null ? null : diretoria.getDecisaoDiretoriaPorNome())
                    .decisaoDiretoriaEm(diretoria == null ? null : diretoria.getDecisaoDiretoriaEm())
                    .parecerOrigem(mesmo == null ? null : mesmo.getParecerOrigem())
                    .parecerComite(mesmo == null ? null : mesmo.getParecerComite())
                    .parecerDiretoria(mesmo == null ? null : mesmo.getParecerDiretoria())
                    .build());
        }
        sacadoRepository.saveAll(linhas);
    }

    private static boolean carteiraMudou(List<LiberacaoSacadoEntity> atuais, List<DadosSacado> novos) {
        Map<String, CarteiraSacado> antes = new HashMap<>();
        atuais.forEach(sacado -> antes.put(sacado.getCnpj(), sacado.getCarteira()));
        return novos.stream().anyMatch(sacado -> sacado.carteira() != null && !sacado.carteira().equals(antes.get(sacado.documento())));
    }

    /** Parcial só vale abaixo do valor do sacado; se o valor baixou até ele, a decisão cai. */
    private static boolean decisaoCabe(ResultadoLiberacao situacao, BigDecimal valorAprovado, BigDecimal novoValor) {
        return situacao != ResultadoLiberacao.PARCIAL
                || novoValor == null
                || valorAprovado == null
                || valorAprovado.compareTo(novoValor) < 0;
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
            porDocumento.put(documento, new DadosSacado(documento, textoOuNulo(sacado.nome()), sacado.valor(), sacado.carteira()));
        }
        if (porDocumento.isEmpty()) {
            return List.of();
        }

        List<String> semNome = porDocumento.values().stream()
                .filter(sacado -> sacado.nome() == null && sacado.documento().length() == 14)
                .map(DadosSacado::documento)
                .toList();
        Map<String, EmpresaResolver.Empresa> daBase = semNome.isEmpty() ? Map.of() : empresaResolver.resolver(semNome);
        return porDocumento.values().stream()
                .map(sacado -> sacado.nome() != null || !daBase.containsKey(sacado.documento()) ? sacado
                        : new DadosSacado(sacado.documento(), daBase.get(sacado.documento()).nome(), sacado.valor(), sacado.carteira()))
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
        Optional<String> daBase = Optional.ofNullable(empresaResolver.resolver(List.of(cnpj)).get(cnpj))
                .map(EmpresaResolver.Empresa::nome);
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

    /** Anexo enviado ou removido entra no histórico e atualiza o quadro dos outros. */
    public void registrarAnexo(LiberacaoCardEntity card, boolean adicionado, String nome, UserEntity autor) {
        registrar(card, adicionado ? TipoEventoLiberacao.ANEXO_ADICIONADO : TipoEventoLiberacao.ANEXO_REMOVIDO,
                null, null, null, null, null, nome, autor);
        avisarEdicao(card, autor);
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

    /** "2,5%"; "—" sem taxa, como {@link #moeda}. */
    static String percentual(BigDecimal taxa) {
        if (taxa == null) {
            return "—";
        }
        java.text.NumberFormat formato = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));
        formato.setMaximumFractionDigits(4);
        return formato.format(taxa) + "%";
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

    /** "AR de 08/10/2026 15:59" para o histórico; nulo quando o card não tinha proposta. */
    static String rotuloProposta(PropostaAr proposta) {
        if (proposta == null) {
            return null;
        }
        LocalDateTime emitida = proposta.emitidaEmData();
        return emitida == null ? "AR importada" : "AR de " + emitida.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private static String rotuloPosicao(PosicaoParecer posicao) {
        return posicao == null ? "—" : rotulo(posicao);
    }

    static String rotulo(PosicaoParecer posicao) {
        return switch (posicao) {
            case FAVORAVEL -> "Favorável";
            case COM_RESSALVAS -> "Com ressalvas";
            case DESFAVORAVEL -> "Desfavorável";
        };
    }
}
