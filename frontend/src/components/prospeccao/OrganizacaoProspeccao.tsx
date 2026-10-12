"use client";

import { useEffect, useRef, useState } from "react";
import Icon from "@/components/ui/Icon";
import { CAMPO } from "@/components/liberacao/Dialogo";
import { TOM } from "@/components/liberacao/cores";
import { confirmar } from "@/components/liberacao/formatters";
import {
  useApagarEtiquetaProspeccao,
  useCriarEtiquetaProspeccao,
  useDefinirCorProspeccao,
  useDefinirEtiquetasProspeccao,
  useEtiquetasProspeccao,
} from "@/hooks/useProspeccao";
import { CORES, type CorLiberacao } from "@/types/liberacao";
import type { Prospeccao } from "@/types/prospeccao";

/** Popover que fecha com clique fora e Esc, sem fechar o diálogo do card em volta. */
function usePopover() {
  const [aberto, setAberto] = useState(false);
  const caixa = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!aberto) return;
    function fora(event: MouseEvent) {
      if (caixa.current && !caixa.current.contains(event.target as Node)) setAberto(false);
    }
    function esc(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.stopPropagation();
        setAberto(false);
      }
    }
    document.addEventListener("mousedown", fora);
    window.addEventListener("keydown", esc, true);
    return () => {
      document.removeEventListener("mousedown", fora);
      window.removeEventListener("keydown", esc, true);
    };
  }, [aberto]);
  return { aberto, setAberto, caixa };
}

const PAINEL =
  "absolute left-0 z-30 mt-1.5 w-64 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl dark:border-slate-700 dark:bg-slate-800";

const GATILHO =
  "inline-flex h-7 cursor-pointer items-center gap-1 rounded-full border border-dashed border-slate-300 px-2.5 text-[11px] font-medium " +
  "text-slate-500 transition-colors hover:border-slate-400 hover:bg-slate-50 hover:text-slate-800 focus:outline-none " +
  "focus-visible:ring-2 focus-visible:ring-[#612035] dark:border-slate-600 dark:hover:bg-slate-800 dark:hover:text-slate-200";

/**
 * Etiquetas e cor do card, na linha logo abaixo do título — como no Trello e na esteira de
 * liberação. Qualquer pessoa da equipe organiza; apagar uma etiqueta da esteira toda é do admin.
 */
export default function OrganizacaoProspeccao({ card, admin }: { card: Prospeccao; admin: boolean }) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {card.etiquetas.map(etiqueta => (
        <span key={etiqueta.id} className={`inline-flex h-7 items-center rounded-full px-2.5 text-[11px] font-semibold ${TOM[etiqueta.cor].chip}`}>
          {etiqueta.nome}
        </span>
      ))}
      <Etiquetas card={card} admin={admin} />
      <Cor card={card} />
    </div>
  );
}

