package com.portal.serasa.application.service;

import com.portal.serasa.api.rest.dto.request.CompanyCommercialInformationRequest;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialInformationAttachmentEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialInformationEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialInformationAttachmentJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialInformationAttachmentJpaRepository.AttachmentInfo;
import com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialInformationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyCommercialInformationService {

    static final long MAX_ATTACHMENT_SIZE = 5L * 1024 * 1024;
    static final int MAX_ATTACHMENTS_PER_RECORD = 10;
    /** PDF e imagem que o navegador mostra. SVG fica de fora: pode carregar script. */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp");
    private static final Map<String, String> TYPE_BY_EXTENSION = Map.of(
            "pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "gif", "image/gif", "webp", "image/webp");

    private final ClientJpaRepository clientJpaRepository;
    private final CompanyCommercialInformationJpaRepository commercialInformationJpaRepository;
    private final CompanyCommercialInformationAttachmentJpaRepository attachmentJpaRepository;

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

    @Transactional(readOnly = true)
    public Map<UUID, List<AttachmentInfo>> attachmentsByRecord(Collection<UUID> recordIds) {
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        return attachmentJpaRepository.findInfoByRecordIdIn(recordIds).stream()
                .collect(Collectors.groupingBy(AttachmentInfo::getRecordId));
    }

    @Transactional
    public CompanyCommercialInformationEntity addAttachments(String cnpj, UUID recordId, List<MultipartFile> files, UserEntity currentUser) {
        CompanyCommercialInformationEntity record = getRecord(cnpj, recordId);
        assertCanManage(record, currentUser);
        List<MultipartFile> received = files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (received.isEmpty()) {
            throw new IllegalArgumentException("Nenhum arquivo enviado");
        }
        if (attachmentJpaRepository.countByRecordId(recordId) + received.size() > MAX_ATTACHMENTS_PER_RECORD) {
            throw new IllegalArgumentException("Cada informação comercial aceita no máximo " + MAX_ATTACHMENTS_PER_RECORD + " anexos");
        }
        List<CompanyCommercialInformationAttachmentEntity> attachments = received.stream()
                .map(file -> CompanyCommercialInformationAttachmentEntity.builder()
                        .recordId(recordId)
                        .fileName(file.getOriginalFilename())
                        .contentType(acceptedType(file))
                        .fileSize(file.getSize())
                        .data(readBytes(file))
                        .createdAt(LocalDateTime.now())
                        .build())
                .toList();
        attachmentJpaRepository.saveAll(attachments);
        return record;
    }

    @Transactional
    public void deleteAttachment(String cnpj, UUID recordId, UUID attachmentId, UserEntity currentUser) {
        CompanyCommercialInformationEntity record = getRecord(cnpj, recordId);
        assertCanManage(record, currentUser);
        attachmentJpaRepository.delete(getAttachmentEntity(recordId, attachmentId));
    }

    @Transactional(readOnly = true)
    public CompanyCommercialInformationAttachmentEntity getAttachment(String cnpj, UUID recordId, UUID attachmentId) {
        getRecord(cnpj, recordId);
        return getAttachmentEntity(recordId, attachmentId);
    }

    private CompanyCommercialInformationAttachmentEntity getAttachmentEntity(UUID recordId, UUID attachmentId) {
        return attachmentJpaRepository.findByIdAndRecordId(attachmentId, recordId)
                .orElseThrow(() -> new EntityNotFoundException("Anexo não encontrado"));
    }

    private CompanyCommercialInformationEntity getRecord(String cnpj, UUID recordId) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyCommercialInformationEntity record = commercialInformationJpaRepository.findById(recordId)
                .orElseThrow(() -> new EntityNotFoundException("Informação comercial não encontrada"));
        if (!record.getClientId().equals(client.getId())) {
            throw new EntityNotFoundException("Informação comercial não encontrada para esta empresa");
        }
        return record;
    }

    /** O tipo que o navegador mandou; sem ele (ou genérico), o da extensão. Fora de PDF e imagem, recusa. */
    private String acceptedType(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "arquivo" : file.getOriginalFilename();
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new IllegalArgumentException("\"" + name + "\" passa de 5 MB");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) {
            int dot = name.lastIndexOf('.');
            type = dot < 0 ? "" : TYPE_BY_EXTENSION.getOrDefault(name.substring(dot + 1).toLowerCase(Locale.ROOT), "");
        }
        if (type.isEmpty()) {
            throw new IllegalArgumentException("\"" + name + "\" não é PDF nem imagem (PNG, JPG, GIF ou WEBP)");
        }
        return type;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível ler o anexo enviado");
        }
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
