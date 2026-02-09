package com.portal.serasa.application.service;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.infrastructure.integration.cnpja.CnpjApiClient;
import com.portal.serasa.infrastructure.integration.cnpja.CompanyDetailDtoMapper;
import com.portal.serasa.infrastructure.integration.cnpja.dto.CompanyDetailDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        CompanyDetailDto dto = cnpjApiClient.consultarCnpj(documentNumber);
        if (dto == null) {
            throw new EntityNotFoundException("CNPJ não encontrado na API CNPJ Já: " + documentNumber);
        }

        CompanyDetail companyDetail = dtoMapper.toDomain(dto);

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

    private String normalizeCnpj(String cnpj) {
        if (cnpj == null || cnpj.isBlank()) {
            return null;
        }
        String digits = cnpj.replaceAll("\\D", "");
        return digits.length() >= 14 ? digits.substring(0, 14) : digits;
    }
}
