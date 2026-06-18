package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.PersonAnalysisEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PersonAnalysisJpaRepository extends JpaRepository<PersonAnalysisEntity, Long> {

    Optional<PersonAnalysisEntity> findFirstByCpfOrderByConsultaEmDesc(String cpf);

    List<PersonAnalysisEntity> findByCpf(String cpf);

    void deleteByCpf(String cpf);

    Page<PersonAnalysisEntity> findByCpfContainingIgnoreCaseOrPersonNameContainingIgnoreCase(
            String cpf, String personName, Pageable pageable);

    /** Retorna a análise mais recente de cada CPF para um conjunto de CPFs. */
    @Query("""
        SELECT pa FROM PersonAnalysisEntity pa
        WHERE pa.cpf IN :cpfs
          AND pa.consultaEm = (
              SELECT MAX(pa2.consultaEm) FROM PersonAnalysisEntity pa2
              WHERE pa2.cpf = pa.cpf
          )
        """)
    List<PersonAnalysisEntity> findLatestByCpfIn(@Param("cpfs") Collection<String> cpfs);
}
