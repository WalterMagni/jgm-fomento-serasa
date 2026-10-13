package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.RedefinicaoSenhaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedefinicaoSenhaRepository extends JpaRepository<RedefinicaoSenhaEntity, UUID> {

    Optional<RedefinicaoSenhaEntity> findByTokenHash(String tokenHash);

    long countByUserIdAndCriadoEmAfter(UUID userId, Instant desde);

    /** Cancela os links ainda abertos do usuário: só o mais novo (ou nenhum) vale. */
    @Modifying
    @Query("update RedefinicaoSenhaEntity r set r.usadoEm = :agora where r.userId = :userId and r.usadoEm is null")
    int encerrarAbertos(@Param("userId") UUID userId, @Param("agora") Instant agora);
}
