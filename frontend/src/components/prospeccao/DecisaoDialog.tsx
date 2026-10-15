"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { RESSALVA } from "@/lib/ressalva";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "@/components/liberacao/Dialogo";
import { useDecidir } from "@/hooks/useProspeccao";
import { ROTULO_MOTIVO, type Decisao, type MotivoRecusa, type ResultadoDecisao } from "@/types/prospeccao";
import { formatMoeda } from "./formatters";

const MOTIVOS = Object.keys(ROTULO_MOTIVO) as MotivoRecusa[];

const BOTAO_REPROVAR =
  "inline-flex min-h-10 cursor-pointer items-center justify-center gap-1.5 rounded-lg bg-rose-700 px-4 text-sm font-medium text-white shadow-sm " +
  "transition-colors hover:bg-rose-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-600 focus-visible:ring-offset-2 " +
  "disabled:cursor-not-allowed disabled:opacity-50 dark:focus-visible:ring-offset-slate-900";

/**
 * Decisão da análise — a análise inicial.
 *
 * <p>Aprovar, com ou sem ressalva, manda o comercial juntar os documentos. Limite e condições por
 * produto não se definem aqui: são do comitê, depois dos documentos (pedido do time em
 * 2026-10-15). Reprovar pede o motivo da lista fechada. Quando há pedido do cliente, ele fica à
 * vista.</p>
 */
