package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.NotificacaoEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificacaoJpaRepository extends JpaRepository<NotificacaoEntity, UUID> {

    List<NotificacaoEntity> findByDestinatarioIdOrderByCriadaEmDesc(UUID destinatarioId, Pageable pagina);

    long countByDestinatarioIdAndLidaEmIsNull(UUID destinatarioId);

    @Modifying
    @Query("update NotificacaoEntity n set n.lidaEm = :agora where n.destinatarioId = :destinatarioId and n.lidaEm is null")
    int marcarTodasLidas(@Param("destinatarioId") UUID destinatarioId, @Param("agora") LocalDateTime agora);

    /** Abrir o card resolve tudo o que apontava para ele: quem abriu já viu. */
    @Modifying
    @Query("update NotificacaoEntity n set n.lidaEm = :agora where n.destinatarioId = :destinatarioId and n.lidaEm is null and n.link = :link")
    int marcarLidasDoLink(@Param("destinatarioId") UUID destinatarioId, @Param("link") String link, @Param("agora") LocalDateTime agora);
}
