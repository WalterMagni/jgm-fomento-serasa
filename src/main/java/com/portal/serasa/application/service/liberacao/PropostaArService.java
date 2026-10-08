package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.application.service.liberacao.PropostaArParser.Leitura;
import com.portal.serasa.application.service.liberacao.PropostaArParser.SacadoLido;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.domain.model.liberacao.CarteiraSacado;
import com.portal.serasa.domain.model.liberacao.PropostaAr;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Importa a proposta da operação a partir do PDF da Análise de Risco (AR).
 *
 * <p>Só lê e devolve: nada é gravado aqui. A tela pré-preenche o formulário do card com o
 * resultado, a pessoa confere, e a proposta vai junto quando o card é salvo.</p>
 *
 * <p>O relatório não traz o CNPJ do cedente, só o código do cliente no sistema de operações. A
 * ponte é a mesma da Praça de Pagamento: {@code clients.client_code}, sem zeros à esquerda.</p>
 */
@Service
@RequiredArgsConstructor
public class PropostaArService {

    static final int LIMITE_BYTES = 10 * 1024 * 1024;

    private final PropostaArParser parser;
    private final ClientRepository clientRepository;
    private final EmpresaResolver empresaResolver;

    /** {@code valor}: face do sacado nesta proposta. */
    public record SacadoImportado(String documento, String nome, BigDecimal valor, CarteiraSacado carteira) {
    }

    /**
     * @param cedenteCnpj       nulo quando o código do cliente não está vinculado a uma empresa do portal
     * @param cedenteCadastrado o CNPJ tem página de empresa no portal
     * @param semTitulo   sacados que aparecem na AR sem título nesta proposta; não entram no card
     */
    public record PropostaImportada(String cedenteCnpj, String cedenteNome, boolean cedenteCadastrado,
                                    String clienteCodigo, String clienteNome,
                                    PropostaAr proposta, List<SacadoImportado> sacados,
                                    List<SacadoImportado> semTitulo, List<String> avisos) {
    }

