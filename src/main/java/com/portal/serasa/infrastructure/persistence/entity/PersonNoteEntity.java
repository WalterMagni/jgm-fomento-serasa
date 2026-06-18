package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "person_notes")
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonNoteEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 11)
    private String cpf;

    @Column(name = "author_user_id")
    private UUID authorUserId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "author_email", nullable = false)
    private String authorEmail;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "parent_note_id")
    private UUID parentNoteId;

    @Column(name = "attachment_file_name")
    private String attachmentFileName;

    @Column(name = "attachment_content_type")
    private String attachmentContentType;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "attachment_data", columnDefinition = "bytea")
    private byte[] attachmentData;

    @org.springframework.data.annotation.CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @org.springframework.data.annotation.LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
