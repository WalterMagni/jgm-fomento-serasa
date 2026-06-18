package com.portal.serasa.api.rest.dto.response;

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
public class CompanyNoteResponse {

    private UUID id;
    private String content;
    private String authorName;
    private String authorEmail;
    private LocalDateTime createdAt;
    private boolean canDelete;
    private UUID repliedToId;
    private String repliedToAuthorName;
    private String repliedToContent;
    private boolean hasAttachment;
    private String attachmentFileName;
    private String attachmentContentType;
    private Long attachmentSize;
    private List<CompanyNoteAttachmentResponse> attachments;
}
