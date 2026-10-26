package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.domain.model.ServicoJudit;
import com.portal.serasa.domain.model.SituacaoConsultaJudit;
import com.portal.serasa.infrastructure.persistence.entity.JuditConsultaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JuditConsultaJpaRepository extends JpaRepository<JuditConsultaEntity, UUID> {

    Optional<JuditConsultaEntity> findFirstByDocumentoAndServicoOrderByCriadoEmDesc(String documento, ServicoJudit servico);

    Optional<JuditConsultaEntity> findFirstByDocumentoAndCnjAndServicoOrderByCriadoEmDesc(
            String documento, String cnj, ServicoJudit servico);

    /** Linha travada: o acompanhamento e o "verificar agora" não concluem a mesma consulta duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from JuditConsultaEntity c where c.id = :id")
    Optional<JuditConsultaEntity> travar(@Param("id") UUID id);

    /** Situação dos processos detalhados de uma empresa, sem carregar as respostas. Mais recente primeiro. */
    interface DetalheResumido {
        String getCnj();

        SituacaoConsultaJudit getSituacao();
    }

    @Query("""
            select c.cnj as cnj, c.situacao as situacao from JuditConsultaEntity c
            where c.documento = :documento and c.servico = com.portal.serasa.domain.model.ServicoJudit.DETALHE
            order by c.criadoEm desc""")
    List<DetalheResumido> detalhesDa(@Param("documento") String documento);

    @Query("select coalesce(sum(c.custo), 0) from JuditConsultaEntity c where c.criadoEm >= :inicio")
    BigDecimal somarCustoDesde(@Param("inicio") LocalDateTime inicio);

    /** Pedidos pendentes recentes, para o acompanhamento. São poucos: não precisa de projeção. */
    List<JuditConsultaEntity> findBySituacaoAndCriadoEmAfterOrderByCriadoEmAsc(
            SituacaoConsultaJudit situacao, LocalDateTime desde);

    @Modifying
    @Query("update JuditConsultaEntity c set c.verificadoEm = :quando where c.id = :id")
    void marcarVerificado(@Param("id") UUID id, @Param("quando") LocalDateTime quando);

    /** Linha do painel de consumo, sem a resposta (a lista inteira pode passar de 1 MB). */
    interface ConsultaDoPainel {
        UUID getId();

        String getDocumento();

        String getNomeEmpresa();

        ServicoJudit getServico();

        String getCnj();

        SituacaoConsultaJudit getSituacao();

        BigDecimal getCusto();

        Integer getTotalProcessos();

        String getUsuarioNome();

        LocalDateTime getCriadoEm();
    }

    @Query("""
            select c.id as id, c.documento as documento, c.nomeEmpresa as nomeEmpresa, c.servico as servico,
                   c.cnj as cnj, c.situacao as situacao, c.custo as custo, c.totalProcessos as totalProcessos,
                   c.usuarioNome as usuarioNome, c.criadoEm as criadoEm
            from JuditConsultaEntity c where c.criadoEm >= :inicio order by c.criadoEm desc""")
    List<ConsultaDoPainel> doPainelDesde(@Param("inicio") LocalDateTime inicio);

    /**
     * Trava de transação do Postgres: duas consultas ao mesmo tempo esperam uma pela outra, para que
     * as duas não passem pela conferência do limite com a mesma soma. Solta sozinha no commit.
     */
    @Query(value = "SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(:chave)) trava", nativeQuery = true)
    long travarLimite(@Param("chave") long chave);
}
