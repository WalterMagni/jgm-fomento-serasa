package com.portal.serasa.application.service;

/**
 * Conversão entre o CPF completo e a máscara publicada nos dados abertos da Receita Federal.
 *
 * <p>A Receita publica o CPF do sócio como {@code ***216508**}: três asteriscos, os dígitos
 * 4 a 9 do CPF, dois asteriscos. É um recorte posicional fixo, não um hash — dado o CPF
 * completo a máscara é reconstruível, o que permite casar um CPF vindo de fonte paga
 * (QSA do Serasa, por exemplo) com a linha correspondente na base gratuita.</p>
 *
 * <p>O caminho inverso não existe: a máscara descarta 5 dos 11 dígitos. Seis dígitos dão
 * ~1 milhão de combinações para ~200 milhões de CPFs, então máscaras colidem e nunca devem
 * ser usadas sozinhas como identidade — sempre acompanhadas do nome.</p>
 */
public final class CpfMask {

    private static final int MASK_START_INDEX = 3;   // 0-based: pula os 3 primeiros dígitos
    private static final int MASK_LENGTH = 6;
    private static final int CPF_LENGTH = 11;
    /** "***" + 6 digitos + "**" — coincide com o tamanho do CPF, mas nao e o mesmo conceito. */
    private static final int MASK_TOTAL_LENGTH = 11;

    private CpfMask() {
    }

    /**
     * Máscara no formato da Receita a partir de um CPF completo.
     *
     * @return {@code null} se o valor não for um CPF de 11 dígitos.
     */
    public static String fromCpf(String cpf) {
        String digits = digitsOnly(cpf);
        if (digits == null || digits.length() != CPF_LENGTH) {
            return null;
        }
        return "***" + digits.substring(MASK_START_INDEX, MASK_START_INDEX + MASK_LENGTH) + "**";
    }

    /** {@code true} se o valor já está no formato de máscara da Receita. */
    public static boolean isMask(String value) {
        return value != null && value.length() == MASK_TOTAL_LENGTH
                && value.startsWith("***") && value.endsWith("**");
    }

    /** Só os dígitos, ou {@code null} se não sobrar nenhum. */
    public static String digitsOnly(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }
}
