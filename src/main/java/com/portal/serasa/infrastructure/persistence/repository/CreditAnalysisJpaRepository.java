package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CreditAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CreditAnalysisJpaRepository extends JpaRepository<CreditAnalysisEntity, Long> {

    interface PortfolioAnalysisMetricsProjection {
        Number getAnalyzedClients();
        Number getCedenteSimCount();
        Number getHighRiskCount();
        Number getAvgScore();
    }

    List<CreditAnalysisEntity> findByCnpj(String cnpj);

    Optional<CreditAnalysisEntity> findFirstByCnpjOrderByConsultaEmDesc(String cnpj);

    @Query(value = """
        select * from (
            select ca.*,
                   row_number() over (partition by ca.cnpj order by ca.consulta_em desc, ca.id desc) as rn
            from credit_analysis ca
            where ca.cnpj in (:cnpjs)
        ) ranked
        where ranked.rn = 1
        """, nativeQuery = true)
    List<CreditAnalysisEntity> findLatestByCnpjIn(@Param("cnpjs") Collection<String> cnpjs);

    @Query(value = """
        select
            count(ca.id) as analyzedClients,
            count(ca.id) filter (where ca.visao_cedente = 'SIM') as cedenteSimCount,
            count(ca.id) filter (where ca.risk_class in ('1', '2')) as highRiskCount,
            coalesce(round(avg(ca.score)), 0) as avgScore
        from clients c
        left join lateral (
            select ca.*
            from credit_analysis ca
            where ca.cnpj = c.document_number
            order by ca.consulta_em desc, ca.id desc
            limit 1
        ) ca on true
        """, nativeQuery = true)
    PortfolioAnalysisMetricsProjection getPortfolioAnalysisMetrics();

    void deleteByCnpj(String cnpj);

    long countByVisaoCedente(String visaoCedente);

    /** Retorna a análise mais recente de cada CNPJ onde visaoCedente = :visaoCedente */
    @Query("""
        SELECT ca FROM CreditAnalysisEntity ca
        WHERE ca.visaoCedente = :visaoCedente
          AND ca.consultaEm = (
              SELECT MAX(ca2.consultaEm) FROM CreditAnalysisEntity ca2
              WHERE ca2.cnpj = ca.cnpj
          )
        ORDER BY ca.consultaEm DESC
        """)
    List<CreditAnalysisEntity> findLatestByVisaoCedente(String visaoCedente);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE CreditAnalysisEntity e SET e.aiAnalysis = :aiAnalysis, e.aiAnalysisDate = :date WHERE e.id = :id")
    void updateAiAnalysis(@org.springframework.data.repository.query.Param("id") Long id,
                          @org.springframework.data.repository.query.Param("aiAnalysis") String aiAnalysis,
                          @org.springframework.data.repository.query.Param("date") java.time.LocalDateTime date);
}
