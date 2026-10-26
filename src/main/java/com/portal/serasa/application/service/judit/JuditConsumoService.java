package com.portal.serasa.application.service.judit;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.model.PoloJudit;
import com.portal.serasa.domain.model.ServicoJudit;
import com.portal.serasa.domain.model.SituacaoConsultaJudit;
import com.portal.serasa.infrastructure.persistence.entity.JuditLiberacaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.JuditConsultaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.JuditLiberacaoJpaRepository;
import com.portal.serasa.infrastructure.security.AdminAllowList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Painel "Consumo Judit" das Configurações, no padrão do Consumo Serasa, e a liberação de valor a mais
 * no limite do mês, que só o admin faz.
 *
 * <p>A barra da franquia é sempre do mês corrente ({@link JuditFranquia}); o período de 7 a 90 dias
 * filtra só a tabela de consultas.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JuditConsumoService {

    /** Uma liberação acima disso é quase certamente erro de digitação. */
    static final BigDecimal MAIOR_LIBERACAO = new BigDecimal("10000");

    private final JuditConsultaJpaRepository consultaRepository;
    private final JuditLiberacaoJpaRepository liberacaoRepository;
    private final JuditFranquia franquia;
    /** O mesmo admin que marca a Judit nos usuários — não o papel ADMIN dos catálogos da esteira. */
    private final AdminAllowList adminAllowList;

    public record Liberacao(UUID id, BigDecimal valor, String motivo, String adminNome, LocalDateTime criadoEm) {
    }

    /**
     * @param custoEstimado  o que a tela prometeu (só On Demand); difere de {@code custo} quando o
     *                       tribunal trouxe mais ou menos que a estimativa
     * @param comPreConsulta só no On Demand: a estimativa saiu de uma pré-consulta. Nulo nos outros
     */
    public record Consulta(UUID id, String usuarioNome, String documento, String nomeEmpresa, ServicoJudit servico,
                           PoloJudit polo, String cnj, SituacaoConsultaJudit situacao, BigDecimal custoEstimado,
                           BigDecimal custo, Boolean comPreConsulta, Integer totalProcessos, LocalDateTime criadoEm) {
    }

    /** @param podeLiberar quem está vendo é admin: a tela mostra o botão de liberar */
    public record Painel(JuditFranquia.Consumo mes, LocalDate mesReferencia, boolean podeLiberar,
                         List<Liberacao> liberacoes, int dias, List<Consulta> consultas) {
    }

    @Transactional(readOnly = true)
    public Painel painel(int dias, UserEntity usuario) {
        int periodo = Math.max(1, Math.min(dias, 366));
        LocalDate mes = JuditFranquia.mesCorrente();
        List<Consulta> consultas = consultaRepository.doPainelDesde(LocalDate.now().minusDays(periodo).atStartOfDay())
                .stream()
                .map(c -> new Consulta(c.getId(), c.getUsuarioNome(), c.getDocumento(), c.getNomeEmpresa(), c.getServico(),
                        c.getPolo(), c.getCnj(), c.getSituacao(), c.getCustoEstimado(), c.getCusto(),
                        c.getServico() == ServicoJudit.ON_DEMAND ? c.getPreConsultaId() != null : null,
                        c.getTotalProcessos(), c.getCriadoEm()))
                .toList();
        return new Painel(franquia.consumo(), mes, adminAllowList.contem(usuario.getEmail()), liberacoes(mes), periodo,
                consultas);
    }

    /** Soma um valor ao limite do mês corrente. Vale só neste mês; no próximo o limite volta ao configurado. */
    @Transactional
    public Painel liberar(BigDecimal valor, String motivo, UserEntity admin) {
        if (!adminAllowList.contem(admin.getEmail())) {
            throw new AcessoNegadoException("Só o admin libera valor a mais no limite da Judit.");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Informe um valor maior que zero.");
        }
        if (valor.compareTo(MAIOR_LIBERACAO) > 0) {
            throw new IllegalArgumentException("Valor acima de R$ 10.000,00. Confira o número.");
        }
        liberacaoRepository.save(JuditLiberacaoEntity.builder()
                .mes(JuditFranquia.mesCorrente())
                .valor(valor)
                .motivo(motivo == null || motivo.isBlank() ? null : motivo.strip())
                .adminId(admin.getId())
                .adminNome(admin.getName())
                .criadoEm(LocalDateTime.now())
                .build());
        log.info("Judit: {} liberou {} a mais no limite de {}", admin.getEmail(), valor, JuditFranquia.mesCorrente());
        return painel(30, admin);
    }

    private List<Liberacao> liberacoes(LocalDate mes) {
        return liberacaoRepository.findByMesOrderByCriadoEmDesc(mes).stream()
                .map(l -> new Liberacao(l.getId(), l.getValor(), l.getMotivo(), l.getAdminNome(), l.getCriadoEm()))
                .toList();
    }
}
