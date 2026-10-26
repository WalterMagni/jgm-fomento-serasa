package com.portal.serasa.application.service.judit;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.model.ServicoJudit;
import com.portal.serasa.domain.model.SituacaoConsultaJudit;
import com.portal.serasa.infrastructure.integration.judit.JuditProperties;
import com.portal.serasa.infrastructure.persistence.entity.JuditLiberacaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.JuditConsultaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.JuditLiberacaoJpaRepository;
import com.portal.serasa.infrastructure.security.AdminAllowList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Painel de consumo: franquia do mês, auditoria da pré-consulta e liberação só pelo admin. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JuditConsumoServiceTest {

    @Mock private JuditConsultaJpaRepository consultas;
    @Mock private JuditLiberacaoJpaRepository liberacoes;

    private JuditConsumoService service;
    private UserEntity admin;
    private UserEntity analista;

    @BeforeEach
    void setUp() {
        JuditProperties properties = new JuditProperties("chave", "l", "r", new BigDecimal("0.75"),
                new BigDecimal("1.50"), new BigDecimal("0.35"), new BigDecimal("1000"), new BigDecimal("1500"), 30, 5, 10);
        service = new JuditConsumoService(consultas, liberacoes, new JuditFranquia(consultas, liberacoes, properties),
                new AdminAllowList("walter@admin.com"));
        admin = UserEntity.builder().id(UUID.randomUUID()).name("Walter").email("walter@admin.com").build();
        analista = UserEntity.builder().id(UUID.randomUUID()).name("Ana").email("ana@jgm.com.br").analista(true).judit(true).build();
        when(consultas.somarCustoDesde(any())).thenReturn(new BigDecimal("1012.75"));
        when(liberacoes.somarDoMes(any())).thenReturn(new BigDecimal("500"));
        when(liberacoes.findByMesOrderByCriadoEmDesc(any())).thenReturn(List.of(JuditLiberacaoEntity.builder()
                .id(UUID.randomUUID()).valor(new BigDecimal("500")).motivo("fim do mês").adminNome("Walter")
                .criadoEm(LocalDateTime.now()).build()));
    }

    private static JuditConsultaJpaRepository.ConsultaDoPainel linha(ServicoJudit servico, String nome) {
        JuditConsultaJpaRepository.ConsultaDoPainel linha = mock(JuditConsultaJpaRepository.ConsultaDoPainel.class);
        when(linha.getServico()).thenReturn(servico);
        when(linha.getNomeEmpresa()).thenReturn(nome);
        when(linha.getSituacao()).thenReturn(SituacaoConsultaJudit.CONCLUIDA);
        when(linha.getCusto()).thenReturn(new BigDecimal("1.50"));
        return linha;
    }

    @Test
    @DisplayName("painel: limite do mês soma a liberação e a tabela traz as consultas do período")
    void painel() {
        List<JuditConsultaJpaRepository.ConsultaDoPainel> linhas = List.of(
                linha(ServicoJudit.PROCESSOS, "CISCO"),
                linha(ServicoJudit.RESUMO, "CISCO"),
                linha(ServicoJudit.DETALHE, null));
        when(consultas.doPainelDesde(any())).thenReturn(linhas);

        JuditConsumoService.Painel painel = service.painel(30, analista);

        assertThat(painel.mes().gastoMes()).isEqualByComparingTo("1012.75");
        assertThat(painel.mes().franquia()).isEqualByComparingTo("1000");
        assertThat(painel.mes().limite()).isEqualByComparingTo("2000");
        assertThat(painel.mes().liberado()).isEqualByComparingTo("500");
        assertThat(painel.podeLiberar()).isFalse();
        assertThat(painel.liberacoes()).hasSize(1);
        assertThat(painel.consultas()).extracting(JuditConsumoService.Consulta::servico)
                .containsExactly(ServicoJudit.PROCESSOS, ServicoJudit.RESUMO, ServicoJudit.DETALHE);
        assertThat(painel.consultas()).extracting(JuditConsumoService.Consulta::nomeEmpresa)
                .containsExactly("CISCO", "CISCO", null);
    }

    @Test
    @DisplayName("painel: período fora da faixa vira 1 a 366 dias")
    void painelPeriodo() {
        assertThat(service.painel(0, admin).dias()).isEqualTo(1);
        assertThat(service.painel(9999, admin).dias()).isEqualTo(366);
        assertThat(service.painel(30, admin).podeLiberar()).isTrue();
    }

    @Test
    @DisplayName("liberar: grava no mês corrente com quem liberou")
    void liberar() {
        service.liberar(new BigDecimal("500"), "  fim do mês  ", admin);

        ArgumentCaptor<JuditLiberacaoEntity> salva = ArgumentCaptor.forClass(JuditLiberacaoEntity.class);
        verify(liberacoes).save(salva.capture());
        assertThat(salva.getValue().getMes()).isEqualTo(JuditFranquia.mesCorrente());
        assertThat(salva.getValue().getValor()).isEqualByComparingTo("500");
        assertThat(salva.getValue().getMotivo()).isEqualTo("fim do mês");
        assertThat(salva.getValue().getAdminNome()).isEqualTo("Walter");
    }

    @Test
    @DisplayName("liberar: só o admin, valor positivo e sem exagero")
    void liberarRecusa() {
        assertThatThrownBy(() -> service.liberar(new BigDecimal("500"), null, analista))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.liberar(BigDecimal.ZERO, null, admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.liberar(new BigDecimal("10000.01"), null, admin))
                .isInstanceOf(IllegalArgumentException.class);
        verify(liberacoes, never()).save(any());
    }
}
