package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProspeccaoJpaRepository extends JpaRepository<ProspeccaoEntity, UUID> {

    /** Card em aberto de um CNPJ. O índice parcial da V56 garante que seja no máximo um. */
    @Query("""
            SELECT p FROM ProspeccaoEntity p
             WHERE p.cnpj = :cnpj
               AND p.estagio NOT IN (
                   com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.REPROVADO,
                   com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.REMOVIDO_RADAR,
                   com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.PRONTO_HABILITACAO)
            """)
    Optional<ProspeccaoEntity> findAberta(@Param("cnpj") String cnpj);

    List<ProspeccaoEntity> findByCnpjOrderByCreatedAtDesc(String cnpj);

    List<ProspeccaoEntity> findByEstagioOrderByEstagioDesdeAsc(EstagioProspeccao estagio);

    List<ProspeccaoEntity> findByClosedAtIsNullOrderByEstagioDesdeAsc();

    /**
     * Abertos mais os encerrados recentemente.
     *
     * <p>A tela mostra reprovados e removidos do radar numa seção à parte, e o relatório precisa
     * deles para a coluna de motivo da recusa fazer sentido. O corte por data evita que a lista
     * cresça para sempre — desfecho de um ano atrás é assunto de relatório, não de esteira.</p>
     */
    @Query("""
            SELECT p FROM ProspeccaoEntity p
             WHERE p.closedAt IS NULL OR p.closedAt >= :desde
             ORDER BY p.estagioDesde ASC
            """)
    List<ProspeccaoEntity> findAbertasEEncerradasDesde(@Param("desde") LocalDateTime desde);

    /**
     * Assume a análise sem corrida: o analista que chegar primeiro grava o próprio id, e a
     * segunda tentativa não afeta linha nenhuma, devolvendo 0. O default é que o analista puxe
     * da fila, e não que alguém distribua — ver os defaults de 2026-09-28.
     */
    // clearAutomatically é obrigatório aqui: o UPDATE em JPQL não passa pelo contexto de
    // persistência, então uma leitura posterior na mesma transação devolveria a entidade antiga
    // em cache — o card apareceria como não assumido logo depois de ser assumido.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProspeccaoEntity p
               SET p.analistaId = :analistaId,
                   p.estagio = com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.EM_ANALISE,
                   p.estagioDesde = :agora,
                   p.prazoEstagioDias = :prazo,
                   p.updatedAt = :agora
             WHERE p.id = :id
               AND p.analistaId IS NULL
               AND p.estagio = com.portal.serasa.domain.model.prospeccao.EstagioProspeccao.TRIAGEM
            """)
    int assumirAnalise(@Param("id") UUID id,
                       @Param("analistaId") UUID analistaId,
                       @Param("prazo") int prazo,
                       @Param("agora") LocalDateTime agora);

    /** Cards parados além do prazo, para o job de SLA. Dias úteis são contados fora daqui. */
    @Query("""
            SELECT p FROM ProspeccaoEntity p
             WHERE p.closedAt IS NULL
               AND p.prazoEstagioDias > 0
             ORDER BY p.estagioDesde ASC
            """)
    List<ProspeccaoEntity> findAbertasComPrazo();

    boolean existsByCreditAnalysisId(Long creditAnalysisId);

    long countByEstagio(EstagioProspeccao estagio);
}
