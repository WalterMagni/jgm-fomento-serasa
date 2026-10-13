"use client";

import Icon from "@/components/ui/Icon";
import { RESSALVA } from "@/lib/ressalva";
import { ROTULO_MOTIVO, ROTULO_RESPOSTA, type Aceite, type Decisao } from "@/types/prospeccao";
import { formatDateTime, formatMoeda } from "./formatters";

/**
 * Aba "Decisão e aceite": a proposta vigente em destaque e, abaixo, a conversa com o cliente —
 * decisões e respostas intercaladas na ordem em que aconteceram.
 */
export default function DecisaoAba({
  decisoes,
  aceites,
  legado,
  emRevisao = false,
  acao,
}: {
  decisoes: Decisao[];
  aceites: Aceite[];
  legado: boolean;
  /** Card voltou à análise com a proposta ainda vigente — a contraproposta está sendo avaliada. */
  emRevisao?: boolean;
  /** Botão do estágio atual (decidir, registrar resposta), quando cabe. */
  acao?: React.ReactNode;
}) {
  const vigente = decisoes.find(decisao => decisao.vigente) ?? null;

  if (decisoes.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-slate-300 px-6 py-10 text-center dark:border-slate-700">
        <Icon name="gavel" size={24} className="text-slate-300" />
        <p className="text-sm text-slate-600 dark:text-slate-300">
          {legado ? "Card da esteira antiga: a decisão foi tomada antes de existir este registro." : "Nenhuma decisão ainda."}
        </p>
        <p className="text-xs text-slate-400">A analista dona decide quando o card está em análise.</p>
        {acao}
      </div>
    );
  }

  // Linha do tempo única: decisão e resposta do cliente se alternam.
  const historico = [
    ...decisoes.filter(decisao => !decisao.vigente).map(decisao => ({ tipo: "decisao" as const, em: decisao.decididoEm, decisao })),
    ...aceites.map(aceite => ({ tipo: "aceite" as const, em: aceite.registradoEm, aceite })),
  ].sort((a, b) => b.em.localeCompare(a.em));

  return (
    <div className="space-y-5">
      {vigente && <DecisaoCard decisao={vigente} destaque emRevisao={emRevisao} acao={acao} />}

      {historico.length > 0 && (
        <section>
          <h3 className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">Histórico da negociação</h3>
          <ol className="space-y-2">
            {historico.map(item =>
              item.tipo === "decisao" ? (
                <li key={item.decisao.id}><DecisaoCard decisao={item.decisao} /></li>
              ) : (
                <li key={item.aceite.id}><AceiteLinha aceite={item.aceite} /></li>
              ),
            )}
          </ol>
        </section>
      )}
    </div>
  );
}

function DecisaoCard({ decisao, destaque = false, emRevisao = false, acao }: {
  decisao: Decisao;
  destaque?: boolean;
  emRevisao?: boolean;
  acao?: React.ReactNode;
}) {
  const aprovada = decisao.resultado === "APROVADO";
  return (
    <article
      className={`rounded-xl border p-4 ${
        destaque
          ? aprovada
            ? "border-emerald-200 bg-emerald-50/60 dark:border-emerald-900/50 dark:bg-emerald-950/20"
            : "border-rose-200 bg-rose-50/60 dark:border-rose-900/50 dark:bg-rose-950/20"
          : "border-slate-200 opacity-80 dark:border-slate-700"
      }`}
    >
      <header className="flex flex-wrap items-center justify-between gap-2">
        <span className="inline-flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-white">
          <Icon name={aprovada ? "check_circle" : "block"} size={16} className={aprovada ? "text-emerald-600" : "text-rose-600"} />
          {aprovada
            ? `${decisao.ressalva ? "Aprovado com ressalva" : "Aprovado"} — limite de ${formatMoeda(decisao.limite)}`
            : `Reprovado${decisao.motivoRecusa ? ` · ${ROTULO_MOTIVO[decisao.motivoRecusa]}` : ""}`}
          {destaque && (
            <span className={`rounded px-1.5 py-px text-[10px] font-semibold uppercase ${
              emRevisao ? "bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-200" : "bg-white/80 text-slate-500 dark:bg-slate-900/60"
            }`}>
              {emRevisao ? "em revisão" : "vigente"}
            </span>
          )}
        </span>
        <span className="text-[11px] text-slate-500">
          Decisão {decisao.versao} · {decisao.decididoPorNome ?? "—"} · {formatDateTime(decisao.decididoEm)}
        </span>
      </header>

      {decisao.condicoes.length > 0 && (
        <table className="mt-3 w-full text-sm">
          <tbody>
            {decisao.condicoes.map(condicao => (
              <tr key={condicao.produto} className="border-t border-black/5 dark:border-white/10">
                <td className="py-1.5 text-slate-600 dark:text-slate-300">{condicao.produto}</td>
                <td className="py-1.5 text-right tabular-nums text-slate-900 dark:text-white">{formatMoeda(condicao.valor)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {decisao.ressalva && (
        <p className={`mt-3 rounded-md border px-3 py-2 text-sm ${RESSALVA.caixa}`}>
          <span className="font-semibold">Ressalva:</span> {decisao.ressalva}
        </p>
      )}
      {decisao.parecer && <p className="mt-3 whitespace-pre-line text-sm text-slate-700 dark:text-slate-200">{decisao.parecer}</p>}
      {acao && <div className="mt-3 flex justify-end">{acao}</div>}
    </article>
  );
}

const COR_RESPOSTA = {
  ACEITO: "text-emerald-700 dark:text-emerald-300",
  RECUSADO: "text-rose-700 dark:text-rose-300",
  CONTRAPROPOSTA: "text-[#2956E0] dark:text-sky-300",
} as const;

function AceiteLinha({ aceite }: { aceite: Aceite }) {
  return (
    <article className="rounded-xl border border-dashed border-slate-300 px-4 py-3 text-sm dark:border-slate-600">
      <header className="flex flex-wrap items-center justify-between gap-2">
        <span className={`inline-flex items-center gap-1.5 font-semibold ${COR_RESPOSTA[aceite.resposta]}`}>
          <Icon name="handshake" size={15} />
          Cliente: {ROTULO_RESPOSTA[aceite.resposta].toLowerCase()}
        </span>
        <span className="text-[11px] text-slate-500">
          {aceite.registradoPorNome ?? "—"}
          {aceite.emNomeDoComercial && " (pelo comercial)"} · {formatDateTime(aceite.registradoEm)}
        </span>
      </header>
      {aceite.mensagem && <p className="mt-1 text-slate-700 dark:text-slate-200">“{aceite.mensagem}”</p>}
      {(aceite.limiteSugerido != null || aceite.condicoesSugeridas.length > 0) && (
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
          Pediu
          {aceite.limiteSugerido != null && ` limite de ${formatMoeda(aceite.limiteSugerido)}`}
          {aceite.condicoesSugeridas.length > 0 &&
            `${aceite.limiteSugerido != null ? ";" : ""} ${aceite.condicoesSugeridas.map(c => `${c.produto} ${formatMoeda(c.valor)}`).join(", ")}`}
        </p>
      )}
    </article>
  );
}
