package com.portal.serasa.api.rest.dto.response;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/** Empresa ligada à consultada por sócio em comum. */
@Builder
public record RelatedCompanyResponse(
        String cnpjRaiz,
        /**
         * CNPJ completo da matriz cadastrada no portal. Nulo quando a empresa não tem perfil —
         * o vínculo de parceira exige perfil existente, então sem isso não há o que vincular.
         */
        String cnpj,
        /** CNPJ da matriz segundo a Receita. Existe mesmo para empresa sem perfil no portal. */
        String receitaCnpj,
        String companyName,
        /** situacao_cadastral da Receita: 02=ativa, 03=suspensa, 04=inapta, 08=baixada. */
        String companyStatus,
        String companyStatusLabel,
        /** Situação diferente de ativa é sinal de alerta (empresa-espelho, sucessão). */
        boolean irregular,
        /** Já existe cadastro desta empresa no portal. */
        boolean inSystem,
        /** Já vinculada manualmente como empresa parceira. */
        boolean alreadyLinked,
        List<SharedShareholder> sharedShareholders) {

    @Builder
    public record SharedShareholder(
            String name,
            String document,
            String documentMask,
            String qualification,
            LocalDate entryDate) {
    }
}
