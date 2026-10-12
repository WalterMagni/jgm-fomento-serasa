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

    /**
     * Última análise de cada CPF já recortada para a lista de Pessoas Físicas. Nativa por causa
     * do {@code DISTINCT ON} e dos operadores jsonb; o {@code original_payload} fica de fora.
     */
    @Query(value = """
        SELECT DISTINCT ON (pa.cpf)
               pa.cpf AS cpf,
               pa.person_name AS nome,
               pa.consulta_em AS consultaEm,
               pa.registration #>> '{address,city}' AS cidade,
               pa.registration #>> '{address,state}' AS uf,
               CAST(jsonb_build_object(
                   'pefin', pa.negative_summary #> '{pefin,summary}',
                   'refin', pa.negative_summary #> '{refin,summary}',
                   'check', pa.negative_summary #> '{check,summary}',
                   'notary', pa.negative_summary #> '{notary,summary}',
                   'collectionRecords', pa.negative_summary #> '{collectionRecords,summary}',
                   'judgementFilings', pa.facts #> '{judgementFilings,summary}',
                   'bankrupts', pa.facts #> '{bankrupts,summary}'
               ) AS text) AS restricoesJson,
               CAST(pa.partner_companies -> 'partnershipResponse' AS text) AS sociosJson
          FROM person_analysis pa
         ORDER BY pa.cpf, pa.consulta_em DESC, pa.id DESC
        """, nativeQuery = true)
    List<UltimaAnaliseView> findUltimaAnalisePorCpf();

    interface UltimaAnaliseView {
        String getCpf();
        String getNome();
        java.time.LocalDateTime getConsultaEm();
        String getCidade();
        String getUf();
        String getRestricoesJson();
        String getSociosJson();
    }
}
