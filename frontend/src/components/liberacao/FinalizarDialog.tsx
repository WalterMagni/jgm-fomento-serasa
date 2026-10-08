"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import MentionTextarea from "@/components/ui/mencao/MentionTextarea";
import { useLiberacaoDetalhe } from "@/hooks/useLiberacao";
import {
  ROTULO_RESULTADO,
  ROTULO_RESULTADO_CURTO,
  type DecisaoSacado,
  type LiberacaoCard,
  type Resultado,
} from "@/types/liberacao";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, ROTULO } from "./Dialogo";
import { COR_RESULTADO, ICONE_RESULTADO, formatDocumento, formatMoeda, moedaParaCampo, parseMoeda } from "./formatters";

const OPCOES: Resultado[] = ["APROVADO", "PARCIAL", "REPROVADO"];

type Escolha = { situacao: Resultado | null; valorAprovado: string };

/** Mesmo cálculo do servidor (ResultadoLiberacao.doCard), para mostrar antes de confirmar. */
function resultadoDe(situacoes: (Resultado | null)[]): Resultado | null {
  if (situacoes.length === 0 || situacoes.some(situacao => !situacao)) return null;
  if (situacoes.every(situacao => situacao === "APROVADO")) return "APROVADO";
  if (situacoes.every(situacao => situacao === "REPROVADO")) return "REPROVADO";
  return "PARCIAL";
}

type Props = {
  card: LiberacaoCard;
  /** Aviso de parecer faltando no Comitê, quando houver. */
  aviso: string | null;
  enviando: boolean;
  onConfirmar: (dados: { decisoes: DecisaoSacado[]; resultado?: Resultado; observacao?: string }) => void;
  onFechar: () => void;
};

/**
 * Finalizar é decidir cada sacado. O resultado do card sai das decisões — todos aprovados,
 * todos reprovados ou parcialmente aprovado — e aparece aqui antes de confirmar.
 *
 * <p>Vem preenchido com o que já foi decidido na lista do card; só falta o resto.</p>
 */