export default function DecisaoDialog({
  cardId,
  razaoSocial,
  anterior,
  pedidoCliente,
  onFechar,
}: {
  cardId: string;
  razaoSocial: string;
  /** Decisão vigente anterior, quando há — ponto de partida da nova. */
  anterior?: Decisao | null;
  /** Contraproposta do cliente, quando foi ela que trouxe o card de volta. */
  pedidoCliente?: { mensagem: string | null; limiteSugerido: number | null; condicoes: { produto: string; valor: number }[] } | null;
  onFechar: () => void;
}) {
  const decidir = useDecidir();
  const base = anterior?.resultado === "APROVADO" ? anterior : null;
  const [resultado, setResultado] = useState<ResultadoDecisao>("APROVADO");
  const [parecer, setParecer] = useState("");
  const [motivo, setMotivo] = useState<MotivoRecusa | "">("");
  const [observacao, setObservacao] = useState("");
  const [comRessalva, setComRessalva] = useState(Boolean(base?.ressalva));
  const [ressalva, setRessalva] = useState(base?.ressalva ?? "");

  const aprova = resultado === "APROVADO";
  const valido = aprova
    ? !comRessalva || ressalva.trim().length > 0
    : motivo !== "" && (motivo !== "OUTRO" || observacao.trim().length > 0);

  function enviar() {
    if (!valido || decidir.isPending) return;
    decidir.mutate(
      {
        id: cardId,
        decisao: aprova
          ? { resultado, parecer: parecer.trim() || undefined, ressalva: comRessalva ? ressalva.trim() : undefined }
          : {
              resultado,
              motivoRecusa: motivo as MotivoRecusa,
              observacao: observacao.trim() || undefined,
              parecer: parecer.trim() || undefined,
            },
      },
      { onSuccess: onFechar },
    );
  }

  return (
    <Dialogo
      titulo="Decisão da análise"
      subtitulo={razaoSocial}
      onFechar={onFechar}
      largura="lg"
      rodape={
        <>
          <button type="button" onClick={onFechar} className={BOTAO_SECUNDARIO}>Cancelar</button>
          <button
            type="button"
            onClick={enviar}
            disabled={!valido || decidir.isPending}
            className={aprova ? BOTAO_PRIMARIO : BOTAO_REPROVAR}
          >
            <Icon name={aprova ? "check" : "block"} size={16} />
            {decidir.isPending ? "Registrando…" : aprova ? "Aprovar e avisar o comercial" : "Reprovar"}
          </button>
        </>
      }
    >
      <div className="space-y-5 px-5 py-4">
        {pedidoCliente && (
          <div className="rounded-xl border border-[#2956E0]/20 bg-[#2956E0]/5 px-4 py-3 text-sm dark:border-sky-400/20 dark:bg-sky-400/10">
            <p className="font-medium text-slate-800 dark:text-slate-100">Contraproposta do cliente</p>
            {pedidoCliente.mensagem && <p className="mt-0.5 text-slate-600 dark:text-slate-300">“{pedidoCliente.mensagem}”</p>}
            {(pedidoCliente.limiteSugerido != null || pedidoCliente.condicoes.length > 0) && (
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Pede
                {pedidoCliente.limiteSugerido != null && ` limite de ${formatMoeda(pedidoCliente.limiteSugerido)}`}
                {pedidoCliente.condicoes.length > 0 &&
                  `${pedidoCliente.limiteSugerido != null ? ";" : ""} ${pedidoCliente.condicoes.map(c => `${c.produto} ${formatMoeda(c.valor)}`).join(", ")}`}
              </p>
            )}
          </div>
        )}

        <div role="radiogroup" aria-label="Resultado" className="grid grid-cols-2 gap-2">
          {(["APROVADO", "REPROVADO"] as ResultadoDecisao[]).map(opcao => (
            <button
              key={opcao}
              type="button"
              role="radio"
              aria-checked={resultado === opcao}
              onClick={() => setResultado(opcao)}
              className={`flex min-h-12 cursor-pointer items-center justify-center gap-2 rounded-xl border-2 text-sm font-semibold transition-colors
                focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] ${
                  resultado === opcao
                    ? opcao === "APROVADO"
                      ? "border-emerald-600 bg-emerald-50 text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200"
                      : "border-rose-600 bg-rose-50 text-rose-800 dark:bg-rose-950/40 dark:text-rose-200"
                    : "border-slate-200 text-slate-500 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
                }`}
            >
              <Icon name={opcao === "APROVADO" ? "check_circle" : "block"} size={18} />
              {opcao === "APROVADO" ? "Aprovar" : "Reprovar"}
            </button>
          ))}
        </div>

        {aprova ? (
          <>
            <p className="flex gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600 dark:bg-slate-800/60 dark:text-slate-300">
              <Icon name="info" size={14} className="mt-px shrink-0" />
              Análise inicial: aprovar avisa o comercial para juntar os documentos. Limite e produtos são definidos no comitê.
            </p>
            <div className={`rounded-lg border px-3 py-2.5 ${comRessalva ? RESSALVA.caixa : "border-slate-200 dark:border-slate-700"}`}>
              <label className="flex cursor-pointer items-center gap-2 text-sm font-medium">
                <input
                  type="checkbox"
                  checked={comRessalva}
                  onChange={event => setComRessalva(event.target.checked)}
                  className="h-4 w-4 accent-yellow-500"
                />
                Aprovar com ressalva
              </label>
              {comRessalva && (
                <textarea
                  value={ressalva}
                  onChange={event => setRessalva(event.target.value)}
                  rows={2}
                  maxLength={4000}
                  autoFocus
                  placeholder="Qual é a ressalva? Ex.: exigir aval dos sócios, limitar a duplicata…"
                  aria-label="Texto da ressalva"
                  className="mt-2 w-full resize-y rounded-md border border-yellow-300 bg-white px-2.5 py-2 text-sm text-slate-800 outline-none focus:border-yellow-500 dark:bg-slate-900 dark:text-slate-100"
                />
              )}
            </div>
          </>
        ) : (
          <>
            <fieldset>
              <legend className={ROTULO}>Motivo</legend>
              <select value={motivo} onChange={event => setMotivo(event.target.value as MotivoRecusa)} className={`${CAMPO} cursor-pointer`}>
                <option value="">Escolha o motivo…</option>
                {MOTIVOS.map(opcao => (
                  <option key={opcao} value={opcao}>{ROTULO_MOTIVO[opcao]}</option>
                ))}
              </select>
            </fieldset>
            <label className="block">
              <span className={ROTULO}>Observação{motivo === "OUTRO" ? "" : " (opcional)"}</span>
              <textarea rows={2} value={observacao} onChange={event => setObservacao(event.target.value)} className={`${CAMPO} resize-y`} />
            </label>
          </>
        )}

        <label className="block">
          <span className={ROTULO}>Parecer (opcional)</span>
          <textarea
            rows={4}
            value={parecer}
            onChange={event => setParecer(event.target.value)}
            placeholder={aprova ? "O que pesou na aprovação" : "O que pesou na reprovação"}
            className={`${CAMPO} resize-y`}
          />
        </label>
      </div>
    </Dialogo>
  );
}
