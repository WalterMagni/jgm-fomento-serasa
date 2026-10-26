package com.portal.serasa.application.service.empresa;

import com.portal.serasa.infrastructure.persistence.entity.EmpresaLimiteSacadoEntity;
import com.portal.serasa.infrastructure.persistence.entity.EmpresaLimiteSacadoHistoricoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.EmpresaLimiteSacadoHistoricoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.EmpresaLimiteSacadoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Limite de sacado: qualquer usuário define, troca ou retira; toda mudança vai para o histórico. */
@ExtendWith(MockitoExtension.class)
class EmpresaLimiteSacadoServiceTest {

    private static final String CNPJ = "59304716000125";

    @Mock private EmpresaLimiteSacadoJpaRepository limites;
    @Mock private EmpresaLimiteSacadoHistoricoJpaRepository historico;

    private EmpresaLimiteSacadoService service;
    private UserEntity usuario;

    @BeforeEach
    void setUp() {
        service = new EmpresaLimiteSacadoService(limites, historico);
        usuario = UserEntity.builder().id(UUID.randomUUID()).name("Maria Silva").email("maria@jgm.com.br").build();
    }

    private EmpresaLimiteSacadoEntity limiteAtual(String valor) {
        return EmpresaLimiteSacadoEntity.builder().cnpj(CNPJ).valor(new BigDecimal(valor)).usuarioId(UUID.randomUUID())
                .usuarioNome("Ana").definidoEm(LocalDateTime.now().minusDays(3)).build();
    }

    @Test
    @DisplayName("primeiro limite grava o valor com quem definiu e uma linha no histórico")
    void shouldSetFirstLimit() {
        when(limites.findById(CNPJ)).thenReturn(Optional.empty());

        service.definir("59.304.716/0001-25", new BigDecimal("150000"), usuario);

        ArgumentCaptor<EmpresaLimiteSacadoEntity> salvo = ArgumentCaptor.forClass(EmpresaLimiteSacadoEntity.class);
        verify(limites).save(salvo.capture());
        assertThat(salvo.getValue().getCnpj()).isEqualTo(CNPJ);
        assertThat(salvo.getValue().getValor()).isEqualByComparingTo("150000.00");
        assertThat(salvo.getValue().getUsuarioNome()).isEqualTo("Maria Silva");
        ArgumentCaptor<EmpresaLimiteSacadoHistoricoEntity> linha = ArgumentCaptor.forClass(EmpresaLimiteSacadoHistoricoEntity.class);
        verify(historico).save(linha.capture());
        assertThat(linha.getValue().getValor()).isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("mesmo valor (150000 = 150000.00) não grava nada nem suja o histórico")
    void shouldIgnoreSameValue() {
        when(limites.findById(CNPJ)).thenReturn(Optional.of(limiteAtual("150000.00")));

        service.definir(CNPJ, new BigDecimal("150000"), usuario);

        verify(limites, never()).save(any());
        verify(historico, never()).save(any());
    }

    @Test
    @DisplayName("valor nulo retira o limite e registra a retirada; retirar de quem não tem não faz nada")
    void shouldRemoveLimit() {
        when(limites.findById(CNPJ)).thenReturn(Optional.of(limiteAtual("50000")));

        service.definir(CNPJ, null, usuario);

        verify(limites).deleteById(CNPJ);
        ArgumentCaptor<EmpresaLimiteSacadoHistoricoEntity> linha = ArgumentCaptor.forClass(EmpresaLimiteSacadoHistoricoEntity.class);
        verify(historico).save(linha.capture());
        assertThat(linha.getValue().getValor()).isNull();

        when(limites.findById("45723174000110")).thenReturn(Optional.empty());
        service.definir("45723174000110", null, usuario);
        verify(historico).save(any());
    }

    @Test
    @DisplayName("limite zero ou negativo é recusado; CNPJ inválido também")
    void shouldRejectInvalidValues() {
        assertThatThrownBy(() -> service.definir(CNPJ, BigDecimal.ZERO, usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que zero");
        assertThatThrownBy(() -> service.definir("123", new BigDecimal("10"), usuario))
                .isInstanceOf(IllegalArgumentException.class);
        verify(limites, never()).save(any());
    }

    @Test
    @DisplayName("limitesDe: só CNPJ, numa consulta; sem documento não vai ao banco")
    void shouldLoadLimitsInBatch() {
        when(limites.findByCnpjIn(List.of(CNPJ))).thenReturn(List.of(limiteAtual("75000")));

        assertThat(service.limitesDe(List.of(CNPJ, "12345678909", CNPJ))).containsOnlyKeys(CNPJ);
        assertThat(service.limitesDe(List.of("12345678909"))).isEmpty();
        verify(limites).findByCnpjIn(any());
    }
}
