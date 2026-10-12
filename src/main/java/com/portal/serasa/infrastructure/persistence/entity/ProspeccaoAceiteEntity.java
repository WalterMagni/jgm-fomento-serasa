package com.portal.serasa.infrastructure.persistence.entity;

import com.portal.serasa.domain.model.prospeccao.CondicaoSugerida;
import com.portal.serasa.domain.model.prospeccao.RespostaCliente;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Resposta do cliente a uma decisão. Ver V75. */
@Entity
@Table(name = "prospeccao_aceite")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProspeccaoAceiteEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "prospeccao_id", nullable = false)
    private UUID prospeccaoId;

    @Column(name = "decisao_id", nullable = false)
    private UUID decisaoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RespostaCliente resposta;

    @Column(name = "limite_sugerido", precision = 16, scale = 2)
    private BigDecimal limiteSugerido;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condicoes_sugeridas", columnDefinition = "jsonb")
    private List<CondicaoSugerida> condicoesSugeridas;

    @Column(columnDefinition = "text")
    private String mensagem;

    @Column(name = "registrado_por")
    private UUID registradoPor;

    @Column(name = "registrado_por_nome", length = 200)
    private String registradoPorNome;

    @Column(name = "em_nome_do_comercial", nullable = false)
    @Builder.Default
    private Boolean emNomeDoComercial = false;

    @Column(name = "registrado_em", nullable = false)
    private LocalDateTime registradoEm;
}
