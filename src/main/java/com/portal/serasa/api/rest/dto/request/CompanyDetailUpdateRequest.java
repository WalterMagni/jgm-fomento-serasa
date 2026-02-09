package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class CompanyDetailUpdateRequest {

    @Size(max = 500)
    private String companyName;

    @Size(max = 255)
    private String alias;

    private LocalDate founded;

    private Boolean head;

    private LocalDate statusDate;

    private Integer statusId;

    @Size(max = 100)
    private String statusText;

    private BigDecimal companyEquity;

    private Integer natureId;

    @Size(max = 255)
    private String natureText;

    @Size(max = 50)
    private String sizeAcronym;

    @Size(max = 100)
    private String sizeText;

    @Size(max = 255)
    private String street;

    @Size(max = 50)
    private String number;

    @Size(max = 255)
    private String details;

    @Size(max = 255)
    private String district;

    @Size(max = 255)
    private String city;

    @Size(max = 10)
    private String state;

    @Size(max = 20)
    private String zip;

    private Integer countryId;

    @Size(max = 100)
    private String countryName;

    private Double latitude;

    private Double longitude;

    private List<Map<String, Object>> members;

    private List<Map<String, Object>> phones;

    private List<Map<String, Object>> emails;

    private Map<String, Object> mainActivity;

    private List<Map<String, Object>> sideActivities;
}
