package com.portal.serasa.application.service;

import com.portal.serasa.api.rest.dto.request.CompanyCommercialInformationRequest;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialReferenceFileEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialInformationEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialReferenceFileJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialReferenceFileJpaRepository.ReferenceInfo;
import com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialInformationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyCommercialInformationService {

    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    /** PDF e imagem que o navegador mostra. SVG fica de fora: pode carregar script. */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp");
    private static final Map<String, String> TYPE_BY_EXTENSION = Map.of(
            "pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "gif", "image/gif", "webp", "image/webp");

    private final ClientJpaRepository clientJpaRepository;
    private final CompanyCommercialInformationJpaRepository commercialInformationJpaRepository;
    private final CompanyCommercialReferenceFileJpaRepository referenceFileJpaRepository;

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

    // ---------------------------------------------- colinha (arquivo de referência)

    /** O arquivo de referência da empresa, sem os bytes. Vazio quando ninguém enviou. */
    @Transactional(readOnly = true)
    public Optional<ReferenceInfo> referenceInfo(String cnpj) {
        return referenceFileJpaRepository.findInfoByClientId(getClientByCnpj(cnpj).getId());
    }

    /**
     * Grava o arquivo de referência da empresa — a "colinha" que o time consulta ao preencher as
     * fichas. É um por empresa: enviar outro substitui o anterior. Qualquer usuário do portal envia,
     * e fica dito quem e quando.
     */
    @Transactional
    public ReferenceInfo replaceReference(String cnpj, MultipartFile file, UserEntity currentUser) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Escolha um arquivo PDF ou imagem");
        }
        ClientEntity client = getClientByCnpj(cnpj);
        referenceFileJpaRepository.save(CompanyCommercialReferenceFileEntity.builder()
                .clientId(client.getId())
                .fileName(file.getOriginalFilename())
                .contentType(acceptedType(file))
                .fileSize(file.getSize())
                .data(readBytes(file))
                .uploadedByName(currentUser.getName())
                .uploadedAt(LocalDateTime.now())
                .build());
        return referenceFileJpaRepository.findInfoByClientId(client.getId())
                .orElseThrow(() -> new IllegalStateException("Arquivo de referência não foi gravado"));
    }

    @Transactional(readOnly = true)
    public CompanyCommercialReferenceFileEntity getReference(String cnpj) {
        return referenceFileJpaRepository.findById(getClientByCnpj(cnpj).getId())
                .orElseThrow(() -> new EntityNotFoundException("Nenhum arquivo de referência para esta empresa"));
    }

    @Transactional
    public void deleteReference(String cnpj) {
        UUID clientId = getClientByCnpj(cnpj).getId();
        if (referenceFileJpaRepository.existsById(clientId)) {
            referenceFileJpaRepository.deleteById(clientId);
        }
    }

    /** O tipo que o navegador mandou; sem ele (ou genérico), o da extensão. Fora de PDF e imagem, recusa. */
    private String acceptedType(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "arquivo" : file.getOriginalFilename();
        if (file.getSize() > MAX_FILE_SIZE) {
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
            throw new IllegalArgumentException("Não foi possível ler o arquivo enviado");
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
