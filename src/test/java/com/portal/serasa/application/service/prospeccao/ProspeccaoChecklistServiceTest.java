package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
import com.portal.serasa.domain.model.prospeccao.PapelPessoa;
import com.portal.serasa.infrastructure.persistence.entity.DocumentoTipoEntity;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.StatusDocumento;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.repository.CompanyDetailJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.DocumentoTipoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDocumentoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ShareholderCompanyJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ShareholderJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProspeccaoChecklistServiceTest {

    @Mock private DocumentoTipoJpaRepository documentoTipoRepository;
    @Mock private ProspeccaoDocumentoJpaRepository documentoRepository;
    @Mock private ShareholderCompanyJpaRepository shareholderCompanyRepository;
    @Mock private ShareholderJpaRepository shareholderRepository;
    @Mock private CompanyDetailJpaRepository companyDetailRepository;

    @InjectMocks private ProspeccaoChecklistService service;

    private ProspeccaoEntity card() {
        return ProspeccaoEntity.builder()
                .id(UUID.randomUUID())
                .cnpj("11222333000181")
                .razaoSocial("ACME LTDA")
                .estagio(EstagioProspeccao.APROVADO)
                .estagioDesde(LocalDateTime.now())
                .prazoEstagioHoras(1)
                .build();
    }

    @Test
    @DisplayName("checklist já existente não é criado de novo — voltar para aprovado duplicaria tudo")
    void shouldNotDuplicateExistingChecklist() {
        ProspeccaoEntity card = card();
        ProspeccaoDocumentoEntity jaExiste = ProspeccaoDocumentoEntity.builder()
                .id(UUID.randomUUID())
                .prospeccaoId(card.getId())
                .codigoSnapshot("receita-federal")
                .nomeSnapshot("Receita Federal")
                .obrigatorioSnapshot(true)
                .informativoSnapshot(false)
                .escopo(EscopoDocumento.EMPRESA)
                .socioAtivo(true)
                .status(StatusDocumento.VALIDADO)
                .observacao("CRC válido")
                .atualizadoEm(LocalDateTime.now())
                .build();
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(jaExiste));

        List<ProspeccaoDocumentoEntity> resultado = service.materializar(card);

        assertThat(resultado).containsExactly(jaExiste);
        // O que já estava conferido continua conferido.
        assertThat(resultado.get(0).getStatus()).isEqualTo(StatusDocumento.VALIDADO);
        assertThat(resultado.get(0).getObservacao()).isEqualTo("CRC válido");
        verify(documentoRepository, never()).saveAll(any());
        verify(documentoTipoRepository, never()).findByAtivoTrueOrderByEscopoAscOrdemAsc();
    }

    @Test
    @DisplayName("avalista entra com os mesmos itens do sócio, e não vem do quadro societário")
    void shouldAddGuarantorBlock() {
        ProspeccaoEntity card = card();
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of());
        when(documentoTipoRepository.findByEscopoAndAtivoTrueOrderByOrdemAsc(EscopoDocumento.SOCIO))
                .thenReturn(List.of(tipoSocio("socio-identidade"), tipoSocio("socio-comprovante-endereco")));
        when(documentoRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ProspeccaoDocumentoEntity> itens = service.adicionarPessoa(
                card, "  Joana Ribeiro  ", "123.456.789-09", PapelPessoa.AVALISTA);

        assertThat(itens).hasSize(2);
        assertThat(itens).allSatisfy(item -> {
            assertThat(item.getPessoaPapel()).isEqualTo(PapelPessoa.AVALISTA);
            assertThat(item.getSocioNome()).isEqualTo("Joana Ribeiro");
            assertThat(item.getSocioDocumento()).isEqualTo("12345678909");
            assertThat(item.getEscopo()).isEqualTo(EscopoDocumento.SOCIO);
        });
    }

    @Test
    @DisplayName("mesma pessoa não entra duas vezes no checklist")
    void shouldRejectDuplicatePerson() {
        ProspeccaoEntity card = card();
        ProspeccaoDocumentoEntity existente = ProspeccaoDocumentoEntity.builder()
                .id(UUID.randomUUID()).prospeccaoId(card.getId())
                .codigoSnapshot("socio-identidade").nomeSnapshot("RG")
                .obrigatorioSnapshot(true).informativoSnapshot(false)
                .escopo(EscopoDocumento.SOCIO).socioNome("Joana Ribeiro").socioAtivo(true)
                .status(StatusDocumento.PENDENTE).atualizadoEm(LocalDateTime.now()).build();
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of(existente));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.adicionarPessoa(card, "joana ribeiro", null, PapelPessoa.AVALISTA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Já existe bloco");
    }

    @Test
    @DisplayName("avalista sem nome não entra")
    void shouldRequireName() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.adicionarPessoa(card(), "   ", null, PapelPessoa.AVALISTA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DocumentoTipoEntity tipoSocio(String codigo) {
        return DocumentoTipoEntity.builder()
                .id(UUID.randomUUID()).codigo(codigo).nome(codigo)
                .escopo(EscopoDocumento.SOCIO).obrigatorio(true).informativo(false)
                .admiteExcecao(false).ativo(true).ordem(1)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("card sem checklist materializa a partir do catálogo")
    void shouldBuildChecklistWhenEmpty() {
        ProspeccaoEntity card = card();
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of());
        when(documentoTipoRepository.findByAtivoTrueOrderByEscopoAscOrdemAsc()).thenReturn(List.of());
        when(documentoRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.materializar(card);

        verify(documentoTipoRepository).findByAtivoTrueOrderByEscopoAscOrdemAsc();
        verify(documentoRepository).saveAll(any());
    }
}
