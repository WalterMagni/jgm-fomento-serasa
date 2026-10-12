package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoParecerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProspeccaoParecerJpaRepository extends JpaRepository<ProspeccaoParecerEntity, UUID> {

    List<ProspeccaoParecerEntity> findByProspeccaoIdOrderByRodadaDescCriadoEmAsc(UUID prospeccaoId);

    List<ProspeccaoParecerEntity> findByProspeccaoIdAndRodadaOrderByCriadoEmAsc(UUID prospeccaoId, Integer rodada);

    Optional<ProspeccaoParecerEntity> findByProspeccaoIdAndRodadaAndUsuarioId(UUID prospeccaoId, Integer rodada, UUID usuarioId);

    /** Pareceres da rodada vigente de cada card, numa consulta só, para o quadro. */
    @Query("""
            select p from ProspeccaoParecerEntity p, ProspeccaoEntity c
            where p.prospeccaoId = c.id and p.rodada = c.rodadaComite and c.id in :ids
            order by p.criadoEm
            """)
    List<ProspeccaoParecerEntity> rodadaVigente(@Param("ids") Collection<UUID> ids);

    /** Cards no comitê esperando o parecer desta pessoa, para o aviso do topo do quadro. */
    @Query("""
            select c.id from ProspeccaoParecerEntity p, ProspeccaoEntity c
            where p.prospeccaoId = c.id and p.rodada = c.rodadaComite and p.usuarioId = :usuarioId
              and p.posicao is null
              and c.estagio = com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.COMITE
            """)
    List<UUID> cardsAguardandoParecerDe(@Param("usuarioId") UUID usuarioId);

    /** Rodada que ficou para trás: quem não chegou a registrar não é histórico, é ruído. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProspeccaoParecerEntity p where p.prospeccaoId = :id and p.rodada = :rodada and p.posicao is null")
    int apagarAguardando(@Param("id") UUID prospeccaoId, @Param("rodada") Integer rodada);
}
