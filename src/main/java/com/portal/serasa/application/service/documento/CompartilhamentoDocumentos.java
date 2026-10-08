package com.portal.serasa.application.service.documento;

import com.portal.serasa.application.service.SystemSettingService;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Gravação e leitura no compartilhamento de rede de documentos ({@code /mnt/clientes}).
 *
 * <p>Mesmas regras dos arquivos da esteira de prospecção (ver {@code ProspeccaoArquivoService},
 * que ainda tem a própria cópia): allowlist de tipos, teto de 10 MB, nome saneado no disco e
 * nenhum caminho fora da pasta base, nem por link simbólico. A esteira de liberação usa daqui; a
 * prospecção pode migrar para cá quando for mexida.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompartilhamentoDocumentos {

    public static final long TAMANHO_MAXIMO_BYTES = 10L * 1024 * 1024;

    /** Allowlist, não denylist: o que não está aqui não sobe. */
    private static final Set<String> MIMES_ACEITOS = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/heic",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/msword",
            "text/plain",
            "text/csv");

    private final SystemSettingService systemSettingService;

    /** Valida tipo, tamanho e nome. Devolve o nome original, que é o que o banco guarda. */
    public String validar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecione um arquivo para enviar");
        }
        if (file.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new IllegalArgumentException("O arquivo passa de 10MB");
        }
        String mime = file.getContentType();
        if (mime == null || !MIMES_ACEITOS.contains(mime.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Tipo de arquivo não aceito: " + mime
                    + ". Aceitos: PDF, imagem, Word, Excel, texto e CSV.");
        }
        return nomeOriginal(file);
    }

    /** Grava em {@code relativo} dentro da base. Erro de rede vira mensagem que o usuário entende. */
    public void gravar(String relativo, MultipartFile file) {
        Path destino = resolverDentroDaBase(base(), relativo);
        try {
            Files.createDirectories(destino.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            log.warn("Falha ao gravar {} no compartilhamento: {}", relativo, ex.getMessage());
            throw new IllegalArgumentException("Não foi possível gravar o arquivo no compartilhamento de rede. "
                    + "Verifique se a pasta de documentos está acessível.");
        }
    }

    public byte[] ler(String relativo) {
        Path caminho = resolverDentroDaBase(base(), relativo);
        if (!Files.isRegularFile(caminho)) {
            throw new EntityNotFoundException(
                    "O arquivo não está no compartilhamento. Ele pode ter sido movido ou renomeado fora do portal.");
        }
        try {
            return Files.readAllBytes(caminho);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível ler o arquivo no compartilhamento de rede");
        }
    }

    /** Nome preservado no banco; no disco vai sem acento, espaço nem caractere de caminho. */
    public String nomeSanitizado(MultipartFile file) {
        String nome = nomeOriginal(file);
        int ponto = nome.lastIndexOf('.');
        String corpo = ponto > 0 ? nome.substring(0, ponto) : nome;
        String extensao = ponto > 0 ? nome.substring(ponto).toLowerCase(Locale.ROOT) : "";
        return slug(corpo) + extensao.replaceAll("[^a-z0-9.]", "");
    }

    public static String slug(String valor) {
        String base = valor == null ? "" : valor;
        String semAcento = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String limpo = semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (limpo.isBlank()) {
            return "arquivo";
        }
        return limpo.length() > 80 ? limpo.substring(0, 80) : limpo;
    }

    private String nomeOriginal(MultipartFile file) {
        String nome = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        if (nome.isBlank() || nome.length() > 255) {
            throw new IllegalArgumentException("Nome de arquivo inválido");
        }
        if (nome.equals(".") || nome.equals("..") || nome.contains("/") || nome.contains("\\")
                || nome.matches(".*\\p{Cntrl}.*")) {
            throw new IllegalArgumentException("Nome de arquivo inválido");
        }
        return nome;
    }

    private Path base() {
        try {
            return systemSettingService.requireDocumentStorageBasePath();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Compartilhamento de documentos indisponível: " + ex.getMessage());
        }
    }

    /**
     * Confere que o destino cai dentro da base, sem depender de o arquivo já existir: a checagem
     * de link simbólico é feita sobre o ancestral mais próximo que já existe.
     */
    static Path resolverDentroDaBase(Path base, String relativo) {
        Path candidato = Path.of(relativo);
        if (candidato.isAbsolute()) {
            throw new IllegalArgumentException("Caminho inválido");
        }
        Path destino = base.resolve(candidato).normalize();
        if (!destino.startsWith(base)) {
            throw new IllegalArgumentException("Acesso fora da pasta de documentos não permitido");
        }
        Path existente = destino;
        while (existente != null && !Files.exists(existente)) {
            existente = existente.getParent();
        }
        if (existente != null) {
            try {
                if (!existente.toRealPath().startsWith(base)) {
                    throw new IllegalArgumentException("Acesso fora da pasta de documentos não permitido");
                }
            } catch (IOException ex) {
                throw new IllegalArgumentException("Pasta de documentos inacessível");
            }
        }
        return destino;
    }
}
