"use client";

import { useMemo, useRef, useState, useEffect } from "react";
import Icon from "@/components/ui/Icon";
import {
  useAdicionarMembro,
  useApagarEtiqueta,
  useCriarEtiqueta,
  useDefinirCor,
  useDefinirEtiquetas,
  useDiretorio,
  useEtiquetas,
  useRemoverMembro,
} from "@/hooks/useLiberacao";
import { CORES, type CorLiberacao, type LiberacaoCard } from "@/types/liberacao";
import Avatar from "./Avatar";
import { CAMPO } from "./Dialogo";
import { TOM } from "./cores";
import { confirmar } from "./formatters";

const TITULO = "mb-2 flex items-center justify-between font-sans text-[11px] font-bold uppercase tracking-[0.08em] text-slate-500";
const BOTAO_MINI =
  "inline-flex h-7 cursor-pointer items-center gap-1 rounded-md px-1.5 text-[11px] font-medium normal-case tracking-normal text-slate-500 " +
  "hover:bg-slate-200/70 hover:text-slate-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] " +
  "disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-slate-700 dark:hover:text-slate-200";

/** Popover simples que fecha com clique fora e Esc, sem fechar o diálogo em volta. */
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
  "absolute right-0 z-30 mt-1 w-64 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl dark:border-slate-700 dark:bg-slate-800";

// ------------------------------------------------------------------ etiquetas

