package com.portal.serasa.application.port.out;

import com.portal.serasa.domain.model.CompanyDetail;

import java.util.Optional;

public interface CompanyDetailRepository {

    CompanyDetail save(CompanyDetail companyDetail);

    Optional<CompanyDetail> findByDocumentNumber(String documentNumber);
}
