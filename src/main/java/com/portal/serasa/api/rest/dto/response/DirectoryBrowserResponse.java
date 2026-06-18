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
public class DirectoryBrowserResponse {
    private boolean configured;
    private String basePath;
    private String currentPath;
    private String parentPath;
    private List<DirectoryItem> directories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectoryItem {
        private String name;
        private String path;
        private LocalDateTime modifiedAt;
    }
}
