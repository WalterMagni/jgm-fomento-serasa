package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
import com.portal.serasa.domain.model.prospeccao.StatusDocumento;
import com.portal.serasa.infrastructure.persistence.entity.DocumentoTipoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ShareholderCompanyEntity;
import com.portal.serasa.infrastructure.persistence.entity.ShareholderEntity;
import com.portal.serasa.infrastructure.persistence.repository.CompanyDetailJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.DocumentoTipoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDocumentoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ShareholderCompanyJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ShareholderJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Materializa o checklist de um card a partir do catálogo vigente.
 *
 * <p>Roda uma vez, na aprovação. O card guarda cópia de nome e obrigatoriedade para que mexer
 * no catálogo depois não reescreva o que já foi cobrado.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProspeccaoChecklistService {

    private final DocumentoTipoJpaRepository documentoTipoRepository;
    private final ProspeccaoDocumentoJpaRepository documentoRepository;
    private final ShareholderCompanyJpaRepository shareholderCompanyRepository;
    private final ShareholderJpaRepository shareholderRepository;
    private final CompanyDetailJpaRepository companyDetailRepository;

    /**
     * Cria os itens do checklist do card.
     *
     * <p>Os itens de empresa saem direto do catálogo. Os de sócio são multiplicados por cada
     * sócio pessoa física do quadro societário — que já está carregado em
     * {@code shareholders} — e nascem com nome e documento preenchidos. Sem QSA carregado, os
     * itens de sócio não são criados: a analista adiciona o bloco à mão, e o checklist não
     * finge saber o que não sabe.</p>
     */
    @Transactional
    public List<ProspeccaoDocumentoEntity> materializar(ProspeccaoEntity card) {
        List<DocumentoTipoEntity> catalogo = documentoTipoRepository.findByAtivoTrueOrderByEscopoAscOrdemAsc();
        String uf = ufDaEmpresa(card.getCnpj());
        LocalDateTime agora = LocalDateTime.now();

        List<ProspeccaoDocumentoEntity> itens = new ArrayList<>();

        catalogo.stream()
                .filter(tipo -> tipo.getEscopo() == EscopoDocumento.EMPRESA)
                .forEach(tipo -> itens.add(itemDaEmpresa(card, tipo, uf, agora)));

        List<Socio> socios = sociosPessoaFisica(card.getCnpj());
        List<DocumentoTipoEntity> tiposDeSocio = catalogo.stream()
                .filter(tipo -> tipo.getEscopo() == EscopoDocumento.SOCIO)
                .toList();

        for (Socio socio : socios) {
            for (DocumentoTipoEntity tipo : tiposDeSocio) {
                itens.add(itemDoSocio(card, tipo, socio, agora));
            }
        }

        if (socios.isEmpty()) {
            log.info("Checklist do card {} criado sem bloco de sócio: QSA não carregado para o CNPJ {}",
                    card.getId(), card.getCnpj());
        }

        return documentoRepository.saveAll(itens);
    }

    private ProspeccaoDocumentoEntity itemDaEmpresa(ProspeccaoEntity card,
                                                    DocumentoTipoEntity tipo,
                                                    String uf,
                                                    LocalDateTime agora) {
        // Item com UF fixa fora daquela UF já nasce resolvido. A certidão simplificada é
        // retirada na JUCESP: para uma empresa de MG ela não existe, e a planilha registra
        // isso à mão ("Não tem, cliente de MG").
        boolean foraDaUf = tipo.getSomenteUf() != null && uf != null && !tipo.getSomenteUf().equalsIgnoreCase(uf);
        return base(card, tipo, agora)
                .escopo(EscopoDocumento.EMPRESA)
                .status(foraDaUf ? StatusDocumento.NAO_APLICAVEL : StatusDocumento.PENDENTE)
                .motivo(foraDaUf ? "Item exclusivo de " + tipo.getSomenteUf() + "; empresa é de " + uf : null)
                .build();
    }

    private ProspeccaoDocumentoEntity itemDoSocio(ProspeccaoEntity card,
                                                  DocumentoTipoEntity tipo,
                                                  Socio socio,
                                                  LocalDateTime agora) {
        return base(card, tipo, agora)
                .escopo(EscopoDocumento.SOCIO)
                .socioNome(socio.nome())
                .socioDocumento(socio.documento())
                .status(StatusDocumento.PENDENTE)
                .build();
    }

    private ProspeccaoDocumentoEntity.ProspeccaoDocumentoEntityBuilder base(ProspeccaoEntity card,
                                                                            DocumentoTipoEntity tipo,
                                                                            LocalDateTime agora) {
        return ProspeccaoDocumentoEntity.builder()
                .prospeccaoId(card.getId())
                .documentoTipoId(tipo.getId())
                .codigoSnapshot(tipo.getCodigo())
                .nomeSnapshot(tipo.getNome())
                .obrigatorioSnapshot(Boolean.TRUE.equals(tipo.getObrigatorio()))
                .informativoSnapshot(Boolean.TRUE.equals(tipo.getInformativo()))
                .socioAtivo(true)
                .atualizadoEm(agora);
    }

    /** UF da matriz, para decidir os itens com UF fixa. Null quando a empresa não foi enriquecida. */
    private String ufDaEmpresa(String cnpj) {
        return companyDetailRepository.findByDocumentNumber(cnpj)
                .map(detalhe -> detalhe.getState())
                .filter(uf -> uf != null && !uf.isBlank())
                .orElse(null);
    }

    /**
     * Sócios pessoa física da empresa, a partir do quadro societário já consolidado.
     *
     * <p>O vínculo é por raiz do CNPJ (8 dígitos), que é como a Receita publica o quadro: filial
     * não tem sócio próprio.</p>
     */
    private List<Socio> sociosPessoaFisica(String cnpj) {
        if (cnpj == null || cnpj.length() < 8) {
            return List.of();
        }
        List<ShareholderCompanyEntity> vinculos = shareholderCompanyRepository.findByCnpjRaiz(cnpj.substring(0, 8));
        if (vinculos.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = vinculos.stream().map(ShareholderCompanyEntity::getShareholderId).distinct().toList();
        Map<UUID, ShareholderEntity> porId = shareholderRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ShareholderEntity::getId, Function.identity()));

        return vinculos.stream()
                .map(vinculo -> porId.get(vinculo.getShareholderId()))
                .filter(java.util.Objects::nonNull)
                .filter(s -> ShareholderEntity.TYPE_CPF.equals(s.getDocumentType()))
                // O documento completo nem sempre é conhecido: a carga gratuita da Receita traz
                // o CPF mascarado. O nome é o que sempre existe, e é por ele que o bloco é
                // identificado na tela.
                .map(s -> new Socio(s.getName(), s.getDocument()))
                .sorted(Comparator.comparing(Socio::nome))
                .distinct()
                .toList();
    }

    private record Socio(String nome, String documento) {
    }
}
