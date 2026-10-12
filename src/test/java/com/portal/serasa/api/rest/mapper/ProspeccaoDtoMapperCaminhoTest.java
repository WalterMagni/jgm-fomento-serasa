package com.portal.serasa.api.rest.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ProspeccaoDtoMapperCaminhoTest {

    @Test
    @DisplayName("sem raiz de rede configurada, o caminho começa em CLIENTES, com barra de Windows")
    void shouldStartAtClientesWithoutNetworkRoot() {
        ProspeccaoDtoMapper mapper = new ProspeccaoDtoMapper(null);

        assertThat(mapper.caminhoVisivel("11222333000181/esteira/empresa/contrato/v1-contrato.pdf"))
                .isEqualTo("CLIENTES\\11222333000181\\esteira\\empresa\\contrato\\v1-contrato.pdf");
        assertThat(mapper.caminhoVisivel(null)).isNull();
    }

    @Test
    @DisplayName("com raiz de rede, o caminho sai pronto para colar no Explorer, sem barra dobrada")
    void shouldUseNetworkRoot() {
        ProspeccaoDtoMapper mapper = new ProspeccaoDtoMapper(null);
        ReflectionTestUtils.setField(mapper, "caminhoRede", "\\\\SRVGinfo\\FM\\Cadastro e Comercial\\CLIENTES\\");

        assertThat(mapper.caminhoVisivel("11222333000181/esteira"))
                .isEqualTo("\\\\SRVGinfo\\FM\\Cadastro e Comercial\\CLIENTES\\11222333000181\\esteira");
    }
}
