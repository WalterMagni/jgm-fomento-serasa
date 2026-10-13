package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.application.service.documento.ArmazenamentoEsteiras;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
import com.portal.serasa.domain.model.prospeccao.StatusDocumento;
import com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoArquivoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoArquivoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDocumentoJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Arquivos da esteira: metadado no banco, byte no compartilhamento de rede que o backend já
 * monta ({@code DOCUMENTS_BASE_HOST_PATH} → {@code /mnt/clientes}).
 *
 * <p>Guardar ali é melhor que um bucket, e não apenas mais simples: o arquivo cai na pasta
 * CLIENTES onde a equipe já trabalha, aparece no Explorer e entra no backup que já existe. Um
 * bucket criaria um segundo lugar onde documento mora — exatamente o problema que a esteira
 * quer resolver.</p>
 *
 * <p>A remoção é lógica. O compartilhamento é usado por outras equipes, e apagar byte de lá é
 * destrutivo e irreversível; o registro sai da tela e o arquivo fica no disco.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProspeccaoArquivoService {

    /** Mesmo teto do Portal Comercial, para que compartilhar na fase 2 seja tradução direta. */
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

    private final ProspeccaoArquivoJpaRepository arquivoRepository;
    private final ProspeccaoDocumentoJpaRepository documentoRepository;
    private final ProspeccaoService prospeccaoService;
    private final ArmazenamentoEsteiras armazenamento;

    /**
     * Sobe um arquivo e marca o item do checklist como recebido.
     *
     * <p>Recebido, não validado: quem confere é o backoffice, num segundo passo. Na planilha a
     * observação do documento é conferência de conteúdo — "OK - CRC válido", "OK - Sofisa,
     * Bradesco, Itaú" — então alguém abre e lê antes de dar por resolvido.</p>
     */
    @Transactional
    public ProspeccaoArquivoEntity enviar(UUID prospeccaoId, UUID documentoId, MultipartFile file,
                                          UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(prospeccaoId);
        ProspeccaoDocumentoEntity documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new EntityNotFoundException("Item do checklist não encontrado"));
        if (!documento.getProspeccaoId().equals(prospeccaoId)) {
            throw new IllegalArgumentException("O item do checklist não pertence a este card");
        }
        validar(file);

        Path base = baseDeDocumentos();
        int versao = proximaVersao(documentoId);
        String relativo = caminhoRelativo(card, documento, file, versao);
        Path destino = resolverDentroDaBase(base, relativo);

        try {
            Files.createDirectories(destino.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            // Escrita em SMB montado via WSL falha por motivos que o usuário consegue agir:
            // compartilhamento fora do ar, permissão, disco cheio. A mensagem diz isso.
            log.warn("Falha ao gravar {} no compartilhamento: {}", relativo, ex.getMessage());
            throw new IllegalArgumentException(
                    "Não foi possível gravar o arquivo no compartilhamento de rede. "
                            + "Verifique se a pasta de documentos está acessível.");
        }

        ProspeccaoArquivoEntity arquivo = arquivoRepository.save(ProspeccaoArquivoEntity.builder()
                .prospeccaoId(prospeccaoId)
                .documentoId(documentoId)
                .caminhoRelativo(relativo)
                .nomeOriginal(nomeOriginal(file))
                .mimeType(file.getContentType())
                .tamanhoBytes(file.getSize())
                .versao(versao)
                .enviadoPor(autor != null ? autor.getId() : null)
                .enviadoEm(LocalDateTime.now())
                .build());

        marcarRecebido(documento, autor);
        prospeccaoService.registrarEvento(card, TipoEventoProspeccao.ARQUIVO_ENVIADO, null, null, null,
                documento.getNomeSnapshot() + ": " + arquivo.getNomeOriginal(), autor);
        prospeccaoService.seguirParaConferenciaSeCompleto(prospeccaoId, autor);
        return arquivo;
    }

    /** Conteúdo do arquivo para download autenticado. Nunca expõe o caminho absoluto. */
    @Transactional(readOnly = true)
    public byte[] conteudo(UUID arquivoId) {
        ProspeccaoArquivoEntity arquivo = buscarAtivo(arquivoId);
        Path caminho = resolverDentroDaBase(baseDeDocumentos(), arquivo.getCaminhoRelativo());
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

    /**
     * Remoção lógica: o registro sai da tela, o byte permanece no compartilhamento.
     *
     * <p>Deliberado. A pasta é compartilhada com outras equipes e apagar de lá é irreversível —
     * um clique errado no portal não pode destruir documento de cliente.</p>
     */
    @Transactional
    public void remover(UUID arquivoId, UserEntity autor) {
        ProspeccaoArquivoEntity arquivo = buscarAtivo(arquivoId);
        arquivo.setRemovidoEm(LocalDateTime.now());
        arquivo.setRemovidoPor(autor != null ? autor.getId() : null);
        arquivoRepository.save(arquivo);

        // Sem nenhuma versão ativa, o item volta a ser cobrado.
        if (arquivo.getDocumentoId() != null
                && arquivoRepository.findByDocumentoIdAndRemovidoEmIsNullOrderByVersaoDesc(
                        arquivo.getDocumentoId()).isEmpty()) {
            documentoRepository.findById(arquivo.getDocumentoId()).ifPresent(documento -> {
                documento.setStatus(StatusDocumento.PENDENTE);
                documento.setRecebidoEm(null);
                documento.setRecebidoPor(null);
                documento.setAtualizadoEm(LocalDateTime.now());
                documentoRepository.save(documento);
            });
        }

        prospeccaoService.registrarEvento(prospeccaoService.buscar(arquivo.getProspeccaoId()),
                TipoEventoProspeccao.ARQUIVO_REMOVIDO, null, null, null, arquivo.getNomeOriginal(), autor);
    }

    // ---------------------------------------------------------------- zip

    /** Um arquivo dentro do ZIP: onde ele entra e de onde vem. */
    public record ItemZip(String caminhoNoZip, Path origem) {
    }

    /**
     * O que vai no ZIP, resolvido antes de começar a escrever.
     *
     * <p>{@code faltando} lista o que tem registro na esteira mas não está mais no compartilhamento
     * — movido ou renomeado fora do portal. Vira um LEIA-ME dentro do ZIP em vez de derrubar o
     * download inteiro por causa de um arquivo.</p>
     */
    public record PlanoZip(String nomeDoArquivo, List<ItemZip> itens, List<String> faltando) {
    }

    /**
     * Monta o plano do ZIP com a versão mais recente de cada documento do card.
     *
     * <p>Os arquivos entram com o nome do documento, e não o nome com que foram enviados — quem
     * abre o ZIP procura "Contrato social", não "scan_0042.pdf" — organizados em uma pasta para a
     * empresa e uma para cada sócio ou avalista.</p>
     */
    @Transactional(readOnly = true)
    public PlanoZip planoDoZip(UUID prospeccaoId) {
        ProspeccaoEntity card = prospeccaoService.buscar(prospeccaoId);
        java.util.Map<UUID, ProspeccaoDocumentoEntity> documentos = new java.util.HashMap<>();
        documentoRepository.findByProspeccaoId(prospeccaoId).forEach(documento -> documentos.put(documento.getId(), documento));

        // A mais nova de cada item: as versões anteriores ficam só no portal.
        java.util.Map<UUID, ProspeccaoArquivoEntity> maisNovo = new java.util.LinkedHashMap<>();
        for (ProspeccaoArquivoEntity arquivo : listar(prospeccaoId)) {
            if (arquivo.getDocumentoId() == null) {
                continue;
            }
            maisNovo.merge(arquivo.getDocumentoId(), arquivo,
                    (atual, outro) -> outro.getVersao() > atual.getVersao() ? outro : atual);
        }
        if (maisNovo.isEmpty()) {
            throw new EntityNotFoundException("Nenhum arquivo anexado neste card ainda.");
        }

        Path base = baseDeDocumentos();
        java.util.Set<String> usados = new java.util.HashSet<>();
        List<ItemZip> itens = new java.util.ArrayList<>();
        List<String> faltando = new java.util.ArrayList<>();
        for (ProspeccaoArquivoEntity arquivo : maisNovo.values()) {
            ProspeccaoDocumentoEntity documento = documentos.get(arquivo.getDocumentoId());
            String caminho = nomeUnico(caminhoNoZip(documento, arquivo.getNomeOriginal()), usados);
            Path origem = resolverDentroDaBase(base, arquivo.getCaminhoRelativo());
            if (Files.isRegularFile(origem)) {
                itens.add(new ItemZip(caminho, origem));
            } else {
                faltando.add(caminho + " (enviado como " + arquivo.getNomeOriginal() + ")");
            }
        }
        itens.sort(java.util.Comparator.comparing(ItemZip::caminhoNoZip));
        return new PlanoZip(nomeParaArquivo(card.getRazaoSocial()) + " - documentos.zip", itens, faltando);
    }

    /** {@code Empresa/Contrato social.pdf}, {@code Sócio - João Silva/RG ou CNH.jpg}. */
    static String caminhoNoZip(ProspeccaoDocumentoEntity documento, String nomeOriginal) {
        String pasta;
        String nome;
        if (documento == null) {
            pasta = "Outros";
            nome = nomeOriginal;
        } else {
            pasta = documento.getEscopo() == EscopoDocumento.SOCIO
                    ? (documento.getPessoaPapel() == com.portal.serasa.domain.model.prospeccao.PapelPessoa.AVALISTA ? "Avalista - " : "Sócio - ")
                            + documento.getSocioNome()
                    : "Empresa";
            nome = documento.getNomeSnapshot();
        }
        int ponto = nomeOriginal == null ? -1 : nomeOriginal.lastIndexOf('.');
        String extensao = ponto > 0 ? nomeOriginal.substring(ponto).toLowerCase(Locale.ROOT) : "";
        String semExtensao = nome != null && extensao.length() > 0 && nome.toLowerCase(Locale.ROOT).endsWith(extensao)
                ? nome.substring(0, nome.length() - extensao.length())
                : nome;
        return nomeParaArquivo(pasta) + "/" + nomeParaArquivo(semExtensao) + extensao;
    }

    /** Tira o que Windows não aceita em nome de arquivo, e o "/" que abriria pasta no ZIP. */
    static String nomeParaArquivo(String valor) {
        String limpo = valor == null ? "" : valor.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (limpo.isBlank()) {
            return "arquivo";
        }
        return limpo.length() > 120 ? limpo.substring(0, 120).trim() : limpo;
    }

    /** Dois itens com o mesmo nome (dois avalistas homônimos) não se sobrescrevem no ZIP. */
    static String nomeUnico(String caminho, java.util.Set<String> usados) {
        String candidato = caminho;
        int contador = 2;
        while (!usados.add(candidato.toLowerCase(Locale.ROOT))) {
            int ponto = caminho.lastIndexOf('.');
            int barra = caminho.lastIndexOf('/');
            candidato = ponto > barra
                    ? caminho.substring(0, ponto) + " (" + contador + ")" + caminho.substring(ponto)
                    : caminho + " (" + contador + ")";
            contador++;
        }
        return candidato;
    }

    /** Metadado do arquivo ativo, para montar o cabeçalho do download. */
    @Transactional(readOnly = true)
    public ProspeccaoArquivoEntity buscar(UUID arquivoId) {
        return buscarAtivo(arquivoId);
    }

    @Transactional(readOnly = true)
    public List<ProspeccaoArquivoEntity> listar(UUID prospeccaoId) {
        return arquivoRepository.findByProspeccaoIdAndRemovidoEmIsNullOrderByEnviadoEmDesc(prospeccaoId);
    }

    // ------------------------------------------------------------- internals

    private void marcarRecebido(ProspeccaoDocumentoEntity documento, UserEntity autor) {
        documento.setStatus(StatusDocumento.RECEBIDO);
        documento.setRecebidoEm(LocalDateTime.now());
        documento.setRecebidoPor(autor != null ? autor.getId() : null);
        documento.setAtualizadoEm(LocalDateTime.now());
        documentoRepository.save(documento);
    }

    private ProspeccaoArquivoEntity buscarAtivo(UUID arquivoId) {
        ProspeccaoArquivoEntity arquivo = arquivoRepository.findById(arquivoId)
                .orElseThrow(() -> new EntityNotFoundException("Arquivo não encontrado"));
        if (arquivo.getRemovidoEm() != null) {
            throw new EntityNotFoundException("Arquivo removido");
        }
        return arquivo;
    }

    private int proximaVersao(UUID documentoId) {
        // Conta inclusive as versões removidas logicamente: o byte antigo continua no disco e o
        // caminho não pode ser reaproveitado.
        return (int) arquivoRepository.countByDocumentoId(documentoId) + 1;
    }

    private Path baseDeDocumentos() {
        return armazenamento.base();
    }

    /**
     * {@code {cnpj}/esteira/{categoria}/{codigo}/v{n}-{nome}}.
     *
     * <p>O CNPJ é o nome da pasta, e por isso o upload exige card com CNPJ — o que não atrapalha:
     * a coleta documental acontece depois da aprovação, quando o CNPJ já é conhecido.</p>
     */
    private String caminhoRelativo(ProspeccaoEntity card, ProspeccaoDocumentoEntity documento,
                                   MultipartFile file, int versao) {
        String categoria = documento.getEscopo() == EscopoDocumento.SOCIO
                ? "socio-" + slug(documento.getSocioNome())
                : "empresa";
        return String.join("/",
                card.getCnpj(),
                "esteira",
                categoria,
                slug(documento.getCodigoSnapshot()),
                "v" + versao + "-" + nomeSanitizado(file));
    }

    /**
     * Confere que o destino cai dentro da base, sem depender de o arquivo já existir.
     *
     * <p>{@code toRealPath} resolve link simbólico mas exige caminho existente, então a
     * verificação é feita sobre o ancestral mais próximo que já existe — é ele que um link
     * poderia desviar para fora da base.</p>
     */
    private Path resolverDentroDaBase(Path base, String relativo) {
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

    private void validar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecione um arquivo para enviar");
        }
        if (file.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new IllegalArgumentException("O arquivo passa de 10MB");
        }
        String mime = file.getContentType();
        if (mime == null || !MIMES_ACEITOS.contains(mime.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Tipo de arquivo não aceito: " + mime);
        }
        nomeOriginal(file);
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

    /** Nome preservado no banco; no disco vai sem acento, espaço nem caractere de caminho. */
    private String nomeSanitizado(MultipartFile file) {
        String nome = nomeOriginal(file);
        int ponto = nome.lastIndexOf('.');
        String corpo = ponto > 0 ? nome.substring(0, ponto) : nome;
        String extensao = ponto > 0 ? nome.substring(ponto).toLowerCase(Locale.ROOT) : "";
        return slug(corpo) + extensao.replaceAll("[^a-z0-9.]", "");
    }

    static String slug(String valor) {
        String base = valor == null ? "" : valor;
        String semAcento = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String limpo = semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (limpo.isBlank()) {
            return "arquivo";
        }
        return limpo.length() > 80 ? limpo.substring(0, 80) : limpo;
    }
}
