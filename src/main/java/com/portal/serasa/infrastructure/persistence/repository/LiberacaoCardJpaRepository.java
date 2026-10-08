package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiberacaoCardJpaRepository extends JpaRepository<LiberacaoCardEntity, UUID> {

    Optional<LiberacaoCardEntity> findByIdAndExcluidoEmIsNull(UUID id);

    /**
     * Cards do quadro: todo card aberto e os finalizados a partir de uma data.
     *
     * <p>Sem o corte, Aprovado e Reprovado cresceriam para sempre e o quadro inteiro ficaria
     * lento só para desenhar colunas que ninguém rola.</p>
     */
    @Query("""
            select c from LiberacaoCardEntity c
            where c.excluidoEm is null
              and (c.finalizadoEm is null or c.finalizadoEm >= :finalizadosDesde)
            """)
    List<LiberacaoCardEntity> listarQuadro(@Param("finalizadosDesde") LocalDateTime finalizadosDesde);

    @Query("""
            select c.id from LiberacaoCardEntity c
            where c.excluidoEm is null and c.etapa in :etapas
            """)
    List<UUID> idsAbertosNasEtapas(@Param("etapas") List<EtapaLiberacao> etapas);
}
