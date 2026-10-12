package com.portal.serasa.application.service.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Onde ficam os arquivos das esteiras (anexos da liberação, documentos da prospecção).
 *
 * <p>Dentro do próprio portal, não na pasta de rede das empresas (decisão de 2026-10-12): em
 * produção o compartilhamento nunca foi montado no servidor, e a esteira não pode depender dele.
 * No Docker é um volume nomeado montado em {@code /app/dados/esteiras}, que sobrevive a deploy
 * e a {@code docker compose down}. Fora do Docker, {@code ./dados/esteiras} na pasta do projeto.</p>
 *
 * <p>A pasta de documentos da página da empresa continua na configuração do Sistema.</p>
 */
@Component
public class ArmazenamentoEsteiras {

    private final Path pasta;

    public ArmazenamentoEsteiras(@Value("${app.documentos.esteiras:./dados/esteiras}") String pasta) {
        this.pasta = Path.of(pasta).toAbsolutePath().normalize();
    }

    /** A pasta, criada se ainda não existe. */
    public Path base() {
        try {
            Files.createDirectories(pasta);
            return pasta.toRealPath();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Pasta de arquivos das esteiras indisponível (" + pasta + ")");
        }
    }
}
