package com.portal.serasa.domain.exception;

/**
 * Falha ao consultar uma API externa (CNPJ Já, Serasa, Bacen...).
 *
 * <p>Carrega o nome do provedor e o status HTTP original para que o handler global
 * devolva uma mensagem em português para a tela, em vez de vazar o corpo bruto do
 * provedor (ex.: {@code {"code":500,"message":"unexpected error","traceId":"..."}}).</p>
 */
public class ExternalApiException extends DomainException {

    private final String provider;
    private final int upstreamStatus;

    public ExternalApiException(String provider, int upstreamStatus, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.upstreamStatus = upstreamStatus;
    }

    public String getProvider() {
        return provider;
    }

    /** Status devolvido pelo provedor; 0 quando nem houve resposta (timeout/DNS). */
    public int getUpstreamStatus() {
        return upstreamStatus;
    }

    /** true quando o problema é do provedor (5xx/indisponível), não do CNPJ consultado. */
    public boolean isUpstreamOutage() {
        return upstreamStatus == 0 || upstreamStatus >= 500;
    }
}
