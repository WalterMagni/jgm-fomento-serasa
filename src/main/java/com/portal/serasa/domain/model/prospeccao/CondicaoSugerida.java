package com.portal.serasa.domain.model.prospeccao;

import java.math.BigDecimal;

/** Produto e valor que o cliente pediu na contraproposta. Guardado em JSONB — ver V75. */
public record CondicaoSugerida(String produto, BigDecimal valor) {
}
