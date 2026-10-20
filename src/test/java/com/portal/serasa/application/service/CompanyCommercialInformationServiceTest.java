package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialInformationAttachmentEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialInformationEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialInformationAttachmentJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialInformationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyCommercialInformationServiceTest {

    private static final String CNPJ = "11222333000181";

    @Mock
    private ClientJpaRepository clientJpaRepository;
    @Mock
    private CompanyCommercialInformationJpaRepository commercialInformationJpaRepository;
    @Mock
    private CompanyCommercialInformationAttachmentJpaRepository attachmentJpaRepository;
    @InjectMocks
    private CompanyCommercialInformationService service;

    private final UserEntity author = UserEntity.builder().id(UUID.randomUUID()).name("Autora").build();
    private final UUID recordId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ClientEntity client = ClientEntity.builder().id(UUID.randomUUID()).documentNumber(CNPJ).build();
        CompanyCommercialInformationEntity record = CompanyCommercialInformationEntity.builder()
                .id(recordId).clientId(client.getId()).authorUserId(author.getId()).build();
        lenient().when(clientJpaRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.of(client));
        lenient().when(commercialInformationJpaRepository.findById(recordId)).thenReturn(Optional.of(record));
    }

    private static MultipartFile file(String name, String type, int size) {
        return new MockMultipartFile("files", name, type, new byte[size]);
    }

    @Test
    @DisplayName("anexar: PDF e imagem entram; tipo genérico cai na extensão")
    @SuppressWarnings("unchecked")
    void shouldStorePdfAndImages() {
        service.addAttachments(CNPJ, recordId, List.of(
                file("ar.pdf", "application/pdf", 10),
                file("print.png", "image/png", 10),
                file("extrato.PDF", "application/octet-stream", 10)), author);

        ArgumentCaptor<List<CompanyCommercialInformationAttachmentEntity>> salvos = ArgumentCaptor.forClass(List.class);
        verify(attachmentJpaRepository).saveAll(salvos.capture());
        assertThat(salvos.getValue()).extracting(CompanyCommercialInformationAttachmentEntity::getContentType)
                .containsExactly("application/pdf", "image/png", "application/pdf");
        assertThat(salvos.getValue()).allSatisfy(anexo -> assertThat(anexo.getRecordId()).isEqualTo(recordId));
    }

    @Test
    @DisplayName("anexar: arquivo acima de 5 MB é recusado")
    void shouldRejectFileOver5Mb() {
        MultipartFile grande = file("grande.pdf", "application/pdf", (int) CompanyCommercialInformationService.MAX_ATTACHMENT_SIZE + 1);

        assertThatThrownBy(() -> service.addAttachments(CNPJ, recordId, List.of(grande), author))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB");
        verify(attachmentJpaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("anexar: fora de PDF e imagem (inclusive SVG) é recusado")
    void shouldRejectOtherTypes() {
        assertThatThrownBy(() -> service.addAttachments(CNPJ, recordId, List.of(file("planilha.xlsx", "application/vnd.ms-excel", 10)), author))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addAttachments(CNPJ, recordId, List.of(file("logo.svg", "image/svg+xml", 10)), author))
                .isInstanceOf(IllegalArgumentException.class);
        verify(attachmentJpaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("anexar: só quem criou a ficha anexa")
    void shouldRejectNonAuthor() {
        UserEntity outra = UserEntity.builder().id(UUID.randomUUID()).build();

        assertThatThrownBy(() -> service.addAttachments(CNPJ, recordId, List.of(file("ar.pdf", "application/pdf", 10)), outra))
                .isInstanceOf(IllegalArgumentException.class);
        verify(attachmentJpaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("anexar: passa do limite de anexos por ficha")
    void shouldRejectOverLimit() {
        when(attachmentJpaRepository.countByRecordId(recordId))
                .thenReturn((long) CompanyCommercialInformationService.MAX_ATTACHMENTS_PER_RECORD);

        assertThatThrownBy(() -> service.addAttachments(CNPJ, recordId, List.of(file("ar.pdf", "application/pdf", 10)), author))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no máximo");
    }
}
