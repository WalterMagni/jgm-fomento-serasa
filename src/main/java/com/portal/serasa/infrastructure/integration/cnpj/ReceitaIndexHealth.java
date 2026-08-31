package com.portal.serasa.infrastructure.integration.cnpj;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Confere se a cópia local do cadastro da Receita tem os índices de que o portal depende.
 *
 * <p>O banco da Receita é propriedade do <a
 * href="https://github.com/caiopizzol/cnpj-data-pipeline">cnpj-data-pipeline</a>, não da
 * aplicação: uma recarga pode recriar as tabelas e levar os índices junto. Sem
 * {@code idx_socios_cpf_nome} a busca reversa de sócios varre 27 milhões de linhas — passa de
 * ~25ms para ~2700ms por consulta e degrada em silêncio, sem erro nenhum.</p>
 *
 * <p>Esta classe só <b>detecta e avisa</b>. Criar índice é DDL num banco de outro dono, e o
 * pool daqui é read-only de propósito; a criação fica no script
 * {@code scripts/receita-indices.sql}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceitaIndexHealth {

    /** Índice → SQL de criação, para o aviso já sair acionável. */
    private static final Map<String, String> REQUIRED_INDEXES = Map.of(
            "idx_socios_cpf_nome",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_socios_cpf_nome ON socios (cnpj_cpf_do_socio, nome_socio);");

    private static final String QUERY = "SELECT indexname FROM pg_indexes WHERE tablename = 'socios'";

    private final ReceitaDataSourceProvider receita;

    /** Índices exigidos que estão faltando. Vazio também quando a base está indisponível. */
    public List<String> missingIndexes() {
        if (!receita.isAvailable()) {
            return List.of();
        }
        try {
            List<String> present = receita.template(200).queryForList(QUERY, String.class);
            return REQUIRED_INDEXES.keySet().stream()
                    .filter(name -> !present.contains(name))
                    .sorted()
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Nao foi possivel conferir os indices da base da Receita: {}", ex.getMessage());
            return List.of();
        }
    }

    /** Índice faltando → resultado correto, só lento. Por isso avisa alto, em vez de falhar. */
    public Map<String, String> missingIndexesWithFix() {
        Map<String, String> fixes = new LinkedHashMap<>();
        missingIndexes().forEach(name -> fixes.put(name, REQUIRED_INDEXES.get(name)));
        return fixes;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warnOnStartup() {
        Map<String, String> missing = missingIndexesWithFix();
        if (missing.isEmpty()) {
            return;
        }
        missing.forEach((name, sql) -> log.warn(
                "Indice ausente na base da Receita: {} — consultas de socios ficarao ~100x mais lentas. "
                        + "Rode em cnpj-pipeline-postgres: {}", name, sql));
    }
}
