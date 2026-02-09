package com.portal.serasa.application.port.out;

import com.portal.serasa.domain.model.Client;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository {

    Client save(Client client);

    Optional<Client> findByDocumentNumber(String documentNumber);
}
