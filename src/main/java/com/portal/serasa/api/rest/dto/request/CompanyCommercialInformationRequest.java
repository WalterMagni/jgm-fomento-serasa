package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCommercialInformationRequest {

    @Size(max = 10)
    private String data;
    @Size(max = 50)
    private String tipo;
    @Size(max = 255)
    private String parceiro;
    @Size(max = 10)
    private String clienteDesde;
    @Size(max = 10)
    private String ultimaOperacaoData;
    @Size(max = 50)
    private String ultimaOperacaoValor;
    @Size(max = 50)
    private String limite;
    @Size(max = 50)
    private String riscoDuplicata;
    @Size(max = 50)
    private String riscoCheque;
    @Size(max = 50)
    private String riscoComissaria;
    @Size(max = 10)
    private String vencidosData;
    @Size(max = 50)
    private String vencidosValorMonetario;
    @Size(max = 10)
    private String vencidosValor;
    @Size(max = 50)
    private String vop;
    @Size(max = 20)
    private String pontual;
    @Size(max = 20)
    private String atraso;
    @Size(max = 20)
    private String recompra;
    @Size(max = 20)
    private String cartorio;
    @Size(max = 5000)
    private String observacao;
}
