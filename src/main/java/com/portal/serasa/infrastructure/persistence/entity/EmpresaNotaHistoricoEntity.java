package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.NotaEmpresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma troca de nota. Nota nula = retirada. Ver V87. */
@Entity
@Table(name = "empresa_nota_historico")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaNotaHistoricoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 1)
    private NotaEmpresa nota;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nome")
    private String usuarioNome;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
