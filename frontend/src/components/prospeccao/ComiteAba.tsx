"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import Avatar from "@/components/liberacao/Avatar";
import { BOTAO_PRIMARIO, CAMPO, ROTULO } from "@/components/liberacao/Dialogo";
import { COR_POSICAO, ICONE_POSICAO } from "@/components/liberacao/formatters";
import { useRegistrarParecerProspeccao } from "@/hooks/useProspeccao";
import type { ParecerProspeccao, PosicaoParecer } from "@/types/prospeccao";
import { formatDateTime } from "./formatters";

export const ROTULO_POSICAO: Record<PosicaoParecer, string> = {
  FAVORAVEL: "Favorável",
  COM_RESSALVAS: "Com ressalvas",
  DESFAVORAVEL: "Desfavorável",
};

const POSICOES: PosicaoParecer[] = ["FAVORAVEL", "COM_RESSALVAS", "DESFAVORAVEL"];

function iniciais(nome: string) {
  return nome.split(/\s+/).filter(Boolean).slice(0, 2).map(parte => parte[0]?.toUpperCase() ?? "").join("");
}

/**
 * Aba Comitê: os pareceres da rodada vigente, o formulário de quem foi convocado e as rodadas
 * anteriores recolhidas. Mesmas regras e mesma escala de posição da esteira de liberação.
 */
export default function ComiteAba({
  cardId,
  pareceres,
  rodadaVigente,
  noComite,
  convocado,
  euId,
  acao,
}: {
  cardId: string;
  pareceres: ParecerProspeccao[];
  rodadaVigente: number;
  /** Card está no comitê agora: a rodada vigente aceita parecer. */
  noComite: boolean;
  convocado: boolean;
  euId?: string;
  acao?: React.ReactNode;
}) {
  const vigente = pareceres.filter(parecer => parecer.rodada === rodadaVigente);
  const anteriores = pareceres.filter(parecer => parecer.rodada !== rodadaVigente);
  const meu = vigente.find(parecer => parecer.usuarioId === euId);
  const registrados = vigente.filter(parecer => parecer.posicao).length;

  if (pareceres.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-slate-300 px-6 py-10 text-center dark:border-slate-700">
        <Icon name="groups" size={24} className="text-slate-300" />
        <p className="text-sm text-slate-600 dark:text-slate-300">O card ainda não passou pelo comitê.</p>
        <p className="text-xs text-slate-400">Com a documentação completa, a dona do card envia ao comitê.</p>
        {acao}
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {vigente.length > 0 && (
        <section className="rounded-xl border border-slate-200 p-4 dark:border-slate-700">
          <header className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-white">
              Rodada {rodadaVigente}
              <span className="ml-2 text-xs font-normal text-slate-500">
                {registrados} de {vigente.length} pareceres{noComite ? "" : " · encerrada"}
              </span>
            </h3>
            {acao}
          </header>
          <ol className="space-y-3">
            {vigente.map(parecer => (
              <ItemParecer key={parecer.id} parecer={parecer} />
            ))}
          </ol>
          {noComite && convocado && meu && <MeuParecer key={meu.id} cardId={cardId} meu={meu} />}
        </section>
      )}

      {anteriores.length > 0 && (
        <details className="group rounded-xl border border-slate-200 px-4 py-2 dark:border-slate-700">
          <summary className="flex min-h-8 cursor-pointer list-none items-center justify-between text-xs font-medium text-slate-500 [&::-webkit-details-marker]:hidden">
            Rodadas anteriores
            <Icon name="expand_more" size={14} className="transition-transform group-open:rotate-180" />
          </summary>
          <ol className="mt-2 space-y-3 pb-1">
            {anteriores.map(parecer => (
              <ItemParecer key={parecer.id} parecer={parecer} mostrarRodada />
            ))}
          </ol>
        </details>
      )}
    </div>
  );
}

function ItemParecer({ parecer, mostrarRodada = false }: { parecer: ParecerProspeccao; mostrarRodada?: boolean }) {
  return (
    <li className="flex gap-3">
      <span className="relative inline-flex shrink-0">
        <Avatar nome={parecer.usuarioNome} iniciais={iniciais(parecer.usuarioNome)} tamanho="xs" aguardando={!parecer.posicao} />
        {parecer.posicao && (
          <span className={`absolute -bottom-1 -right-1 flex h-3.5 w-3.5 items-center justify-center rounded-full ring-2 ring-white dark:ring-slate-900 ${COR_POSICAO[parecer.posicao]}`}>
            <Icon name={ICONE_POSICAO[parecer.posicao]} size={8} strokeWidth={3} />
          </span>
        )}
      </span>
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-baseline gap-x-2 text-sm">
          <strong className="font-semibold text-slate-900 dark:text-white">{parecer.usuarioNome}</strong>
          <span className={parecer.posicao ? "font-medium text-slate-700 dark:text-slate-200" : "text-slate-400"}>
            {parecer.posicao ? ROTULO_POSICAO[parecer.posicao] : "aguardando"}
          </span>
          <span className="text-[11px] text-slate-400">
            {mostrarRodada && `rodada ${parecer.rodada} · `}
            {parecer.registradoEm && formatDateTime(parecer.registradoEm)}
          </span>
        </p>
        {parecer.texto && <p className="mt-0.5 whitespace-pre-line text-sm text-slate-600 dark:text-slate-300">{parecer.texto}</p>}
      </div>
    </li>
  );
}

function MeuParecer({ cardId, meu }: { cardId: string; meu: ParecerProspeccao }) {
  const registrar = useRegistrarParecerProspeccao();
  const [posicao, setPosicao] = useState<PosicaoParecer | null>(meu.posicao);
  const [texto, setTexto] = useState(meu.texto ?? "");

  return (
    <div className="mt-4 space-y-3 rounded-xl bg-[#612035]/5 p-4 dark:bg-[#612035]/20">
      <p className="text-sm font-semibold text-slate-900 dark:text-white">{meu.posicao ? "Rever meu parecer" : "Seu parecer é esperado"}</p>
      <div role="radiogroup" aria-label="Posição" className="grid gap-2 sm:grid-cols-3">
        {POSICOES.map(opcao => (
          <button
            key={opcao}
            type="button"
            role="radio"
            aria-checked={posicao === opcao}
            onClick={() => setPosicao(opcao)}
            className={`inline-flex min-h-10 cursor-pointer items-center justify-center gap-1.5 rounded-lg border-2 text-sm font-medium transition-colors
              focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] ${
                posicao === opcao
                  ? "border-[#612035] bg-white text-slate-900 dark:border-[#e8a3b6] dark:bg-slate-900 dark:text-white"
                  : "border-transparent bg-white/60 text-slate-600 hover:bg-white dark:bg-slate-900/40 dark:text-slate-300"
              }`}
          >
            <Icon name={ICONE_POSICAO[opcao]} size={14} />
            {ROTULO_POSICAO[opcao]}
          </button>
        ))}
      </div>
      <label className="block">
        <span className={ROTULO}>Comentário do parecer (opcional)</span>
        <textarea rows={3} value={texto} onChange={event => setTexto(event.target.value)} className={`${CAMPO} resize-y`} />
      </label>
      <div className="flex justify-end">
        <button
          type="button"
          disabled={!posicao || registrar.isPending}
          onClick={() => posicao && registrar.mutate({ id: cardId, posicao, texto: texto.trim() || undefined })}
          className={BOTAO_PRIMARIO}
        >
          {registrar.isPending ? "Registrando…" : meu.posicao ? "Rever parecer" : "Registrar parecer"}
        </button>
      </div>
    </div>
  );
}
