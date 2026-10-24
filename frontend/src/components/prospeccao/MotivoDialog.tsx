"use client";

import { useState } from "react";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "@/components/liberacao/Dialogo";
import { ROTULO_ESTAGIO, ROTULO_MOTIVO, type EstagioProspeccao, type MotivoRecusa } from "@/types/prospeccao";

const MOTIVOS = Object.keys(ROTULO_MOTIVO) as MotivoRecusa[];

/**
 * Pergunta o que a passagem exige antes de mover: motivo para reprovar e inerte, texto livre para
 * a recusa do cliente e para reabrir.
 *
 * <p>Substitui o {@code window.prompt} numerado da primeira versão, em que a analista digitava
 * "3" para dizer "Pefin com fundo".</p>
 */
export default function MotivoDialog({
  destino,
  modo,
  onConfirmar,
  onFechar,
  enviando,
}: {
  destino: EstagioProspeccao;
  /**
   * motivo: enum + observação (obrigatória em Outro), com título, botão e aviso próprios quando a
   * passagem não é a simples (o cancelamento). texto: só observação, obrigatória ou não.
   */
  modo:
    | { tipo: "motivo"; titulo?: string; botao?: string; aviso?: string }
    | { tipo: "texto"; titulo: string; rotulo: string; obrigatorio: boolean; botao: string };
  onConfirmar: (dados: { motivoRecusa?: MotivoRecusa; observacao?: string }) => void;
  onFechar: () => void;
  enviando: boolean;
}) {
  const [motivo, setMotivo] = useState<MotivoRecusa | "">("");
  const [texto, setTexto] = useState("");

  const exigeTexto = modo.tipo === "motivo" ? motivo === "OUTRO" : modo.obrigatorio;
  const valido = (modo.tipo === "texto" || motivo !== "") && (!exigeTexto || texto.trim().length > 0);

  function confirmar() {
    if (!valido || enviando) return;
    onConfirmar({
      motivoRecusa: modo.tipo === "motivo" && motivo !== "" ? motivo : undefined,
      observacao: texto.trim() || undefined,
    });
  }

  return (
    <Dialogo
      titulo={modo.tipo === "motivo" ? (modo.titulo ?? `${ROTULO_ESTAGIO[destino]}: qual o motivo?`) : modo.titulo}
      onFechar={onFechar}
      largura="sm"
      rodape={
        <>
          <button type="button" onClick={onFechar} className={BOTAO_SECUNDARIO}>Cancelar</button>
          <button type="button" onClick={confirmar} disabled={!valido || enviando} className={BOTAO_PRIMARIO}>
            {enviando ? "Salvando…" : modo.tipo === "motivo" ? (modo.botao ?? ROTULO_ESTAGIO[destino]) : modo.botao}
          </button>
        </>
      }
    >
      <div className="space-y-4 px-5 py-4">
        {modo.tipo === "motivo" && modo.aviso && <p className="text-sm text-slate-600 dark:text-slate-300">{modo.aviso}</p>}
        {modo.tipo === "motivo" && (
          <fieldset>
            <legend className={ROTULO}>Motivo</legend>
            <div className="grid gap-1.5">
              {MOTIVOS.map(opcao => (
                <label
                  key={opcao}
                  className={`flex min-h-10 cursor-pointer items-center gap-2.5 rounded-lg border px-3 text-sm transition-colors ${
                    motivo === opcao
                      ? "border-[#612035] bg-[#612035]/5 text-slate-900 dark:border-[#e8a3b6] dark:bg-[#e8a3b6]/10 dark:text-white"
                      : "border-slate-200 text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
                  }`}
                >
                  <input
                    type="radio"
                    name="motivo"
                    value={opcao}
                    checked={motivo === opcao}
                    onChange={() => setMotivo(opcao)}
                    className="accent-[#612035]"
                  />
                  {ROTULO_MOTIVO[opcao]}
                </label>
              ))}
            </div>
          </fieldset>
        )}
        <label className="block">
          <span className={ROTULO}>
            {modo.tipo === "motivo" ? `Observação${exigeTexto ? "" : " (opcional)"}` : modo.rotulo}
          </span>
          <textarea
            rows={3}
            value={texto}
            onChange={event => setTexto(event.target.value)}
            data-autofocus={modo.tipo === "texto" ? true : undefined}
            className={`${CAMPO} resize-y`}
          />
        </label>
      </div>
    </Dialogo>
  );
}
