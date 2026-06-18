package com.portal.serasa.application.service;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyNoteAttachmentEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyNoteEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyNoteAttachmentJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyNoteJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyNoteService {

    private final ClientJpaRepository clientJpaRepository;
    private final CompanyNoteJpaRepository companyNoteJpaRepository;
    private final CompanyNoteAttachmentJpaRepository companyNoteAttachmentJpaRepository;

    @Transactional(readOnly = true)
    public List<CompanyNoteView> listByCnpj(String cnpj, UserEntity currentUser) {
        ClientEntity client = getClientByCnpj(cnpj);
        List<CompanyNoteEntity> notes = companyNoteJpaRepository.findByClientIdOrderByCreatedAtDesc(client.getId());
        Map<UUID, CompanyNoteEntity> notesById = notes.stream()
                .collect(Collectors.toMap(CompanyNoteEntity::getId, Function.identity()));
        Map<UUID, List<CompanyNoteAttachmentEntity>> attachmentsByNoteId = companyNoteAttachmentJpaRepository
                .findByNoteIdIn(notes.stream().map(CompanyNoteEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(CompanyNoteAttachmentEntity::getNoteId));

        return notes.stream()
                .map(note -> toView(note, currentUser, notesById.get(note.getParentNoteId()), attachmentsByNoteId.get(note.getId())))
                .toList();
    }

    @Transactional
    public CompanyNoteView create(String cnpj, String content, UUID parentNoteId, List<MultipartFile> files, UserEntity currentUser) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyNoteEntity parentNote = null;
        if (parentNoteId != null) {
            parentNote = companyNoteJpaRepository.findById(parentNoteId)
                    .orElseThrow(() -> new EntityNotFoundException("Anotação citada não encontrada"));
            if (!parentNote.getClientId().equals(client.getId())) {
                throw new IllegalArgumentException("A anotação citada não pertence a esta empresa");
            }
        }

        validateAttachments(files);

        CompanyNoteEntity saved = companyNoteJpaRepository.save(
                CompanyNoteEntity.builder()
                        .clientId(client.getId())
                        .authorUserId(currentUser.getId())
                        .authorName(currentUser.getName())
                        .authorEmail(currentUser.getEmail())
                        .content(content.trim())
                        .parentNoteId(parentNote != null ? parentNote.getId() : null)
                        .build()
        );

        List<CompanyNoteAttachmentEntity> attachments = normalizeFiles(files).stream()
                .map(file -> CompanyNoteAttachmentEntity.builder()
                        .noteId(saved.getId())
                        .fileName(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .fileSize(file.getSize())
                        .data(readAttachmentBytes(file))
                        .createdAt(java.time.LocalDateTime.now())
                        .build())
                .map(companyNoteAttachmentJpaRepository::save)
                .toList();

        return toView(saved, currentUser, parentNote, attachments);
    }

    @Transactional
    public void delete(String cnpj, UUID noteId, UserEntity currentUser) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyNoteEntity note = getEditableNote(client, noteId, currentUser);

        companyNoteJpaRepository.delete(note);
    }

    @Transactional
    public CompanyNoteView update(String cnpj, UUID noteId, String content, UserEntity currentUser) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyNoteEntity note = getEditableNote(client, noteId, currentUser);

        if (content == null || content.trim().isBlank()) {
            throw new IllegalArgumentException("A anotação não pode ficar em branco");
        }

        if (content.trim().length() > 5000) {
            throw new IllegalArgumentException("A anotação pode ter no máximo 5000 caracteres");
        }

        note.setContent(content.trim());
        CompanyNoteEntity saved = companyNoteJpaRepository.save(note);
        CompanyNoteEntity parentNote = saved.getParentNoteId() != null
                ? companyNoteJpaRepository.findById(saved.getParentNoteId()).orElse(null)
                : null;
        List<CompanyNoteAttachmentEntity> attachments = companyNoteAttachmentJpaRepository.findByNoteIdOrderByCreatedAtAsc(saved.getId());
        return toView(saved, currentUser, parentNote, attachments);
    }

    @Transactional
    public CompanyNoteView addAttachments(String cnpj, UUID noteId, List<MultipartFile> files, UserEntity currentUser) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyNoteEntity note = getEditableNote(client, noteId, currentUser);

        validateAttachments(files);
        List<CompanyNoteAttachmentEntity> newAttachments = normalizeFiles(files).stream()
                .map(file -> CompanyNoteAttachmentEntity.builder()
                        .noteId(note.getId())
                        .fileName(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .fileSize(file.getSize())
                        .data(readAttachmentBytes(file))
                        .createdAt(java.time.LocalDateTime.now())
                        .build())
                .map(companyNoteAttachmentJpaRepository::save)
                .toList();

        List<CompanyNoteAttachmentEntity> attachments = companyNoteAttachmentJpaRepository.findByNoteIdOrderByCreatedAtAsc(note.getId());
        CompanyNoteEntity parentNote = note.getParentNoteId() != null
                ? companyNoteJpaRepository.findById(note.getParentNoteId()).orElse(null)
                : null;
        return toView(note, currentUser, parentNote, attachments.isEmpty() ? newAttachments : attachments);
    }

    @Transactional
    public void deleteAttachment(String cnpj, UUID noteId, UUID attachmentId, UserEntity currentUser) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyNoteEntity note = getEditableNote(client, noteId, currentUser);

        if (attachmentId == null) {
            if (note.getAttachmentData() == null || note.getAttachmentData().length == 0) {
                throw new EntityNotFoundException("Anexo não encontrado para esta anotação");
            }
            note.setAttachmentFileName(null);
            note.setAttachmentContentType(null);
            note.setAttachmentSize(null);
            note.setAttachmentData(null);
            companyNoteJpaRepository.save(note);
            return;
        }

        CompanyNoteAttachmentEntity attachment = companyNoteAttachmentJpaRepository.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("Anexo não encontrado para esta anotação"));
        if (!attachment.getNoteId().equals(note.getId())) {
            throw new EntityNotFoundException("Anexo não encontrado para esta anotação");
        }
        companyNoteAttachmentJpaRepository.delete(attachment);
    }

    @Transactional(readOnly = true)
    public CompanyNoteAttachmentView getAttachment(String cnpj, UUID noteId, UUID attachmentId) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyNoteEntity note = companyNoteJpaRepository.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException("Anotação não encontrada"));

        if (!note.getClientId().equals(client.getId())) {
            throw new EntityNotFoundException("Anotação não encontrada para esta empresa");
        }

        if (attachmentId != null) {
            CompanyNoteAttachmentEntity attachment = companyNoteAttachmentJpaRepository.findById(attachmentId)
                    .orElseThrow(() -> new EntityNotFoundException("Anexo não encontrado para esta anotação"));
            if (!attachment.getNoteId().equals(note.getId())) {
                throw new EntityNotFoundException("Anexo não encontrado para esta anotação");
            }
            return CompanyNoteAttachmentView.builder()
                    .id(attachment.getId())
                    .fileName(attachment.getFileName())
                    .contentType(attachment.getContentType())
                    .content(attachment.getData())
                    .build();
        }

        if (note.getAttachmentData() != null && note.getAttachmentData().length > 0) {
            return CompanyNoteAttachmentView.builder()
                    .fileName(note.getAttachmentFileName())
                    .contentType(note.getAttachmentContentType())
                    .content(note.getAttachmentData())
                    .build();
        }

        CompanyNoteAttachmentEntity firstAttachment = companyNoteAttachmentJpaRepository.findByNoteIdOrderByCreatedAtAsc(note.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Anexo não encontrado para esta anotação"));

        return CompanyNoteAttachmentView.builder()
                .id(firstAttachment.getId())
                .fileName(firstAttachment.getFileName())
                .contentType(firstAttachment.getContentType())
                .content(firstAttachment.getData())
                .build();
    }

    private ClientEntity getClientByCnpj(String cnpj) {
        return clientJpaRepository.findByDocumentNumber(cnpj)
                .orElseThrow(() -> new EntityNotFoundException("Empresa não encontrada para CNPJ: " + cnpj));
    }

    private CompanyNoteEntity getEditableNote(ClientEntity client, UUID noteId, UserEntity currentUser) {
        CompanyNoteEntity note = companyNoteJpaRepository.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException("Anotação não encontrada"));

        if (!note.getClientId().equals(client.getId())) {
            throw new EntityNotFoundException("Anotação não encontrada para esta empresa");
        }

        if (note.getAuthorUserId() == null || !note.getAuthorUserId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Você só pode alterar anotações feitas por você");
        }

        return note;
    }

    private CompanyNoteView toView(CompanyNoteEntity note, UserEntity currentUser, CompanyNoteEntity parentNote, List<CompanyNoteAttachmentEntity> attachments) {
        List<CompanyNoteAttachmentItemView> attachmentViews = buildAttachmentViews(note, attachments);
        return CompanyNoteView.builder()
                .id(note.getId())
                .content(note.getContent())
                .authorName(note.getAuthorName())
                .authorEmail(note.getAuthorEmail())
                .createdAt(note.getCreatedAt())
                .canDelete(note.getAuthorUserId() != null && note.getAuthorUserId().equals(currentUser.getId()))
                .repliedToId(parentNote != null ? parentNote.getId() : null)
                .repliedToAuthorName(parentNote != null ? parentNote.getAuthorName() : null)
                .repliedToContent(parentNote != null ? parentNote.getContent() : null)
                .hasAttachment(!attachmentViews.isEmpty())
                .attachmentFileName(attachmentViews.isEmpty() ? note.getAttachmentFileName() : attachmentViews.get(0).getFileName())
                .attachmentContentType(attachmentViews.isEmpty() ? note.getAttachmentContentType() : attachmentViews.get(0).getContentType())
                .attachmentSize(attachmentViews.isEmpty() ? note.getAttachmentSize() : attachmentViews.get(0).getFileSize())
                .attachments(attachmentViews)
                .build();
    }

    private void validateAttachments(List<MultipartFile> files) {
        for (MultipartFile file : normalizeFiles(files)) {
            if (file.getSize() > 5 * 1024 * 1024) {
                throw new IllegalArgumentException("Cada anexo deve ter no máximo 5 MB");
            }
        }
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
        if (files == null) return List.of();
        return files.stream().filter(this::hasAttachment).toList();
    }

    private List<CompanyNoteAttachmentItemView> buildAttachmentViews(CompanyNoteEntity note, List<CompanyNoteAttachmentEntity> attachments) {
        List<CompanyNoteAttachmentItemView> attachmentViews = (attachments == null ? List.<CompanyNoteAttachmentEntity>of() : attachments).stream()
                .sorted(Comparator.comparing(CompanyNoteAttachmentEntity::getCreatedAt))
                .map(attachment -> CompanyNoteAttachmentItemView.builder()
                        .id(attachment.getId())
                        .fileName(attachment.getFileName())
                        .contentType(attachment.getContentType())
                        .fileSize(attachment.getFileSize())
                        .build())
                .toList();

        if (!attachmentViews.isEmpty()) {
            return attachmentViews;
        }

        if (note.getAttachmentData() == null || note.getAttachmentData().length == 0) {
            return List.of();
        }

        return List.of(CompanyNoteAttachmentItemView.builder()
                .id(null)
                .fileName(note.getAttachmentFileName())
                .contentType(note.getAttachmentContentType())
                .fileSize(note.getAttachmentSize())
                .build());
    }

    private boolean hasAttachment(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private byte[] readAttachmentBytes(MultipartFile file) {
        if (!hasAttachment(file)) {
            return null;
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível ler o anexo enviado");
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class CompanyNoteView {
        private UUID id;
        private String content;
        private String authorName;
        private String authorEmail;
        private java.time.LocalDateTime createdAt;
        private boolean canDelete;
        private UUID repliedToId;
        private String repliedToAuthorName;
        private String repliedToContent;
        private boolean hasAttachment;
        private String attachmentFileName;
        private String attachmentContentType;
        private Long attachmentSize;
        private List<CompanyNoteAttachmentItemView> attachments;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class CompanyNoteAttachmentView {
        private UUID id;
        private String fileName;
        private String contentType;
        private byte[] content;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class CompanyNoteAttachmentItemView {
        private UUID id;
        private String fileName;
        private String contentType;
        private Long fileSize;
    }
}
