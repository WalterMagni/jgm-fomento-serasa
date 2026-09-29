package com.portal.serasa.domain.model.prospeccao;

/**
 * Papel de quem entrega documento pessoal no checklist.
 *
 * <p>O avalista entrega os mesmos documentos do sócio, mas não consta do quadro societário — por
 * isso é adicionado à mão pela analista, e não sai da carga da Receita.</p>
 */
public enum PapelPessoa {
    SOCIO,
    AVALISTA
}
