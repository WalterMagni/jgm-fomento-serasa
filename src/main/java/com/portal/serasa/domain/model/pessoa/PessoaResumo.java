package com.portal.serasa.domain.model.pessoa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Última consulta Serasa de um CPF, resumida para a lista de Pessoas Físicas.
 *
 * @param restricoes só os tipos com ao menos uma ocorrência
 * @param vinculos   empresas da carteira em que a pessoa aparece como sócia
 */
public record PessoaResumo(
        String cpf,
        String nome,
        String cidade,
        String uf,
        LocalDateTime consultaEm,
        List<Restricao> restricoes,
        int totalOcorrencias,
        BigDecimal dividaTotal,
        SituacaoPessoa situacao,
        List<Vinculo> vinculos) {

    public record Restricao(TipoRestricao tipo, int quantidade, BigDecimal valor) {
    }

    /** {@code razaoSocial} pode vir nula: o cadastro manual da carteira às vezes não tem nome. */
    public record Vinculo(String cnpj, String razaoSocial) {
    }
}
