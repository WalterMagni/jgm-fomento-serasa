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

import java.time.LocalDateTime;
import java.util.UUID;

/** Nota atual da empresa, uma linha por CNPJ. Ver V87. */
@Entity
@Table(name = "empresa_nota")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaNotaEntity {

    @Id
    @Column(length = 14)
    private String cnpj;

    /** varchar(1) na V87; sem o JdbcTypeCode o Hibernate valida como char(1). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 1)
    private NotaEmpresa nota;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nome")
    private String usuarioNome;

    @Column(name = "atribuida_em", nullable = false)
    private LocalDateTime atribuidaEm;
}
