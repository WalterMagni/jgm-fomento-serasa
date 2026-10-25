package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCommercialInformationResponse {

    private UUID id;
    private String cnpj;
    private String empresa;
    private String authorName;
    private String authorEmail;
    private boolean canManage;
    private String data;
    private String tipo;
    private String parceiro;
    private String clienteDesde;
    private String ultimaOperacaoData;
    private String ultimaOperacaoValor;
    private String limite;
    private String riscoDuplicata;
    private String riscoCheque;
    private String riscoComissaria;
    private String vencidosData;
    private String vencidosValorMonetario;
    private String vencidosValor;
    private String vop;
    private String pontual;
    private String atraso;
    private String recompra;
    private String cartorio;
    private String observacao;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
