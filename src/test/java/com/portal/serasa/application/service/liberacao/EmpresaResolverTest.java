package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.infrastructure.persistence.entity.PersonAnalysisEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmpresaResolverTest {

    @Mock private CompanyDetailRepository companyDetailRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private com.portal.serasa.infrastructure.persistence.repository.PersonAnalysisJpaRepository personAnalysisRepository;
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
    @DisplayName("CPF sem consulta Serasa PF fica de fora e não consulta as bases de empresa")
    void shouldSkipCpf() {
        assertThat(resolver.resolver(List.of("52998224725"))).isEmpty();
        verify(companyDetailRepository, never()).findByDocumentNumberIn(anyCollection());
    }

    @Test
    @DisplayName("CPF já consultado no Serasa PF: nome e cidade da última consulta, conta como cadastrado")
    void shouldResolveConsultedCpf() throws Exception {
        when(personAnalysisRepository.findLatestByCpfIn(List.of("52998224725"))).thenReturn(List.of(pessoa("52998224725", "MARIA DA SILVA")));

        var pessoa = resolver.resolver(List.of("52998224725")).get("52998224725");

        assertThat(pessoa.nome()).isEqualTo("MARIA DA SILVA");
        assertThat(pessoa.praca()).isEqualTo("Bauru/SP");
        assertThat(pessoa.cadastrada()).isTrue();
    }

    @Test
    @DisplayName("buscar: empresas primeiro, depois uma pessoa física por CPF, com no máximo cinco")
    void shouldSearchCompaniesThenPeopleOncePerCpf() throws Exception {
        when(companyDetailRepository.searchByNameOrDocument("silva", 8)).thenReturn(List.of(
                CompanyDetail.builder().documentNumber("11222333000181").companyName("SILVA TRANSPORTES LTDA").city("Bauru").state("SP").build()));
        List<PersonAnalysisEntity> pessoas = new java.util.ArrayList<>();
        pessoas.add(pessoa("52998224725", "MARIA DA SILVA"));
        pessoas.add(pessoa("52998224725", "MARIA DA SILVA")); // consulta antiga do mesmo CPF
        for (int i = 0; i < 6; i++) pessoas.add(pessoa("1111111111" + i, "SILVA " + i));
        when(personAnalysisRepository.findByCpfContainingIgnoreCaseOrPersonNameContainingIgnoreCase(
                eq("###sem-digitos###"), eq("silva"), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(pessoas));

        var encontrados = resolver.buscar(" silva ");

        assertThat(encontrados).hasSize(6);
        assertThat(encontrados.get(0)).isEqualTo(new EmpresaResolver.Encontrado("11222333000181", "SILVA TRANSPORTES LTDA", "Bauru/SP", false));
        assertThat(encontrados.get(1)).isEqualTo(new EmpresaResolver.Encontrado("52998224725", "MARIA DA SILVA", "Bauru/SP", true));
        assertThat(encontrados.stream().filter(EmpresaResolver.Encontrado::pessoaFisica)).hasSize(5);
    }

    @Test
    @DisplayName("buscar: termo com menos de dois caracteres não vai ao banco")
    void shouldIgnoreShortTerm() {
        assertThat(resolver.buscar("a")).isEmpty();
        assertThat(resolver.buscar(null)).isEmpty();
        verify(companyDetailRepository, never()).searchByNameOrDocument(anyString(), anyInt());
    }

    private static PersonAnalysisEntity pessoa(String cpf, String nome) throws Exception {
        return PersonAnalysisEntity.builder()
                .cpf(cpf)
                .personName(nome)
                .registration(new ObjectMapper().readTree("{\"address\":{\"city\":\"Bauru\",\"state\":\"SP\"}}"))
                .build();
    }
}
