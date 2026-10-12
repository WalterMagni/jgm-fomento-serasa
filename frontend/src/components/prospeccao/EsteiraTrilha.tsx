"use client";

import Icon from "@/components/ui/Icon";
import { ROTULO_ESTAGIO, type EstagioProspeccao, type ProspeccaoEvento } from "@/types/prospeccao";
import { formatDate } from "./formatters";

/** O caminho feliz, na ordem em que o card anda. */
const CAMINHO: EstagioProspeccao[] = ["NOVA", "EM_ANALISE", "AGUARDANDO_ACEITE", "DOCUMENTACAO", "COMITE", "CONTRATO", "HABILITADO"];

const ROTULO_CURTO: Partial<Record<EstagioProspeccao, string>> = {
  AGUARDANDO_ACEITE: "Aceite",
  DOCUMENTACAO: "Documentos",
};

/**
 * Trilha das etapas do card, com a data em que ele entrou em cada uma.
 *
 * <p>A data vem da timeline — a última entrada em cada estágio —, não de colunas no card: o card
 * pode voltar e avançar de novo, e o que interessa é quando ele chegou pela última vez.
 * Desfecho negativo corta a trilha no ponto onde o card parou, em vermelho.</p>
 */
export default function EsteiraTrilha({
  estagio,
  timeline,
  criadoEm,
}: {
  estagio: EstagioProspeccao;
  timeline: ProspeccaoEvento[];
  criadoEm: string;
}) {
  const chegada = new Map<EstagioProspeccao, string>();
  chegada.set("NOVA", criadoEm);
  // A timeline vem do mais novo para o mais antigo: a primeira ocorrência é a mais recente.
  for (const evento of timeline) {
    if (evento.estagioPara && !chegada.has(evento.estagioPara)) chegada.set(evento.estagioPara, evento.criadoEm);
  }

  const negativo = estagio === "REPROVADO" || estagio === "RECUSADO_CLIENTE" || estagio === "INERTE";
  // Onde o card estava quando saiu do caminho: o último estágio do caminho por onde passou.
  const ultimoNoCaminho = negativo
    ? [...CAMINHO].reverse().find(etapa => chegada.has(etapa) && etapa !== "HABILITADO") ?? "NOVA"
    : estagio;
  const atual = CAMINHO.indexOf(ultimoNoCaminho);

  return (
    <ol className="flex items-start overflow-x-auto pb-1" aria-label="Etapas do card">
      {CAMINHO.map((etapa, i) => {
        const feita = i < atual || (!negativo && estagio === "HABILITADO" && i === atual);
        const corrente = i === atual && !negativo && estagio !== "HABILITADO";
        const corte = negativo && i === atual;
        return (
          <li key={etapa} className="flex min-w-[5.5rem] flex-1 flex-col items-center gap-1 text-center">
            <div className="flex w-full items-center">
              <span className={`h-0.5 flex-1 ${i === 0 ? "invisible" : feita || corrente || corte ? "bg-[#612035] dark:bg-[#e8a3b6]" : "bg-slate-200 dark:bg-slate-700"}`} />
              <span
                aria-current={corrente ? "step" : undefined}
                className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 ${
                  corte
                    ? "border-rose-600 bg-rose-600 text-white"
                    : feita
                      ? "border-[#612035] bg-[#612035] text-white dark:border-[#e8a3b6] dark:bg-[#e8a3b6] dark:text-slate-900"
                      : corrente
                        ? "border-[#612035] bg-white dark:border-[#e8a3b6] dark:bg-slate-900"
                        : "border-slate-300 bg-white dark:border-slate-600 dark:bg-slate-900"
                }`}
              >
                {corte ? <Icon name="close" size={12} strokeWidth={3} /> : feita ? <Icon name="check" size={12} strokeWidth={3} /> : corrente ? (
                  <span className="h-2 w-2 rounded-full bg-[#612035] dark:bg-[#e8a3b6]" />
                ) : null}
              </span>
              <span className={`h-0.5 flex-1 ${i === CAMINHO.length - 1 ? "invisible" : feita ? "bg-[#612035] dark:bg-[#e8a3b6]" : "bg-slate-200 dark:bg-slate-700"}`} />
            </div>
            <span className={`text-[11px] font-medium ${corrente ? "text-slate-900 dark:text-white" : corte ? "text-rose-700 dark:text-rose-300" : "text-slate-500 dark:text-slate-400"}`}>
              {corte ? ROTULO_ESTAGIO[estagio] : ROTULO_CURTO[etapa] ?? ROTULO_ESTAGIO[etapa]}
            </span>
            <span className="text-[10px] tabular-nums text-slate-400">
              {(feita || corrente || corte) && chegada.has(etapa) ? formatDate(chegada.get(etapa)) : " "}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
