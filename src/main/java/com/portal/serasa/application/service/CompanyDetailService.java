package com.portal.serasa.application.service;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.infrastructure.integration.cnpja.CnpjApiClient;
import com.portal.serasa.infrastructure.integration.cnpja.CompanyDetailDtoMapper;
import com.portal.serasa.infrastructure.integration.cnpja.dto.CnpjApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyDetailService {

    private final CnpjApiClient cnpjApiClient;
    private final CompanyDetailDtoMapper dtoMapper;
    private final com.portal.serasa.application.port.out.CompanyDetailRepository companyDetailRepository;

    /**
     * Enriquece os dados da empresa consultando a API CNPJ Já e persiste no banco.
     * Se já existir registro para o CNPJ, atualiza com os novos dados.
     */
    public CompanyDetail enrichByCnpj(String cnpj) {
        String documentNumber = normalizeCnpj(cnpj);
        if (documentNumber == null || documentNumber.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter 14 dígitos numéricos");
        }

        log.info("Enriquecendo dados da empresa CNPJ: {}", documentNumber);

        CnpjApiResponse response = cnpjApiClient.consultarCnpj(documentNumber);
        if (response == null || response.dto() == null) {
            throw new EntityNotFoundException("CNPJ não encontrado na API CNPJ Já: " + documentNumber);
        }

        CompanyDetail companyDetail = dtoMapper.toDomain(response.dto(), response.rawJson());

        var existing = companyDetailRepository.findByDocumentNumber(documentNumber);
        if (existing.isPresent()) {
            companyDetail.setId(existing.get().getId());
            companyDetail.setCreatedAt(existing.get().getCreatedAt());
        }

        return companyDetailRepository.save(companyDetail);
    }

    public Optional<CompanyDetail> findByDocumentNumber(String documentNumber) {
        return companyDetailRepository.findByDocumentNumber(normalizeCnpj(documentNumber));
    }

    public CompanyDetail create(CompanyDetail companyDetail) {
        String doc = normalizeCnpj(companyDetail.getDocumentNumber());
        if (doc == null || doc.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter 14 dígitos numéricos");
        }
        if (companyDetailRepository.existsByDocumentNumber(doc)) {
            throw new IllegalArgumentException("Já existe empresa cadastrada com CNPJ: " + doc);
        }
        companyDetail.setDocumentNumber(doc);
        return companyDetailRepository.save(companyDetail);
    }

    public CompanyDetail update(String documentNumber, CompanyDetail updates) {
        String doc = normalizeCnpj(documentNumber);
        CompanyDetail existing = companyDetailRepository.findByDocumentNumber(doc)
                .orElseThrow(() -> new EntityNotFoundException("Empresa não encontrada para CNPJ: " + doc));
        applyUpdates(existing, updates);
        return companyDetailRepository.save(existing);
    }

    public void deleteByDocumentNumber(String documentNumber) {
        String doc = normalizeCnpj(documentNumber);
        if (!companyDetailRepository.existsByDocumentNumber(doc)) {
            throw new EntityNotFoundException("Empresa não encontrada para CNPJ: " + doc);
        }
        companyDetailRepository.deleteByDocumentNumber(doc);
    }

    public Page<CompanyDetail> findAll(Pageable pageable) {
        return companyDetailRepository.findAll(pageable);
    }

    private void applyUpdates(CompanyDetail target, CompanyDetail source) {
        if (source.getCompanyName() != null) target.setCompanyName(source.getCompanyName());
        if (source.getAlias() != null) target.setAlias(source.getAlias());
        if (source.getFounded() != null) target.setFounded(source.getFounded());
        if (source.getHead() != null) target.setHead(source.getHead());
        if (source.getStatusDate() != null) target.setStatusDate(source.getStatusDate());
        if (source.getStatusId() != null) target.setStatusId(source.getStatusId());
        if (source.getStatusText() != null) target.setStatusText(source.getStatusText());
        if (source.getCompanyEquity() != null) target.setCompanyEquity(source.getCompanyEquity());
        if (source.getNatureId() != null) target.setNatureId(source.getNatureId());
        if (source.getNatureText() != null) target.setNatureText(source.getNatureText());
        if (source.getSizeAcronym() != null) target.setSizeAcronym(source.getSizeAcronym());
        if (source.getSizeText() != null) target.setSizeText(source.getSizeText());
        if (source.getStreet() != null) target.setStreet(source.getStreet());
        if (source.getNumber() != null) target.setNumber(source.getNumber());
        if (source.getDetails() != null) target.setDetails(source.getDetails());
        if (source.getDistrict() != null) target.setDistrict(source.getDistrict());
        if (source.getCity() != null) target.setCity(source.getCity());
        if (source.getState() != null) target.setState(source.getState());
        if (source.getZip() != null) target.setZip(source.getZip());
        if (source.getCountryId() != null) target.setCountryId(source.getCountryId());
        if (source.getCountryName() != null) target.setCountryName(source.getCountryName());
        if (source.getLatitude() != null) target.setLatitude(source.getLatitude());
        if (source.getLongitude() != null) target.setLongitude(source.getLongitude());
        if (source.getMembers() != null) target.setMembers(source.getMembers());
        if (source.getPhones() != null) target.setPhones(source.getPhones());
        if (source.getEmails() != null) target.setEmails(source.getEmails());
        if (source.getMainActivity() != null) target.setMainActivity(source.getMainActivity());
        if (source.getSideActivities() != null) target.setSideActivities(source.getSideActivities());
    }

    private String normalizeCnpj(String cnpj) {
        if (cnpj == null || cnpj.isBlank()) {
            return null;
        }
        String digits = cnpj.replaceAll("\\D", "");
        return digits.length() >= 14 ? digits.substring(0, 14) : digits;
    }
}
