package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialInformationEntity;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialInformationJpaRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyCommercialInformationExportServiceTest {

    @Mock
    private CompanyCommercialInformationJpaRepository commercialInformationJpaRepository;
    @Mock
    private ClientJpaRepository clientJpaRepository;
    @InjectMocks
    private CompanyCommercialInformationExportService service;

    private final ClientEntity zeta = ClientEntity.builder().id(UUID.randomUUID()).name("ZETA LTDA").documentNumber("11222333000181").build();
    private final ClientEntity alfa = ClientEntity.builder().id(UUID.randomUUID()).name("ALFA SA").documentNumber("24155399000148").build();

    private CompanyCommercialInformationEntity ficha(ClientEntity empresa, String parceiro, String data) {
        return CompanyCommercialInformationEntity.builder().id(UUID.randomUUID()).clientId(empresa.getId())
                .partner(parceiro).operationDate(data).build();
    }

    private Sheet exportar(List<CompanyCommercialInformationEntity> fichas) throws Exception {
        when(commercialInformationJpaRepository.findAllById(any())).thenReturn(fichas);
        when(clientJpaRepository.findAllById(any())).thenReturn(List.of(zeta, alfa));
        byte[] bytes = service.exportar(fichas.stream().map(CompanyCommercialInformationEntity::getId).toList());
        return new XSSFWorkbook(new ByteArrayInputStream(bytes)).getSheetAt(0);
    }

    private static Row linha(Sheet aba, int inicio, String rotulo) {
        for (int i = inicio; i <= aba.getLastRowNum(); i++) {
            Row row = aba.getRow(i);
            if (row != null && row.getCell(0) != null && rotulo.equals(row.getCell(0).getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("linha não encontrada: " + rotulo);
    }

    @Test
    @DisplayName("exportar: um bloco por empresa em ordem alfabética; parceiros por data, a mais recente à direita")
    void shouldWriteOneBlockPerCompanySortedByName() throws Exception {
        Sheet aba = exportar(List.of(
                ficha(zeta, "DAVOS", "13/10/2026"),
                ficha(alfa, "ONE7", "12/08/2026"),
                ficha(alfa, "NEO FOMENTO", "17/07/2026")));

        assertThat(aba.getRow(0).getCell(0).getStringCellValue()).isEqualTo("ALFA SA · 24.155.399/0001-48");
        assertThat(aba.getRow(1).getCell(1).getStringCellValue()).isEqualTo("NEO FOMENTO");
        assertThat(aba.getRow(1).getCell(2).getStringCellValue()).isEqualTo("ONE7");
        assertThat(linha(aba, 0, "Data").getCell(1).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 17));

        int segundoBloco = linha(aba, 0, "Observação").getRowNum() + 2;
        assertThat(aba.getRow(segundoBloco).getCell(0).getStringCellValue()).isEqualTo("ZETA LTDA · 11.222.333/0001-81");
        assertThat(aba.getMergedRegions()).anySatisfy(regiao -> {
            assertThat(regiao.getFirstRow()).isEqualTo(0);
            assertThat(regiao.getLastColumn()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("exportar: valores viram número; vencido sai em vermelho só no parceiro que tem vencido")
    void shouldWriteNumbersAndPaintOverdueRed() throws Exception {
        CompanyCommercialInformationEntity comVencido = ficha(alfa, "ONE7", "12/08/2026");
        comVencido.setOverdueValue("447.435,27");
        comVencido.setOverdueDate("25/05/2026");
        comVencido.setCreditLimit("1.700.000,00");
        comVencido.setPunctualPercentage("77,35");
        CompanyCommercialInformationEntity semVencido = ficha(alfa, "NEO", "17/07/2026");

        Sheet aba = exportar(List.of(comVencido, semVencido));

        Cell limite = linha(aba, 0, "Limite").getCell(2);
        assertThat(limite.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(limite.getNumericCellValue()).isEqualTo(1_700_000.00);
        assertThat(linha(aba, 0, "L1 (Pontual)").getCell(2).getNumericCellValue()).isEqualTo(0.7735, org.assertj.core.data.Offset.offset(1e-9));

        Row valorVencido = linha(aba, 0, "Valor vencido");
        assertThat(valorVencido.getCell(2).getNumericCellValue()).isEqualTo(447_435.27);
        assertThat(vermelho(valorVencido.getCell(2))).isTrue();
        assertThat(vermelho(linha(aba, 0, "Data do vencido").getCell(2))).isTrue();
        assertThat(vermelho(valorVencido.getCell(1))).isFalse();
    }

    private static boolean vermelho(Cell cell) {
        XSSFCellStyle estilo = (XSSFCellStyle) cell.getCellStyle();
        return estilo.getFont().getXSSFColor() != null
                && "FFC00000".equals(estilo.getFont().getXSSFColor().getARGBHex());
    }

    @Test
    @DisplayName("numero e data: formato brasileiro converte; texto solto fica nulo")
    void shouldParseBrazilianFormats() {
        assertThat(CompanyCommercialInformationExportService.numero("R$ 1.700.000,00")).isEqualByComparingTo("1700000.00");
        assertThat(CompanyCommercialInformationExportService.numero("sem informação")).isNull();
        assertThat(CompanyCommercialInformationExportService.data("17/07/2026")).isEqualTo(LocalDate.of(2026, 7, 17));
        assertThat(CompanyCommercialInformationExportService.data("julho")).isNull();
    }
}