    public PropostaImportada importar(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new IllegalArgumentException("Arquivo vazio.");
        }
        if (pdf.length > LIMITE_BYTES) {
            throw new IllegalArgumentException("PDF acima de 10 MB.");
        }
        if (pdf.length < 5 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
            throw new IllegalArgumentException("O arquivo não é um PDF.");
        }
        return montar(parser.ler(pdf));
    }

    PropostaImportada montar(Leitura leitura) {
        List<String> avisos = new ArrayList<>();

        String codigo = codigoCanonico(leitura.clienteCodigo());
        Optional<Client> cliente = codigo == null ? Optional.empty() : clientRepository.findByClientCode(codigo);
        String cnpj = cliente.map(Client::getDocumentNumber)
                .map(documento -> documento.replaceAll("\\D", ""))
                .filter(documento -> documento.length() == 14)
                .orElse(null);
        String cedenteNome = leitura.clienteNome();
        boolean cedenteCadastrado = false;
        if (cnpj != null) {
            EmpresaResolver.Empresa empresa = empresaResolver.resolver(List.of(cnpj)).get(cnpj);
            cedenteCadastrado = empresa != null && empresa.cadastrada();
            if (empresa != null && empresa.nome() != null) {
                cedenteNome = empresa.nome();
            }
        } else if (leitura.clienteCodigo() != null) {
            avisos.add("O código " + leitura.clienteCodigo() + " (" + leitura.clienteNome() + ") não está vinculado a uma"
                    + " empresa do portal. Informe o CNPJ do cedente; depois vincule o código na página da empresa.");
        }

        // Mesmo documento em duas linhas (dois códigos internos, ou relatório quebrado em páginas):
        // é o mesmo sacado no card, então face, títulos e carteira se somam.
        Map<String, SacadoImportado> porDocumento = new LinkedHashMap<>();
        for (SacadoLido lido : leitura.sacados()) {
            porDocumento.merge(lido.documento(),
                    new SacadoImportado(lido.documento(), lido.nome(), lido.face(), lido.carteira()),
                    (primeiro, outro) -> new SacadoImportado(primeiro.documento(), primeiro.nome(),
                            somar(primeiro.valor(), outro.valor()), somar(primeiro.carteira(), outro.carteira())));
        }
        List<SacadoImportado> comTitulo = porDocumento.values().stream().filter(sacado -> titulos(sacado) > 0).toList();
        List<SacadoImportado> semTitulo = porDocumento.values().stream().filter(sacado -> titulos(sacado) == 0).toList();

        if (!semTitulo.isEmpty()) {
            avisos.add(semTitulo.size() == 1
                    ? "1 sacado sem título nesta proposta ficou de fora: " + descrever(semTitulo) + "."
                    : semTitulo.size() + " sacados sem título nesta proposta ficaram de fora: " + descrever(semTitulo) + ".");
        }

        int somaTitulos = leitura.sacados().stream().mapToInt(sacado -> sacado.carteira().titulos()).sum();
        BigDecimal somaFace = leitura.sacados().stream().map(SacadoLido::face).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (leitura.totalFace() == null) {
            avisos.add("O relatório não trouxe a linha de total dos sacados. Confira a lista antes de salvar.");
        } else if (somaFace.compareTo(leitura.totalFace()) != 0 || somaTitulos != leitura.totalTitulos()) {
            avisos.add("A soma dos sacados lidos (" + moeda(somaFace) + ", " + somaTitulos + " títulos) não bate com o total"
                    + " do relatório (" + moeda(leitura.totalFace()) + ", " + leitura.totalTitulos()
                    + " títulos). Confira a lista antes de salvar.");
        }

        PropostaAr proposta = leitura.resumo();
        if (proposta.qtdTotal() != null && proposta.qtdLiberados() != null
                && !proposta.qtdTotal().equals(proposta.qtdLiberados()) && proposta.valorTotal() != null) {
            avisos.add("A proposta tem " + proposta.qtdTotal() + " títulos (" + moeda(proposta.valorTotal()) + "); entram no card"
                    + " os " + proposta.qtdLiberados() + " liberados (" + moeda(proposta.faceLiberados()) + ").");
        }

        return new PropostaImportada(cnpj, cedenteNome, cedenteCadastrado, leitura.clienteCodigo(), leitura.clienteNome(), proposta,
                comTitulo, semTitulo, List.copyOf(avisos));
    }

    /** Mesma forma canônica do {@code client_code}: dígitos, sem zeros à esquerda. */
    private static String codigoCanonico(String codigo) {
        if (codigo == null) {
            return null;
        }
        String digitos = codigo.replaceAll("\\D", "").replaceFirst("^0+", "");
        return digitos.isEmpty() ? null : digitos;
    }

    private static CarteiraSacado somar(CarteiraSacado a, CarteiraSacado b) {
        if (a == null || b == null) {
            return a == null ? b : a;
        }
        return new CarteiraSacado(
                (a.titulos() == null ? 0 : a.titulos()) + (b.titulos() == null ? 0 : b.titulos()),
                somar(a.vencidos(), b.vencidos()), somar(a.vincendos(), b.vincendos()), somar(a.abertos(), b.abertos()),
                somar(a.liquidados(), b.liquidados()), somar(a.recomprados(), b.recomprados()));
    }

    private static BigDecimal somar(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null ? b : a;
        }
        return a.add(b);
    }

    private static int titulos(SacadoImportado sacado) {
        return sacado.carteira() == null || sacado.carteira().titulos() == null ? 0 : sacado.carteira().titulos();
    }

    private static String descrever(List<SacadoImportado> sacados) {
        return sacados.stream()
                .map(sacado -> sacado.nome() + " (" + LiberacaoService.formatarDocumento(sacado.documento()) + ")")
                .collect(Collectors.joining(", "));
    }

    private static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor);
    }
}