function Etiquetas({ card, admin }: { card: Prospeccao; admin: boolean }) {
  const { data: todas = [] } = useEtiquetasProspeccao();
  const definir = useDefinirEtiquetasProspeccao();
  const criar = useCriarEtiquetaProspeccao();
  const apagar = useApagarEtiquetaProspeccao();
  const { aberto, setAberto, caixa } = usePopover();
  const [busca, setBusca] = useState("");
  const [corNova, setCorNova] = useState<CorLiberacao>("AZUL");

  const marcadas = new Set(card.etiquetas.map(etiqueta => etiqueta.id));
  const termo = busca.trim().toLowerCase();
  const filtradas = todas.filter(etiqueta => etiqueta.nome.toLowerCase().includes(termo));
  const existe = todas.some(etiqueta => etiqueta.nome.toLowerCase() === termo);

  function alternar(id: string) {
    const ids = marcadas.has(id) ? [...marcadas].filter(item => item !== id) : [...marcadas, id];
    definir.mutate({ id: card.id, ids });
  }

  function criarEMarcar() {
    if (!termo) return;
    criar.mutate(
      { nome: busca.trim(), cor: corNova },
      {
        onSuccess: etiqueta => {
          setBusca("");
          if (!marcadas.has(etiqueta.id)) definir.mutate({ id: card.id, ids: [...marcadas, etiqueta.id] });
        },
      },
    );
  }

  return (
    <div className="relative" ref={caixa}>
      <button type="button" onClick={() => setAberto(atual => !atual)} aria-expanded={aberto} className={GATILHO}>
        <Icon name="tag" size={12} />
        {card.etiquetas.length === 0 ? "Etiquetas" : "Editar"}
      </button>
      {aberto && (
        <div className={PAINEL} role="dialog" aria-label="Etiquetas do card">
          <div className="border-b border-slate-100 p-2 dark:border-slate-700">
            <input
              autoFocus
              value={busca}
              onChange={event => setBusca(event.target.value)}
              onKeyDown={event => {
                if (event.key === "Enter" && termo && !existe) {
                  event.preventDefault();
                  criarEMarcar();
                }
              }}
              placeholder="Buscar ou criar etiqueta"
              maxLength={40}
              className={`${CAMPO} py-1.5 text-xs`}
            />
          </div>
          <ul className="max-h-56 overflow-y-auto py-1">
            {filtradas.map(etiqueta => (
              <li key={etiqueta.id} className="group flex items-center gap-1 px-2">
                <button
                  type="button"
                  onClick={() => alternar(etiqueta.id)}
                  aria-pressed={marcadas.has(etiqueta.id)}
                  className="flex min-h-9 flex-1 cursor-pointer items-center gap-2 rounded-md px-1 text-left hover:bg-slate-50 dark:hover:bg-slate-700"
                >
                  <span
                    className={`flex h-4 w-4 shrink-0 items-center justify-center rounded border ${
                      marcadas.has(etiqueta.id) ? "border-[#612035] bg-[#612035] text-white" : "border-slate-300 dark:border-slate-600"
                    }`}
                  >
                    {marcadas.has(etiqueta.id) && <Icon name="check" size={11} strokeWidth={3} />}
                  </span>
                  <span className={`truncate rounded-full px-2 py-0.5 text-[11px] font-semibold ${TOM[etiqueta.cor].chip}`}>{etiqueta.nome}</span>
                </button>
                {admin && (
                  <button
                    type="button"
                    onClick={() => confirmar(`Apagar a etiqueta "${etiqueta.nome}" de todos os cards?`) && apagar.mutate(etiqueta.id)}
                    aria-label={`Apagar etiqueta ${etiqueta.nome}`}
                    className="invisible inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-md text-slate-400 hover:bg-rose-50 hover:text-rose-600 group-hover:visible"
                  >
                    <Icon name="delete" size={13} />
                  </button>
                )}
              </li>
            ))}
            {filtradas.length === 0 && !termo && <li className="px-3 py-2 text-xs text-slate-400">Nenhuma etiqueta ainda.</li>}
          </ul>
          {termo && !existe && (
            <div className="space-y-2 border-t border-slate-100 p-2 dark:border-slate-700">
              <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="Cor da nova etiqueta">
                {CORES.map(cor => (
                  <button
                    key={cor}
                    type="button"
                    role="radio"
                    aria-checked={corNova === cor}
                    aria-label={TOM[cor].nome}
                    onClick={() => setCorNova(cor)}
                    className={`h-5 w-5 cursor-pointer rounded-full ${TOM[cor].amostra} ${corNova === cor ? "ring-2 ring-slate-900 ring-offset-1 dark:ring-white dark:ring-offset-slate-800" : ""}`}
                  />
                ))}
              </div>
              <button
                type="button"
                onClick={criarEMarcar}
                disabled={criar.isPending}
                className="flex min-h-8 w-full cursor-pointer items-center gap-1.5 rounded-md px-2 text-left text-xs font-medium text-[#612035] hover:bg-[#612035]/5 dark:text-[#e8a3b6]"
              >
                <Icon name="add" size={13} /> Criar “{busca.trim()}”
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function Cor({ card }: { card: Prospeccao }) {
  const definir = useDefinirCorProspeccao();
  const { aberto, setAberto, caixa } = usePopover();

  function escolher(cor: CorLiberacao | null) {
    definir.mutate({ id: card.id, cor });
    setAberto(false);
  }

  return (
    <div className="relative" ref={caixa}>
      <button type="button" onClick={() => setAberto(atual => !atual)} aria-expanded={aberto} className={GATILHO}>
        {card.cor ? <span className={`h-3 w-3 rounded-full ${TOM[card.cor].amostra}`} /> : <Icon name="palette" size={12} />}
        Cor
      </button>
      {aberto && (
        <div className={`${PAINEL} w-56 p-3`} role="dialog" aria-label="Cor do card">
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-500">Faixa do card</p>
          <div className="grid grid-cols-4 gap-2">
            {CORES.map(cor => (
              <button
                key={cor}
                type="button"
                onClick={() => escolher(cor)}
                aria-label={TOM[cor].nome}
                title={TOM[cor].nome}
                className={`h-8 cursor-pointer rounded-lg ${TOM[cor].amostra} transition-transform hover:scale-105 ${
                  card.cor === cor ? "ring-2 ring-slate-900 ring-offset-2 dark:ring-white dark:ring-offset-slate-800" : ""
                }`}
              />
            ))}
          </div>
          {card.cor && (
            <button
              type="button"
              onClick={() => escolher(null)}
              className="mt-3 w-full cursor-pointer rounded-md py-1.5 text-xs font-medium text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-700"
            >
              Tirar a cor
            </button>
          )}
        </div>
      )}
    </div>
  );
}