function Etiquetas({ card, ehAnalista }: { card: LiberacaoCard; ehAnalista: boolean }) {
  const { data: todas = [] } = useEtiquetas();
  const definir = useDefinirEtiquetas();
  const criar = useCriarEtiqueta();
  const apagar = useApagarEtiqueta();
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
    if (!busca.trim()) return;
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
      <h3 className={TITULO}>
        Etiquetas
        <button
          type="button"
          disabled={!card.podeEditar}
          title={card.podeEditar ? "Escolher etiquetas" : "A partir do Comitê, só analista altera o card"}
          onClick={() => setAberto(atual => !atual)}
          className={BOTAO_MINI}
        >
          <Icon name="tag" size={12} /> editar
        </button>
      </h3>
      <div className="flex flex-wrap gap-1">
        {card.etiquetas.map(etiqueta => (
          <span key={etiqueta.id} className={`rounded px-2 py-0.5 text-[11px] font-semibold ${TOM[etiqueta.cor].chip}`}>
            {etiqueta.nome}
          </span>
        ))}
        {card.etiquetas.length === 0 && <span className="text-xs text-slate-400">nenhuma</span>}
      </div>

      {aberto && (
        <div className={PAINEL} role="dialog" aria-label="Etiquetas">
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
                    {marcadas.has(etiqueta.id) && <Icon name="check" size={10} strokeWidth={3} />}
                  </span>
                  <span className={`truncate rounded px-1.5 py-0.5 text-[11px] font-semibold ${TOM[etiqueta.cor].chip}`}>{etiqueta.nome}</span>
                </button>
                {ehAnalista && (
                  <button
                    type="button"
                    aria-label={`Apagar a etiqueta ${etiqueta.nome}`}
                    title="Apagar de todos os cards"
                    onClick={() => confirmar(`Apagar a etiqueta "${etiqueta.nome}" de todos os cards?`) && apagar.mutate(etiqueta.id)}
                    className="invisible flex h-7 w-7 cursor-pointer items-center justify-center rounded-md text-slate-400 hover:bg-rose-50
                      hover:text-rose-600 group-hover:visible focus:visible dark:hover:bg-rose-900/30"
                  >
                    <Icon name="delete" size={12} />
                  </button>
                )}
              </li>
            ))}
            {filtradas.length === 0 && !termo && <li className="px-3 py-2 text-xs text-slate-400">Nenhuma etiqueta ainda.</li>}
          </ul>
          {termo && !existe && (
            <div className="border-t border-slate-100 p-2 dark:border-slate-700">
              <div className="mb-2 flex gap-1" role="radiogroup" aria-label="Cor da etiqueta nova">
                {CORES.map(cor => (
                  <button
                    key={cor}
                    type="button"
                    role="radio"
                    aria-checked={corNova === cor}
                    aria-label={TOM[cor].nome}
                    onClick={() => setCorNova(cor)}
                    className={`h-5 w-5 cursor-pointer rounded-full ${TOM[cor].amostra} ${
                      corNova === cor ? "ring-2 ring-slate-900 ring-offset-1 dark:ring-white dark:ring-offset-slate-800" : ""
                    }`}
                  />
                ))}
              </div>
              <button
                type="button"
                onClick={criarEMarcar}
                disabled={criar.isPending}
                className="flex min-h-8 w-full cursor-pointer items-center justify-center gap-1 rounded-lg bg-[#612035] text-xs font-medium text-white hover:bg-[#4e1a2a]"
              >
                <Icon name="add" size={12} /> Criar &quot;{busca.trim()}&quot;
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ------------------------------------------------------------------------ cor

function Cor({ card }: { card: LiberacaoCard }) {
  const definir = useDefinirCor();
  return (
    <div>
      <h3 className={TITULO}>Cor do card</h3>
      <div className="flex flex-wrap items-center gap-1.5" role="radiogroup" aria-label="Cor do card">
        {CORES.map(cor => (
          <button
            key={cor}
            type="button"
            role="radio"
            aria-checked={card.cor === cor}
            aria-label={TOM[cor].nome}
            title={card.podeEditar ? TOM[cor].nome : "A partir do Comitê, só analista altera o card"}
            disabled={!card.podeEditar || definir.isPending}
            onClick={() => definir.mutate({ id: card.id, cor: card.cor === cor ? null : cor })}
            className={`flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg ${TOM[cor].amostra} transition-transform
              disabled:cursor-not-allowed disabled:opacity-40 ${card.cor === cor ? "ring-2 ring-slate-900 ring-offset-2 dark:ring-white dark:ring-offset-slate-900" : ""}`}
          >
            {card.cor === cor && <Icon name="check" size={13} strokeWidth={3} className="text-white" />}
          </button>
        ))}
        {card.cor && card.podeEditar && (
          <button type="button" onClick={() => definir.mutate({ id: card.id, cor: null })} className={BOTAO_MINI}>
            sem cor
          </button>
        )}
      </div>
    </div>
  );
}

// -------------------------------------------------------------------- membros

function Membros({ card, euId }: { card: LiberacaoCard; euId?: string }) {
  const { data: pessoas = [] } = useDiretorio();
  const adicionar = useAdicionarMembro();
  const remover = useRemoverMembro();
  const { aberto, setAberto, caixa } = usePopover();
  const [busca, setBusca] = useState("");
  const souMembro = card.membros.some(membro => membro.id === euId);
  const presentes = useMemo(() => new Set(card.membros.map(membro => membro.id)), [card.membros]);
  const candidatas = pessoas
    .filter(pessoa => !presentes.has(pessoa.id))
    .filter(pessoa => pessoa.nome.toLowerCase().includes(busca.trim().toLowerCase()));

  return (
    <div className="relative" ref={caixa}>
      <h3 className={TITULO}>
        Membros
        <span className="flex items-center gap-0.5">
          {euId && (
            <button
              type="button"
              disabled={adicionar.isPending || remover.isPending}
              onClick={() =>
                souMembro ? remover.mutate({ id: card.id, usuarioId: euId }) : adicionar.mutate({ id: card.id, usuarioId: euId })
              }
              title={souMembro ? "Parar de receber avisos deste card" : "Receber avisos deste card"}
              className={BOTAO_MINI}
            >
              <Icon name={souMembro ? "visibility_off" : "visibility"} size={12} />
              {souMembro ? "deixar de seguir" : "seguir"}
            </button>
          )}
          <button
            type="button"
            disabled={!card.podeEditar}
            aria-label="Adicionar membro"
            title={card.podeEditar ? "Adicionar membro" : "A partir do Comitê, só analista muda os membros de outra pessoa"}
            onClick={() => setAberto(atual => !atual)}
            className={BOTAO_MINI}
          >
            <Icon name="add" size={12} />
          </button>
        </span>
      </h3>
      <ul className="space-y-1">
        {card.membros.map(membro => (
          <li key={membro.id} className="group flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200">
            <Avatar nome={membro.nome} iniciais={membro.iniciais} tamanho="xs" />
            <span className="min-w-0 flex-1 truncate">{membro.nome}</span>
            {membro.id === euId && <span className="text-[10px] text-slate-400">você</span>}
            {card.podeEditar && membro.id !== euId && (
              <button
                type="button"
                aria-label={`Remover ${membro.nome}`}
                onClick={() => remover.mutate({ id: card.id, usuarioId: membro.id })}
                className="invisible flex h-6 w-6 cursor-pointer items-center justify-center rounded text-slate-400 hover:bg-slate-200
                  hover:text-slate-700 group-hover:visible focus:visible dark:hover:bg-slate-700"
              >
                <Icon name="close" size={12} />
              </button>
            )}
          </li>
        ))}
      </ul>
      <p className="mt-1.5 text-[10.5px] leading-snug text-slate-400">Membros recebem aviso quando o card muda de coluna ou é comentado.</p>

      {aberto && (
        <div className={PAINEL} role="dialog" aria-label="Adicionar membro">
          <div className="border-b border-slate-100 p-2 dark:border-slate-700">
            <input autoFocus value={busca} onChange={event => setBusca(event.target.value)} placeholder="Buscar pelo nome" className={`${CAMPO} py-1.5 text-xs`} />
          </div>
          <ul className="max-h-56 overflow-y-auto py-1">
            {candidatas.map(pessoa => (
              <li key={pessoa.id}>
                <button
                  type="button"
                  onClick={() => {
                    adicionar.mutate({ id: card.id, usuarioId: pessoa.id });
                    setAberto(false);
                    setBusca("");
                  }}
                  className="flex min-h-9 w-full cursor-pointer items-center gap-2 px-3 text-left text-sm text-slate-700 hover:bg-slate-50
                    dark:text-slate-200 dark:hover:bg-slate-700"
                >
                  <Avatar nome={pessoa.nome} iniciais={pessoa.iniciais} tamanho="xs" />
                  <span className="truncate">{pessoa.nome}</span>
                </button>
              </li>
            ))}
            {candidatas.length === 0 && <li className="px-3 py-2 text-xs text-slate-400">Ninguém para adicionar.</li>}
          </ul>
        </div>
      )}
    </div>
  );
}

/** Lateral do detalhe: o que organiza o card sem ser campo da operação. */
export default function OrganizacaoCard({ card, euId, ehAnalista }: { card: LiberacaoCard; euId?: string; ehAnalista: boolean }) {
  return (
    <>
      <Etiquetas card={card} ehAnalista={ehAnalista} />
      <Cor card={card} />
      <Membros card={card} euId={euId} />
    </>
  );
}
