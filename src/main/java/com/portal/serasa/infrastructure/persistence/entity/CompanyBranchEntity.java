package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "company_branches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyBranchEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "cnpj_raiz", nullable = false, length = 8)
    private String cnpjRaiz;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(name = "matriz_filial", length = 1)
    private String matrizFilial;

    @Column(name = "nome_fantasia", length = 255)
    private String nomeFantasia;

    @Column(length = 2)
    private String uf;

    @Column(name = "id_municipio", length = 7)
    private String idMunicipio;

    @Column(name = "nome_municipio", length = 255)
    private String nomeMunicipio;

    @Column(length = 255)
    private String logradouro;

    @Column(length = 30)
    private String numero;

    @Column(length = 255)
    private String bairro;

    @Column(length = 10)
    private String cep;

    @Column(precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
