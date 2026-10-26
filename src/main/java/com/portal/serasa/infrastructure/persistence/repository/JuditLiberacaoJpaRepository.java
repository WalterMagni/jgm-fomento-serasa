package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.JuditLiberacaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface JuditLiberacaoJpaRepository extends JpaRepository<JuditLiberacaoEntity, UUID> {

    @Query("select coalesce(sum(l.valor), 0) from JuditLiberacaoEntity l where l.mes = :mes")
    BigDecimal somarDoMes(@Param("mes") LocalDate mes);

    List<JuditLiberacaoEntity> findByMesOrderByCriadoEmDesc(LocalDate mes);
}
