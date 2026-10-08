package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.LiberacaoPendenciaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LiberacaoPendenciaJpaRepository extends JpaRepository<LiberacaoPendenciaEntity, UUID> {

    List<LiberacaoPendenciaEntity> findByCardIdOrderByAbertaEm(UUID cardId);

    List<LiberacaoPendenciaEntity> findByCardIdInAndRespondidaEmIsNull(Collection<UUID> cardIds);

    long countByCardIdAndRespondidaEmIsNull(UUID cardId);

    @Query("""
            select distinct p.cardId from LiberacaoPendenciaEntity p, LiberacaoCardEntity c
            where p.cardId = c.id and p.destinatarioId = :usuarioId
              and p.respondidaEm is null and c.excluidoEm is null
              and c.etapa = com.portal.serasa.domain.model.liberacao.EtapaLiberacao.PENDENCIA
            """)
    List<UUID> cardsComPendenciaPara(@Param("usuarioId") UUID usuarioId);

    @Query("""
            select count(p) from LiberacaoPendenciaEntity p, LiberacaoCardEntity c
            where p.cardId = c.id and p.destinatarioId = :usuarioId
              and p.respondidaEm is null and c.excluidoEm is null
              and c.etapa = com.portal.serasa.domain.model.liberacao.EtapaLiberacao.PENDENCIA
            """)
    long contarAbertasPara(@Param("usuarioId") UUID usuarioId);
}
