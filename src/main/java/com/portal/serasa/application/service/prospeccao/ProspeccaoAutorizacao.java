package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Quem pode o quê na esteira.
 *
 * <p>As regras ficam num componente explícito em vez de {@code @PreAuthorize} porque a principal
 * delas não é sobre papel, e sim sobre dono: o comercial enxerga a esteira toda e mexe apenas
 * nos cards dele. Isso não se expressa bem em anotação, e espalhar metade da regra em SpEL e
 * metade no serviço é pior do que ter as duas no mesmo lugar.</p>
 *
 * <p>{@code ROLE_USER} é o papel legado — todo usuário do portal tinha esse valor antes da
 * esteira existir. Ele é tratado como comercial em vez de ser reescrito no banco: reescrever em
 * massa transformaria as analistas em comerciais silenciosamente.</p>
 */
@Component
public class ProspeccaoAutorizacao {

    public static final String ADMIN = "ROLE_ADMIN";
    public static final String GESTOR = "ROLE_GESTOR";
    public static final String ANALISTA = "ROLE_ANALISTA";
    public static final String BACKOFFICE = "ROLE_BACKOFFICE";
    public static final String COMERCIAL = "ROLE_COMERCIAL";
    /** Papel legado, equivalente a comercial. */
    public static final String LEGADO = "ROLE_USER";

    private static final Set<String> VEEM_TUDO = Set.of(ADMIN, GESTOR, ANALISTA, BACKOFFICE);
    private static final Set<String> DECIDEM = Set.of(ADMIN, GESTOR, ANALISTA);
    private static final Set<String> CONFEREM_DOCUMENTO = Set.of(ADMIN, GESTOR, ANALISTA, BACKOFFICE);
    private static final Set<String> CRIAM = Set.of(ADMIN, GESTOR, ANALISTA, COMERCIAL, LEGADO);

    /** Aprovar, reprovar, assumir análise e reabrir card. */
    public void exigirDecisor(UserEntity usuario) {
        if (!DECIDEM.contains(papel(usuario))) {
            throw new AcessoNegadoException("Só analista, gestor ou admin decide sobre a análise.");
        }
    }

    /** Validar, rejeitar ou dispensar documento — a conferência de conteúdo. */
    public void exigirConferente(UserEntity usuario) {
        if (!CONFEREM_DOCUMENTO.contains(papel(usuario))) {
            throw new AcessoNegadoException("Só backoffice, analista, gestor ou admin confere documento.");
        }
    }

    public void exigirCriador(UserEntity usuario) {
        if (!CRIAM.contains(papel(usuario))) {
            throw new AcessoNegadoException("Seu papel não permite abrir prospecção.");
        }
    }

    public void exigirAdmin(UserEntity usuario) {
        if (!ADMIN.equals(papel(usuario))) {
            throw new AcessoNegadoException("Só admin altera o catálogo de documentos.");
        }
    }

    /**
     * Escrita no card: cobrança, nota, upload de arquivo.
     *
     * <p>Comercial só mexe no card dele. Card sem comercial vinculado — o "DIRETO" da planilha —
     * fica com quem vê tudo, para não ficar órfão de dono e sem ninguém que possa tocá-lo.</p>
     */
    public void exigirEscrita(ProspeccaoEntity card, UserEntity usuario) {
        String papel = papel(usuario);
        if (VEEM_TUDO.contains(papel)) {
            return;
        }
        if (!CRIAM.contains(papel)) {
            throw new AcessoNegadoException("Seu papel não permite alterar cards da esteira.");
        }
        boolean dono = usuario != null && usuario.getId() != null
                && usuario.getId().equals(card.getComercialId());
        if (!dono) {
            throw new AcessoNegadoException(
                    "Este card é de outro comercial. Você pode acompanhar, mas não alterar.");
        }
    }

    /** Leitura é liberada para qualquer papel autenticado — a esteira inteira é visível. */
    public boolean podeDecidir(UserEntity usuario) {
        return DECIDEM.contains(papel(usuario));
    }

    private String papel(UserEntity usuario) {
        return usuario == null || usuario.getRole() == null ? "" : usuario.getRole();
    }
}
