"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "@/components/liberacao/Dialogo";
import { toastResultado, useResultadoComite } from "@/hooks/useProspeccao";
import { ROTULO_MOTIVO, type Decisao, type MotivoRecusa, type ResultadoComite } from "@/types/prospeccao";
import CondicoesEditor, { condicoesValidas, novaLinha, type LinhaCondicao } from "./CondicoesEditor";
import InputMoeda from "./InputMoeda";
import { formatMoeda } from "./formatters";

const OPCOES: { resultado: ResultadoComite; titulo: string; ajuda: string; icone: string }[] = [
  { resultado: "APROVADO", titulo: "Aprovar", ajuda: "Segue para o aceite do cliente", icone: "check_circle" },
  { resultado: "DEVOLVIDO", titulo: "Devolver", ajuda: "Volta para a analista decidir de novo", icone: "undo" },
  { resultado: "REPROVADO", titulo: "Reprovar", ajuda: "Encerra o card, com motivo", icone: "block" },
];

const MOTIVOS = Object.keys(ROTULO_MOTIVO) as MotivoRecusa[];

/**
 * Resultado do comitê. Quem não deu parecer fica dito no histórico; o servidor exige ao menos um.
 *
 * <p>Aprovar mantém a proposta da análise ou, marcando o ajuste, grava limite e condições do comitê —
 * é a essa proposta que o cliente responde no aceite. Numa rodada aberta por contraproposta, o que o
 * cliente pediu aparece em cima, para o comitê decidir com o pedido à vista.</p>
 */
