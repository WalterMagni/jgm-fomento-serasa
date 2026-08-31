package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ShareholderCompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareholderCompanyJpaRepository extends JpaRepository<ShareholderCompanyEntity, UUID> {

    List<ShareholderCompanyEntity> findByCnpjRaiz(String cnpjRaiz);

    List<ShareholderCompanyEntity> findByShareholderId(UUID shareholderId);

    List<ShareholderCompanyEntity> findByShareholderIdIn(List<UUID> shareholderIds);

    Optional<ShareholderCompanyEntity> findByShareholderIdAndCnpjRaiz(UUID shareholderId, String cnpjRaiz);

    @Query("select max(sc.fetchedAt) from ShareholderCompanyEntity sc where sc.cnpjRaiz = :raiz")
    Optional<LocalDateTime> findLastFetchedAt(@Param("raiz") String raiz);

    /**
     * Raízes que compartilham ao menos um sócio com a raiz informada — o cruzamento que
     * revela grupo econômico de fato (e, entre cedente e sacado, possível triangulação).
     */
    @Query("""
            select distinct other.cnpjRaiz
            from ShareholderCompanyEntity mine, ShareholderCompanyEntity other
            where mine.cnpjRaiz = :raiz
              and other.shareholderId = mine.shareholderId
              and other.cnpjRaiz <> mine.cnpjRaiz
            """)
    List<String> findRelatedRoots(@Param("raiz") String raiz);
}
