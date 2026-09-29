package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entrada automática na esteira: análise de crédito que resulta {@code visaoCedente = SIM} vira
 * card em TRIAGEM.
 *
 * <p>O sinal é <b>calculado</b> a partir do histórico de pagamentos do Serasa, não é ato humano de
 * aprovação. Por isso ele só abre o card no primeiro estágio, e quem aprova ou reprova continua
 * sendo a analista — o automático sugere, o humano decide, mesmo padrão já validado na praça de
 * pagamento. O sinal calculado e o veredito ficam lado a lado e um nunca sobrescreve o outro.</p>
 *
 * <p>O volume é pequeno por construção: {@code credit_analysis} só nasce quando alguém consulta o
 * Serasa, que é consulta paga. Não há risco de inundar a fila.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProspeccaoEntradaAutomatica {

    private final ProspeccaoService prospeccaoService;
    private final CreditAnalysisRepository creditAnalysisRepository;

    /**
     * Abre o card a partir de uma análise recém-salva.
     *
     * <p>Falha aqui não pode derrubar o enriquecimento: a consulta ao Serasa é paga e já foi
     * cobrada quando este método roda. Por isso o erro é registrado e engolido — perder o card é
     * recuperável pelo backfill, perder a análise não é.</p>
     */
    public void aoSalvarAnalise(CreditAnalysis analise) {
        if (analise == null || !"SIM".equals(analise.getVisaoCedente())) {
            return;
        }
        try {
            ProspeccaoEntity card = prospeccaoService.criarPorAnalise(
                    analise.getCnpj(), nomeDaEmpresa(analise), analise.getId());
            if (card != null) {
                log.info("Esteira: card {} criado automaticamente para o CNPJ {} (visão cedente SIM)",
                        card.getId(), analise.getCnpj());
            }
        } catch (RuntimeException ex) {
            log.warn("Esteira: não foi possível abrir card automático para o CNPJ {}: {}",
                    analise.getCnpj(), ex.getMessage());
        }
    }

    /** Teto de segurança por execução. Em produção há centenas de análises com visão cedente SIM. */
    public static final int LIMITE_PADRAO = 25;

    /** Quantas empresas entrariam, sem criar nada. */
    public record Previa(int comVisaoCedente, int jaNaEsteira, int seriamCriados, List<String> amostra) {
    }

    /**
     * Quantas empresas o backfill traria, sem criar nada.
     *
     * <p>Existe porque a ação é de mão única: em produção são centenas de análises com visão
     * cedente SIM, e apertar o botão sem saber o número despejaria a fila inteira em triagem sem
     * desfazer. A tela mostra o número e pede confirmação.</p>
     */
    @Transactional(readOnly = true)
    public Previa previa(LocalDate desde) {
        List<CreditAnalysis> candidatas = candidatas(desde);
        List<CreditAnalysis> novas = candidatas.stream().filter(this::aindaNaoEstaNaEsteira).toList();
        return new Previa(
                candidatas.size(),
                candidatas.size() - novas.size(),
                novas.size(),
                novas.stream().limit(5).map(this::nomeDaEmpresa).toList());
    }

    /**
     * Puxa para a esteira as análises {@code SIM} que já existiam antes desta tela.
     *
     * <p>Não roda no deploy, de propósito: entra por botão, para a analista decidir se quer a fila
     * cheia de histórico no primeiro dia. Reexecutar é seguro — quem já tem card é ignorado.</p>
     *
     * <p>O lote é limitado. Sem teto, um clique em produção criaria centenas de cards em triagem
     * de uma vez, e não há como desfazer em massa. Quem quiser mais roda de novo.</p>
     *
     * @return quantos cards foram criados nesta execução
     */
    @Transactional
    public int backfill(LocalDate desde, int limite) {
        int teto = limite <= 0 ? LIMITE_PADRAO : limite;
        List<CreditAnalysis> candidatas = candidatas(desde);
        int criados = 0;
        for (CreditAnalysis analise : candidatas) {
            if (criados >= teto) {
                log.info("Esteira: backfill parou no teto de {} cards; sobraram candidatas", teto);
                break;
            }
            try {
                if (prospeccaoService.criarPorAnalise(
                        analise.getCnpj(), nomeDaEmpresa(analise), analise.getId()) != null) {
                    criados++;
                }
            } catch (RuntimeException ex) {
                // CNPJ inválido no histórico não pode abortar o lote inteiro.
                log.warn("Esteira: backfill ignorou o CNPJ {}: {}", analise.getCnpj(), ex.getMessage());
            }
        }
        log.info("Esteira: backfill avaliou {} análises com visão cedente SIM e abriu {} cards",
                candidatas.size(), criados);
        return criados;
    }

    /** Análises SIM, opcionalmente só as consultadas a partir de uma data. */
    private List<CreditAnalysis> candidatas(LocalDate desde) {
        List<CreditAnalysis> todas = creditAnalysisRepository.findLatestByVisaoCedente("SIM");
        if (desde == null) {
            return todas;
        }
        LocalDateTime corte = desde.atStartOfDay();
        return todas.stream()
                .filter(analise -> analise.getConsultaEm() != null && !analise.getConsultaEm().isBefore(corte))
                .toList();
    }

    private boolean aindaNaoEstaNaEsteira(CreditAnalysis analise) {
        try {
            return prospeccaoService.cardAbertoDoCnpj(analise.getCnpj()).isEmpty()
                    && (analise.getId() == null || !prospeccaoService.jaVeioDaAnalise(analise.getId()));
        } catch (RuntimeException ex) {
            // CNPJ malformado no histórico: não entraria de qualquer forma.
            return false;
        }
    }

    private String nomeDaEmpresa(CreditAnalysis analise) {
        String nome = analise.getCompanyName();
        return nome == null || nome.isBlank() ? analise.getCnpj() : nome;
    }
}
