package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientJpaRepository extends JpaRepository<ClientEntity, UUID> {

    Optional<ClientEntity> findByDocumentNumber(String documentNumber);

    Optional<ClientEntity> findByClientCode(String clientCode);

    void deleteByDocumentNumber(String documentNumber);

    Page<ClientEntity> findByDocumentNumberContainingIgnoreCaseOrNameContainingIgnoreCase(
            String documentNumber, String name, Pageable pageable);

    @Query("""
        select c
        from ClientEntity c
        where (:searchBlank = true
               or lower(c.documentNumber) like lower(concat('%', :search, '%'))
               or lower(c.name) like lower(concat('%', :search, '%'))
               or exists (
                   select cd.id
                   from CompanyDetailEntity cd
                   where cd.documentNumber = c.documentNumber
                     and lower(cd.companyName) like lower(concat('%', :search, '%'))
               ))
          and (:analysisStatusBlank = true
               or (:analysisStatus = 'ANALYZED' and exists (
                   select ca.id
                   from CreditAnalysisEntity ca
                   where ca.cnpj = c.documentNumber
               ))
               or (:analysisStatus = 'PENDING' and not exists (
                   select ca.id
                   from CreditAnalysisEntity ca
                   where ca.cnpj = c.documentNumber
               )))
          and (:visaoCedenteBlank = true
               or exists (
                   select ca.id
                   from CreditAnalysisEntity ca
                   where ca.cnpj = c.documentNumber
                     and ca.visaoCedente = :visaoCedente
                     and ca.consultaEm = (
                         select max(ca2.consultaEm)
                         from CreditAnalysisEntity ca2
                         where ca2.cnpj = c.documentNumber
                     )
               )
               or (:visaoCedente = 'PENDENTE' and not exists (
                   select ca.id
                   from CreditAnalysisEntity ca
                   where ca.cnpj = c.documentNumber
               )))
        """)
    Page<ClientEntity> searchProfiles(
            @Param("search") String search,
            @Param("searchBlank") boolean searchBlank,
            @Param("visaoCedente") String visaoCedente,
            @Param("visaoCedenteBlank") boolean visaoCedenteBlank,
            @Param("analysisStatus") String analysisStatus,
            @Param("analysisStatusBlank") boolean analysisStatusBlank,
            Pageable pageable);

    List<ClientEntity> findByDocumentNumberIn(Collection<String> documentNumbers);

    List<ClientEntity> findByDocumentNumberStartingWith(String documentRoot);
}
