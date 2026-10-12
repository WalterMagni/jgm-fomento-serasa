"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import Icon from "@/components/ui/Icon";
import { useProspeccoesDoCnpj } from "@/hooks/useProspeccao";
import { ROTULO_ESTAGIO, ROTULO_MOTIVO, ehTerminal } from "@/types/prospeccao";
import NovaProspeccaoDialog from "./NovaProspeccaoDialog";
import { CLASSE_BADGE_SEMAFORO, formatDate, semaforoDe, textoPrazo } from "./formatters";

/**
 * Faixa de prospecção na página da empresa.
 *
 * <p>Com card em aberto, mostra onde ele está e leva até ele. Sem card, oferece abrir — já com CNPJ
 * e razão social —, e lembra a última passagem pela esteira, se houve: empresa reprovada que volta
 * é caso comum, e quem abre precisa saber disso antes.</p>
 */
export default function ProspeccaoEmpresaBloco({ cnpj, razaoSocial }: { cnpj: string; razaoSocial?: string }) {
  const router = useRouter();
  const { data: cards = [], isLoading } = useProspeccoesDoCnpj(cnpj);
  const [abrindo, setAbrindo] = useState(false);

  if (isLoading) return null;

  const aberto = cards.find(card => !ehTerminal(card.estagio));
  // A lista vem do mais novo para o mais antigo.
  const ultimoEncerrado = cards.find(card => ehTerminal(card.estagio));

  return (
    <div className="mb-6 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-xl border border-border-light bg-surface-light px-4 py-3 shadow-sm
      dark:border-border-dark dark:bg-surface-dark print:hidden">
      <span className="inline-flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
        <Icon name="conveyor_belt" size={15} />
        Esteira de prospecção
      </span>

      {aberto ? (
        <>
          <span className="inline-flex items-center gap-2 text-sm">
            <span className="rounded-md bg-[#612035]/10 px-2 py-0.5 font-semibold text-[#612035] dark:bg-[#e8a3b6]/15 dark:text-[#e8a3b6]">
              {ROTULO_ESTAGIO[aberto.estagio]}
            </span>
            <span className="text-slate-600 dark:text-slate-300">
              {aberto.analistaNome ? `com ${aberto.analistaNome}` : "aguardando analista"}
            </span>
            <span className={`text-xs ${CLASSE_BADGE_SEMAFORO[semaforoDe(aberto)]}`}>{textoPrazo(aberto)}</span>
          </span>
          <Link
            href={`/prospeccao?card=${aberto.id}`}
            className="ml-auto inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 text-xs font-medium
              text-slate-700 transition-colors hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
              dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
          >
            Abrir card <Icon name="arrow_forward" size={14} />
          </Link>
        </>
      ) : (
        <>
          <span className="text-sm text-slate-600 dark:text-slate-300">
            {ultimoEncerrado ? (
              <>
                Última passagem:{" "}
                <Link href={`/prospeccao?card=${ultimoEncerrado.id}`} className="font-medium hover:underline">
                  {ROTULO_ESTAGIO[ultimoEncerrado.estagio]}
                </Link>
                {ultimoEncerrado.motivoRecusa && ` · ${ROTULO_MOTIVO[ultimoEncerrado.motivoRecusa]}`}
                {ultimoEncerrado.closedAt && ` em ${formatDate(ultimoEncerrado.closedAt)}`}
              </>
            ) : (
              "Nenhuma prospecção para esta empresa."
            )}
          </span>
          <button
            type="button"
            onClick={() => setAbrindo(true)}
            className="ml-auto inline-flex min-h-9 cursor-pointer items-center gap-1.5 rounded-lg bg-[#612035] px-3 text-xs font-medium text-white
              shadow-sm transition-colors hover:bg-[#4e1a2a] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] focus-visible:ring-offset-2"
          >
            <Icon name="add" size={14} /> Abrir prospecção
          </button>
        </>
      )}

      {abrindo && (
        <NovaProspeccaoDialog
          inicial={{ cnpj, razaoSocial }}
          onFechar={() => setAbrindo(false)}
          onCriado={card => router.push(`/prospeccao?card=${card.id}`)}
        />
      )}
    </div>
  );
}
