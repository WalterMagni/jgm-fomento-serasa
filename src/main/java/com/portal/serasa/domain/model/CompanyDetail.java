package com.portal.serasa.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDetail {

    private UUID id;
    private String documentNumber;
    private LocalDateTime updatedAt;
    private String alias;
    private LocalDate founded;
    private Boolean head;
    private LocalDate statusDate;
    private Integer statusId;
    private String statusText;
    private String companyName;
    private Long companyId;
    private BigDecimal companyEquity;
    private Integer natureId;
    private String natureText;
    private String sizeAcronym;
    private String sizeText;
    private String street;
    private String number;
    private String details;
    private String district;
    private String city;
    private String state;
    private String zip;
    private Integer countryId;
    private String countryName;
    private Double latitude;
    private Double longitude;
    private List<Map<String, Object>> members;
    private List<Map<String, Object>> phones;
    private List<Map<String, Object>> emails;
    private Map<String, Object> mainActivity;
    private List<Map<String, Object>> sideActivities;
    private String rawJson;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
