package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Puxa para a esteira as análises {@code SIM} que já existiam antes desta tela.
     *
     * <p>Não roda no deploy, de propósito: entra por botão, para a analista decidir se quer a fila
     * cheia de histórico no primeiro dia. Reexecutar é seguro — quem já tem card é ignorado.</p>
     *
     * @return quantos cards foram criados nesta execução
     */
    @Transactional
    public int backfill() {
        List<CreditAnalysis> candidatas = creditAnalysisRepository.findLatestByVisaoCedente("SIM");
        int criados = 0;
        for (CreditAnalysis analise : candidatas) {
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

    private String nomeDaEmpresa(CreditAnalysis analise) {
        String nome = analise.getCompanyName();
        return nome == null || nome.isBlank() ? analise.getCnpj() : nome;
    }
}
