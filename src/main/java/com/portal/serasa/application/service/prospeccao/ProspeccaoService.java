package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.DocumentacaoIncompletaException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.prospeccao.CanalContato;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.OrigemProspeccao;
import com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEventoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDocumentoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoEventoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Regras da esteira de prospecção.
 *
 * <p>Toda mudança de estado passa por aqui e grava um evento na mesma transação. A timeline não
 * tem outra porta de escrita: é ela que sustenta o SLA por estágio, a métrica que a planilha
 * pedia na coluna QTDE DE DIAS PARA RETORNO e nunca teve preenchida.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProspeccaoService {

    /** Silêncio do cliente que faz o sistema sugerir a remoção do radar. Ele sugere, não remove. */
    public static final int DIAS_SILENCIO_PARA_SUGERIR_REMOCAO = 30;

    private final ProspeccaoJpaRepository prospeccaoRepository;
    private final ProspeccaoEventoJpaRepository eventoRepository;
    private final ProspeccaoDocumentoJpaRepository documentoRepository;
    private final ProspeccaoChecklistService checklistService;

    // ---------------------------------------------------------------- criação

    /** Entrada manual: o comercial informa o CNPJ. */
    @Transactional
    public ProspeccaoEntity criar(String cnpj, String razaoSocial, UUID comercialId,
                                  String comercialNome, UserEntity autor) {
        String normalizado = normalizarCnpj(cnpj);
        prospeccaoRepository.findAberta(normalizado).ifPresent(aberta -> {
            throw new TransicaoInvalidaException(
                    "Já existe card aberto para este CNPJ em " + aberta.getEstagio() + ".");
        });
        return nascer(normalizado, razaoSocial, OrigemProspeccao.MANUAL, null,
                comercialId, comercialNome, autor);
    }

    /**
     * Entrada automática: uma análise resultou {@code visaoCedente = SIM}.
     *
     * <p>O sinal é calculado a partir do Serasa, não é veredito humano — por isso ele só cria o
     * card em TRIAGEM, e quem aprova ou reprova continua sendo a analista. Se já houver card
     * aberto para o CNPJ, ou se a mesma análise já tiver gerado um, nada acontece: importar de
     * novo não pode duplicar.</p>
     */
    @Transactional
    public ProspeccaoEntity criarPorAnalise(String cnpj, String razaoSocial, Long creditAnalysisId) {
        String normalizado = normalizarCnpj(cnpj);
        if (prospeccaoRepository.findAberta(normalizado).isPresent()
                || (creditAnalysisId != null && prospeccaoRepository.existsByCreditAnalysisId(creditAnalysisId))) {
            return null;
        }
        return nascer(normalizado, razaoSocial, OrigemProspeccao.AUTOMATICA, creditAnalysisId,
                null, null, null);
    }

    private ProspeccaoEntity nascer(String cnpj, String razaoSocial, OrigemProspeccao origem,
                                    Long creditAnalysisId, UUID comercialId, String comercialNome,
                                    UserEntity autor) {
        LocalDateTime agora = LocalDateTime.now();
        ProspeccaoEntity card = prospeccaoRepository.save(ProspeccaoEntity.builder()
                .cnpj(cnpj)
                .razaoSocial(razaoSocial)
                .estagio(EstagioProspeccao.TRIAGEM)
                .origem(origem)
                .creditAnalysisId(creditAnalysisId)
                .comercialId(comercialId)
                .comercialNome(comercialNome)
                .estagioDesde(agora)
                .prazoEstagioDias(EstagioProspeccao.TRIAGEM.prazoDiasUteis())
                .reaberturas(0)
                .createdAt(agora)
                .updatedAt(agora)
                .build());

        registrarEvento(card, TipoEventoProspeccao.CRIACAO, null, null, null,
                origem == OrigemProspeccao.AUTOMATICA
                        ? "Criado automaticamente por visão cedente SIM"
                        : "Criado manualmente",
                autor);
        return card;
    }

    // ------------------------------------------------------------- transições

    /**
     * Assume a análise. O analista puxa da fila; quem chegar primeiro fica com o card.
     *
     * <p>O update é condicional no banco, então duas analistas clicando ao mesmo tempo não
     * dependem de sorte: a segunda recebe 409 com o nome de quem pegou.</p>
     */
    @Transactional
    public ProspeccaoEntity assumir(UUID id, UserEntity analista) {
        ProspeccaoEntity card = buscar(id);
        int afetadas = prospeccaoRepository.assumirAnalise(
                id, analista.getId(), EstagioProspeccao.EM_ANALISE.prazoDiasUteis(), LocalDateTime.now());
        if (afetadas == 0) {
            throw new TransicaoInvalidaException(card.getAnalistaId() != null
                    ? "Card já assumido por outro analista."
                    : "Card não está em triagem.");
        }
        ProspeccaoEntity atualizado = buscar(id);
        registrarEvento(atualizado, TipoEventoProspeccao.TRANSICAO,
                EstagioProspeccao.TRIAGEM, EstagioProspeccao.EM_ANALISE, null,
                "Análise assumida", analista);
        return atualizado;
    }

    /**
     * Move o card de estágio.
     *
     * <p>Avanço é validado pela máquina de estados; retrocesso declarado é permitido, para
     * corrigir engano de operação sem exigir card novo. Aprovar materializa o checklist e cai
     * direto em DOCS_PENDENTES — não existe estado em que o card está aprovado e ninguém sabe
     * o que pedir.</p>
     */
    @Transactional
    public ProspeccaoEntity transicionar(UUID id, EstagioProspeccao destino, MotivoRecusa motivo,
                                         String observacao, UserEntity autor) {
        ProspeccaoEntity card = buscar(id);
        EstagioProspeccao origem = card.getEstagio();

        if (origem == destino) {
            return card;
        }
        if (!origem.aceita(destino)) {
            throw new TransicaoInvalidaException(
                    "Transição de " + origem + " para " + destino + " não é permitida.");
        }
        if (destino.exigeMotivo() && motivo == null) {
            throw new TransicaoInvalidaException("Motivo é obrigatório para " + destino + ".");
        }
        if (motivo == MotivoRecusa.OUTRO && (observacao == null || observacao.isBlank())) {
            throw new TransicaoInvalidaException("Motivo OUTRO exige observação preenchida.");
        }
        if (destino == EstagioProspeccao.DOCS_COMPLETOS) {
            exigirDocumentacaoCompleta(card);
        }

        aplicarEstagio(card, destino, autor);

        if (destino.exigeMotivo()) {
            card.setMotivoRecusa(motivo);
        }
        if (observacao != null && !observacao.isBlank()) {
            card.setObservacao(observacao);
        }
        card.setClosedAt(destino.terminal() ? LocalDateTime.now() : null);
        prospeccaoRepository.save(card);

        registrarEvento(card, TipoEventoProspeccao.TRANSICAO, origem, destino, null, observacao, autor);

        // Aprovar sem checklist deixaria o card num limbo: aprovado e sem nada a cobrar.
        if (destino == EstagioProspeccao.APROVADO) {
            checklistService.materializar(card);
            aplicarEstagio(card, EstagioProspeccao.DOCS_PENDENTES, autor);
            prospeccaoRepository.save(card);
            registrarEvento(card, TipoEventoProspeccao.TRANSICAO,
                    EstagioProspeccao.APROVADO, EstagioProspeccao.DOCS_PENDENTES, null,
                    "Checklist gerado", autor);
        }
        return card;
    }

    /**
     * Reabre um card terminal, devolvendo-o para triagem.
     *
     * <p>Existe porque a planilha tem empresa reprovada que depois foi habilitada. Sem
     * reabertura o time criaria card duplicado, que é pior: o motivo da recusa anterior fica na
     * timeline e o contador distingue reanálise de card novo.</p>
     */
    @Transactional
    public ProspeccaoEntity reabrir(UUID id, String justificativa, UserEntity autor) {
        ProspeccaoEntity card = buscar(id);
        if (!card.getEstagio().terminal()) {
            throw new TransicaoInvalidaException("Só card em estágio terminal pode ser reaberto.");
        }
        if (card.getEstagio() == EstagioProspeccao.PRONTO_HABILITACAO) {
            throw new TransicaoInvalidaException(
                    "Card já foi repassado para a habilitação; reabrir aqui não teria efeito.");
        }
        prospeccaoRepository.findAberta(card.getCnpj()).ifPresent(aberta -> {
            throw new TransicaoInvalidaException("Já existe card aberto para este CNPJ.");
        });

        EstagioProspeccao origem = card.getEstagio();
        aplicarEstagio(card, EstagioProspeccao.TRIAGEM, autor);
        card.setReaberturas(card.getReaberturas() + 1);
        card.setClosedAt(null);
        card.setAnalistaId(null);
        prospeccaoRepository.save(card);

        registrarEvento(card, TipoEventoProspeccao.REABERTURA, origem, EstagioProspeccao.TRIAGEM,
                null, justificativa, autor);
        return card;
    }

    private void aplicarEstagio(ProspeccaoEntity card, EstagioProspeccao destino, UserEntity autor) {
        card.setEstagio(destino);
        card.setEstagioDesde(LocalDateTime.now());
        card.setPrazoEstagioDias(destino.prazoDiasUteis());
        card.setUpdatedAt(LocalDateTime.now());
        if (destino == EstagioProspeccao.EM_ANALISE && card.getAnalistaId() == null && autor != null) {
            card.setAnalistaId(autor.getId());
        }
    }

    private void exigirDocumentacaoCompleta(ProspeccaoEntity card) {
        List<String> pendentes = documentoRepository.findByProspeccaoId(card.getId()).stream()
                .filter(ProspeccaoDocumentoEntity::bloqueiaFechamento)
                .map(doc -> doc.getSocioNome() == null
                        ? doc.getNomeSnapshot()
                        : doc.getNomeSnapshot() + " (" + doc.getSocioNome() + ")")
                .toList();
        if (!pendentes.isEmpty()) {
            throw new DocumentacaoIncompletaException(pendentes);
        }
    }

    // --------------------------------------------------------------- timeline

    /** Cobrança ao cliente. Zera o relógio de silêncio do card. */
    @Transactional
    public ProspeccaoEventoEntity registrarCobranca(UUID id, CanalContato canal, String texto,
                                                    UserEntity autor) {
        ProspeccaoEntity card = buscar(id);
        card.setUltimoContatoEm(LocalDateTime.now());
        card.setUpdatedAt(LocalDateTime.now());
        prospeccaoRepository.save(card);
        return registrarEvento(card, TipoEventoProspeccao.COBRANCA, null, null, canal, texto, autor);
    }

    @Transactional
    public ProspeccaoEventoEntity registrarNota(UUID id, String texto, UserEntity autor) {
        return registrarEvento(buscar(id), TipoEventoProspeccao.NOTA, null, null, null, texto, autor);
    }

    @Transactional
    public ProspeccaoEventoEntity registrarEvento(ProspeccaoEntity card, TipoEventoProspeccao tipo,
                                                  EstagioProspeccao de, EstagioProspeccao para,
                                                  CanalContato canal, String texto, UserEntity autor) {
        return eventoRepository.save(ProspeccaoEventoEntity.builder()
                .prospeccaoId(card.getId())
                .tipo(tipo)
                .canal(canal)
                .estagioDe(de)
                .estagioPara(para)
                .texto(texto)
                .usuarioId(autor != null ? autor.getId() : null)
                .usuarioNome(autor != null ? autor.getName() : "Sistema")
                .criadoEm(LocalDateTime.now())
                .build());
    }

    // ------------------------------------------------------------------- SLA

    /** Dias úteis parados no estágio atual. */
    public int diasNoEstagio(ProspeccaoEntity card) {
        return DiasUteis.entre(card.getEstagioDesde().toLocalDate(), LocalDate.now());
    }

    /** Estourou o prazo do estágio. Estágio sem prazo nunca estoura. */
    public boolean slaEstourado(ProspeccaoEntity card) {
        return card.getEstagio().contaSla() && diasNoEstagio(card) > card.getPrazoEstagioDias();
    }

    /** Perto de estourar: 70% do prazo, que é o ponto do amarelo na tela. */
    public boolean slaEmAtencao(ProspeccaoEntity card) {
        if (!card.getEstagio().contaSla() || slaEstourado(card)) {
            return false;
        }
        return diasNoEstagio(card) >= Math.ceil(card.getPrazoEstagioDias() * 0.7);
    }

    /**
     * Silêncio longo do cliente. Vira sugestão de remover do radar — e só sugestão: a aba
     * REMOVIDAS DO RADAR tem 193 linhas, todas decisão de alguém.
     */
    public boolean silencioProlongado(ProspeccaoEntity card) {
        if (card.getEstagio() != EstagioProspeccao.DOCS_PENDENTES) {
            return false;
        }
        LocalDateTime referencia = card.getUltimoContatoEm() != null
                ? card.getUltimoContatoEm()
                : card.getEstagioDesde();
        return referencia.toLocalDate().plusDays(DIAS_SILENCIO_PARA_SUGERIR_REMOCAO)
                .isBefore(LocalDate.now());
    }

    // ---------------------------------------------------------------- leitura

    @Transactional(readOnly = true)
    public ProspeccaoEntity buscar(UUID id) {
        return prospeccaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Card de prospecção não encontrado: " + id));
    }

    /** Janela de desfechos que a esteira ainda mostra; mais antigo que isso é assunto de relatório. */
    public static final int DIAS_DE_DESFECHO_VISIVEL = 90;

    @Transactional(readOnly = true)
    public List<ProspeccaoEntity> listarAbertas() {
        return prospeccaoRepository.findByClosedAtIsNullOrderByEstagioDesdeAsc();
    }

    /**
     * O que a tela mostra: os cards em aberto e os encerrados nos últimos 90 dias.
     *
     * <p>Sem os encerrados a seção de reprovados e removidos do radar ficaria sempre vazia, e o
     * motivo da recusa — o dado que a planilha mais registra e ninguém consegue tabular — nunca
     * apareceria em lugar nenhum.</p>
     */
    @Transactional(readOnly = true)
    public List<ProspeccaoEntity> listarVisiveis() {
        return prospeccaoRepository.findAbertasEEncerradasDesde(
                LocalDateTime.now().minusDays(DIAS_DE_DESFECHO_VISIVEL));
    }

    /**
     * Todo o histórico de um CNPJ, incluindo cards fechados.
     *
     * <p>Importa porque a empresa pode ter sido reprovada, voltado e sido aprovada depois — ver
     * a planilha. A tela do card precisa mostrar que houve passagem anterior.</p>
     */
    @Transactional(readOnly = true)
    public List<ProspeccaoEntity> listarPorCnpj(String cnpj) {
        return prospeccaoRepository.findByCnpjOrderByCreatedAtDesc(normalizarCnpj(cnpj));
    }

    @Transactional(readOnly = true)
    public List<ProspeccaoEventoEntity> timeline(UUID id) {
        return eventoRepository.findByProspeccaoIdOrderByCriadoEmDesc(id);
    }

    static String normalizarCnpj(String cnpj) {
        String digitos = cnpj == null ? "" : cnpj.replaceAll("\\D", "");
        if (digitos.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve ter 14 dígitos: " + cnpj);
        }
        return digitos;
    }
}
