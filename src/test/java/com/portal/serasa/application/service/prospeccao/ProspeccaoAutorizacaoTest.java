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
    @ValueSource(strings = {ProspeccaoAutorizacao.ANALISTA, ProspeccaoAutorizacao.GESTOR,
            ProspeccaoAutorizacao.ADMIN})
    @DisplayName("decidir sobre a análise: analista, gestor e admin")
    void shouldAllowDecisionRoles(String papel) {
        assertThatCode(() -> autorizacao.exigirDecisor(usuario(papel))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {ProspeccaoAutorizacao.COMERCIAL, ProspeccaoAutorizacao.BACKOFFICE,
            ProspeccaoAutorizacao.LEGADO})
    @DisplayName("decidir sobre a análise: comercial e backoffice não decidem")
    void shouldBlockNonDecisionRoles(String papel) {
        assertThatThrownBy(() -> autorizacao.exigirDecisor(usuario(papel)))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("decide sobre a análise");
    }

    @Test
    @DisplayName("conferir documento: backoffice pode, comercial não")
    void shouldRestrictDocumentReview() {
        assertThatCode(() -> autorizacao.exigirConferente(usuario(ProspeccaoAutorizacao.BACKOFFICE)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> autorizacao.exigirConferente(usuario(ProspeccaoAutorizacao.COMERCIAL)))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("comercial mexe no card dele")
    void shouldAllowOwnerToWrite() {
        UserEntity comercial = usuario(ProspeccaoAutorizacao.COMERCIAL);

        assertThatCode(() -> autorizacao.exigirEscrita(cardDe(comercial.getId()), comercial))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("comercial não mexe no card de outro comercial, mas a mensagem explica por quê")
    void shouldBlockWritingAnotherSalesCard() {
        UserEntity comercial = usuario(ProspeccaoAutorizacao.COMERCIAL);

        assertThatThrownBy(() -> autorizacao.exigirEscrita(cardDe(UUID.randomUUID()), comercial))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessageContaining("de outro comercial");
    }

    @Test
    @DisplayName("papel legado ROLE_USER se comporta como comercial, sem reescrever o banco")
    void shouldTreatLegacyRoleAsSales() {
        UserEntity legado = usuario(ProspeccaoAutorizacao.LEGADO);

        assertThatCode(() -> autorizacao.exigirEscrita(cardDe(legado.getId()), legado))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> autorizacao.exigirEscrita(cardDe(UUID.randomUUID()), legado))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {ProspeccaoAutorizacao.ANALISTA, ProspeccaoAutorizacao.BACKOFFICE,
            ProspeccaoAutorizacao.GESTOR, ProspeccaoAutorizacao.ADMIN})
    @DisplayName("quem vê tudo mexe em qualquer card, inclusive nos sem comercial vinculado")
    void shouldLetInternalRolesWriteAnyCard(String papel) {
        assertThatCode(() -> autorizacao.exigirEscrita(cardDe(null), usuario(papel)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("card sem comercial — o DIRETO da planilha — não fica intocável para o comercial")
    void shouldBlockSalesOnUnassignedCard() {
        // Sem dono, só quem vê tudo mexe. Evita que qualquer comercial altere card alheio.
        assertThatThrownBy(() -> autorizacao.exigirEscrita(cardDe(null), usuario(ProspeccaoAutorizacao.COMERCIAL)))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("catálogo de documentos é só do admin")
    void shouldRestrictCatalogToAdmin() {
        assertThatCode(() -> autorizacao.exigirAdmin(usuario(ProspeccaoAutorizacao.ADMIN)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> autorizacao.exigirAdmin(usuario(ProspeccaoAutorizacao.GESTOR)))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("usuário sem papel não passa de nenhuma porta")
    void shouldRejectRolelessUser() {
        UserEntity semPapel = UserEntity.builder().id(UUID.randomUUID()).name("X").build();

        assertThatThrownBy(() -> autorizacao.exigirDecisor(semPapel)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> autorizacao.exigirCriador(semPapel)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> autorizacao.exigirAdmin(semPapel)).isInstanceOf(AcessoNegadoException.class);
    }
}
