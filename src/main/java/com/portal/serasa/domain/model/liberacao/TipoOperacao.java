package com.portal.serasa.domain.model.liberacao;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Tipos de operação. Era lista fechada; desde a V68 é texto livre com sugestões.
 *
 * <p>Os padrões abaixo aparecem sempre, nesta ordem. Os que o time criar aparecem depois, lidos
 * dos próprios cards. Antes de gravar, o nome digitado é casado com os existentes sem diferenciar
 * maiúscula nem acento: "comissaria" vira "Comissária", e o quadro não ganha dois tipos iguais.</p>
 */
public final class TipoOperacao {

    public static final List<String> PADRAO = List.of("Duplicata", "Cheque", "Comissária", "Intercompany");

    public static final int LIMITE = 40;

    private TipoOperacao() {
    }

    /**
     * Nome a gravar: nulo se vazio; o existente equivalente se houver; senão o digitado, com
     * espaços normalizados e primeira letra maiúscula.
     */
    public static String resolver(String digitado, Collection<String> existentes) {
        if (digitado == null || digitado.isBlank()) {
            return null;
        }
        String limpo = digitado.trim().replaceAll("\\s+", " ");
        if (limpo.length() > LIMITE) {
            throw new IllegalArgumentException("Tipo de operação com até " + LIMITE + " caracteres.");
        }
        return equivalente(limpo, PADRAO)
                .or(() -> equivalente(limpo, existentes))
                .orElse(limpo.substring(0, 1).toUpperCase(Locale.ROOT) + limpo.substring(1));
    }

    private static Optional<String> equivalente(String nome, Collection<String> candidatos) {
        String chave = chave(nome);
        return candidatos.stream().filter(candidato -> candidato != null && chave(candidato).equals(chave)).findFirst();
    }

    static String chave(String nome) {
        return Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
