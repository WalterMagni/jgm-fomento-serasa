package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.infrastructure.persistence.entity.PersonAnalysisEntity;
import com.portal.serasa.infrastructure.persistence.repository.PersonAnalysisJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nome e praça de empresas pelo CNPJ, procurando em todas as bases do portal.
 *
 * <p>A empresa entra no portal por dois caminhos: o CNPJ Já grava em {@code company_details}, e a
 * consulta ao Serasa grava só em {@code clients}. Procurar numa só deixava sacado consultado pelo
 * Serasa aparecendo "sem nome na base". O CNPJ Já vem primeiro porque traz razão social e
 * endereço completos; {@code clients} completa o que faltar.</p>
 *
 * <p>Resolve na hora de mostrar, e não só ao salvar o card: empresa consultada depois que o card
 * foi criado passa a aparecer com nome sem ninguém precisar editar o card.</p>
 *
 * <p>Sacado também pode ser pessoa física: CPF já consultado no Serasa PF entra com o nome e a
 * cidade da última consulta, e conta como cadastrado (tem ficha em Pessoas Físicas).</p>
 */
@Component
@RequiredArgsConstructor
public class EmpresaResolver {

    private final CompanyDetailRepository companyDetailRepository;
    private final ClientRepository clientRepository;
    private final PersonAnalysisJpaRepository personAnalysisRepository;

    /** Quantas pessoas físicas a busca de sacado devolve, depois das empresas. */
    private static final int LIMITE_PF = 5;

    /** Sacado achado pela busca: empresa ou pessoa física que o portal já conhece. */
    public record Encontrado(String documento, String nome, String praca, boolean pessoaFisica) {
    }

    /** O que o portal sabe de uma empresa. {@code cadastrada}: tem página no portal. */
    public record Empresa(String nome, String cidade, String uf, boolean cadastrada) {
        public String praca() {
            if (cidade == null || cidade.isBlank()) {
                return null;
            }
            return uf == null || uf.isBlank() ? cidade : cidade + "/" + uf;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Empresa> resolver(Collection<String> documentos) {
        List<String> cnpjs = documentos.stream()
                .filter(documento -> documento != null && documento.length() == 14)
                .distinct()
                .toList();
        Map<String, Empresa> empresas = new HashMap<>();
        resolverPessoas(documentos, empresas);
        if (cnpjs.isEmpty()) {
            return empresas;
        }
        for (CompanyDetail empresa : companyDetailRepository.findByDocumentNumberIn(cnpjs)) {
            empresas.put(empresa.getDocumentNumber(), new Empresa(texto(empresa.getCompanyName()),
                    texto(empresa.getCity()), texto(empresa.getState()), true));
        }
        for (Client cliente : clientRepository.findByDocumentNumberIn(cnpjs)) {
            Empresa ja = empresas.get(cliente.getDocumentNumber());
            // "Cliente 123…" é o nome provisório que a consulta grava quando o Serasa não trouxe nome.
            String nome = cliente.getName() != null && !cliente.getName().startsWith("Cliente ") ? texto(cliente.getName()) : null;
            if (ja == null) {
                empresas.put(cliente.getDocumentNumber(),
                        new Empresa(nome, texto(cliente.getAddressCity()), texto(cliente.getAddressUf()), true));
            } else {
                empresas.put(cliente.getDocumentNumber(), new Empresa(
                        ja.nome() != null ? ja.nome() : nome,
                        ja.cidade() != null ? ja.cidade() : texto(cliente.getAddressCity()),
                        ja.uf() != null ? ja.uf() : texto(cliente.getAddressUf()),
                        true));
            }
        }
        return empresas;
    }

    /** CPFs com consulta Serasa PF: nome e cidade da última consulta. */
    private void resolverPessoas(Collection<String> documentos, Map<String, Empresa> destino) {
        List<String> cpfs = documentos.stream()
                .filter(documento -> documento != null && documento.length() == 11)
                .distinct()
                .toList();
        if (cpfs.isEmpty()) {
            return;
        }
        for (PersonAnalysisEntity pessoa : personAnalysisRepository.findLatestByCpfIn(cpfs)) {
            destino.putIfAbsent(pessoa.getCpf(), new Empresa(texto(pessoa.getPersonName()),
                    endereco(pessoa.getRegistration(), "city"), endereco(pessoa.getRegistration(), "state"), true));
        }
    }

    /**
     * Busca de sacado por nome ou documento: empresas primeiro (as mesmas da busca do cedente),
     * depois as pessoas físicas já consultadas, uma por CPF.
     */
    @Transactional(readOnly = true)
    public List<Encontrado> buscar(String termo) {
        if (termo == null || termo.trim().length() < 2) {
            return List.of();
        }
        String limpo = termo.trim();
        List<Encontrado> encontrados = new ArrayList<>();
        for (CompanyDetail empresa : companyDetailRepository.searchByNameOrDocument(limpo, 8)) {
            encontrados.add(new Encontrado(empresa.getDocumentNumber(), texto(empresa.getCompanyName()),
                    new Empresa(null, texto(empresa.getCity()), texto(empresa.getState()), true).praca(), false));
        }
        String digitos = limpo.replaceAll("\\D", "");
        // Sem dígitos no termo, um marcador que nunca casa evita o like '%%' pegar todos os CPFs.
        String termoCpf = digitos.isEmpty() ? "###sem-digitos###" : digitos;
        Set<String> vistos = new HashSet<>();
        for (PersonAnalysisEntity pessoa : personAnalysisRepository.findByCpfContainingIgnoreCaseOrPersonNameContainingIgnoreCase(
                termoCpf, limpo, PageRequest.of(0, 30, Sort.by(Sort.Direction.DESC, "consultaEm")))) {
            if (vistos.size() < LIMITE_PF && vistos.add(pessoa.getCpf())) {
                Empresa local = new Empresa(null, endereco(pessoa.getRegistration(), "city"),
                        endereco(pessoa.getRegistration(), "state"), true);
                encontrados.add(new Encontrado(pessoa.getCpf(), texto(pessoa.getPersonName()), local.praca(), true));
            }
        }
        return encontrados;
    }

    private static String endereco(JsonNode cadastro, String campo) {
        return cadastro == null ? null : texto(cadastro.path("address").path(campo).asText(null));
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