export default function ResultadoComiteDialog({
  cardId,
  razaoSocial,
  faltando,
  inicial = "APROVADO",
  proposta,
  pedidoCliente,
  onFechar,
}: {
  cardId: string;
  razaoSocial: string;
  /** Membros que ainda não deram parecer na rodada. */
  faltando: string[];
  inicial?: ResultadoComite;
  /** Proposta aprovada vigente (a da análise, ou a última ajustada). */
  proposta?: Decisao | null;
  /** Contraproposta do cliente que abriu esta rodada. */
  pedidoCliente?: { mensagem: string | null; limiteSugerido: number | null; condicoes: { produto: string; valor: number }[] } | null;
  onFechar: () => void;
}) {
  const registrar = useResultadoComite();
  const [resultado, setResultado] = useState<ResultadoComite>(inicial);
  const [motivo, setMotivo] = useState<MotivoRecusa | "">("");
  const [observacao, setObservacao] = useState("");
  const [ajustar, setAjustar] = useState(false);
  const [limite, setLimite] = useState<number | null>(proposta?.limite ?? null);
  const [linhas, setLinhas] = useState<LinhaCondicao[]>(() =>
    proposta && proposta.condicoes.length > 0 ? proposta.condicoes.map(c => novaLinha(c.produto, c.valor)) : [novaLinha()],
  );

  const reprova = resultado === "REPROVADO";
  const aprova = resultado === "APROVADO";
  const comAjuste = aprova && ajustar;
  const condicoes = condicoesValidas(linhas);
  const valido =
    (!reprova || (motivo !== "" && (motivo !== "OUTRO" || observacao.trim().length > 0))) &&
    (!comAjuste || (limite != null && limite > 0 && condicoes.length > 0));

  function enviar() {
    if (!valido || registrar.isPending) return;
    registrar.mutate(
      {
        id: cardId,
        resultado,
        motivoRecusa: reprova ? (motivo as MotivoRecusa) : undefined,
        observacao: observacao.trim() || undefined,
        ...(comAjuste ? { limite, condicoes } : {}),
      },
      {
        onSuccess: () => {
          toastResultado(resultado);
          onFechar();
        },
      },
    );
  }

  return (
    <Dialogo
      titulo="Resultado do comitê"
      subtitulo={razaoSocial}
      onFechar={onFechar}
      largura="md"
      rodape={
        <>
          <button type="button" onClick={onFechar} className={BOTAO_SECUNDARIO}>Cancelar</button>
          <button type="button" onClick={enviar} disabled={!valido || registrar.isPending} className={BOTAO_PRIMARIO}>
            {registrar.isPending ? "Registrando…" : "Registrar resultado"}
          </button>
        </>
      }
    >
      <div className="space-y-4 px-5 py-4">
        {faltando.length > 0 && (
          <p className="flex gap-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:bg-amber-950/30 dark:text-amber-100">
            <Icon name="warning" size={14} className="mt-px shrink-0" />
            Ainda sem parecer: {faltando.join(", ")}. Fica registrado no histórico.
          </p>
        )}
        {pedidoCliente && (
          <div className="rounded-lg border border-[#D1732C]/30 bg-[#D1732C]/8 px-3 py-2 text-xs text-[#7a3e12] dark:text-[#f7c9a6]">
            <p className="font-semibold">Contraproposta do cliente</p>
            {pedidoCliente.mensagem && <p className="mt-0.5">{pedidoCliente.mensagem}</p>}
            {(pedidoCliente.limiteSugerido != null || pedidoCliente.condicoes.length > 0) && (
              <p className="mt-0.5">
                Pede
                {pedidoCliente.limiteSugerido != null && ` limite de ${formatMoeda(pedidoCliente.limiteSugerido)}`}
                {pedidoCliente.condicoes.length > 0 &&
                  `${pedidoCliente.limiteSugerido != null ? ";" : ""} ${pedidoCliente.condicoes.map(c => `${c.produto} ${formatMoeda(c.valor)}`).join(", ")}`}
              </p>
            )}
          </div>
        )}
        <div role="radiogroup" aria-label="Resultado" className="grid gap-2">
          {OPCOES.map(opcao => (
            <button
              key={opcao.resultado}
              type="button"
              role="radio"
              aria-checked={resultado === opcao.resultado}
              onClick={() => setResultado(opcao.resultado)}
              className={`flex cursor-pointer items-center gap-3 rounded-xl border-2 px-3 py-2.5 text-left transition-colors
                focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] ${
                  resultado === opcao.resultado
                    ? "border-[#612035] bg-[#612035]/5 dark:border-[#e8a3b6] dark:bg-[#e8a3b6]/10"
                    : "border-slate-200 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
                }`}
            >
              <Icon name={opcao.icone} size={18} className="text-slate-500" />
              <span>
                <span className="block text-sm font-semibold text-slate-900 dark:text-white">{opcao.titulo}</span>
                <span className="block text-[11px] text-slate-500">{opcao.ajuda}</span>
              </span>
            </button>
          ))}
        </div>
        {aprova && proposta && (
          <div className="space-y-3 rounded-xl border border-slate-200 px-3 py-2.5 dark:border-slate-700">
            <p className="text-xs text-slate-600 dark:text-slate-300">
              <span className="font-semibold">Proposta vigente:</span> limite de {formatMoeda(proposta.limite ?? 0)}
              {proposta.condicoes.length > 0 && ` — ${proposta.condicoes.map(c => `${c.produto} ${formatMoeda(c.valor)}`).join(", ")}`}
            </p>
            <label className="flex cursor-pointer items-center gap-2 text-sm font-medium text-slate-800 dark:text-slate-100">
              <input type="checkbox" checked={ajustar} onChange={event => setAjustar(event.target.checked)} className="h-4 w-4 accent-[#612035]" />
              Ajustar limite e condições
            </label>
            {ajustar && (
              <div className="space-y-3">
                <InputMoeda valor={limite} onMudar={setLimite} rotulo="Limite aprovado pelo comitê" />
                <div>
                  <span className={ROTULO}>Condições (destinação do limite por produto)</span>
                  <CondicoesEditor linhas={linhas} onMudar={setLinhas} limite={limite} />
                </div>
              </div>
            )}
          </div>
        )}
        {reprova && (
          <label className="block">
            <span className={ROTULO}>Motivo</span>
            <select value={motivo} onChange={event => setMotivo(event.target.value as MotivoRecusa)} className={`${CAMPO} cursor-pointer`}>
              <option value="">Escolha o motivo…</option>
              {MOTIVOS.map(opcao => (
                <option key={opcao} value={opcao}>{ROTULO_MOTIVO[opcao]}</option>
              ))}
            </select>
          </label>
        )}
        <label className="block">
          <span className={ROTULO}>Observação{reprova && motivo === "OUTRO" ? "" : " (opcional)"}</span>
          <textarea rows={3} value={observacao} onChange={event => setObservacao(event.target.value)} className={`${CAMPO} resize-y`} />
        </label>
      </div>
    </Dialogo>
  );
}
