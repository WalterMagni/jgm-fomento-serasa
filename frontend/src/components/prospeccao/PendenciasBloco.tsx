"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import TextoRico from "@/components/ui/mencao/TextoRico";
import { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO } from "@/components/liberacao/Dialogo";
import { confirmar } from "@/components/liberacao/formatters";
import { useCancelarPendencia, useResponderPendencia } from "@/hooks/useProspeccao";
import { ROTULO_CAMPO_COMERCIAL, type Pendencia, type ProspeccaoDocumento } from "@/types/prospeccao";
import { formatDateTime } from "./formatters";

/**
 * Pendências do card: as abertas em destaque, com a resposta ali mesmo; as fechadas recolhidas.
 *
 * <p>Na Fase A quem responde é a equipe, com o que o comercial mandou — por isso qualquer pessoa
 * vê o botão. Cancelar é de quem abriu ou da dona do card; o servidor confere.</p>
 */
export default function PendenciasBloco({
  cardId,
  pendencias,
  documentos,
  podeCancelar,
}: {
  cardId: string;
  pendencias: Pendencia[];
  documentos: ProspeccaoDocumento[];
  podeCancelar: boolean;
}) {
  const abertas = pendencias.filter(p => p.status === "ABERTA");
  const fechadas = pendencias.filter(p => p.status !== "ABERTA");
  const nomeDoc = new Map(documentos.map(doc => [doc.id, doc.socioNome ? `${doc.nome} · ${doc.socioNome}` : doc.nome]));

  if (pendencias.length === 0) return null;

  return (
    <section className="space-y-2">
      {abertas.map(pendencia => (
        <PendenciaAberta key={pendencia.id} cardId={cardId} pendencia={pendencia} nomeDoc={nomeDoc} podeCancelar={podeCancelar} />
      ))}
      {fechadas.length > 0 && (
        <details className="group rounded-xl border border-slate-200 px-4 py-2 text-sm dark:border-slate-700">
          <summary className="flex min-h-8 cursor-pointer list-none items-center justify-between text-xs font-medium text-slate-500 [&::-webkit-details-marker]:hidden">
            Pendências anteriores ({fechadas.length})
            <Icon name="expand_more" size={14} className="transition-transform group-open:rotate-180" />
          </summary>
          <ol className="mt-2 space-y-3 pb-1">
            {fechadas.map(pendencia => (
              <li key={pendencia.id} className="border-t border-slate-100 pt-2 first:border-0 first:pt-0 dark:border-slate-800">
                <p className="text-slate-700 dark:text-slate-200"><TextoRico texto={pendencia.pergunta} /></p>
                {pendencia.status === "RESPONDIDA" ? (
                  <p className="mt-1 text-slate-600 dark:text-slate-300">
                    <Icon name="reply" size={12} className="mr-1 inline text-emerald-600" />
                    {pendencia.resposta}
                  </p>
                ) : (
                  <p className="mt-1 text-xs text-slate-400">Cancelada</p>
                )}
                <p className="mt-0.5 text-[11px] text-slate-400">
                  {pendencia.abertaPorNome} · {formatDateTime(pendencia.abertaEm)}
                  {pendencia.respondidaPorNome && ` → ${pendencia.respondidaPorNome}${pendencia.emNomeDoComercial ? " (pelo comercial)" : ""}`}
                </p>
              </li>
            ))}
          </ol>
        </details>
      )}
    </section>
  );
}

function PendenciaAberta({
  cardId,
  pendencia,
  nomeDoc,
  podeCancelar,
}: {
  cardId: string;
  pendencia: Pendencia;
  nomeDoc: Map<string, string>;
  podeCancelar: boolean;
}) {
  const responder = useResponderPendencia();
  const cancelar = useCancelarPendencia();
  const [respondendo, setRespondendo] = useState(false);
  const [resposta, setResposta] = useState("");

  return (
    <article className="rounded-xl border border-[#D1732C]/30 bg-[#D1732C]/5 px-4 py-3 dark:border-[#D1732C]/40 dark:bg-[#D1732C]/10">
      <header className="flex flex-wrap items-center justify-between gap-2">
        <span className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-[#a8551a] dark:text-[#f0a46b]">
          <Icon name="hourglass_top" size={14} />
          Aguardando comercial · prazo parado
        </span>
        <span className="text-[11px] text-slate-500">{pendencia.abertaPorNome} · {formatDateTime(pendencia.abertaEm)}</span>
      </header>
      <p className="mt-2 text-sm text-slate-800 dark:text-slate-100"><TextoRico texto={pendencia.pergunta} /></p>
      {(pendencia.camposExigidos.length > 0 || pendencia.documentosPedidos.length > 0) && (
        <ul className="mt-2 flex flex-wrap gap-1">
          {pendencia.camposExigidos.map(campo => (
            <li key={campo} className="rounded bg-white/80 px-1.5 py-0.5 text-[11px] text-slate-600 dark:bg-slate-900/50 dark:text-slate-300">
              {ROTULO_CAMPO_COMERCIAL[campo] ?? campo}
            </li>
          ))}
          {pendencia.documentosPedidos.map(id => (
            <li key={id} className="inline-flex items-center gap-1 rounded bg-white/80 px-1.5 py-0.5 text-[11px] text-slate-600 dark:bg-slate-900/50 dark:text-slate-300">
              <Icon name="description" size={11} />
              {nomeDoc.get(id) ?? "documento"}
            </li>
          ))}
        </ul>
      )}

      {respondendo ? (
        <div className="mt-3 space-y-2">
          <textarea
            rows={3}
            autoFocus
            value={resposta}
            onChange={event => setResposta(event.target.value)}
            placeholder="O que o comercial respondeu ou mandou"
            aria-label="Resposta da pendência"
            className={`${CAMPO} resize-y`}
          />
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setRespondendo(false)} className={BOTAO_SECUNDARIO}>Cancelar</button>
            <button
              type="button"
              disabled={!resposta.trim() || responder.isPending}
              onClick={() => responder.mutate({ id: cardId, pendenciaId: pendencia.id, texto: resposta.trim() })}
              className={BOTAO_PRIMARIO}
            >
              {responder.isPending ? "Registrando…" : "Registrar resposta"}
            </button>
          </div>
        </div>
      ) : (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <button type="button" onClick={() => setRespondendo(true)} className={BOTAO_PRIMARIO}>
            <Icon name="reply" size={15} /> Responder
          </button>
          {podeCancelar && (
            <button
              type="button"
              disabled={cancelar.isPending}
              onClick={() => confirmar("Cancelar esta pendência? O prazo volta a correr se não houver outra aberta.") &&
                cancelar.mutate({ id: cardId, pendenciaId: pendencia.id })}
              className="cursor-pointer text-xs text-slate-500 hover:text-rose-700 hover:underline"
            >
              cancelar pendência
            </button>
          )}
        </div>
      )}
    </article>
  );
}
