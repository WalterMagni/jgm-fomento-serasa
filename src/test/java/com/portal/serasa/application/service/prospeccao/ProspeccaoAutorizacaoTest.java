package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProspeccaoAutorizacaoTest {

    private final ProspeccaoAutorizacao autorizacao = new ProspeccaoAutorizacao();

    private UserEntity usuario(String papel) {
        return UserEntity.builder().id(UUID.randomUUID()).name("Fulano").role(papel).build();
    }

    private ProspeccaoEntity cardDe(UUID comercialId) {
        return ProspeccaoEntity.builder()
                .id(UUID.randomUUID())
                .cnpj("11222333000181")
                .estagio(EstagioProspeccao.DOCS_PENDENTES)
                .comercialId(comercialId)
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_COMERCIAL", "ROLE_ANALISTA", "ROLE_BACKOFFICE",
            "ROLE_GESTOR", "ROLE_ADMIN", "USER", "ADMIN", "qualquer_coisa"})
    @DisplayName("qualquer papel opera a esteira inteira")
    void shouldLetEveryRoleOperate(String papel) {
        UserEntity pessoa = usuario(papel);

        assertThatCode(() -> autorizacao.exigirCriador(pessoa)).doesNotThrowAnyException();
        assertThatCode(() -> autorizacao.exigirDecisor(pessoa)).doesNotThrowAnyException();
        assertThatCode(() -> autorizacao.exigirConferente(pessoa)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("card de outro comercial pode ser alterado — a equipe cobre a carteira uma da outra")
    void shouldLetAnyoneWriteOnAnyCard() {
        UserEntity comercial = usuario("ROLE_USER");

        assertThatCode(() -> autorizacao.exigirEscrita(cardDe(UUID.randomUUID()), comercial))
                .doesNotThrowAnyException();
        assertThatCode(() -> autorizacao.exigirEscrita(cardDe(null), comercial))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sem usuário autenticado nada passa")
    void shouldStillRequireAuthentication() {
        assertThatThrownBy(() -> autorizacao.exigirCriador(null)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> autorizacao.exigirDecisor(null)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> autorizacao.exigirEscrita(cardDe(null), null))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> autorizacao.exigirAdmin(null)).isInstanceOf(AcessoNegadoException.class);

        UserEntity semId = UserEntity.builder().name("Sem id").role("ROLE_ADMIN").build();
        assertThatThrownBy(() -> autorizacao.exigirDecisor(semId)).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("catálogo de documentos continua só do admin: é configuração, não operação")
    void shouldKeepCatalogAdminOnly() {
        assertThatCode(() -> autorizacao.exigirAdmin(usuario("ROLE_ADMIN"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> autorizacao.exigirAdmin(usuario("ROLE_USER")))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("catálogo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "admin", "  ROLE_ADMIN  "})
    @DisplayName("admin vale nas duas grafias: o seeder grava ADMIN e o cadastro grava ROLE_")
    void shouldAcceptAdminInBothSpellings(String papel) {
        assertThatCode(() -> autorizacao.exigirAdmin(usuario(papel))).doesNotThrowAnyException();
    }
}
