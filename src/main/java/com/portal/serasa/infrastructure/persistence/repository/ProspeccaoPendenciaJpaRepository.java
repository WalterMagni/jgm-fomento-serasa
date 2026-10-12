package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.domain.model.prospeccao.StatusPendencia;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoPendenciaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProspeccaoPendenciaJpaRepository extends JpaRepository<ProspeccaoPendenciaEntity, UUID> {

    List<ProspeccaoPendenciaEntity> findByProspeccaoIdOrderByAbertaEmDesc(UUID prospeccaoId);

    long countByProspeccaoIdAndStatus(UUID prospeccaoId, StatusPendencia status);

    /** Pendências abertas por card, numa consulta só, para o selo do quadro. */
    @Query("""
            select p.prospeccaoId, count(p) from ProspeccaoPendenciaEntity p
            where p.prospeccaoId in :ids
              and p.status = com.portal.serasa.domain.model.prospeccao.StatusPendencia.ABERTA
            group by p.prospeccaoId
            """)
    List<Object[]> contarAbertas(@Param("ids") Collection<UUID> ids);
}
