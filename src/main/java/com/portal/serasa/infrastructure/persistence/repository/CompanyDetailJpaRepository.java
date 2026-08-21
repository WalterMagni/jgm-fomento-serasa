package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CompanyDetailEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyDetailJpaRepository extends JpaRepository<CompanyDetailEntity, UUID> {

    Optional<CompanyDetailEntity> findByDocumentNumber(String documentNumber);

    List<CompanyDetailEntity> findByDocumentNumberIn(Collection<String> documentNumbers);

    @Query(value = """
        select count(distinct cd.document_number)
        from company_details cd
        join clients c on c.document_number = cd.document_number
        where cd.company_name is not null
          and cd.company_name <> ''
        """, nativeQuery = true)
    long countRegisteredEnrichedClients();

    @Query(value = """
        select * from company_details cd
        where cd.document_number like concat('%', :digits, '%')
           or lower(cd.company_name) like concat('%', :term, '%')
           or lower(cd.alias) like concat('%', :term, '%')
        order by cd.company_name
        limit :limit
        """, nativeQuery = true)
    List<CompanyDetailEntity> searchByNameOrDocument(@Param("term") String term,
                                                     @Param("digits") String digits,
                                                     @Param("limit") int limit);

    void deleteByDocumentNumber(String documentNumber);
}
