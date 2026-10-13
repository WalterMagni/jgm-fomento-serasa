"use client";

import type { CSSProperties } from "react";
import Icon from "@/components/ui/Icon";
import { ROTULO_ESTAGIO, type EstagioProspeccao, type ProspeccaoEvento } from "@/types/prospeccao";
import { COR_ESTAGIO } from "./estilo";
import { formatDate } from "./formatters";

/** O caminho feliz, na ordem em que o card anda. */
const CAMINHO: EstagioProspeccao[] = ["NOVA", "EM_ANALISE", "APROVADO", "DOCUMENTACAO", "COMITE", "AGUARDANDO_ACEITE", "CONTRATO", "HABILITADO"];

const ROTULO_CURTO: Partial<Record<EstagioProspeccao, string>> = {
  AGUARDANDO_ACEITE: "Aceite",
  DOCUMENTACAO: "Documentos",
};

const ICONE: Record<string, string> = {
  NOVA: "inbox",
  EM_ANALISE: "search",
  APROVADO: "thumb_up",
  DOCUMENTACAO: "folder_open",
  AGUARDANDO_ACEITE: "handshake",
  COMITE: "groups",
  CONTRATO: "contract",
  HABILITADO: "verified",
};

/** Intervalo entre uma etapa e a próxima na animação de abertura. */
const PASSO_MS = 90;

/**
 * Trilha das etapas do card, com a data em que ele chegou em cada uma.
 *
 * <p>Cada etapa concluída leva a cor do próprio estágio, e o trecho entre duas etapas faz o
 * degradê de uma para a outra — a trilha mostra o caminho percorrido, não só onde o card está. Ao
 * abrir, os trechos se preenchem em sequência; a etapa atual pulsa e um brilho corre em direção à
 * próxima. Quem pediu menos movimento vê a trilha pronta, sem animação.</p>
 *
 * <p>A data vem da timeline — a última entrada em cada estágio —, porque o card pode voltar e
 * avançar de novo. Desfecho negativo corta a trilha onde o card parou, em vermelho.</p>
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
  const ultimoNoCaminho = negativo
    ? [...CAMINHO].reverse().find(etapa => chegada.has(etapa) && etapa !== "HABILITADO") ?? "NOVA"
    : estagio;
  const atual = CAMINHO.indexOf(ultimoNoCaminho);
  const concluiu = estagio === "HABILITADO";

  return (
    <ol className="flex items-start overflow-x-auto pb-1" aria-label="Etapas do card">
      {CAMINHO.map((etapa, i) => {
        const feita = i < atual || (concluiu && i === atual);
        const corrente = i === atual && !negativo && !concluiu;
        const corte = negativo && i === atual;
        const alcancada = feita || corrente || corte;
        const cor = COR_ESTAGIO[etapa].hex;
        const corAnterior = i > 0 ? COR_ESTAGIO[CAMINHO[i - 1]].hex : cor;
        const atraso = i * PASSO_MS;

        return (
          <li
            key={etapa}
            className="flex min-w-[5.5rem] flex-1 flex-col items-center gap-1.5 text-center"
            style={{ "--cor-etapa": corte ? "#e11d48" : cor } as CSSProperties}
          >
            <div className="flex w-full items-center">
              {/* Trecho que chega nesta etapa. */}
              <span className={`relative h-1 flex-1 overflow-hidden rounded-full ${i === 0 ? "invisible" : "bg-slate-200 dark:bg-slate-700"}`}>
                {i > 0 && alcancada && (
                  <span
                    className="trilha-preencher absolute inset-0 rounded-full"
                    style={{
                      // Metade de chegada: do ponto médio entre as duas cores até a cor desta etapa.
                      background: corte ? "#e11d48" : `linear-gradient(90deg, color-mix(in srgb, ${corAnterior} 50%, ${cor}), ${cor})`,
                      animationDelay: `${atraso}ms`,
                    }}
                  />
                )}
              </span>

              <span
                aria-current={corrente ? "step" : undefined}
                className={`relative flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 transition-colors
                  ${feita || corte ? "trilha-no text-white" : ""}
                  ${corrente ? "trilha-pulso bg-white dark:bg-slate-900" : ""}
                  ${!alcancada ? "border-slate-200 bg-white text-slate-300 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-600" : ""}`}
                style={{
                  borderColor: alcancada ? (corte ? "#e11d48" : cor) : undefined,
                  backgroundColor: feita ? cor : corte ? "#e11d48" : undefined,
                  color: corrente ? cor : undefined,
                  animationDelay: feita || corte ? `${atraso + 150}ms` : undefined,
                }}
              >
                {corte ? (
                  <Icon name="close" size={14} strokeWidth={3} />
                ) : feita ? (
                  <Icon name="check" size={14} strokeWidth={3} />
                ) : (
                  <Icon name={ICONE[etapa]} size={14} />
                )}
              </span>

              {/* Trecho que sai desta etapa: corre um brilho enquanto o card está aqui. */}
              <span className={`relative h-1 flex-1 overflow-hidden rounded-full ${i === CAMINHO.length - 1 ? "invisible" : "bg-slate-200 dark:bg-slate-700"}`}>
                {i < CAMINHO.length - 1 && i < atual && !corte && (
                  <span
                    className="trilha-preencher absolute inset-0 rounded-full"
                    // Metade de saída: da cor desta etapa até o ponto médio com a próxima.
                    style={{
                      background: `linear-gradient(90deg, ${cor}, color-mix(in srgb, ${cor} 50%, ${COR_ESTAGIO[CAMINHO[i + 1]].hex}))`,
                      animationDelay: `${atraso + PASSO_MS / 2}ms`,
                    }}
                  />
                )}
                {corrente && i < CAMINHO.length - 1 && <span className="trilha-fluxo absolute inset-0" />}
              </span>
            </div>
            <span
              className={`text-[11px] font-semibold ${
                corrente ? "" : corte ? "text-rose-700 dark:text-rose-300" : feita ? "text-slate-700 dark:text-slate-200" : "text-slate-400"
              }`}
              style={corrente ? { color: cor } : undefined}
            >
              {corte ? ROTULO_ESTAGIO[estagio] : ROTULO_CURTO[etapa] ?? ROTULO_ESTAGIO[etapa]}
            </span>
            <span className="-mt-1 text-[10px] tabular-nums text-slate-400">
              {alcancada && chegada.has(etapa) ? formatDate(chegada.get(etapa)) : " "}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
