package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.LiberacaoSacadoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LiberacaoSacadoJpaRepository extends JpaRepository<LiberacaoSacadoEntity, UUID> {

    List<LiberacaoSacadoEntity> findByCardIdOrderByOrdem(UUID cardId);

    List<LiberacaoSacadoEntity> findByCardIdIn(Collection<UUID> cardIds);

    /**
     * Decisões já tomadas sobre estes documentos, em qualquer card que não foi apagado, da mais
     * recente para a mais antiga. É o "este sacado já foi reprovado antes?".
     */
    @Query("""
            select s, c from LiberacaoSacadoEntity s, LiberacaoCardEntity c
            where s.cardId = c.id and c.excluidoEm is null and s.situacao is not null and s.cnpj in :documentos
            order by s.situacaoEm desc
            """)
    List<Object[]> decisoesAnteriores(@Param("documentos") Collection<String> documentos);

    /** Delete em lote antes de regravar: o índice único (card, cnpj) não pode ver as duas versões. */
    @Modifying(flushAutomatically = true)
    @Query("delete from LiberacaoSacadoEntity s where s.cardId = :cardId")
    void apagarDoCard(@Param("cardId") UUID cardId);
}
