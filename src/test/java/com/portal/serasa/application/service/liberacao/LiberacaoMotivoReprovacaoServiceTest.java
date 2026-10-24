package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMotivoReprovacaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMotivoReprovacaoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiberacaoMotivoReprovacaoServiceTest {

    @Mock private LiberacaoMotivoReprovacaoJpaRepository repository;

    private LiberacaoMotivoReprovacaoService service;

    private final UserEntity admin = UserEntity.builder().id(UUID.randomUUID()).name("Admin").role("ADMIN").build();
    private final UserEntity analista = UserEntity.builder().id(UUID.randomUUID()).name("Andressa").role("ROLE_USER")
            .analista(true).comite(true).build();

    @BeforeEach
    void setUp() {
        service = new LiberacaoMotivoReprovacaoService(repository, new LiberacaoAutorizacao());
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("admin cria motivo com o nome limpo, ativo e no fim da lista quando não diz a ordem")
    void shouldLetAdminCreate() {
        LiberacaoMotivoReprovacaoEntity criado = service.criar("  Sacado   em Recuperação  ", null, admin);

        assertThat(criado.getNome()).isEqualTo("Sacado em Recuperação");
        assertThat(criado.getAtivo()).isTrue();
        assertThat(criado.getOrdem()).isEqualTo(99);
    }

    @Test
    @DisplayName("quem não é admin não cria nem edita, mesmo sendo do Comitê")
    void shouldDenyNonAdmin() {
        assertThatThrownBy(() -> service.criar("Novo", 1, analista))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Só admin altera os catálogos da esteira.");
        assertThatThrownBy(() -> service.editar(UUID.randomUUID(), "Novo", null, false, analista))
                .isInstanceOf(AcessoNegadoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("nome repetido (sem diferenciar maiúscula) é recusado, na criação e ao renomear")
    void shouldRefuseDuplicateName() {
        when(repository.existsByNomeIgnoreCase("praça de pgto")).thenReturn(true);
        assertThatThrownBy(() -> service.criar("praça de pgto", 1, admin))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Motivo já existe na lista: praça de pgto");

        LiberacaoMotivoReprovacaoEntity outro = motivo("Sacado sem Limite");
        when(repository.findById(outro.getId())).thenReturn(Optional.of(outro));
        assertThatThrownBy(() -> service.editar(outro.getId(), "praça de pgto", null, null, admin))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    @DisplayName("admin renomeia, reordena e desativa; trocar só a caixa do próprio nome não conta como repetido")
    void shouldLetAdminEdit() {
        LiberacaoMotivoReprovacaoEntity motivo = motivo("Praça de Pgto");
        when(repository.findById(motivo.getId())).thenReturn(Optional.of(motivo));

        LiberacaoMotivoReprovacaoEntity editado = service.editar(motivo.getId(), "PRAÇA DE PGTO", 3, false, admin);

        assertThat(editado.getNome()).isEqualTo("PRAÇA DE PGTO");
        assertThat(editado.getOrdem()).isEqualTo(3);
        assertThat(editado.getAtivo()).isFalse();
    }

    @Test
    @DisplayName("nome em branco é recusado")
    void shouldRefuseBlankName() {
        assertThatThrownBy(() -> service.criar("   ", 1, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Informe o motivo.");
    }

    @Test
    @DisplayName("listar devolve o catálogo inteiro, desativados inclusive, na ordem do repositório")
    void shouldListWholeCatalog() {
        List<LiberacaoMotivoReprovacaoEntity> todos = List.of(motivo("A"), motivo("B"));
        when(repository.findAllByOrderByOrdemAscNomeAsc()).thenReturn(todos);

        assertThat(service.listar()).isEqualTo(todos);
    }

    private static LiberacaoMotivoReprovacaoEntity motivo(String nome) {
        return LiberacaoMotivoReprovacaoEntity.builder().id(UUID.randomUUID()).nome(nome).ordem(1).ativo(true).build();
    }
}
