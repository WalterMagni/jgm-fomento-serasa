package com.portal.serasa.application.port.out;

import com.portal.serasa.domain.model.CompanyDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CompanyDetailRepository {

    CompanyDetail save(CompanyDetail companyDetail);

    Optional<CompanyDetail> findByDocumentNumber(String documentNumber);

    Page<CompanyDetail> findAll(Pageable pageable);

    void deleteByDocumentNumber(String documentNumber);

    boolean existsByDocumentNumber(String documentNumber);
}
