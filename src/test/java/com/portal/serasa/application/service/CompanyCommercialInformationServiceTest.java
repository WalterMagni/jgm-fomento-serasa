package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
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
    private com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialReferenceFileJpaRepository referenceFileJpaRepository;
    @InjectMocks
    private CompanyCommercialInformationService service;

    private final UserEntity author = UserEntity.builder().id(UUID.randomUUID()).name("Autora").build();

    private ClientEntity client;

    @BeforeEach
    void setUp() {
        client = ClientEntity.builder().id(UUID.randomUUID()).documentNumber(CNPJ).build();
        lenient().when(clientJpaRepository.findByDocumentNumber(CNPJ)).thenReturn(Optional.of(client));
    }

    private static MultipartFile file(String name, String type, int size) {
        return new MockMultipartFile("files", name, type, new byte[size]);
    }

    // ---------------------------------------------- colinha (arquivo de referência)

    @Test
    @DisplayName("colinha: grava um arquivo por empresa, com quem enviou; enviar outro grava por cima, na mesma chave")
    void shouldReplaceReferenceFile() {
        var info = org.mockito.Mockito.mock(
                com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialReferenceFileJpaRepository.ReferenceInfo.class);
        when(referenceFileJpaRepository.findInfoByClientId(client.getId())).thenReturn(Optional.of(info));

        service.replaceReference(CNPJ, file("dados-externos.pdf", "application/pdf", 10), author);
        service.replaceReference(CNPJ, file("print.png", "image/png", 10), author);
        // navegador que manda tipo genérico: vale a extensão
        service.replaceReference(CNPJ, file("extrato.PDF", "application/octet-stream", 10), author);

        ArgumentCaptor<com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialReferenceFileEntity> salvos =
                ArgumentCaptor.forClass(com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialReferenceFileEntity.class);
        verify(referenceFileJpaRepository, org.mockito.Mockito.times(3)).save(salvos.capture());
        assertThat(salvos.getAllValues()).allSatisfy(salvo -> {
            assertThat(salvo.getClientId()).isEqualTo(client.getId());
            assertThat(salvo.getUploadedByName()).isEqualTo("Autora");
            assertThat(salvo.getUploadedAt()).isNotNull();
        });
        assertThat(salvos.getAllValues()).extracting(
                        com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialReferenceFileEntity::getContentType)
                .containsExactly("application/pdf", "image/png", "application/pdf");
    }

    @Test
    @DisplayName("colinha: sem arquivo, acima de 5 MB ou fora de PDF e imagem é recusado, e nada é gravado")
    void shouldRefuseInvalidReferenceFile() {
        assertThatThrownBy(() -> service.replaceReference(CNPJ, null, author))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escolha um arquivo PDF ou imagem");
        assertThatThrownBy(() -> service.replaceReference(CNPJ, file("grande.pdf", "application/pdf", 5 * 1024 * 1024 + 1), author))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB");
        assertThatThrownBy(() -> service.replaceReference(CNPJ, file("logo.svg", "image/svg+xml", 10), author))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não é PDF nem imagem");
        verify(referenceFileJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("colinha: remover é idempotente")
    void shouldDeleteReferenceFileOnlyWhenPresent() {
        when(referenceFileJpaRepository.existsById(client.getId())).thenReturn(false, true);

        service.deleteReference(CNPJ);
        verify(referenceFileJpaRepository, never()).deleteById(any());

        service.deleteReference(CNPJ);
        verify(referenceFileJpaRepository).deleteById(client.getId());
    }
}
