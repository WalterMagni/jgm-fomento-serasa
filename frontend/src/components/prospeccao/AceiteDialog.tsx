"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "@/components/liberacao/Dialogo";
import { useRegistrarAceite } from "@/hooks/useProspeccao";
import type { Decisao, RespostaCliente } from "@/types/prospeccao";
import CondicoesEditor, { condicoesValidas, novaLinha, type LinhaCondicao } from "./CondicoesEditor";
import InputMoeda from "./InputMoeda";
import { formatMoeda } from "./formatters";

const OPCOES: { resposta: RespostaCliente; titulo: string; ajuda: string; icone: string }[] = [
  { resposta: "ACEITO", titulo: "Aceitou", ajuda: "Segue para a documentação", icone: "check_circle" },
  { resposta: "CONTRAPROPOSTA", titulo: "Contraproposta", ajuda: "Volta para a analista decidir de novo", icone: "swap_horiz" },
  { resposta: "RECUSADO", titulo: "Recusou", ajuda: "Encerra o card", icone: "block" },
];

/**
 * Resposta do cliente à proposta, como o comercial registrava no Portal Comercial.
 *
 * <p>Na Fase A quem registra é a equipe interna, com o que o comercial trouxe; o servidor marca a
 * resposta como "em nome do comercial".</p>
 */
export default function AceiteDialog({
  cardId,
  razaoSocial,
  proposta,
  inicial = "ACEITO",
  onFechar,
}: {
  cardId: string;
  razaoSocial: string;
  proposta: Decisao;
  inicial?: RespostaCliente;
  onFechar: () => void;
}) {
  const registrar = useRegistrarAceite();
  const [resposta, setResposta] = useState<RespostaCliente>(inicial);
  const [mensagem, setMensagem] = useState("");
  const [limite, setLimite] = useState<number | null>(null);
  const [linhas, setLinhas] = useState<LinhaCondicao[]>([novaLinha()]);

  const contraproposta = resposta === "CONTRAPROPOSTA";
  const valido = !contraproposta || mensagem.trim().length > 0;

  function enviar() {
    if (!valido || registrar.isPending) return;
    registrar.mutate(
      {
        id: cardId,
        aceite: {
          resposta,
          mensagem: mensagem.trim() || undefined,
          limiteSugerido: contraproposta ? limite : undefined,
          condicoesSugeridas: contraproposta ? condicoesValidas(linhas) : undefined,
        },
      },
      { onSuccess: onFechar },
    );
  }

  return (
    <Dialogo
      titulo="Resposta do cliente"
      subtitulo={razaoSocial}
      onFechar={onFechar}
      largura="lg"
      rodape={
        <>
          <button type="button" onClick={onFechar} className={BOTAO_SECUNDARIO}>Cancelar</button>
          <button type="button" onClick={enviar} disabled={!valido || registrar.isPending} className={BOTAO_PRIMARIO}>
            {registrar.isPending ? "Registrando…" : "Registrar resposta"}
          </button>
        </>
      }
    >
      <div className="space-y-5 px-5 py-4">
        <div className="rounded-xl border border-slate-200 px-4 py-3 text-sm dark:border-slate-700">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">Proposta enviada</p>
          <p className="mt-1 font-semibold text-slate-900 dark:text-white">Limite de {formatMoeda(proposta.limite)}</p>
          <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
            {proposta.condicoes.map(c => `${c.produto} ${formatMoeda(c.valor)}`).join(" · ")}
          </p>
        </div>

        <div role="radiogroup" aria-label="Resposta do cliente" className="grid gap-2 sm:grid-cols-3">
          {OPCOES.map(opcao => (
            <button
              key={opcao.resposta}
              type="button"
              role="radio"
              aria-checked={resposta === opcao.resposta}
              onClick={() => setResposta(opcao.resposta)}
              className={`flex cursor-pointer flex-col items-start gap-0.5 rounded-xl border-2 px-3 py-2.5 text-left transition-colors
                focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] ${
                  resposta === opcao.resposta
                    ? "border-[#612035] bg-[#612035]/5 dark:border-[#e8a3b6] dark:bg-[#e8a3b6]/10"
                    : "border-slate-200 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
                }`}
            >
              <span className="inline-flex items-center gap-1.5 text-sm font-semibold text-slate-900 dark:text-white">
                <Icon name={opcao.icone} size={16} />
                {opcao.titulo}
              </span>
              <span className="text-[11px] text-slate-500 dark:text-slate-400">{opcao.ajuda}</span>
            </button>
          ))}
        </div>

        {contraproposta && (
          <div className="space-y-3 rounded-xl bg-slate-50 p-4 dark:bg-slate-800/50">
            <label className="block max-w-xs">
              <span className={ROTULO}>Limite que o cliente pede (opcional)</span>
              <InputMoeda valor={limite} onMudar={setLimite} rotulo="Limite sugerido" />
            </label>
            <div>
              <span className={ROTULO}>Condições que o cliente pede (opcional)</span>
              <CondicoesEditor linhas={linhas} onMudar={setLinhas} limite={limite} />
            </div>
          </div>
        )}

        <label className="block">
          <span className={ROTULO}>
            {contraproposta ? "O que o cliente pediu" : resposta === "RECUSADO" ? "O que o cliente disse (opcional)" : "Observação (opcional)"}
          </span>
          <textarea rows={3} value={mensagem} onChange={event => setMensagem(event.target.value)} className={`${CAMPO} resize-y`} />
        </label>
      </div>
    </Dialogo>
  );
}
