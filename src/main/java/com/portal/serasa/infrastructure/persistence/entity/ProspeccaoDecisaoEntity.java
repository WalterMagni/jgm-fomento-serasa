package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.ResultadoDecisao;
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
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Uma decisão da analista sobre o card. Ver V75.
 *
 * <p>Decisão nova não edita a anterior: a antiga perde {@code vigente} e vira histórico. É o que
 * permite mostrar o que mudou entre a proposta e a contraproposta.</p>
 */
@Entity
@Table(name = "prospeccao_decisao")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProspeccaoDecisaoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "prospeccao_id", nullable = false)
    private UUID prospeccaoId;

    @Column(nullable = false)
    private Integer versao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ResultadoDecisao resultado;

    @Column(precision = 16, scale = 2)
    private BigDecimal limite;

    @Column(columnDefinition = "text")
    private String parecer;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_recusa", length = 40)
    private MotivoRecusa motivoRecusa;

    @Column(nullable = false)
    @Builder.Default
    private Boolean vigente = true;

    @Column(name = "decidido_por")
    private UUID decididoPor;

    @Column(name = "decidido_por_nome", length = 200)
    private String decididoPorNome;

    @Column(name = "decidido_em", nullable = false)
    private LocalDateTime decididoEm;
}
