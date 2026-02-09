package com.portal.serasa.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    private UUID id;
    private String documentNumber;
    private String name;
    private String email;
    private List<String> phones;
    private LocalDateTime createdAt;
}
