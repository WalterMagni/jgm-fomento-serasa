package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Quem pode o quê na esteira.
 *
 * <p><b>Toda pessoa autenticada opera a esteira inteira.</b> A primeira versão separava papéis —
 * só analista decidia sobre a análise, só backoffice conferia documento, e o comercial mexia
 * apenas nos cards dele. Na prática a separação travou mais do que protegeu: a equipe é pequena,
 * cobre a carteira uma da outra, e todos os usuários do portal estavam com o papel legado, o que
 * deixaria a esteira inoperante no primeiro dia.</p>
 *
 * <p>O que garante rastreabilidade é a timeline, não a restrição: todo evento grava autor, data e
 * o que mudou, e o nome fica denormalizado para sobreviver à remoção do usuário.</p>
 *
 * <p>A classe continua existindo, em vez de o controlador simplesmente parar de checar, porque as
 * regras têm um lugar só — se um dia a separação voltar, ela volta aqui, e não espalhada por doze
 * endpoints.</p>
 */
@Component
public class ProspeccaoAutorizacao {

    public static final String ADMIN = "ROLE_ADMIN";

    /** Decidir sobre a análise: assumir, aprovar, reprovar, reabrir. */
    public void exigirDecisor(UserEntity usuario) {
        exigirAutenticado(usuario);
    }

    /** Conferir documento: validar, rejeitar, dispensar, mexer no bloco do sócio. */
    public void exigirConferente(UserEntity usuario) {
        exigirAutenticado(usuario);
    }

    /** Abrir prospecção. */
    public void exigirCriador(UserEntity usuario) {
        exigirAutenticado(usuario);
    }

    /** Escrever no card: cobrança, nota, upload e remoção de arquivo. */
    public void exigirEscrita(ProspeccaoEntity card, UserEntity usuario) {
        exigirAutenticado(usuario);
    }

    /**
     * Catálogo de documentos: continua restrito ao admin.
     *
     * <p>Não é operação da esteira e sim configuração: mexer aqui muda o que passa a ser exigido
     * de toda empresa aprovada dali em diante. Um clique errado não pode redefinir a exigência
     * documental da casa.</p>
     */
    public void exigirAdmin(UserEntity usuario) {
        exigirAutenticado(usuario);
        if (!ADMIN.equals(papel(usuario))) {
            throw new AcessoNegadoException("Só admin altera o catálogo de documentos.");
        }
    }

    private void exigirAutenticado(UserEntity usuario) {
        if (usuario == null || usuario.getId() == null) {
            throw new AcessoNegadoException("Não autenticado");
        }
    }

    /**
     * Papel normalizado para {@code ROLE_*}.
     *
     * <p>As duas grafias existem no banco: {@code AuthController} grava {@code ROLE_USER} e o
     * {@code DatabaseSeeder} grava {@code ADMIN}. Comparar só uma forma trancaria fora justamente
     * os usuários mais antigos, e sem erro que explicasse o motivo.</p>
     */
    private String papel(UserEntity usuario) {
        if (usuario == null || usuario.getRole() == null || usuario.getRole().isBlank()) {
            return "";
        }
        String bruto = usuario.getRole().trim().toUpperCase(Locale.ROOT);
        return bruto.startsWith("ROLE_") ? bruto : "ROLE_" + bruto;
    }
}
