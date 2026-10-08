package com.portal.serasa.application.service.documento;

import com.portal.serasa.application.service.SystemSettingService;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompartilhamentoDocumentosTest {

    @TempDir Path base;
    @Mock private SystemSettingService systemSettingService;

    private CompartilhamentoDocumentos documentos;

    @BeforeEach
    void setUp() throws IOException {
        base = base.toRealPath();
        documentos = new CompartilhamentoDocumentos(systemSettingService);
        when(systemSettingService.requireDocumentStorageBasePath()).thenReturn(base);
    }

    private static MockMultipartFile arquivo(String nome, String mime, byte[] conteudo) {
        return new MockMultipartFile("file", nome, mime, conteudo);
    }

    // ---------------------------------------------------------------- validar

    @Test
    @DisplayName("validar: devolve o nome original de um PDF válido")
    void shouldAcceptValidPdf() {
        assertThat(documentos.validar(arquivo("Contrato Social.pdf", "application/pdf", new byte[]{1})))
                .isEqualTo("Contrato Social.pdf");
    }

    @Test
    @DisplayName("validar: arquivo vazio ou ausente é recusado")
    void shouldRejectEmptyFile() {
        assertThatThrownBy(() -> documentos.validar(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> documentos.validar(arquivo("a.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecione um arquivo para enviar");
    }

    @Test
    @DisplayName("validar: passa de 10 MB é recusado; exatamente 10 MB passa")
    void shouldEnforceTenMegabyteLimit() {
        byte[] limite = new byte[(int) CompartilhamentoDocumentos.TAMANHO_MAXIMO_BYTES];
        byte[] acima = new byte[(int) CompartilhamentoDocumentos.TAMANHO_MAXIMO_BYTES + 1];

        assertThat(documentos.validar(arquivo("a.pdf", "application/pdf", limite))).isEqualTo("a.pdf");
        assertThatThrownBy(() -> documentos.validar(arquivo("a.pdf", "application/pdf", acima)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O arquivo passa de 10MB");
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/x-msdownload", "text/html", "application/javascript", "application/zip",
            "image/svg+xml", "application/octet-stream"})
    @DisplayName("validar: tipo fora da allowlist (executável, HTML, SVG, zip...) é recusado")
    void shouldRejectMimeOutsideAllowlist(String mime) {
        assertThatThrownBy(() -> documentos.validar(arquivo("a.bin", mime, new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Tipo de arquivo não aceito");
    }

    @Test
    @DisplayName("validar: sem content-type é recusado; maiúsculas no tipo são aceitas")
    void shouldHandleMissingAndUppercaseMime() {
        assertThatThrownBy(() -> documentos.validar(arquivo("a.pdf", null, new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(documentos.validar(arquivo("a.pdf", "APPLICATION/PDF", new byte[]{1}))).isEqualTo("a.pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../segredo.pdf", "a/b.pdf", "a\\b.pdf", "..", "."})
    @DisplayName("validar: nome com separador de caminho ou '..' é inválido")
    void shouldRejectPathLikeNames(String nome) {
        assertThatThrownBy(() -> documentos.validar(arquivo(nome, "application/pdf", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nome de arquivo inválido");
    }

    // ------------------------------------------------------------ nome no disco

    @Test
    @DisplayName("nomeSanitizado: sem acento, espaço nem maiúscula; extensão preservada em minúsculas")
    void shouldSanitizeNameKeepingExtension() {
        assertThat(documentos.nomeSanitizado(arquivo("Contrato Social — Revisão Final.PDF", "application/pdf", new byte[]{1})))
                .isEqualTo("contrato-social-revisao-final.pdf");
    }

    @Test
    @DisplayName("slug: vazio ou só símbolos vira 'arquivo'; nome longo é cortado em 80")
    void shouldSlugEdgeCases() {
        assertThat(CompartilhamentoDocumentos.slug("???")).isEqualTo("arquivo");
        assertThat(CompartilhamentoDocumentos.slug(null)).isEqualTo("arquivo");
        assertThat(CompartilhamentoDocumentos.slug("a".repeat(200))).hasSize(80);
    }

    // ---------------------------------------------------------- gravar e ler

    @Test
    @DisplayName("gravar e ler: cria as pastas, grava o byte e devolve o mesmo conteúdo")
    void shouldWriteAndReadBack() throws IOException {
        byte[] conteudo = {10, 20, 30};

        documentos.gravar("11222333000181/liberacao/42/1-contrato.pdf", arquivo("c.pdf", "application/pdf", conteudo));

        assertThat(Files.readAllBytes(base.resolve("11222333000181/liberacao/42/1-contrato.pdf"))).isEqualTo(conteudo);
        assertThat(documentos.ler("11222333000181/liberacao/42/1-contrato.pdf")).isEqualTo(conteudo);
    }

    @Test
    @DisplayName("ler: arquivo que não está no compartilhamento é EntityNotFound com mensagem clara")
    void shouldReportMissingFile() {
        assertThatThrownBy(() -> documentos.ler("11222333000181/liberacao/42/9-sumiu.pdf"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("não está no compartilhamento");
    }

    @Test
    @DisplayName("base não configurada vira 'Compartilhamento de documentos indisponível'")
    void shouldExplainUnavailableShare() {
        when(systemSettingService.requireDocumentStorageBasePath())
                .thenThrow(new IllegalArgumentException("Configure a pasta base dos documentos em Sistema"));

        assertThatThrownBy(() -> documentos.ler("a/b.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Compartilhamento de documentos indisponível");
    }

    // -------------------------------------------------------- segurança de caminho

    @ParameterizedTest
    @ValueSource(strings = {"../fora.pdf", "a/../../fora.pdf", "11222333000181/../../fora.pdf"})
    @DisplayName("gravar e ler: caminho que sai da pasta base é recusado")
    void shouldBlockTraversal(String relativo) {
        assertThatThrownBy(() -> documentos.gravar(relativo, arquivo("a.pdf", "application/pdf", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Acesso fora da pasta de documentos não permitido");
        assertThatThrownBy(() -> documentos.ler(relativo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Acesso fora da pasta de documentos não permitido");
    }

    @Test
    @DisplayName("resolverDentroDaBase: caminho absoluto é recusado")
    void shouldBlockAbsolutePath() {
        assertThatThrownBy(() -> CompartilhamentoDocumentos.resolverDentroDaBase(base, "/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Caminho inválido");
    }

    @Test
    @DisplayName("resolverDentroDaBase: caminho novo dentro da base (pastas ainda inexistentes) é aceito")
    void shouldAcceptNewPathInsideBase() {
        Path destino = CompartilhamentoDocumentos.resolverDentroDaBase(base, "11222333000181/liberacao/42/1-a.pdf");

        assertThat(destino).isEqualTo(base.resolve("11222333000181/liberacao/42/1-a.pdf"));
    }

    @Test
    @DisplayName("resolverDentroDaBase: link simbólico que aponta para fora da base é recusado, mesmo com o arquivo ainda inexistente")
    void shouldBlockSymlinkEscape(@TempDir Path fora) throws IOException {
        Path link = base.resolve("11222333000181");
        try {
            Files.createSymbolicLink(link, fora.toRealPath());
        } catch (UnsupportedOperationException | IOException ex) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "sistema de arquivos sem link simbólico");
        }

        assertThatThrownBy(() -> CompartilhamentoDocumentos.resolverDentroDaBase(base, "11222333000181/liberacao/42/1-a.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Acesso fora da pasta de documentos não permitido");
        assertThat(Files.list(fora).count()).isZero();
    }
}
