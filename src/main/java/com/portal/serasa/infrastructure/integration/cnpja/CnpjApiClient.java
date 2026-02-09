package com.portal.serasa.infrastructure.integration.cnpja;

import com.portal.serasa.infrastructure.integration.cnpja.dto.CompanyDetailDto;

public interface CnpjApiClient {

    CompanyDetailDto consultarCnpj(String cnpj);
}
