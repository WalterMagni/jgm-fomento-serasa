package com.portal.serasa.application.service.liberacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MencaoParserTest {

    private static final UUID ANDRESSA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MYCHELLY = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("usuarios: lê ids das marcações de pessoa e ignora as de empresa")
    void shouldExtractUserIds() {
        String texto = "Oi @[Andressa Lima](user:" + ANDRESSA + ") e @[Mychelly](user:" + MYCHELLY + "), veja @[ACME](cnpj:11222333000181)";
        assertThat(MencaoParser.usuarios(texto)).containsExactly(ANDRESSA, MYCHELLY);
    }

    @Test
    @DisplayName("usuarios: id malformado vira texto comum, sem exceção")
    void shouldIgnoreMalformedUserId() {
        assertThat(MencaoParser.usuarios("@[Fulano](user:nao-e-uuid)")).isEmpty();
        assertThat(MencaoParser.usuarios(null)).isEmpty();
    }

    @Test
    @DisplayName("cnpjs: marcação e CNPJ solto válido, formatado ou não, sem repetir")
    void shouldExtractCnpjs() {
        String texto = "Cedente @[ACME](cnpj:11222333000181), sacado 11.444.777/0001-61 e de novo 11222333000181";
        assertThat(MencaoParser.cnpjs(texto)).containsExactly("11222333000181", "11444777000161");
    }

    @Test
    @DisplayName("cnpjs: solto com dígito verificador errado não vira menção")
    void shouldIgnoreInvalidLooseCnpj() {
        assertThat(MencaoParser.cnpjs("pedido 11222333000182 e 00000000000000")).isEmpty();
    }

    @Test
    @DisplayName("cnpjs: sequência maior que 14 dígitos não é CNPJ")
    void shouldIgnoreLongerDigitRuns() {
        assertThat(MencaoParser.cnpjs("protocolo 9112223330001811")).isEmpty();
    }

    @Test
    @DisplayName("novosUsuarios: só quem entrou nesta edição")
    void shouldReturnOnlyNewMentions() {
        String antes = "@[Andressa](user:" + ANDRESSA + ")";
        String depois = antes + " e @[Mychelly](user:" + MYCHELLY + ")";
        assertThat(MencaoParser.novosUsuarios(antes, depois)).containsExactly(MYCHELLY);
        assertThat(MencaoParser.novosUsuarios(depois, depois)).isEmpty();
        assertThat(MencaoParser.novosUsuarios(null, antes)).containsExactly(ANDRESSA);
    }

    @Test
    @DisplayName("textoPlano: troca a marcação por @Nome")
    void shouldRenderPlainText() {
        assertThat(MencaoParser.textoPlano("Fala @[Andressa Lima](user:" + ANDRESSA + ") sobre @[ACME $1](cnpj:11222333000181)"))
                .isEqualTo("Fala @Andressa Lima sobre @ACME $1");
        assertThat(MencaoParser.textoPlano(null)).isNull();
    }

    @Test
    @DisplayName("cnpjValido: confere os dois dígitos verificadores")
    void shouldValidateCheckDigits() {
        assertThat(MencaoParser.cnpjValido("11222333000181")).isTrue();
        assertThat(MencaoParser.cnpjValido("11444777000161")).isTrue();
        assertThat(MencaoParser.cnpjValido("11222333000191")).isFalse();
        assertThat(MencaoParser.cnpjValido("1122233300018")).isFalse();
        assertThat(MencaoParser.cnpjValido("11111111111111")).isFalse();
    }
}
