package com.portal.serasa.application.service.empresa;

import com.portal.serasa.domain.model.NotaEmpresa;
import com.portal.serasa.infrastructure.persistence.entity.EmpresaNotaEntity;
import com.portal.serasa.infrastructure.persistence.entity.EmpresaNotaHistoricoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.EmpresaNotaHistoricoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.EmpresaNotaJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

/** Nota da equipe: qualquer usuário dá, troca ou retira; toda mudança vai para o histórico. */
@ExtendWith(MockitoExtension.class)
class EmpresaNotaServiceTest {

    private static final String CNPJ = "59304716000125";
    private static final UUID ID = UUID.randomUUID();

    @Mock private EmpresaNotaJpaRepository notas;
    @Mock private EmpresaNotaHistoricoJpaRepository historico;

    private EmpresaNotaService service;
    private UserEntity usuario;

    @BeforeEach
    void setUp() {
        service = new EmpresaNotaService(notas, historico);
        usuario = UserEntity.builder().id(ID).name("Maria Silva").email("maria@jgm.com.br").build();
    }

    private EmpresaNotaEntity notaAtual(NotaEmpresa nota) {
        return EmpresaNotaEntity.builder().cnpj(CNPJ).nota(nota).usuarioId(UUID.randomUUID())
                .usuarioNome("Ana").atribuidaEm(LocalDateTime.now().minusDays(3)).build();
    }

    @Test
    @DisplayName("primeira nota grava a nota atual com quem deu e uma linha no histórico")
    void primeiraNota() {
        when(notas.findById(CNPJ)).thenReturn(Optional.empty());

        service.atribuir("59.304.716/0001-25", NotaEmpresa.A, usuario);

        ArgumentCaptor<EmpresaNotaEntity> salva = ArgumentCaptor.forClass(EmpresaNotaEntity.class);
        verify(notas).save(salva.capture());
        assertThat(salva.getValue().getCnpj()).isEqualTo(CNPJ);
        assertThat(salva.getValue().getNota()).isEqualTo(NotaEmpresa.A);
        assertThat(salva.getValue().getUsuarioId()).isEqualTo(ID);
        assertThat(salva.getValue().getUsuarioNome()).isEqualTo("Maria Silva");
        assertThat(salva.getValue().getAtribuidaEm()).isNotNull();

        ArgumentCaptor<EmpresaNotaHistoricoEntity> linha = ArgumentCaptor.forClass(EmpresaNotaHistoricoEntity.class);
        verify(historico).save(linha.capture());
        assertThat(linha.getValue().getNota()).isEqualTo(NotaEmpresa.A);
        assertThat(linha.getValue().getUsuarioNome()).isEqualTo("Maria Silva");
    }

    @Test
    @DisplayName("trocar a nota atualiza a mesma linha, com o autor novo")
    void trocaNota() {
        EmpresaNotaEntity atual = notaAtual(NotaEmpresa.B);
        when(notas.findById(CNPJ)).thenReturn(Optional.of(atual));

        service.atribuir(CNPJ, NotaEmpresa.D, usuario);

        verify(notas).save(atual);
        assertThat(atual.getNota()).isEqualTo(NotaEmpresa.D);
        assertThat(atual.getUsuarioNome()).isEqualTo("Maria Silva");
        verify(historico).save(any(EmpresaNotaHistoricoEntity.class));
    }

    @Test
    @DisplayName("repetir a nota atual não grava nada nem suja o histórico")
    void mesmaNota() {
        when(notas.findById(CNPJ)).thenReturn(Optional.of(notaAtual(NotaEmpresa.C)));

        EmpresaNotaService.NotaAtual resposta = service.atribuir(CNPJ, NotaEmpresa.C, usuario);

        assertThat(resposta.nota()).isEqualTo(NotaEmpresa.C);
        assertThat(resposta.por()).isEqualTo("Ana");
        verify(notas, never()).save(any());
        verify(historico, never()).save(any());
    }

    @Test
    @DisplayName("retirar apaga a nota atual e registra a retirada no histórico")
    void retirarNota() {
        when(notas.findById(CNPJ)).thenReturn(Optional.of(notaAtual(NotaEmpresa.B)));

        service.atribuir(CNPJ, null, usuario);

        verify(notas).deleteById(CNPJ);
        ArgumentCaptor<EmpresaNotaHistoricoEntity> linha = ArgumentCaptor.forClass(EmpresaNotaHistoricoEntity.class);
        verify(historico).save(linha.capture());
        assertThat(linha.getValue().getNota()).isNull();
    }

    @Test
    @DisplayName("retirar de quem não tem nota não faz nada")
    void retirarSemNota() {
        when(notas.findById(CNPJ)).thenReturn(Optional.empty());

        service.atribuir(CNPJ, null, usuario);

        verify(notas, never()).deleteById(any());
        verify(historico, never()).save(any());
    }

    @Test
    @DisplayName("consulta devolve a nota atual e o histórico, do mais recente ao mais antigo")
    void consulta() {
        when(notas.findById(CNPJ)).thenReturn(Optional.of(notaAtual(NotaEmpresa.A)));
        when(historico.findTop10ByCnpjOrderByCriadoEmDesc(CNPJ)).thenReturn(List.of(
                EmpresaNotaHistoricoEntity.builder().cnpj(CNPJ).nota(NotaEmpresa.A).usuarioNome("Ana")
                        .criadoEm(LocalDateTime.now().minusDays(3)).build(),
                EmpresaNotaHistoricoEntity.builder().cnpj(CNPJ).nota(null).usuarioNome("Bruno")
                        .criadoEm(LocalDateTime.now().minusDays(9)).build()));

        EmpresaNotaService.NotaAtual resposta = service.consultar(CNPJ);

        assertThat(resposta.nota()).isEqualTo(NotaEmpresa.A);
        assertThat(resposta.historico()).extracting(EmpresaNotaService.Registro::por).containsExactly("Ana", "Bruno");
        assertThat(resposta.historico().get(1).nota()).isNull();
    }

    @Test
    @DisplayName("CNPJ sem 14 dígitos é recusado")
    void cnpjInvalido() {
        assertThatThrownBy(() -> service.atribuir("123", NotaEmpresa.A, usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("14 dígitos");
    }
}
