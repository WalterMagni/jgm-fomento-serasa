"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Icon from "@/components/ui/Icon";
import { EstagioProspeccao, MotivoRecusa, ROTULO_ESTAGIO, ROTULO_MOTIVO } from "@/types/prospeccao";
import {
  useAssumirProspeccao,
  useProspeccaoDetalhe,
  useReabrirProspeccao,
  useTransicionarProspeccao,
} from "@/hooks/useProspeccao";
import DocumentChecklist from "./DocumentChecklist";
import ProspeccaoTimeline from "./ProspeccaoTimeline";
import { CLASSE_BADGE_SEMAFORO, formatCnpj, formatDate, semaforoDe, textoPrazo } from "./formatters";

const MOTIVOS: MotivoRecusa[] = [
  "QUANTIDADE_DE_RESTRICOES",
  "SEGMENTO",
  "PEFIN_COM_FUNDO",
  "PROCESSO_COM_FIDC",
  "RECUPERACAO_JUDICIAL",
  "DECISAO_DIRETORIA",
  "OUTRO",
];

export default function ProspeccaoModal({ cardId, onFechar }: { cardId: string; onFechar: () => void }) {
  const { data, isLoading, error } = useProspeccaoDetalhe(cardId);
  const assumir = useAssumirProspeccao();
  const transicionar = useTransicionarProspeccao();
  const reabrir = useReabrirProspeccao();
  const [aba, setAba] = useState<"documentos" | "timeline">("documentos");

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") onFechar();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onFechar]);

  const card = data?.card;

  /** Terminal negativo pede motivo do enum; OUTRO pede também a observação. */
  function mover(destino: EstagioProspeccao) {
    const exigeMotivo = destino === "REPROVADO" || destino === "REMOVIDO_RADAR";
    let motivoRecusa: MotivoRecusa | undefined;
    let observacao: string | undefined;

    if (exigeMotivo) {
      const escolha = window.prompt(
        `Motivo para ${ROTULO_ESTAGIO[destino].toLowerCase()}:\n\n${MOTIVOS.map((motivo, i) => `${i + 1}. ${ROTULO_MOTIVO[motivo]}`).join("\n")}\n\nDigite o número:`,
      );
      const indice = Number(escolha) - 1;
      if (!escolha || Number.isNaN(indice) || indice < 0 || indice >= MOTIVOS.length) return;
      motivoRecusa = MOTIVOS[indice];

      if (motivoRecusa === "OUTRO") {
        const texto = window.prompt("Descreva o motivo:");
        if (!texto || !texto.trim()) return;
        observacao = texto.trim();
      }
    }
    transicionar.mutate({ id: cardId, estagio: destino, motivoRecusa, observacao });
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={event => {
        if (event.target === event.currentTarget) onFechar();
      }}
    >
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col rounded-xl bg-white shadow-2xl dark:bg-slate-800">
        {isLoading && <p className="p-8 text-center text-sm text-slate-500">Carregando…</p>}
        {error && <p className="p-8 text-center text-sm text-red-600">{error.message}</p>}

        {card && data && (
          <>
            <header className="flex items-start justify-between gap-3 border-b border-slate-200 p-4 dark:border-slate-700">
              <div className="min-w-0">
                <h2 className="truncate text-base font-semibold text-slate-800 dark:text-slate-100">
                  {card.razaoSocial}
                </h2>
                <p className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-slate-500 dark:text-slate-400">
                  <Link href={`/clients/${card.cnpj}`} className="font-mono text-[#2956E0] hover:underline dark:text-sky-400">
                    {formatCnpj(card.cnpj)}
                  </Link>
                  <span>·</span>
                  <span>{ROTULO_ESTAGIO[card.estagio]}</span>
                  <span>·</span>
                  <span className={CLASSE_BADGE_SEMAFORO[semaforoDe(card)]}>{textoPrazo(card)}</span>
                </p>
                <p className="mt-0.5 text-[11px] text-slate-400">
                  Comercial: {card.comercialNome ?? "—"} · Analista: {card.analistaNome ?? "não assumido"}
                  {card.reaberturas > 0 && ` · ${card.reaberturas}ª reanálise`}
                </p>
                {card.motivoRecusa && (
                  <p className="mt-1 inline-flex items-center gap-1 rounded bg-red-50 px-1.5 py-0.5 text-[11px] text-red-700 dark:bg-red-950/40 dark:text-red-300">
                    {ROTULO_MOTIVO[card.motivoRecusa]}
                    {card.observacao ? ` — ${card.observacao}` : ""}
                  </p>
                )}
              </div>
              <button type="button" onClick={onFechar} className="shrink-0 text-slate-400 hover:text-slate-600">
                <Icon name="close" />
              </button>
            </header>

            <div className="flex flex-wrap items-center gap-1.5 border-b border-slate-200 px-4 py-2 dark:border-slate-700">
              {card.estagio === "TRIAGEM" && (
                <button
                  type="button"
                  onClick={() => assumir.mutate(cardId)}
                  disabled={assumir.isPending}
                  className="rounded bg-[#612035] px-2.5 py-1 text-xs text-white disabled:opacity-50"
                >
                  {assumir.isPending ? "assumindo…" : "Assumir análise"}
                </button>
              )}
              {data.destinosPossiveis.map(destino => (
                <button
                  key={destino}
                  type="button"
                  onClick={() => mover(destino)}
                  disabled={transicionar.isPending}
                  className="rounded border border-slate-300 px-2.5 py-1 text-xs text-slate-700 hover:bg-slate-50
                    disabled:opacity-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-700"
                >
                  {ROTULO_ESTAGIO[destino]}
                </button>
              ))}
              {(card.estagio === "REPROVADO" || card.estagio === "REMOVIDO_RADAR") && (
                <button
                  type="button"
                  onClick={() => {
                    const texto = window.prompt("Por que reabrir este card?");
                    if (!texto || !texto.trim()) return;
                    reabrir.mutate({ id: cardId, texto: texto.trim() });
                  }}
                  className="rounded border border-[#612035] px-2.5 py-1 text-xs text-[#612035] hover:bg-[#612035]/5"
                >
                  Reabrir em triagem
                </button>
              )}
              {data.destinosPossiveis.length === 0 && card.estagio === "PRONTO_HABILITACAO" && (
                <p className="text-xs text-slate-400">
                  Card repassado para a habilitação — segue fora desta tela.
                </p>
              )}
            </div>

            <nav className="flex gap-4 border-b border-slate-200 px-4 dark:border-slate-700">
              {(["documentos", "timeline"] as const).map(opcao => (
                <button
                  key={opcao}
                  type="button"
                  onClick={() => setAba(opcao)}
                  className={`-mb-px border-b-2 px-1 py-2 text-xs font-medium capitalize ${
                    aba === opcao
                      ? "border-[#612035] text-[#612035] dark:border-[#D1732C] dark:text-[#D1732C]"
                      : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400"
                  }`}
                >
                  {opcao === "documentos"
                    ? `Documentos ${card.documentosTotal > 0 ? `(${card.documentosResolvidos}/${card.documentosTotal})` : ""}`
                    : "Histórico"}
                </button>
              ))}
            </nav>

            <div className="flex-1 overflow-y-auto p-4">
              {aba === "documentos" ? (
                <DocumentChecklist cardId={cardId} documentos={data.documentos} />
              ) : (
                <ProspeccaoTimeline cardId={cardId} eventos={data.timeline} />
              )}
            </div>

            <footer className="border-t border-slate-200 px-4 py-2 text-[11px] text-slate-400 dark:border-slate-700">
              Aberto em {formatDate(card.createdAt)}
              {card.ultimoContatoEm && ` · última cobrança em ${formatDate(card.ultimoContatoEm)}`}
            </footer>
          </>
        )}
      </div>
    </div>
  );
}
