package com.portal.serasa.application.service.judit;

import com.portal.serasa.infrastructure.integration.judit.JuditProperties;
import com.portal.serasa.infrastructure.persistence.repository.JuditConsultaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.JuditLiberacaoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O gasto do mês contra a franquia e o limite da Judit. Mês é o do calendário, do dia 1 até hoje —
 * nunca o filtro de 7 a 90 dias do painel.
 *
 * <p>Três faixas: até a franquia o dinheiro já está pago; da franquia ao limite é excedente; no limite
 * o portal para de consultar. O limite do mês é o de {@code judit.limite-mensal} mais o que o admin
 * liberou naquele mês.</p>
 */
@Component
@RequiredArgsConstructor
public class JuditFranquia {

    private final JuditConsultaJpaRepository consultaRepository;
    private final JuditLiberacaoJpaRepository liberacaoRepository;
    private final JuditProperties properties;

    /** @param limite o limite do mês, já somadas as liberações; {@code liberado} é só a parte do admin */
    public record Consumo(BigDecimal gastoMes, BigDecimal franquia, BigDecimal limite, BigDecimal liberado) {
    }

    public Consumo consumo() {
        BigDecimal liberado = liberadoNoMes();
        return new Consumo(gastoDoMes(), properties.franquiaMensal(), properties.limiteMensal().add(liberado), liberado);
    }

    public BigDecimal gastoDoMes() {
        return consultaRepository.somarCustoDesde(mesCorrente().atStartOfDay());
    }

    public BigDecimal limiteDoMes() {
        return properties.limiteMensal().add(liberadoNoMes());
    }

    private BigDecimal liberadoNoMes() {
        return liberacaoRepository.somarDoMes(mesCorrente());
    }

    /** O dia 1 do mês corrente: é a chave da liberação e o começo da soma do gasto. */
    public static LocalDate mesCorrente() {
        return LocalDate.now().withDayOfMonth(1);
    }
}
