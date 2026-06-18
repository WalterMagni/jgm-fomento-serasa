package com.portal.serasa.application.service;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.PersonAnalysisEntity;
import com.portal.serasa.infrastructure.persistence.entity.PersonNoteAttachmentEntity;
import com.portal.serasa.infrastructure.persistence.entity.PersonNoteEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.PersonAnalysisJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.PersonNoteAttachmentJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.PersonNoteJpaRepository;
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
public class PersonNoteService {

    private final PersonAnalysisJpaRepository personAnalysisJpaRepository;
    private final PersonNoteJpaRepository personNoteJpaRepository;
    private final PersonNoteAttachmentJpaRepository personNoteAttachmentJpaRepository;

    @Transactional(readOnly = true)
    public List<PersonNoteView> listByCpf(String cpf, UserEntity currentUser) {
      ensurePersonExists(cpf);
      List<PersonNoteEntity> notes = personNoteJpaRepository.findByCpfOrderByCreatedAtDesc(cpf);
      Map<UUID, PersonNoteEntity> notesById = notes.stream()
              .collect(Collectors.toMap(PersonNoteEntity::getId, Function.identity()));
      Map<UUID, List<PersonNoteAttachmentEntity>> attachmentsByNoteId = personNoteAttachmentJpaRepository
              .findByNoteIdIn(notes.stream().map(PersonNoteEntity::getId).toList())
              .stream()
              .collect(Collectors.groupingBy(PersonNoteAttachmentEntity::getNoteId));

      return notes.stream()
              .map(note -> toView(note, currentUser, notesById.get(note.getParentNoteId()), attachmentsByNoteId.get(note.getId())))
              .toList();
    }

    @Transactional
    public PersonNoteView create(String cpf, String content, UUID parentNoteId, List<MultipartFile> files, UserEntity currentUser) {
        ensurePersonExists(cpf);
        PersonNoteEntity parentNote = null;
        if (parentNoteId != null) {
            parentNote = personNoteJpaRepository.findById(parentNoteId)
                    .orElseThrow(() -> new EntityNotFoundException("Anotação citada não encontrada"));
            if (!parentNote.getCpf().equals(cpf)) {
                throw new IllegalArgumentException("A anotação citada não pertence a esta pessoa");
            }
        }

        validateAttachments(files);

        PersonNoteEntity saved = personNoteJpaRepository.save(
                PersonNoteEntity.builder()
                        .cpf(cpf)
                        .authorUserId(currentUser.getId())
                        .authorName(currentUser.getName())
                        .authorEmail(currentUser.getEmail())
                        .content(content.trim())
                        .parentNoteId(parentNote != null ? parentNote.getId() : null)
                        .build()
        );

        List<PersonNoteAttachmentEntity> attachments = normalizeFiles(files).stream()
                .map(file -> PersonNoteAttachmentEntity.builder()
                        .noteId(saved.getId())
                        .fileName(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .fileSize(file.getSize())
                        .data(readAttachmentBytes(file))
                        .createdAt(java.time.LocalDateTime.now())
                        .build())
                .map(personNoteAttachmentJpaRepository::save)
                .toList();

        return toView(saved, currentUser, parentNote, attachments);
    }

    @Transactional
    public void delete(String cpf, UUID noteId, UserEntity currentUser) {
        ensurePersonExists(cpf);
        PersonNoteEntity note = personNoteJpaRepository.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException("Anotação não encontrada"));

        if (!note.getCpf().equals(cpf)) {
            throw new EntityNotFoundException("Anotação não encontrada para esta pessoa");
        }

        if (note.getAuthorUserId() == null || !note.getAuthorUserId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Você só pode apagar anotações feitas por você");
        }

        personNoteJpaRepository.delete(note);
    }

    @Transactional(readOnly = true)
    public PersonNoteAttachmentView getAttachment(String cpf, UUID noteId, UUID attachmentId) {
        ensurePersonExists(cpf);
        PersonNoteEntity note = personNoteJpaRepository.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException("Anotação não encontrada"));

        if (!note.getCpf().equals(cpf)) {
            throw new EntityNotFoundException("Anotação não encontrada para esta pessoa");
        }

        if (attachmentId != null) {
            PersonNoteAttachmentEntity attachment = personNoteAttachmentJpaRepository.findById(attachmentId)
                    .orElseThrow(() -> new EntityNotFoundException("Anexo não encontrado para esta anotação"));
            if (!attachment.getNoteId().equals(note.getId())) {
                throw new EntityNotFoundException("Anexo não encontrado para esta anotação");
            }
            return PersonNoteAttachmentView.builder()
                    .id(attachment.getId())
                    .fileName(attachment.getFileName())
                    .contentType(attachment.getContentType())
                    .content(attachment.getData())
                    .build();
        }

        if (note.getAttachmentData() != null && note.getAttachmentData().length > 0) {
            return PersonNoteAttachmentView.builder()
                    .fileName(note.getAttachmentFileName())
                    .contentType(note.getAttachmentContentType())
                    .content(note.getAttachmentData())
                    .build();
        }

        PersonNoteAttachmentEntity firstAttachment = personNoteAttachmentJpaRepository.findByNoteIdOrderByCreatedAtAsc(note.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Anexo não encontrado para esta anotação"));

        return PersonNoteAttachmentView.builder()
                .id(firstAttachment.getId())
                .fileName(firstAttachment.getFileName())
                .contentType(firstAttachment.getContentType())
                .content(firstAttachment.getData())
                .build();
    }

    private PersonAnalysisEntity ensurePersonExists(String cpf) {
        return personAnalysisJpaRepository.findFirstByCpfOrderByConsultaEmDesc(cpf)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada para CPF: " + cpf));
    }

    private PersonNoteView toView(PersonNoteEntity note, UserEntity currentUser, PersonNoteEntity parentNote, List<PersonNoteAttachmentEntity> attachments) {
        List<PersonNoteAttachmentItemView> attachmentViews = buildAttachmentViews(note, attachments);
        return PersonNoteView.builder()
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

    private List<PersonNoteAttachmentItemView> buildAttachmentViews(PersonNoteEntity note, List<PersonNoteAttachmentEntity> attachments) {
        List<PersonNoteAttachmentItemView> attachmentViews = (attachments == null ? List.<PersonNoteAttachmentEntity>of() : attachments).stream()
                .sorted(Comparator.comparing(PersonNoteAttachmentEntity::getCreatedAt))
                .map(attachment -> PersonNoteAttachmentItemView.builder()
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

        return List.of(PersonNoteAttachmentItemView.builder()
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
    public static class PersonNoteView {
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
        private List<PersonNoteAttachmentItemView> attachments;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class PersonNoteAttachmentView {
        private UUID id;
        private String fileName;
        private String contentType;
        private byte[] content;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class PersonNoteAttachmentItemView {
        private UUID id;
        private String fileName;
        private String contentType;
        private Long fileSize;
    }
}