export default function FinalizarDialog({ card, aviso, enviando, onConfirmar, onFechar }: Props) {
  const { data, isLoading } = useLiberacaoDetalhe(card.id);
  const sacados = data?.sacados ?? [];
  const [escolhas, setEscolhas] = useState<Record<string, Escolha>>({});
  const [semSacados, setSemSacados] = useState<Resultado | null>(null);
  const [observacao, setObservacao] = useState("");

  const escolha = (documento: string): Escolha => {
    if (escolhas[documento]) return escolhas[documento];
    const sacado = sacados.find(item => item.documento === documento);
    return { situacao: sacado?.situacao ?? null, valorAprovado: moedaParaCampo(sacado?.valorAprovado) };
  };
  const mudar = (documento: string, parcial: Partial<Escolha>) =>
    setEscolhas(atuais => ({ ...atuais, [documento]: { ...escolha(documento), ...parcial } }));

  const resultado = sacados.length === 0 ? semSacados : resultadoDe(sacados.map(sacado => escolha(sacado.documento).situacao));
  const parcialSemValor = sacados.find(sacado => {
    const atual = escolha(sacado.documento);
    return atual.situacao === "PARCIAL" && !(parseMoeda(atual.valorAprovado) ?? 0);
  });
  const valorAprovado = sacados.reduce((total, sacado) => {
    const atual = escolha(sacado.documento);
    if (atual.situacao === "APROVADO") return total + (sacado.valor ?? 0);
    if (atual.situacao === "PARCIAL") return total + (parseMoeda(atual.valorAprovado) ?? 0);
    return total;
  }, 0);
  const pronto = !!resultado && !parcialSemValor && !isLoading;

  function confirmar() {
    if (!pronto) return;
    onConfirmar({
      decisoes: sacados.map(sacado => {
        const atual = escolha(sacado.documento);
        return {
          documento: sacado.documento,
          situacao: atual.situacao,
          valorAprovado: atual.situacao === "PARCIAL" ? parseMoeda(atual.valorAprovado) : null,
        };
      }),
      resultado: sacados.length === 0 ? semSacados ?? undefined : undefined,
      observacao: observacao.trim() || undefined,
    });
  }

  return (
    <Dialogo
      titulo={`Finalizar #${card.numero}`}
      subtitulo={card.cedenteNome}
      onFechar={onFechar}
      largura="lg"
      rodape={
        <>
          <button type="button" onClick={onFechar} className={BOTAO_SECUNDARIO}>
            Cancelar
          </button>
          <button type="button" disabled={!pronto || enviando} onClick={confirmar} className={BOTAO_PRIMARIO}>
            {enviando && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />}
            {resultado ? `Finalizar: ${ROTULO_RESULTADO[resultado]}` : "Finalizar"}
          </button>
        </>
      }
    >
      <div className="space-y-4 px-5 py-5">
        {aviso && (
          <p className="flex items-start gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-900/30 dark:text-amber-100">
            <Icon name="warning" size={16} className="mt-0.5 shrink-0" />
            <span>{aviso} O card sai do Comitê mesmo assim, e o histórico registra.</span>
          </p>
        )}
        {card.pendenciasAbertas > 0 && (
          <p className="flex items-start gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-900/30 dark:text-amber-100">
            <Icon name="hourglass_top" size={16} className="mt-0.5 shrink-0" />
            Há {card.pendenciasAbertas} pendência(s) sem resposta.
          </p>
        )}

        {isLoading ? (
          <p className="text-sm text-slate-500">Carregando os sacados…</p>
        ) : sacados.length === 0 ? (
          <div>
            <span className={ROTULO}>Card sem sacados: qual o resultado?</span>
            <div className="flex gap-2">
              {(["APROVADO", "REPROVADO"] as Resultado[]).map(opcao => (
                <button
                  key={opcao}
                  type="button"
                  aria-pressed={semSacados === opcao}
                  onClick={() => setSemSacados(opcao)}
                  className={`flex min-h-10 flex-1 cursor-pointer items-center justify-center gap-1.5 rounded-lg border text-sm font-medium ${
                    semSacados === opcao ? `${COR_RESULTADO[opcao]} border-transparent` : "border-slate-200 text-slate-600 dark:border-slate-700 dark:text-slate-300"
                  }`}
                >
                  <Icon name={ICONE_RESULTADO[opcao]} size={15} /> {ROTULO_RESULTADO[opcao]}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div>
            <span className={ROTULO}>Decisão de cada sacado</span>
            <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 dark:divide-slate-800 dark:border-slate-700">
              {sacados.map(sacado => {
                const atual = escolha(sacado.documento);
                return (
                  <li key={sacado.documento} className="flex flex-col gap-2 px-3 py-2.5 sm:flex-row sm:items-center">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-slate-800 dark:text-slate-100">{sacado.nome ?? formatDocumento(sacado.documento)}</p>
                      <p className="text-[11px] text-slate-500">
                        <span className="font-mono">{formatDocumento(sacado.documento)}</span>
                        {sacado.valor != null && <> · {formatMoeda(sacado.valor)}</>}
                      </p>
                    </div>
                    <div className="flex items-center gap-1" role="radiogroup" aria-label={`Decisão de ${sacado.nome ?? sacado.documento}`}>
                      {OPCOES.map(opcao => (
                        <button
                          key={opcao}
                          type="button"
                          role="radio"
                          aria-checked={atual.situacao === opcao}
                          onClick={() => mudar(sacado.documento, { situacao: opcao })}
                          className={`inline-flex min-h-8 cursor-pointer items-center gap-1 rounded-lg border px-2 text-xs font-medium ${
                            atual.situacao === opcao
                              ? `${COR_RESULTADO[opcao]} border-transparent`
                              : "border-slate-200 text-slate-500 hover:border-slate-300 dark:border-slate-700 dark:text-slate-400"
                          }`}
                        >
                          <Icon name={ICONE_RESULTADO[opcao]} size={12} />
                          {ROTULO_RESULTADO_CURTO[opcao]}
                        </button>
                      ))}
                    </div>
                    {atual.situacao === "PARCIAL" && (
                      <div className="relative w-full sm:w-32">
                        <span className="pointer-events-none absolute left-2 top-1/2 -translate-y-1/2 text-xs text-slate-400">R$</span>
                        <input
                          inputMode="decimal"
                          autoFocus
                          value={atual.valorAprovado}
                          onChange={event => mudar(sacado.documento, { valorAprovado: event.target.value })}
                          onBlur={() => mudar(sacado.documento, { valorAprovado: moedaParaCampo(parseMoeda(atual.valorAprovado)) })}
                          placeholder="aprovado"
                          aria-label={`Valor aprovado de ${sacado.nome ?? sacado.documento}`}
                          className="w-full rounded-lg border border-amber-300 bg-white py-1.5 pl-7 pr-2 text-right text-sm tabular-nums focus:outline-none
                            focus:ring-2 focus:ring-amber-400/30 dark:border-amber-700 dark:bg-slate-800 dark:text-white"
                        />
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        )}

        {resultado && (
          <p className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium ${COR_RESULTADO[resultado]}`}>
            <Icon name={ICONE_RESULTADO[resultado]} size={16} />
            Resultado: {ROTULO_RESULTADO[resultado]}
            {sacados.length > 0 && <span className="ml-auto tabular-nums">aprovado {formatMoeda(valorAprovado)}</span>}
          </p>
        )}
        {parcialSemValor && <p className="text-xs text-amber-700 dark:text-amber-300">Informe o valor aprovado de cada sacado parcial.</p>}

        <div>
          <label htmlFor="fin-obs" className={ROTULO}>
            Observação <span className="font-normal text-slate-400">(vai para o histórico)</span>
          </label>
          <MentionTextarea id="fin-obs" value={observacao} onChange={setObservacao} rows={2} />
        </div>
      </div>
    </Dialogo>
  );
}
