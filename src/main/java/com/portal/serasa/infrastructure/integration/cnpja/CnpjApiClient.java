package com.portal.serasa.infrastructure.integration.cnpja;

import com.portal.serasa.infrastructure.integration.cnpja.dto.CnpjApiResponse;

public interface CnpjApiClient {

    CnpjApiResponse consultarCnpj(String cnpj);
}
