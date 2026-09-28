"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { CanalContato, ProspeccaoEvento, ROTULO_CANAL, ROTULO_ESTAGIO, TipoEvento } from "@/types/prospeccao";
import { useRegistrarEvento } from "@/hooks/useProspeccao";
import { formatDateTime } from "./formatters";

const ICONE_EVENTO: Record<TipoEvento, string> = {
  CRIACAO: "add_circle",
  TRANSICAO: "arrow_forward",
  COBRANCA: "campaign",
  NOTA: "sticky_note_2",
  DOC_RECEBIDO: "inbox",
  DOC_VALIDADO: "check_circle",
  DOC_REJEITADO: "cancel",
  DOC_DISPENSADO: "remove_circle",
  ARQUIVO_ENVIADO: "attach_file",
  ARQUIVO_REMOVIDO: "delete",
  ANALISTA_ALTERADO: "person",
  REABERTURA: "restart_alt",
};

const CANAIS: CanalContato[] = ["EMAIL", "WHATSAPP", "LIGACAO", "REUNIAO"];

function descricao(evento: ProspeccaoEvento) {
  if (evento.tipo === "TRANSICAO" && evento.estagioDe && evento.estagioPara) {
    return `${ROTULO_ESTAGIO[evento.estagioDe]} → ${ROTULO_ESTAGIO[evento.estagioPara]}`;
  }
  if (evento.canal && evento.canal !== "SISTEMA") {
    return `Cobrança por ${ROTULO_CANAL[evento.canal]}`;
  }
  return null;
}

export default function ProspeccaoTimeline({ cardId, eventos }: { cardId: string; eventos: ProspeccaoEvento[] }) {
  const registrar = useRegistrarEvento();
  const [texto, setTexto] = useState("");
  const [canal, setCanal] = useState<CanalContato | "">("");

  function enviar() {
    if (!texto.trim()) return;
    registrar.mutate(
      { id: cardId, canal: canal === "" ? null : canal, texto: texto.trim() },
      { onSuccess: () => setTexto("") },
    );
  }

  return (
    <div className="space-y-3">
      <div className="rounded-md border border-slate-200 p-2 dark:border-slate-700">
        <textarea
          value={texto}
          onChange={event => setTexto(event.target.value)}
          rows={2}
          placeholder="Registrar cobrança ou nota interna…"
          className="w-full resize-none rounded border border-slate-300 px-2 py-1.5 text-xs
            dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
        />
        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
          <button
            type="button"
            onClick={() => setCanal("")}
            className={`rounded px-2 py-0.5 text-[11px] ${
              canal === ""
                ? "bg-slate-700 text-white dark:bg-slate-600"
                : "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700"
            }`}
          >
            nota interna
          </button>
          {CANAIS.map(opcao => (
            <button
              key={opcao}
              type="button"
              onClick={() => setCanal(opcao)}
              className={`rounded px-2 py-0.5 text-[11px] ${
                canal === opcao
                  ? "bg-[#612035] text-white"
                  : "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700"
              }`}
            >
              {ROTULO_CANAL[opcao]}
            </button>
          ))}
          <button
            type="button"
            onClick={enviar}
            disabled={!texto.trim() || registrar.isPending}
            className="ml-auto rounded bg-[#612035] px-2.5 py-1 text-[11px] text-white disabled:opacity-40"
          >
            {registrar.isPending ? "registrando…" : "registrar"}
          </button>
        </div>
        {canal !== "" && (
          <p className="mt-1 text-[10px] text-slate-400">
            Cobrança zera o contador de silêncio do card.
          </p>
        )}
      </div>

      <ol className="space-y-2">
        {eventos.map(evento => (
          <li key={evento.id} className="flex gap-2">
            <Icon name={ICONE_EVENTO[evento.tipo]} className="mt-0.5 text-[15px] text-slate-400" />
            <div className="min-w-0 flex-1">
              <p className="text-xs text-slate-700 dark:text-slate-200">
                {descricao(evento) && <span className="font-medium">{descricao(evento)}</span>}
                {descricao(evento) && evento.texto && " · "}
                {evento.texto}
              </p>
              <p className="text-[10px] text-slate-400">
                {evento.usuarioNome ?? "Sistema"} · {formatDateTime(evento.criadoEm)}
              </p>
            </div>
          </li>
        ))}
        {eventos.length === 0 && <li className="text-xs text-slate-400">Sem histórico ainda.</li>}
      </ol>
    </div>
  );
}
