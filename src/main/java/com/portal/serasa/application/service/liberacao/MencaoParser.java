package com.portal.serasa.application.service.liberacao;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê as menções dos textos da esteira.
 *
 * <p>O texto guarda menção como marcação: {@code @[Andressa Lima](user:<uuid>)} e
 * {@code @[ACME LTDA](cnpj:12345678000190)}. O id, e não o nome, é o que vale — a pessoa pode mudar
 * de nome, e um rótulo forjado à mão não engana a tela, que mostra o nome atual do id.</p>
 *
 * <p>CNPJ escrito solto, sem {@code @}, também conta como menção de empresa: é o caso de quem cola
 * o número de um e-mail. Para não transformar qualquer sequência de 14 dígitos em link, o solto só
 * vale com dígito verificador correto.</p>
 */
public final class MencaoParser {

    private static final Pattern MARCACAO = Pattern.compile("@\\[([^\\]]+)]\\((user|cnpj):([^)\\s]+)\\)");

    /** 14 dígitos com ou sem pontuação, sem dígito colado antes ou depois. */
    private static final Pattern CNPJ_SOLTO = Pattern.compile("(?<![\\d:])(\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2})(?!\\d)");

    private MencaoParser() {
    }

    /** Pessoas mencionadas. Id malformado é ignorado em vez de quebrar a gravação do texto. */
    public static Set<UUID> usuarios(String texto) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (texto == null) {
            return ids;
        }
        Matcher matcher = MARCACAO.matcher(texto);
        while (matcher.find()) {
            if ("user".equals(matcher.group(2))) {
                try {
                    ids.add(UUID.fromString(matcher.group(3)));
                } catch (IllegalArgumentException ignorado) {
                    // marcação digitada à mão com id inválido: vira texto comum
                }
            }
        }
        return ids;
    }

    /** Empresas mencionadas, por marcação ou por CNPJ solto válido. */
    public static Set<String> cnpjs(String texto) {
        Set<String> cnpjs = new LinkedHashSet<>();
        if (texto == null) {
            return cnpjs;
        }
        Matcher marcacao = MARCACAO.matcher(texto);
        while (marcacao.find()) {
            String digitos = marcacao.group(3).replaceAll("\\D", "");
            if ("cnpj".equals(marcacao.group(2)) && digitos.length() == 14) {
                cnpjs.add(digitos);
            }
        }
        // Tira a marcação antes de procurar os soltos: o id dentro dela já foi contado.
        Matcher solto = CNPJ_SOLTO.matcher(MARCACAO.matcher(texto).replaceAll(" "));
        while (solto.find()) {
            String digitos = solto.group(1).replaceAll("\\D", "");
            if (cnpjValido(digitos)) {
                cnpjs.add(digitos);
            }
        }
        return cnpjs;
    }

    /** Quem passou a ser mencionado nesta edição. Editar sem mexer na menção não renotifica. */
    public static Set<UUID> novosUsuarios(String antes, String depois) {
        Set<UUID> novos = usuarios(depois);
        novos.removeAll(usuarios(antes));
        return novos;
    }

    /** Texto sem marcação, com {@code @Nome} no lugar: para relatório e busca. */
    public static String textoPlano(String texto) {
        if (texto == null) {
            return null;
        }
        return MARCACAO.matcher(texto).replaceAll(resultado -> Matcher.quoteReplacement("@" + resultado.group(1)));
    }

    static boolean cnpjValido(String cnpj) {
        if (cnpj == null || !cnpj.matches("\\d{14}") || cnpj.chars().distinct().count() == 1) {
            return false;
        }
        return digito(cnpj, 12) == cnpj.charAt(12) - '0' && digito(cnpj, 13) == cnpj.charAt(13) - '0';
    }

    private static int digito(String cnpj, int posicao) {
        int soma = 0;
        int peso = posicao - 7;
        for (int i = 0; i < posicao; i++) {
            soma += (cnpj.charAt(i) - '0') * peso--;
            if (peso < 2) {
                peso = 9;
            }
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
