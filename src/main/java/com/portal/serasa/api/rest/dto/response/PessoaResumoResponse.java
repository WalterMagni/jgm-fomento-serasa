package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.pessoa.PessoaResumo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Linha da lista de Pessoas Físicas: última consulta Serasa do CPF, já resumida. */
public record PessoaResumoResponse(
        String cpf,
        String nome,
        String cidade,
        String uf,
        LocalDateTime consultaEm,
        /** LIMPO, COM_RESTRICAO ou CRITICO. */
        String situacao,
        int totalOcorrencias,
        BigDecimal dividaTotal,
        List<Restricao> restricoes,
        List<Vinculo> vinculos) {

    /** {@code tipo}: PEFIN, REFIN, CHEQUE, PROTESTO, DIVIDA_VENCIDA, ACAO_JUDICIAL ou FALENCIA. */
    public record Restricao(String tipo, int quantidade, BigDecimal valor) {
    }

    public record Vinculo(String cnpj, String razaoSocial) {
    }

    public static PessoaResumoResponse de(PessoaResumo resumo) {
        return new PessoaResumoResponse(
                resumo.cpf(),
                resumo.nome(),
                resumo.cidade(),
                resumo.uf(),
                resumo.consultaEm(),
                resumo.situacao().name(),
                resumo.totalOcorrencias(),
                resumo.dividaTotal(),
                resumo.restricoes().stream()
                        .map(r -> new Restricao(r.tipo().name(), r.quantidade(), r.valor()))
                        .toList(),
                resumo.vinculos().stream()
                        .map(v -> new Vinculo(v.cnpj(), v.razaoSocial()))
                        .toList());
    }
}
