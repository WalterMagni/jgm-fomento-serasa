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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Vínculo sócio × empresa (raiz de CNPJ). */
@Entity
@Table(name = "shareholder_companies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareholderCompanyEntity {

    public static final String SOURCE_RECEITA = "RECEITA";
    public static final String SOURCE_SERASA = "SERASA";

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "shareholder_id", nullable = false)
    private UUID shareholderId;

    @Column(name = "cnpj_raiz", nullable = false, length = 8)
    private String cnpjRaiz;

    @Column(name = "company_name", columnDefinition = "text")
    private String companyName;

    /** CNPJ completo da matriz na Receita. Necessário para cadastrar/vincular a empresa. */
    @Column(name = "company_cnpj", length = 14)
    private String companyCnpj;

    @Column(name = "qualification_code", length = 2)
    private String qualificationCode;

    @Column(name = "qualification_description", length = 255)
    private String qualificationDescription;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    /** Percentual do capital social. Só o Serasa informa. */
    @Column(name = "capital_percent", precision = 9, scale = 4)
    private BigDecimal capitalPercent;

    @Column(name = "partner_status", length = 30)
    private String partnerStatus;

    /** situacao_cadastral da matriz na Receita: 02=ativa, 03=suspensa, 04=inapta, 08=baixada. */
    @Column(name = "company_status", length = 2)
    private String companyStatus;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
