package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMotivoReprovacaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LiberacaoMotivoReprovacaoJpaRepository extends JpaRepository<LiberacaoMotivoReprovacaoEntity, UUID> {

    List<LiberacaoMotivoReprovacaoEntity> findAllByOrderByOrdemAscNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);
}
