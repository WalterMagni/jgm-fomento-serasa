package com.portal.serasa.application.service;

import com.portal.serasa.api.rest.dto.response.CompanyDocumentsResponse;
import com.portal.serasa.api.rest.dto.response.DirectoryBrowserResponse;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyDocumentRootEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyDocumentRootJpaRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CompanyDocumentService {

    private static final ZoneId BRAZIL_TIMEZONE = ZoneId.of("America/Sao_Paulo");

    private final ClientJpaRepository clientJpaRepository;
    private final CompanyDocumentRootJpaRepository companyDocumentRootJpaRepository;
    private final SystemSettingService systemSettingService;

    @Transactional(readOnly = true)
    public CompanyDocumentsResponse list(String cnpj, String relativePath) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyDocumentRootEntity mapping = companyDocumentRootJpaRepository.findByClientId(client.getId()).orElse(null);
        if (mapping == null) {
            return CompanyDocumentsResponse.builder()
                    .mapped(false)
                    .currentPath("")
                    .items(List.of())
                    .build();
        }

        Path root = realRoot(mapping);
        Path current = resolveInsideRoot(root, relativePath);
        if (!Files.isDirectory(current)) {
            throw new IllegalArgumentException("O caminho informado não é uma pasta");
        }

        List<CompanyDocumentsResponse.DocumentItem> items;
        try (var stream = Files.list(current)) {
            items = stream
                    .filter(path -> !isHidden(path))
                    .map(path -> toItem(root, path))
                    .sorted(Comparator
                            .comparing((CompanyDocumentsResponse.DocumentItem item) -> !"folder".equals(item.getType()))
                            .thenComparing(CompanyDocumentsResponse.DocumentItem::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível listar os arquivos desta pasta");
        }

        String currentRelative = toRelative(root, current);
        return CompanyDocumentsResponse.builder()
                .mapped(true)
                .rootPath(root.toString())
                .currentPath(currentRelative)
                .parentPath(parentPath(currentRelative))
                .mappedByName(mapping.getMappedByName())
                .mappedAt(mapping.getUpdatedAt())
                .items(items)
                .build();
    }

    @Transactional
    public CompanyDocumentsResponse mapRoot(String cnpj, String rootPath, UserEntity currentUser) {
        ClientEntity client = getClientByCnpj(cnpj);
        Path baseRoot = systemSettingService.requireDocumentStorageBasePath();
        Path rawRoot = Path.of(rootPath.trim());
        Path root = rawRoot.isAbsolute()
                ? rawRoot.toAbsolutePath().normalize()
                : baseRoot.resolve(rawRoot).normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("A pasta informada não existe ou não é um diretório");
        }

        Path realRoot;
        try {
            realRoot = root.toRealPath();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível acessar a pasta informada");
        }
        if (!realRoot.startsWith(baseRoot)) {
            throw new IllegalArgumentException("A pasta da empresa precisa estar dentro da pasta base configurada no Sistema");
        }

        saveMapping(client, realRoot, currentUser);

        return list(cnpj, "");
    }

    @Transactional
    public CompanyDocumentsResponse createDefaultFolder(String cnpj, UserEntity currentUser) {
        ClientEntity client = getClientByCnpj(cnpj);
        Path baseRoot = systemSettingService.requireDocumentStorageBasePath();
        String folderName = defaultFolderName(client);
        Path defaultFolder = baseRoot.resolve(folderName).normalize();
        if (!defaultFolder.startsWith(baseRoot)) {
            throw new IllegalArgumentException("Nome de pasta inválido");
        }

        try {
            if (Files.exists(defaultFolder) && !Files.isDirectory(defaultFolder)) {
                throw new IllegalArgumentException("Já existe um arquivo com o nome padrão da pasta da empresa");
            }
            Files.createDirectories(defaultFolder);
            saveMapping(client, defaultFolder.toRealPath(), currentUser);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível criar a pasta padrão da empresa");
        }

        return list(cnpj, "");
    }

    @Transactional
    public void removeMapping(String cnpj) {
        ClientEntity client = getClientByCnpj(cnpj);
        companyDocumentRootJpaRepository.deleteByClientId(client.getId());
    }

    @Transactional(readOnly = true)
    public DirectoryBrowserResponse listBaseDirectories(String relativePath) {
        Path baseRoot = systemSettingService.requireDocumentStorageBasePath();
        Path current = resolveInsideRoot(baseRoot, relativePath);
        if (!Files.isDirectory(current)) {
            throw new IllegalArgumentException("O caminho informado não é uma pasta");
        }

        List<DirectoryBrowserResponse.DirectoryItem> directories;
        try (var stream = Files.list(current)) {
            directories = stream
                    .filter(Files::isDirectory)
                    .filter(path -> !isHidden(path))
                    .map(path -> DirectoryBrowserResponse.DirectoryItem.builder()
                            .name(path.getFileName().toString())
                            .path(toRelative(baseRoot, path))
                            .modifiedAt(modifiedAt(path))
                            .build())
                    .sorted(Comparator.comparing(DirectoryBrowserResponse.DirectoryItem::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível listar as pastas");
        }

        String currentRelative = toRelative(baseRoot, current);
        return DirectoryBrowserResponse.builder()
                .configured(true)
                .basePath(baseRoot.toString())
                .currentPath(currentRelative)
                .parentPath(parentPath(currentRelative))
                .directories(directories)
                .build();
    }

    @Transactional(readOnly = true)
    public DocumentFile getFile(String cnpj, String relativePath) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyDocumentRootEntity mapping = companyDocumentRootJpaRepository.findByClientId(client.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pasta de documentos não mapeada para esta empresa"));
        Path root = realRoot(mapping);
        Path file = resolveInsideRoot(root, relativePath);
        if (!Files.isRegularFile(file)) {
            throw new EntityNotFoundException("Arquivo não encontrado");
        }

        try {
            String contentType = Files.probeContentType(file);
            if (contentType == null || contentType.isBlank()) {
                contentType = URLConnection.guessContentTypeFromName(file.getFileName().toString());
            }
            return DocumentFile.builder()
                    .path(file)
                    .fileName(file.getFileName().toString())
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível acessar o arquivo");
        }
    }

    @Transactional(readOnly = true)
    public void openFileWithDefaultApplication(String cnpj, String relativePath) {
        DocumentFile file = getFile(cnpj, relativePath);
        try {
            ProcessBuilder processBuilder = openCommand(file.getPath());
            processBuilder.start();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível abrir o arquivo no aplicativo padrão do sistema");
        }
    }

    @Transactional
    public void renameFile(String cnpj, String relativePath, String newName) {
        DocumentFile file = getFile(cnpj, relativePath);
        String safeName = validateFileName(newName);
        Path target = file.getPath().getParent().resolve(safeName).normalize();
        validateTargetInSameFolder(file.getPath(), target);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Já existe um arquivo com esse nome nesta pasta");
        }

        try {
            Files.move(file.getPath(), target);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível renomear o arquivo");
        }
    }

    @Transactional
    public void duplicateFile(String cnpj, String relativePath) {
        DocumentFile file = getFile(cnpj, relativePath);
        Path target = nextCopyPath(file.getPath());
        validateTargetInSameFolder(file.getPath(), target);

        try {
            Files.copy(file.getPath(), target, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível duplicar o arquivo");
        }
    }

    @Transactional(readOnly = true)
    public void openItemInFileExplorer(String cnpj, String relativePath) {
        Path item = resolveExistingItem(cnpj, relativePath);
        try {
            ProcessBuilder processBuilder = explorerCommand(item);
            processBuilder.start();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível abrir o item no explorador de arquivos");
        }
    }

    @Transactional
    public void renameItem(String cnpj, String relativePath, String newName) {
        Path item = resolveExistingItem(cnpj, relativePath);
        ensureNotRootItem(item, relativePath);
        String safeName = validateFileName(newName);
        Path target = item.getParent().resolve(safeName).normalize();
        validateTargetInSameFolder(item, target);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Já existe um item com esse nome nesta pasta");
        }

        try {
            Files.move(item, target);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível renomear o item");
        }
    }

    @Transactional
    public void duplicateItem(String cnpj, String relativePath) {
        Path item = resolveExistingItem(cnpj, relativePath);
        ensureNotRootItem(item, relativePath);
        Path target = nextCopyPath(item);
        validateTargetInSameFolder(item, target);

        try {
            if (Files.isDirectory(item)) {
                copyDirectory(item, target);
            } else {
                Files.copy(item, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível duplicar o item");
        }
    }

    @Transactional
    public void uploadFiles(String cnpj, String relativePath, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Selecione pelo menos um arquivo para enviar");
        }

        Path targetDirectory = resolveDirectory(cnpj, relativePath);
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String fileName = validateFileName(file.getOriginalFilename());
            Path target = nextAvailablePath(targetDirectory.resolve(fileName).normalize());
            if (!target.getParent().equals(targetDirectory)) {
                throw new IllegalArgumentException("Nome de arquivo inválido");
            }
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            } catch (IOException ex) {
                throw new IllegalArgumentException("Não foi possível enviar o arquivo " + fileName);
            }
        }
    }

    @Transactional
    public void createFolder(String cnpj, String relativePath, String folderName) {
        Path targetDirectory = resolveDirectory(cnpj, relativePath);
        String safeName = validateFileName(folderName);
        Path folder = targetDirectory.resolve(safeName).normalize();
        if (!folder.getParent().equals(targetDirectory)) {
            throw new IllegalArgumentException("Nome de pasta inválido");
        }
        if (Files.exists(folder)) {
            throw new IllegalArgumentException("Já existe uma pasta ou arquivo com esse nome");
        }

        try {
            Files.createDirectory(folder);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível criar a pasta");
        }
    }

    private ClientEntity getClientByCnpj(String cnpj) {
        return clientJpaRepository.findByDocumentNumber(cnpj)
                .orElseThrow(() -> new EntityNotFoundException("Empresa não encontrada para CNPJ: " + cnpj));
    }

    private Path resolveExistingItem(String cnpj, String relativePath) {
        ClientEntity client = getClientByCnpj(cnpj);
        CompanyDocumentRootEntity mapping = companyDocumentRootJpaRepository.findByClientId(client.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pasta de documentos não mapeada para esta empresa"));
        Path root = realRoot(mapping);
        return resolveInsideRoot(root, relativePath);
    }

    private Path resolveDirectory(String cnpj, String relativePath) {
        Path target = resolveExistingItem(cnpj, relativePath);
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("O destino do upload precisa ser uma pasta");
        }
        return target;
    }

    private void saveMapping(ClientEntity client, Path root, UserEntity currentUser) {
        CompanyDocumentRootEntity mapping = companyDocumentRootJpaRepository.findByClientId(client.getId())
                .orElseGet(() -> CompanyDocumentRootEntity.builder().clientId(client.getId()).build());
        mapping.setRootPath(root.toString());
        mapping.setMappedByUserId(currentUser.getId());
        mapping.setMappedByName(currentUser.getName());
        mapping.setMappedByEmail(currentUser.getEmail());
        companyDocumentRootJpaRepository.save(mapping);
    }

    private Path realRoot(CompanyDocumentRootEntity mapping) {
        try {
            return Path.of(mapping.getRootPath()).toAbsolutePath().normalize().toRealPath();
        } catch (IOException ex) {
            throw new IllegalArgumentException("A pasta mapeada não está acessível");
        }
    }

    private Path resolveInsideRoot(Path root, String relativePath) {
        String safeRelative = relativePath == null ? "" : relativePath.trim();
        Path relative = safeRelative.isBlank() ? Path.of("") : Path.of(safeRelative);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Caminho inválido");
        }

        Path target = root.resolve(relative).normalize();
        Path realTarget;
        try {
            realTarget = target.toRealPath();
        } catch (IOException ex) {
            throw new EntityNotFoundException("Caminho não encontrado");
        }

        if (!realTarget.startsWith(root)) {
            throw new IllegalArgumentException("Acesso fora da pasta da empresa não permitido");
        }
        return realTarget;
    }

    private CompanyDocumentsResponse.DocumentItem toItem(Path root, Path path) {
        boolean directory = Files.isDirectory(path);
        return CompanyDocumentsResponse.DocumentItem.builder()
                .name(path.getFileName().toString())
                .path(toRelative(root, path))
                .type(directory ? "folder" : "file")
                .extension(directory ? "" : extension(path.getFileName().toString()))
                .size(directory ? null : size(path))
                .modifiedAt(modifiedAt(path))
                .build();
    }

    private String toRelative(Path root, Path path) {
        String relative = root.relativize(path).toString();
        return relative.replace(path.getFileSystem().getSeparator(), "/");
    }

    private String parentPath(String currentPath) {
        if (currentPath == null || currentPath.isBlank()) {
            return null;
        }
        int slash = currentPath.lastIndexOf('/');
        return slash < 0 ? "" : currentPath.substring(0, slash);
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private Long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return null;
        }
    }

    private LocalDateTime modifiedAt(Path path) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), BRAZIL_TIMEZONE);
        } catch (IOException ex) {
            return null;
        }
    }

    private boolean isHidden(Path path) {
        try {
            return Files.isHidden(path);
        } catch (IOException ex) {
            return false;
        }
    }

    private String defaultFolderName(ClientEntity client) {
        String baseName = client.getDocumentNumber() + " - " + (client.getName() != null && !client.getName().isBlank()
                ? client.getName()
                : "EMPRESA");
        String sanitized = baseName
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() ? client.getDocumentNumber() : sanitized;
    }

    private ProcessBuilder openCommand(Path file) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String filePath = file.toAbsolutePath().toString();
        if (osName.contains("mac")) {
            return new ProcessBuilder("open", filePath);
        }
        if (osName.contains("win")) {
            return new ProcessBuilder("cmd", "/c", "start", "", filePath);
        }
        return new ProcessBuilder("xdg-open", filePath);
    }

    private ProcessBuilder explorerCommand(Path item) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String itemPath = item.toAbsolutePath().toString();
        if (osName.contains("mac")) {
            return Files.isDirectory(item)
                    ? new ProcessBuilder("open", itemPath)
                    : new ProcessBuilder("open", "-R", itemPath);
        }
        if (osName.contains("win")) {
            return Files.isDirectory(item)
                    ? new ProcessBuilder("explorer", itemPath)
                    : new ProcessBuilder("explorer", "/select,", itemPath);
        }
        Path directory = Files.isDirectory(item) ? item : item.getParent();
        return new ProcessBuilder("xdg-open", directory.toAbsolutePath().toString());
    }

    private String validateFileName(String rawName) {
        String fileName = rawName == null ? "" : rawName.trim();
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("Informe o novo nome do arquivo");
        }
        if (fileName.length() > 255) {
            throw new IllegalArgumentException("O novo nome do arquivo é muito longo");
        }
        if (fileName.equals(".") || fileName.equals("..")
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.matches(".*\\p{Cntrl}.*")) {
            throw new IllegalArgumentException("Nome de arquivo inválido");
        }
        return fileName;
    }

    private void validateTargetInSameFolder(Path source, Path target) {
        Path sourceParent = source.getParent().toAbsolutePath().normalize();
        Path targetParent = target.getParent().toAbsolutePath().normalize();
        if (!targetParent.equals(sourceParent)) {
            throw new IllegalArgumentException("Destino inválido");
        }
    }

    private Path nextCopyPath(Path source) {
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        Path parent = source.getParent();

        Path candidate = parent.resolve(baseName + " - copia" + extension).normalize();
        int index = 2;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(baseName + " - copia (" + index + ")" + extension).normalize();
            index++;
        }
        return candidate;
    }

    private Path nextAvailablePath(Path preferredPath) {
        if (!Files.exists(preferredPath)) {
            return preferredPath;
        }
        return nextCopyPath(preferredPath);
    }

    private void ensureNotRootItem(Path item, String relativePath) {
        if (relativePath == null || relativePath.isBlank() || item.getParent() == null) {
            throw new IllegalArgumentException("Essa ação não pode ser feita na raiz da pasta da empresa");
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path destination = target.resolve(source.relativize(path)).normalize();
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class DocumentFile {
        private Path path;
        private String fileName;
        private String contentType;
    }
}
