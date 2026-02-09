package com.portal.serasa.infrastructure.integration.cnpja.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompanyDetailDto(
        LocalDateTime updated,
        String taxId,
        CompanyInfo company,
        String alias,
        LocalDate founded,
        Boolean head,
        LocalDate statusDate,
        StatusInfo status,
        AddressInfo address,
        List<PhoneDto> phones,
        List<EmailDto> emails,
        ActivityDto mainActivity,
        List<ActivityDto> sideActivities
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanyInfo(
            Long id,
            String name,
            BigDecimal equity,
            NatureInfo nature,
            SizeInfo size,
            List<MemberDto> members
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NatureInfo(Integer id, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SizeInfo(Integer id, String acronym, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MemberDto(
            String since,
            RoleInfo role,
            PersonInfo person
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoleInfo(Integer id, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersonInfo(
            String id,
            String name,
            String type,
            String taxId,
            String age
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusInfo(Integer id, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CountryInfo(Integer id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddressInfo(
            Integer municipality,
            String street,
            String number,
            String details,
            String district,
            String city,
            String state,
            String zip,
            CountryInfo country,
            Double latitude,
            Double longitude
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhoneDto(String type, String area, String number) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmailDto(String ownership, String address, String domain) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivityDto(Long id, String text) {}
}
