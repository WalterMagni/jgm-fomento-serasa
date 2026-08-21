package com.portal.serasa.application.port.out;

import com.portal.serasa.domain.model.CompanyDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface CompanyDetailRepository {

    CompanyDetail save(CompanyDetail companyDetail);

    Optional<CompanyDetail> findByDocumentNumber(String documentNumber);

    List<CompanyDetail> findByDocumentNumberIn(Collection<String> documentNumbers);

    Page<CompanyDetail> findAll(Pageable pageable);

    /** Busca empresas já cadastradas por trecho do nome/fantasia ou por CNPJ. */
    List<CompanyDetail> searchByNameOrDocument(String term, int limit);

    long countRegisteredEnrichedClients();

    void deleteByDocumentNumber(String documentNumber);

    boolean existsByDocumentNumber(String documentNumber);
}
