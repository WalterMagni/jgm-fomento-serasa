package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDocumentsResponse {

    private boolean mapped;
    private String rootPath;
    private String currentPath;
    private String parentPath;
    private String mappedByName;
    private LocalDateTime mappedAt;
    private List<DocumentItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentItem {
        private String name;
        private String path;
        private String type;
        private String extension;
        private Long size;
        private LocalDateTime modifiedAt;
    }
}
