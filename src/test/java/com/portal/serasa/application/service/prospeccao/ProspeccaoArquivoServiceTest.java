package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.application.service.SystemSettingService;
import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.StatusDocumento;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoArquivoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoArquivoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDocumentoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProspeccaoArquivoServiceTest {

    @Mock private ProspeccaoArquivoJpaRepository arquivoRepository;
    @Mock private ProspeccaoDocumentoJpaRepository documentoRepository;
    @Mock private ProspeccaoService prospeccaoService;
    @Mock private SystemSettingService systemSettingService;

    private ProspeccaoArquivoService service;

    @TempDir Path base;

    private ProspeccaoEntity card;
    private ProspeccaoDocumentoEntity documento;
    private final UserEntity autor = UserEntity.builder()
            .id(UUID.randomUUID()).name("Nicole").build();

    @BeforeEach
    void setUp() throws Exception {
        service = new ProspeccaoArquivoService(
                arquivoRepository, documentoRepository, prospeccaoService, systemSettingService);

        card = ProspeccaoEntity.builder()
                .id(UUID.randomUUID())
                .cnpj("11222333000181")
                .razaoSocial("ACME LTDA")
                .estagio(EstagioProspeccao.DOCS_PENDENTES)
                .estagioDesde(LocalDateTime.now())
                .prazoEstagioDias(10)
                .build();

        documento = ProspeccaoDocumentoEntity.builder()
                .id(UUID.randomUUID())
                .prospeccaoId(card.getId())
                .codigoSnapshot("balanco-dre")
                .nomeSnapshot("Balanço patrimonial e DRE")
                .obrigatorioSnapshot(true)
                .informativoSnapshot(false)
                .escopo(EscopoDocumento.EMPRESA)
                .socioAtivo(true)
                .status(StatusDocumento.PENDENTE)
                .atualizadoEm(LocalDateTime.now())
                .build();

        when(systemSettingService.requireDocumentStorageBasePath()).thenReturn(base.toRealPath());
        when(prospeccaoService.buscar(card.getId())).thenReturn(card);
        when(documentoRepository.findById(documento.getId())).thenReturn(Optional.of(documento));
        when(documentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(arquivoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(arquivoRepository.countByDocumentoId(any())).thenReturn(0L);
    }

    private MockMultipartFile pdf(String nome) {
        return new MockMultipartFile("file", nome, "application/pdf", "conteudo".getBytes());
    }

    @Test
    @DisplayName("enviar: grava na pasta do CNPJ e devolve caminho relativo à base")
    void shouldWriteUnderCnpjFolder() {
        ProspeccaoArquivoEntity arquivo = service.enviar(card.getId(), documento.getId(), pdf("Balanço 2025.pdf"), autor);

        assertThat(arquivo.getCaminhoRelativo())
                .isEqualTo("11222333000181/esteira/empresa/balanco-dre/v1-balanco-2025.pdf");
        assertThat(base.resolve(arquivo.getCaminhoRelativo())).exists();
        // Nome de exibição fica intacto; só o disco recebe a versão higienizada.
        assertThat(arquivo.getNomeOriginal()).isEqualTo("Balanço 2025.pdf");
    }

    @Test
    @DisplayName("enviar: item de sócio vai para pasta própria, uma por sócio")
    void shouldSeparatePartnerFolders() {
        documento.setEscopo(EscopoDocumento.SOCIO);
        documento.setSocioNome("Antônio Eduardo Meneguini");
        documento.setCodigoSnapshot("socio-irpf");

        ProspeccaoArquivoEntity arquivo = service.enviar(card.getId(), documento.getId(), pdf("irpf.pdf"), autor);

        assertThat(arquivo.getCaminhoRelativo())
                .startsWith("11222333000181/esteira/socio-antonio-eduardo-meneguini/socio-irpf/");
    }

    @Test
    @DisplayName("enviar: marca o item como recebido, não como validado — quem confere é o backoffice")
    void shouldMarkReceivedNotValidated() {
        service.enviar(card.getId(), documento.getId(), pdf("balanco.pdf"), autor);

        assertThat(documento.getStatus()).isEqualTo(StatusDocumento.RECEBIDO);
        assertThat(documento.getRecebidoPor()).isEqualTo(autor.getId());
        assertThat(documento.getValidadoEm()).isNull();
    }

    @Test
    @DisplayName("enviar: cada reenvio é uma versão nova, sem sobrescrever a anterior")
    void shouldVersionEachUpload() {
        when(arquivoRepository.countByDocumentoId(documento.getId())).thenReturn(2L);

        ProspeccaoArquivoEntity arquivo = service.enviar(card.getId(), documento.getId(), pdf("balanco.pdf"), autor);

        assertThat(arquivo.getVersao()).isEqualTo(3);
        assertThat(arquivo.getCaminhoRelativo()).contains("/v3-balanco.pdf");
    }

    @Test
    @DisplayName("enviar: recusa item de checklist de outro card")
    void shouldRejectDocumentFromAnotherCard() {
        documento.setProspeccaoId(UUID.randomUUID());

        assertThatThrownBy(() -> service.enviar(card.getId(), documento.getId(), pdf("x.pdf"), autor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pertence a este card");
    }

    @Test
    @DisplayName("enviar: recusa tipo fora da allowlist")
    void shouldRejectDisallowedMime() {
        MockMultipartFile executavel = new MockMultipartFile(
                "file", "instalador.exe", "application/x-msdownload", "MZ".getBytes());

        assertThatThrownBy(() -> service.enviar(card.getId(), documento.getId(), executavel, autor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não aceito");
    }

    @ParameterizedTest
    @CsvSource({"../../etc/passwd", "pasta/../../fora.pdf", "..\\..\\windows\\system32"})
    @DisplayName("enviar: nome com separador de caminho é recusado de saída")
    void shouldRejectPathTraversalInFilename(String nome) {
        MockMultipartFile arquivo = new MockMultipartFile("file", nome, "application/pdf", "x".getBytes());

        assertThatThrownBy(() -> service.enviar(card.getId(), documento.getId(), arquivo, autor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nome de arquivo inválido");
    }

    @Test
    @DisplayName("enviar: travessia codificada é neutralizada pelo slug, não escapa da pasta")
    void shouldNeutralizeEncodedTraversal() {
        // Sem separador de caminho, o nome passa pela validação — e é o slug que o desarma.
        // O teste fixa isso porque a garantia real é o arquivo continuar dentro da pasta do CNPJ.
        MockMultipartFile arquivo = new MockMultipartFile(
                "file", "..%2Fpasswd", "application/pdf", "x".getBytes());

        ProspeccaoArquivoEntity salvo = service.enviar(card.getId(), documento.getId(), arquivo, autor);

        assertThat(salvo.getCaminhoRelativo())
                .startsWith("11222333000181/esteira/empresa/balanco-dre/")
                .doesNotContain("..");
        assertThat(base.resolve(salvo.getCaminhoRelativo()).normalize())
                .startsWith(base.resolve("11222333000181"));
    }

    @Test
    @DisplayName("enviar: arquivo acima de 10MB não passa")
    void shouldRejectOversizedFile() {
        MockMultipartFile grande = new MockMultipartFile("file", "grande.pdf", "application/pdf",
                new byte[(int) ProspeccaoArquivoService.TAMANHO_MAXIMO_BYTES + 1]);

        assertThatThrownBy(() -> service.enviar(card.getId(), documento.getId(), grande, autor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    @DisplayName("remover: é lógico — o byte fica no compartilhamento de outras equipes")
    void shouldRemoveLogicallyKeepingTheByte() {
        ProspeccaoArquivoEntity arquivo = service.enviar(card.getId(), documento.getId(), pdf("balanco.pdf"), autor);
        Path noDisco = base.resolve(arquivo.getCaminhoRelativo());
        when(arquivoRepository.findById(arquivo.getId())).thenReturn(Optional.of(arquivo));
        when(arquivoRepository.findByDocumentoIdAndRemovidoEmIsNullOrderByVersaoDesc(documento.getId()))
                .thenReturn(List.of());

        service.remover(arquivo.getId(), autor);

        assertThat(arquivo.getRemovidoEm()).isNotNull();
        assertThat(Files.exists(noDisco)).isTrue();
        // Sem versão ativa, o item volta a ser cobrado.
        assertThat(documento.getStatus()).isEqualTo(StatusDocumento.PENDENTE);
    }

    @Test
    @DisplayName("remover: com outra versão ativa, o item continua recebido")
    void shouldKeepReceivedWhenAnotherVersionSurvives() {
        ProspeccaoArquivoEntity arquivo = service.enviar(card.getId(), documento.getId(), pdf("balanco.pdf"), autor);
        when(arquivoRepository.findById(arquivo.getId())).thenReturn(Optional.of(arquivo));
        when(arquivoRepository.findByDocumentoIdAndRemovidoEmIsNullOrderByVersaoDesc(documento.getId()))
                .thenReturn(List.of(ProspeccaoArquivoEntity.builder().id(UUID.randomUUID()).build()));

        service.remover(arquivo.getId(), autor);

        assertThat(documento.getStatus()).isEqualTo(StatusDocumento.RECEBIDO);
    }

    @Test
    @DisplayName("conteudo: devolve os bytes do compartilhamento")
    void shouldReadContent() {
        ProspeccaoArquivoEntity arquivo = service.enviar(card.getId(), documento.getId(), pdf("balanco.pdf"), autor);
        when(arquivoRepository.findById(arquivo.getId())).thenReturn(Optional.of(arquivo));

        assertThat(service.conteudo(arquivo.getId())).isEqualTo("conteudo".getBytes());
    }

    @Test
    @DisplayName("conteudo: arquivo movido fora do portal dá mensagem que diz o que houve")
    void shouldExplainMissingFile() throws Exception {
        ProspeccaoArquivoEntity arquivo = service.enviar(card.getId(), documento.getId(), pdf("balanco.pdf"), autor);
        when(arquivoRepository.findById(arquivo.getId())).thenReturn(Optional.of(arquivo));
        Files.delete(base.resolve(arquivo.getCaminhoRelativo()));

        assertThatThrownBy(() -> service.conteudo(arquivo.getId()))
                .hasMessageContaining("movido ou renomeado fora do portal");
    }

    @Test
    @DisplayName("compartilhamento indisponível não estoura erro genérico")
    void shouldDegradeGracefullyWithoutShare() {
        when(systemSettingService.requireDocumentStorageBasePath())
                .thenThrow(new IllegalArgumentException("A pasta base configurada não está acessível"));

        assertThatThrownBy(() -> service.enviar(card.getId(), documento.getId(), pdf("x.pdf"), autor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Compartilhamento de documentos indisponível");
    }

    @ParameterizedTest
    @CsvSource({
            "Balanço Patrimonial, balanco-patrimonial",
            "  espaços   demais  , espacos-demais",
            "José D'Ávila & Cia, jose-d-avila-cia",
            "///, arquivo"
    })
    @DisplayName("slug: tira acento, pontuação e sobra de hífen")
    void shouldSlugify(String entrada, String esperado) {
        assertThat(ProspeccaoArquivoService.slug(entrada)).isEqualTo(esperado);
    }
}
