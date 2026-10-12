package com.portal.serasa.application.port.out;

import java.time.LocalDateTime;

/**
 * Última análise de um CPF, só com o que a lista de Pessoas Físicas usa.
 *
 * @param restricoesJson objeto com o {@code summary} ({@code count}, {@code balance}) de cada
 *                       tipo de restrição, chaveado como em {@code TipoRestricao.chave()}
 * @param sociosJson     o array {@code partnershipResponse} do relatório, ou nulo
 */
public record UltimaAnalisePessoa(
        String cpf,
        String nome,
        LocalDateTime consultaEm,
        String cidade,
        String uf,
        String restricoesJson,
        String sociosJson) {
}
