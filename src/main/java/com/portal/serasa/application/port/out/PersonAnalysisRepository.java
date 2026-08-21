package com.portal.serasa.application.port.out;

import com.portal.serasa.domain.model.PersonAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PersonAnalysisRepository {

    PersonAnalysis save(PersonAnalysis personAnalysis);

    Optional<PersonAnalysis> findLatestByCpf(String cpf);

    List<PersonAnalysis> findByCpf(String cpf);

    /**
     * Retorna a análise mais recente para cada CPF da lista.
     * Retorna Map<CPF, PersonAnalysis>.
     */
    Map<String, PersonAnalysis> findLatestByCpfIn(Collection<String> cpfs);

    Page<PersonAnalysis> findAll(Pageable pageable);

    Page<PersonAnalysis> search(String term, Pageable pageable);

    void deleteByCpf(String cpf);
}
