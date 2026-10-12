package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiberacaoCardJpaRepository extends JpaRepository<LiberacaoCardEntity, UUID> {

    Optional<LiberacaoCardEntity> findByIdAndExcluidoEmIsNull(UUID id);

    /**
     * Cards do quadro. Arquivado só com o filtro: é o arquivamento que impede Finalizados de
     * crescer para sempre e deixar o quadro lento.
     */
    @Query("""
            select c from LiberacaoCardEntity c
            where c.excluidoEm is null
              and (:incluirArquivados = true or c.arquivadoEm is null)
            """)
    List<LiberacaoCardEntity> listarQuadro(@Param("incluirArquivados") boolean incluirArquivados);

    @Query("""
            select c from LiberacaoCardEntity c
            where c.excluidoEm is null and c.arquivadoEm is null
              and c.etapa = com.portal.serasa.domain.model.liberacao.EtapaLiberacao.FINALIZADO
            """)
    List<LiberacaoCardEntity> finalizadosNaoArquivados();

    /**
     * Cor e etiquetas mudam por consulta direta, sem passar pela versão do card: são ações
     * rápidas, ortogonais aos campos, e não podem invalidar o formulário aberto de outra pessoa.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update liberacao_card set cor = :cor where id = :id", nativeQuery = true)
    void definirCor(@Param("id") UUID id, @Param("cor") String cor);

    /** Registra quem mexeu por último, também sem tocar na versão. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update LiberacaoCardEntity c
            set c.atualizadoEm = :quando, c.atualizadoPorId = :autorId, c.atualizadoPorNome = :autorNome
            where c.id = :id
            """)
    void tocarSemVersao(@Param("id") UUID id, @Param("quando") LocalDateTime quando,
                        @Param("autorId") UUID autorId, @Param("autorNome") String autorNome);

    /** Cards em aberto com prazo vencido: o "atrasado" da esteira, igual para todo mundo. */
    @Query("""
            select c.id from LiberacaoCardEntity c
            where c.excluidoEm is null and c.finalizadoEm is null
              and c.prazo is not null and c.prazo < :agora
            """)
    List<UUID> idsAtrasados(@Param("agora") LocalDateTime agora);

    /** Tipos que algum card em uso tem: é daqui que saem os tipos personalizados do time. */
    @Query("""
            select distinct c.tipoOperacao from LiberacaoCardEntity c
            where c.excluidoEm is null and c.tipoOperacao is not null
            """)
    List<String> tiposUsados();

    @Query("""
            select c.id from LiberacaoCardEntity c
            where c.excluidoEm is null and c.etapa in :etapas
            """)
    List<UUID> idsAbertosNasEtapas(@Param("etapas") List<EtapaLiberacao> etapas);
}
