package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.prospeccao.StatusPendencia;
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
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Pedido de informação ou documento ao comercial. Ver V76. */
@Entity
@Table(name = "prospeccao_pendencia")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProspeccaoPendenciaEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "prospeccao_id", nullable = false)
    private UUID prospeccaoId;

    @Column(nullable = false, columnDefinition = "text")
    private String pergunta;

    /** Campos de dados comerciais exigidos, pelo nome do campo no DTO. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "campos_exigidos", columnDefinition = "jsonb")
    private List<String> camposExigidos;

    /** Itens do checklist pedidos. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documentos_pedidos", columnDefinition = "jsonb")
    private List<UUID> documentosPedidos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private StatusPendencia status = StatusPendencia.ABERTA;

    @Column(name = "aberta_por")
    private UUID abertaPor;

    @Column(name = "aberta_por_nome", length = 200)
    private String abertaPorNome;

    @Column(name = "aberta_em", nullable = false)
    private LocalDateTime abertaEm;

    @Column(columnDefinition = "text")
    private String resposta;

    @Column(name = "respondida_por")
    private UUID respondidaPor;

    @Column(name = "respondida_por_nome", length = 200)
    private String respondidaPorNome;

    @Column(name = "em_nome_do_comercial", nullable = false)
    @Builder.Default
    private Boolean emNomeDoComercial = false;

    @Column(name = "fechada_em")
    private LocalDateTime fechadaEm;
}
