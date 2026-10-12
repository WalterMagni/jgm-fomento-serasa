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
     * Apagar card. Aberto a todos como o resto da esteira: o caso de uso é desfazer card criado
     * errado, e quem criou precisa conseguir desfazer.
     */
    public void exigirExclusao(UserEntity usuario) {
        exigirAutenticado(usuario);
    }

    /**
     * Decidir a análise: só a analista dona do card, ou o admin.
     *
     * <p>Exceção à regra de "todos operam tudo", pedida pelo Walter em 2026-10-11: a decisão leva
     * limite e parecer, e quem assina é quem analisou.</p>
     */
    public void exigirDona(ProspeccaoEntity card, UserEntity usuario) {
        exigirAutenticado(usuario);
        if (!ehDona(card, usuario)) {
            throw new AcessoNegadoException(card.getAnalistaId() == null
                    ? "Assuma a análise antes de decidir."
                    : "Só a analista dona do card decide.");
        }
    }

    /** Mesma regra de {@link #exigirDona}, sem lançar — a tela usa para mostrar o botão. */
    public boolean ehDona(ProspeccaoEntity card, UserEntity usuario) {
        if (usuario == null || usuario.getId() == null) {
            return false;
        }
        return ADMIN.equals(papel(usuario))
                || (card.getAnalistaId() != null && card.getAnalistaId().equals(usuario.getId()));
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
            throw new AcessoNegadoException("Só admin altera os catálogos da esteira.");
        }
    }

    private void exigirAutenticado(UserEntity usuario) {
        if (usuario == null || usuario.getId() == null) {
            throw new AcessoNegadoException("Não autenticado");
        }
    }

    /** Admin pelo papel do usuário, nas duas grafias que existem no banco. */
    public static boolean ehAdmin(UserEntity usuario) {
        return ADMIN.equals(papel(usuario));
    }

    /**
     * Papel normalizado para {@code ROLE_*}.
     *
     * <p>As duas grafias existem no banco: {@code AuthController} grava {@code ROLE_USER} e o
     * {@code DatabaseSeeder} grava {@code ADMIN}. Comparar só uma forma trancaria fora justamente
     * os usuários mais antigos, e sem erro que explicasse o motivo.</p>
     */
    private static String papel(UserEntity usuario) {
        if (usuario == null || usuario.getRole() == null || usuario.getRole().isBlank()) {
            return "";
        }
        String bruto = usuario.getRole().trim().toUpperCase(Locale.ROOT);
        return bruto.startsWith("ROLE_") ? bruto : "ROLE_" + bruto;
    }
}
