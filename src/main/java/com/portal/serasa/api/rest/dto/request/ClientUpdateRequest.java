package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ClientUpdateRequest {

    @Size(max = 500)
    private String name;

    @Size(max = 255)
    private String email;

    private List<String> phones;
}
