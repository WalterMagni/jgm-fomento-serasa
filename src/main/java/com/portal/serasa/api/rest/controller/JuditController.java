package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.service.judit.JuditExportService;
import com.portal.serasa.application.service.judit.JuditService;
import com.portal.serasa.domain.model.PoloJudit;
import com.portal.serasa.infrastructure.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Processos judiciais da empresa pela Judit: o card da Visão 360º e a página de processos.
 *
 * <p>Cobram: {@code POST /resumo} (pré-consulta), {@code POST /on-demand} e o pedido de detalhe; as
 * travas (marca Judit, limite do mês) ficam no serviço. Os GET, os "verificar" e a exportação leem o
 * que já foi pago.</p>
 */
@RestController
@RequestMapping("/api/v1/company/{cnpj}/judit")
@RequiredArgsConstructor
public class JuditController {

    private final JuditService juditService;
    private final JuditExportService exportService;
    private final UsuarioLogado usuarioLogado;

    /** @param cnjs processos na ordem da tela, já filtrados; vazio exporta todos */
    public record ExportacaoRequest(List<String> cnjs) {
    }

    /** @param nomeEmpresa vai no painel de consumo e no link da notificação, para a página abrir com o nome */
    public record DetalheRequest(String nomeEmpresa) {
    }

    /** @param polo PASSIVO (ré, o padrão da tela) ou ATIVO (autora) */
    public record OnDemandRequest(PoloJudit polo, String nomeEmpresa) {
    }

    @GetMapping
    public ResponseEntity<JuditService.Situacao> situacao(@PathVariable String cnpj) {
        return ResponseEntity.ok(juditService.situacao(cnpj, usuarioLogado.obter()));
    }

    /** A lista inteira da última busca. Não cobra. 204 se nunca buscou. */
    @GetMapping("/processos")
    public ResponseEntity<JuditService.ProcessosConsultados> processos(@PathVariable String cnpj) {
        usuarioLogado.obter();
        JuditService.ProcessosConsultados processos = juditService.processos(cnpj);
        return processos == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(processos);
    }

    /** Planilha dos processos que a tela mostra. POST porque a lista de números pode ser longa. Não cobra. */
    @PostMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@PathVariable String cnpj, @RequestBody(required = false) ExportacaoRequest request) {
        usuarioLogado.obter();
        JuditExportService.Planilha planilha = exportService.exportar(cnpj, request == null ? null : request.cnjs());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(planilha.nome()).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(planilha.conteudo());
    }

    /** O último detalhe do processo. Não cobra. 204 se nunca foi detalhado. */
    @GetMapping("/processos/{cnj}/detalhe")
    public ResponseEntity<JuditService.DetalheConsultado> detalhe(@PathVariable String cnpj, @PathVariable String cnj) {
        usuarioLogado.obter();
        JuditService.DetalheConsultado detalhe = juditService.detalhe(cnpj, cnj);
        return detalhe == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(detalhe);
    }

    /** Pede o detalhe à Judit. Cobra. Volta PENDENTE; o sino avisa quando ficar pronto. */
    @PostMapping("/processos/{cnj}/detalhe")
    public ResponseEntity<JuditService.DetalheConsultado> detalhar(@PathVariable String cnpj, @PathVariable String cnj,
                                                                   @RequestBody(required = false) DetalheRequest request) {
        return ResponseEntity.ok(juditService.detalhar(cnpj, cnj, request == null ? null : request.nomeEmpresa(),
                usuarioLogado.obter()));
    }

    /** Retoma o acompanhamento de um detalhe pendente. Não cobra. */
    @PostMapping("/processos/{cnj}/detalhe/verificar")
    public ResponseEntity<JuditService.DetalheConsultado> verificar(@PathVariable String cnpj, @PathVariable String cnj) {
        usuarioLogado.obter();
        return ResponseEntity.ok(juditService.verificar(cnpj, cnj));
    }

    /** Pré-consulta: o resumo do Data Lake, com pontos de atenção e a estimativa do On Demand. Cobra. */
    @PostMapping("/resumo")
    public ResponseEntity<JuditService.Situacao> consultarResumo(@PathVariable String cnpj,
                                                                 @RequestBody(required = false) DetalheRequest request) {
        return ResponseEntity.ok(juditService.consultarResumo(cnpj, request == null ? null : request.nomeEmpresa(),
                usuarioLogado.obter()));
    }

    /** Pede o On Demand de um polo. Cobra a estimativa; volta PENDENTE e o sino avisa quando ficar pronto. */
    @PostMapping("/on-demand")
    public ResponseEntity<JuditService.Situacao> consultarOnDemand(@PathVariable String cnpj,
                                                                   @RequestBody OnDemandRequest request) {
        return ResponseEntity.ok(juditService.consultarOnDemand(cnpj, request.polo(), request.nomeEmpresa(),
                usuarioLogado.obter()));
    }

    /** Retoma o acompanhamento de um On Demand pendente. Não cobra. */
    @PostMapping("/on-demand/verificar")
    public ResponseEntity<JuditService.Situacao> verificarOnDemand(@PathVariable String cnpj,
                                                                   @RequestBody OnDemandRequest request) {
        return ResponseEntity.ok(juditService.verificarOnDemand(cnpj, request.polo(), usuarioLogado.obter()));
    }
}
