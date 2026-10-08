package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.LiberacaoParecerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiberacaoParecerJpaRepository extends JpaRepository<LiberacaoParecerEntity, UUID> {

    List<LiberacaoParecerEntity> findByCardIdOrderByRodadaDescCriadoEm(UUID cardId);

    List<LiberacaoParecerEntity> findByCardIdAndRodadaOrderByCriadoEm(UUID cardId, Integer rodada);

    Optional<LiberacaoParecerEntity> findByCardIdAndRodadaAndUsuarioId(UUID cardId, Integer rodada, UUID usuarioId);

    /** Pareceres da rodada vigente de cada card, numa consulta só para o quadro. */
    @Query("""
            select p from LiberacaoParecerEntity p, LiberacaoCardEntity c
            where p.cardId = c.id and p.rodada = c.rodada and c.id in :cardIds
            order by p.criadoEm
            """)
    List<LiberacaoParecerEntity> rodadaVigente(@Param("cardIds") Collection<UUID> cardIds);

    /** Rodada que ficou para trás: o que ninguém chegou a registrar não é histórico, é ruído. */
    @Modifying(flushAutomatically = true)
    @Query("delete from LiberacaoParecerEntity p where p.cardId = :cardId and p.rodada = :rodada and p.posicao is null")
    int apagarAguardando(@Param("cardId") UUID cardId, @Param("rodada") Integer rodada);

    /**
     * Quem deixa o Comitê não pode continuar travando card. Apaga o parecer que ela ainda devia
     * nos cards que estão no Comitê agora.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from LiberacaoParecerEntity p
            where p.usuarioId = :usuarioId and p.posicao is null
              and p.cardId in (select c.id from LiberacaoCardEntity c
                               where c.etapa = com.portal.serasa.domain.model.liberacao.EtapaLiberacao.COMITE
                                 and c.rodada = p.rodada and c.excluidoEm is null)
            """)
    int apagarAguardandoDoUsuario(@Param("usuarioId") UUID usuarioId);

    /** Cards no Comitê esperando o parecer desta pessoa. */
    @Query("""
            select p.cardId from LiberacaoParecerEntity p, LiberacaoCardEntity c
            where p.cardId = c.id and p.rodada = c.rodada and p.usuarioId = :usuarioId
              and p.posicao is null and c.excluidoEm is null
              and c.etapa = com.portal.serasa.domain.model.liberacao.EtapaLiberacao.COMITE
            """)
    List<UUID> cardsAguardando(@Param("usuarioId") UUID usuarioId);

    /** Pareceres que esperam por esta pessoa, em cards que estão no Comitê. */
    @Query("""
            select count(p) from LiberacaoParecerEntity p, LiberacaoCardEntity c
            where p.cardId = c.id and p.rodada = c.rodada and p.usuarioId = :usuarioId
              and p.posicao is null and c.excluidoEm is null
              and c.etapa = com.portal.serasa.domain.model.liberacao.EtapaLiberacao.COMITE
            """)
    long contarAguardando(@Param("usuarioId") UUID usuarioId);
}
