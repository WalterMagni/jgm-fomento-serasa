package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Perfil comercial da empresa, como o comercial preenchia no Portal Comercial. Ver V74.
 *
 * <p>Todos opcionais: a analista pede o que faltar por pendência, e não há como exigir na
 * abertura o que o cliente ainda não informou.</p>
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DadosComerciais {

    @Column(name = "faturamento_mensal", precision = 16, scale = 2)
    private BigDecimal faturamentoMensal;

    @Column(name = "ticket_medio", precision = 16, scale = 2)
    private BigDecimal ticketMedio;

    @Column(name = "valor_medio_operacoes", precision = 16, scale = 2)
    private BigDecimal valorMedioOperacoes;

    /** Dias. */
    @Column(name = "prazo_medio_vendas")
    private Integer prazoMedioVendas;

    /** Dias. */
    @Column(name = "prazo_medio_pgto_fornecedor")
    private Integer prazoMedioPgtoFornecedor;

    /** Dias. */
    @Column(name = "prazo_medio_entrega")
    private Integer prazoMedioEntrega;

    /** Há quantos dias o cliente guarda canhotos. */
    @Column(name = "possui_canhotos_dias")
    private Integer possuiCanhotosDias;

    @Column(name = "opera_mercadoria_entregue")
    private Boolean operaMercadoriaEntregue;

    @Column(name = "opera_mercadoria_embarcada")
    private Boolean operaMercadoriaEmbarcada;

    @Column(name = "regiao_atendimento", columnDefinition = "text")
    private String regiaoAtendimento;

    /** Com quais produtos a empresa opera. Texto livre, como no Comercial. */
    @Column(name = "produtos", columnDefinition = "text")
    private String produtos;

    @Column(name = "parceiros_atuais", columnDefinition = "text")
    private String parceirosAtuais;

    @Column(name = "outros_dados_comerciais", columnDefinition = "text")
    private String outros;
}
