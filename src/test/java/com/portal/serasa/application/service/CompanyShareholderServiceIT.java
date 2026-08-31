package com.portal.serasa.application.service;

import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.infrastructure.persistence.entity.ShareholderEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercita o quadro societário contra as bases reais (Postgres da aplicação + cópia local da
 * Receita). Depende de dois bancos de pé, então fica fora da suíte padrão — rode com
 * {@code mvn test -Dtest=CompanyShareholderServiceIT -Dreceita.it=true}.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "receita.it", matches = "true")
class CompanyShareholderServiceIT {

    /** LIPSON COSMETICOS — empresa da carteira cujos sócios também estão no QSA do Serasa. */
    private static final String LIPSON = "61610515";

    /** CNPJ completo da Lipson, que tem QSA da Serasa gravado em credit_analysis. */
    private static final String LIPSON_CNPJ = "61610515000106";

    @Autowired
    private CompanyShareholderService service;

    @Autowired
    private CreditAnalysisRepository creditAnalysisRepository;

    @Test
    @DisplayName("sincroniza socios da Receita e encontra o grupo economico pelo socio comum")
    void shouldSyncShareholdersAndFindEconomicGroup() {
        List<ShareholderEntity> shareholders = service.syncRelatedCompanies(LIPSON);

        assertThat(shareholders).isNotEmpty();
        assertThat(shareholders).allSatisfy(s -> {
            assertThat(s.getName()).isNotBlank();
            assertThat(s.getDocumentMask()).isNotBlank();
        });

        // Nao assume a origem: outro teste desta classe pode ja ter aplicado o CPF completo, e a
        // base persiste entre execucoes. O que sempre vale e o par origem/documento ser coerente.
        assertThat(shareholders).allSatisfy(s -> {
            assertThat(s.getDocumentSource())
                    .isIn(ShareholderEntity.SOURCE_RECEITA_MASK, ShareholderEntity.SOURCE_SERASA);
            if (ShareholderEntity.SOURCE_SERASA.equals(s.getDocumentSource())) {
                assertThat(s.hasFullDocument()).isTrue();
            }
            // A mascara nunca pode divergir do documento completo, venha ele de onde vier.
            if (s.hasFullDocument() && ShareholderEntity.TYPE_CPF.equals(s.getDocumentType())) {
                assertThat(s.getDocumentMask()).isEqualTo(CpfMask.fromCpf(s.getDocument()));
            }
        });

        List<String> related = service.findRelatedRoots(LIPSON);
        assertThat(related).isNotEmpty();
        assertThat(related).doesNotContain(LIPSON);
    }

    @Test
    @DisplayName("partes ligadas: detecta socio em comum entre duas empresas da carteira")
    void shouldDetectSharedShareholderBetweenTwoCompanies() {
        // Par real da carteira: ALFA FOODS e CIA DOS PAES compartilham RODRIGO OLIVEIRA CAMPOS.
        String alfaFoods = "50738256";
        String ciaDosPaes = "51492464";
        service.syncShareholders(alfaFoods);
        service.syncShareholders(ciaDosPaes);

        List<String> shared = service.findSharedShareholders(alfaFoods, ciaDosPaes);

        assertThat(shared).contains("RODRIGO OLIVEIRA CAMPOS");
        assertThat(service.shareShareholder(alfaFoods, ciaDosPaes)).isTrue();
        // Simetrico: a ordem dos argumentos nao pode mudar o resultado.
        assertThat(service.findSharedShareholders(ciaDosPaes, alfaFoods)).isEqualTo(shared);
    }

    @Test
    @DisplayName("partes ligadas: nao acusa vinculo entre empresas sem socio em comum")
    void shouldNotFlagUnrelatedCompanies() {
        service.syncShareholders(LIPSON);
        service.syncShareholders("50738256");

        assertThat(service.findSharedShareholders(LIPSON, "50738256")).isEmpty();
        // Mesma empresa nunca conta como parte ligada.
        assertThat(service.findSharedShareholders(LIPSON, LIPSON)).isEmpty();
    }

    @Test
    @DisplayName("importa o QSA gravado pela Serasa e preenche o CPF completo de todos os socios")
    void shouldImportSerasaQsaWithFullCpfs() {
        service.syncShareholders(LIPSON_CNPJ);

        CreditAnalysis analysis = creditAnalysisRepository.findLatestByCnpj(LIPSON_CNPJ).orElseThrow();
        int imported = service.importSerasaQsa(LIPSON_CNPJ, analysis.getPartnerDetails());

        assertThat(imported).isPositive();

        // Depois do import, os socios PF passam a ter documento completo, nao so a mascara.
        List<ShareholderEntity> shareholders = service.syncShareholders(LIPSON_CNPJ);
        assertThat(shareholders)
                .filteredOn(s -> ShareholderEntity.TYPE_CPF.equals(s.getDocumentType()))
                .isNotEmpty()
                .allSatisfy(s -> {
                    assertThat(s.hasFullDocument()).isTrue();
                    assertThat(s.getDocumentSource()).isEqualTo(ShareholderEntity.SOURCE_SERASA);
                    // A mascara guardada tem que continuar coerente com o CPF completo.
                    assertThat(s.getDocumentMask()).isEqualTo(CpfMask.fromCpf(s.getDocument()));
                });
    }

    @Test
    @DisplayName("aplica o CPF completo do Serasa sobre o socio ja conhecido pela mascara")
    void shouldUpgradeShareholderWithFullCpfFromSerasa() {
        service.syncShareholders(LIPSON);

        // Par real: CPF completo do QSA do Serasa e o nome como a Receita publica.
        Optional<ShareholderEntity> upgraded =
                service.applyFullDocument("04321650887", "ALESSIO DE TOLEDO RODRIGUES");

        assertThat(upgraded).isPresent();
        assertThat(upgraded.get().getDocument()).isEqualTo("04321650887");
        assertThat(upgraded.get().getDocumentMask()).isEqualTo("***216508**");
        assertThat(upgraded.get().getDocumentSource()).isEqualTo(ShareholderEntity.SOURCE_SERASA);

        // Upgrade in-place: nao pode ter criado um segundo socio para a mesma pessoa.
        List<ShareholderEntity> shareholders = service.syncShareholders(LIPSON);
        assertThat(shareholders)
                .filteredOn(s -> "ALESSIO DE TOLEDO RODRIGUES".equals(s.getName()))
                .hasSize(1);
    }
}
