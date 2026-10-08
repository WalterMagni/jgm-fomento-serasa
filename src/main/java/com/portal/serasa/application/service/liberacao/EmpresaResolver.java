package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.domain.model.CompanyDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 */
@Component
@RequiredArgsConstructor
public class EmpresaResolver {

    private final CompanyDetailRepository companyDetailRepository;
    private final ClientRepository clientRepository;

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

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
