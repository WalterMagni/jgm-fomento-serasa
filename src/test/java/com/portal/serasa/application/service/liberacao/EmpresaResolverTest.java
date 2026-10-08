package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.domain.model.CompanyDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmpresaResolverTest {

    @Mock private CompanyDetailRepository companyDetailRepository;
    @Mock private ClientRepository clientRepository;
    @InjectMocks private EmpresaResolver resolver;

    @Test
    @DisplayName("sacado consultado só no Serasa: nome e praça vêm de clients")
    void shouldUseClientsWhenOnlySerasaWasQueried() {
        when(companyDetailRepository.findByDocumentNumberIn(anyCollection())).thenReturn(List.of());
        when(clientRepository.findByDocumentNumberIn(anyCollection())).thenReturn(List.of(
                Client.builder().documentNumber("17888244000155").name("ALFA S/A").addressCity("Campinas").addressUf("SP").build()));

        var empresa = resolver.resolver(List.of("17888244000155")).get("17888244000155");

        assertThat(empresa.nome()).isEqualTo("ALFA S/A");
        assertThat(empresa.praca()).isEqualTo("Campinas/SP");
        assertThat(empresa.cadastrada()).isTrue();
    }

    @Test
    @DisplayName("CNPJ Já vence; clients só completa o que falta; nome provisório \"Cliente …\" é ignorado")
    void shouldPreferCnpjaAndIgnorePlaceholderName() {
        when(companyDetailRepository.findByDocumentNumberIn(anyCollection())).thenReturn(List.of(
                CompanyDetail.builder().documentNumber("11222333000181").companyName("ACME LTDA").build()));
        when(clientRepository.findByDocumentNumberIn(anyCollection())).thenReturn(List.of(
                Client.builder().documentNumber("11222333000181").name("Outro nome").addressCity("Bauru").addressUf("SP").build(),
                Client.builder().documentNumber("11444777000161").name("Cliente 11444777000161").build()));

        var empresas = resolver.resolver(List.of("11222333000181", "11444777000161"));

        assertThat(empresas.get("11222333000181").nome()).isEqualTo("ACME LTDA");
        assertThat(empresas.get("11222333000181").praca()).isEqualTo("Bauru/SP");
        assertThat(empresas.get("11444777000161").nome()).isNull();
        assertThat(empresas.get("11444777000161").cadastrada()).isTrue();
    }

    @Test
    @DisplayName("CPF e lista vazia não consultam nada")
    void shouldSkipCpf() {
        assertThat(resolver.resolver(List.of("52998224725"))).isEmpty();
        verify(companyDetailRepository, never()).findByDocumentNumberIn(anyCollection());
    }
}
