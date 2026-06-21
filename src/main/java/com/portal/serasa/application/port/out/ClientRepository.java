package com.portal.serasa.application.port.out;

import com.portal.serasa.domain.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository {

    Client save(Client client);

    Optional<Client> findById(UUID id);

    Optional<Client> findByDocumentNumber(String documentNumber);

    Optional<Client> findByClientCode(String clientCode);

    Page<Client> findAll(Pageable pageable);

    Page<Client> search(String term, Pageable pageable);

    Page<Client> searchProfiles(String term, String visaoCedente, String analysisStatus, String origin, Pageable pageable);

    long count();

    List<String> findExistingDocumentNumbers(Collection<String> documentNumbers);

    List<Client> findByDocumentNumberStartingWith(String documentRoot);

    void deleteById(UUID id);

    void deleteByDocumentNumber(String documentNumber);
}
