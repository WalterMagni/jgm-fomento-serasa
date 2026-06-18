package com.portal.serasa.application.service;

import com.portal.serasa.api.rest.dto.request.CompanyCommercialInformationRequest;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialInformationEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialInformationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyCommercialInformationService {

    private final ClientJpaRepository clientJpaRepository;
    private final CompanyCommercialInformationJpaRepository commercialInformationJpaRepository;

    @Transactional(readOnly = true)
    public List<CompanyCommercialInformationEntity> listByCnpj(String cnpj) {
        ClientEntity client = getClientByCnpj(cnpj);
        return commercialInformationJpaRepository.findByClientIdOrderByCreatedAtDesc(client.getId());
    }

    @Transactional(readOnly = true)
    public List<CompanyCommercialInformationView> listAll() {
        List<CompanyCommercialInformationEntity> records = commercialInformationJpaRepository.findAllByOrderByCreatedAtDesc();
        Map<UUID, ClientEntity> clientsById = clientJpaRepository.findAllById(
                        records.stream().map(CompanyCommercialInformationEntity::getClientId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ClientEntity::getId, Function.identity()));

        return records.stream()
                .map(record -> CompanyCommercialInformationView.builder()
                        .record(record)
                        .client(clientsById.get(record.getClientId()))
                        .build())
                .toList();
    }

    @Transactional
    public CompanyCommercialInformationEntity create(String cnpj, CompanyCommercialInformationRequest request, UserEntity currentUser) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyCommercialInformationEntity entity = CompanyCommercialInformationEntity.builder()
                .clientId(client.getId())
                .authorUserId(currentUser.getId())
                .authorName(currentUser.getName())
                .authorEmail(currentUser.getEmail())
                .updatedByUserId(currentUser.getId())
                .build();
        applyRequest(entity, request);
        return commercialInformationJpaRepository.save(entity);
    }

    @Transactional
    public CompanyCommercialInformationEntity update(String cnpj, UUID id, CompanyCommercialInformationRequest request, UserEntity currentUser) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyCommercialInformationEntity entity = commercialInformationJpaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Informação comercial não encontrada"));
        if (!entity.getClientId().equals(client.getId())) {
            throw new EntityNotFoundException("Informação comercial não encontrada para esta empresa");
        }
        assertCanManage(entity, currentUser);

        entity.setUpdatedByUserId(currentUser.getId());
        applyRequest(entity, request);
        return commercialInformationJpaRepository.save(entity);
    }

    @Transactional
    public void delete(String cnpj, UUID id, UserEntity currentUser) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyCommercialInformationEntity entity = commercialInformationJpaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Informação comercial não encontrada"));
        if (!entity.getClientId().equals(client.getId())) {
            throw new EntityNotFoundException("Informação comercial não encontrada para esta empresa");
        }
        assertCanManage(entity, currentUser);
        commercialInformationJpaRepository.delete(entity);
    }

    private ClientEntity getClientByCnpj(String cnpj) {
        return clientJpaRepository.findByDocumentNumber(cnpj)
                .orElseThrow(() -> new EntityNotFoundException("Empresa não encontrada para CNPJ: " + cnpj));
    }

    private void applyRequest(CompanyCommercialInformationEntity entity, CompanyCommercialInformationRequest request) {
        entity.setOperationDate(clean(request.getData()));
        entity.setOperationType(clean(request.getTipo()));
        entity.setPartner(clean(request.getParceiro()));
        entity.setCustomerSince(clean(request.getClienteDesde()));
        entity.setLastOperationDate(clean(request.getUltimaOperacaoData()));
        entity.setLastOperationValue(clean(request.getUltimaOperacaoValor()));
        entity.setCreditLimit(clean(request.getLimite()));
        entity.setDuplicateRisk(clean(request.getRiscoDuplicata()));
        entity.setCheckRisk(clean(request.getRiscoCheque()));
        entity.setCommissionRisk(clean(request.getRiscoComissaria()));
        entity.setOverdueDate(clean(request.getVencidosData()));
        entity.setOverdueValue(clean(request.getVencidosValorMonetario()));
        entity.setDuplicateDueDate(clean(request.getVencidosValor()));
        entity.setVop(clean(request.getVop()));
        entity.setPunctualPercentage(clean(request.getPontual()));
        entity.setDelayPercentage(clean(request.getAtraso()));
        entity.setRepurchasePercentage(clean(request.getRecompra()));
        entity.setNotaryPercentage(clean(request.getCartorio()));
        entity.setNotes(clean(request.getObservacao()));
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void assertCanManage(CompanyCommercialInformationEntity entity, UserEntity currentUser) {
        if (entity.getAuthorUserId() == null || !entity.getAuthorUserId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Você só pode editar ou apagar informações comerciais criadas por você");
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class CompanyCommercialInformationView {
        private CompanyCommercialInformationEntity record;
        private ClientEntity client;
    }
}
