package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String role;

    @org.springframework.data.annotation.CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @org.springframework.data.annotation.LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "email_notificacao_cedente", nullable = false)
    @Builder.Default
    private boolean emailNotificacaoCedente = true;

    /** Edita e move cards da esteira de liberação a partir do Comitê. Ver V63. */
    @Column(nullable = false)
    @Builder.Default
    private boolean analista = false;

    /** Parecer obrigatório no Comitê. Exige {@link #analista} — restrição no banco. */
    @Column(nullable = false)
    @Builder.Default
    private boolean comite = false;

    /** Decide os cards que o Comitê manda para a Diretoria. Exige {@link #analista}. Ver V88. */
    @Column(nullable = false)
    @Builder.Default
    private boolean diretoria = false;

    /** Liga a Judit numa empresa e consulta processos — cada consulta custa. Exige {@link #analista}. Ver V89. */
    @Column(nullable = false)
    @Builder.Default
    private boolean judit = false;

    /** Som do sino de notificações. Ver V66. */
    @Column(name = "som_notificacao", nullable = false)
    @Builder.Default
    private boolean somNotificacao = true;

    /** Aviso por e-mail da esteira de liberação. Ver V72. */
    @Column(name = "email_liberacao", nullable = false)
    @Builder.Default
    private boolean emailLiberacao = true;

    /** Última troca de senha: login emitido antes disso deixa de valer. Ver V84. */
    @Column(name = "senha_alterada_em")
    private Instant senhaAlteradaEm;
}
