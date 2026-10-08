"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import type { NovaPendencia } from "@/types/liberacao";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "./Dialogo";
import PessoaSelect from "./PessoaSelect";

type Linha = { chave: number; destinatarioId: string | null; texto: string };

type Props = {
  titulo: string;
  subtitulo?: string;
  /** Ao mover para Pendência dá para pedir a várias pessoas de uma vez. */
  multiplas?: boolean;
  euId?: string;
  enviando: boolean;
  onConfirmar: (pendencias: NovaPendencia[]) => void;
  onFechar: () => void;
};

let chave = 1;

export default function PendenciaDialog({ titulo, subtitulo, multiplas = false, euId, enviando, onConfirmar, onFechar }: Props) {
  const [linhas, setLinhas] = useState<Linha[]>([{ chave: chave++, destinatarioId: null, texto: "" }]);
  const completas = linhas.filter(linha => linha.destinatarioId && linha.texto.trim());
  const algumaIncompleta = linhas.some(linha => (linha.destinatarioId ? !linha.texto.trim() : linha.texto.trim()));

  return (
    <Dialogo
      titulo={titulo}
      subtitulo={subtitulo}
      onFechar={onFechar}
      largura="md"
      rodape={
        <>
          <button type="button" onClick={onFechar} className={BOTAO_SECUNDARIO}>
            Cancelar
          </button>
          <button
            type="button"
            disabled={enviando || completas.length === 0 || algumaIncompleta}
            onClick={() => onConfirmar(completas.map(linha => ({ destinatarioId: linha.destinatarioId!, texto: linha.texto.trim() })))}
            className={BOTAO_PRIMARIO}
          >
            {enviando && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />}
            {multiplas ? "Mover para Pendência" : "Abrir pendência"}
          </button>
        </>
      }
    >
      <div className="space-y-4 px-5 py-5">
        {linhas.map((linha, indice) => (
          <div key={linha.chave} className="rounded-xl border border-slate-200 p-3 dark:border-slate-700">
            <div className="mb-2 flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-500">
                {multiplas && linhas.length > 1 ? `Pendência ${indice + 1}` : "Pendência"}
              </span>
              {linhas.length > 1 && (
                <button
                  type="button"
                  onClick={() => setLinhas(atuais => atuais.filter(item => item.chave !== linha.chave))}
                  className="cursor-pointer rounded px-1.5 text-xs text-slate-400 hover:text-rose-600"
                >
                  remover
                </button>
              )}
            </div>
            <label className={ROTULO} htmlFor={`pend-para-${linha.chave}`}>
              Para quem
            </label>
            <PessoaSelect
              id={`pend-para-${linha.chave}`}
              valor={linha.destinatarioId}
              excluir={euId}
              onEscolher={id => setLinhas(atuais => atuais.map(item => (item.chave === linha.chave ? { ...item, destinatarioId: id } : item)))}
            />
            <label className={`${ROTULO} mt-3`} htmlFor={`pend-texto-${linha.chave}`}>
              O que falta
            </label>
            <textarea
              id={`pend-texto-${linha.chave}`}
              value={linha.texto}
              onChange={event =>
                setLinhas(atuais => atuais.map(item => (item.chave === linha.chave ? { ...item, texto: event.target.value } : item)))
              }
              rows={3}
              placeholder="Ex.: confirmar com o sacado o aceite das duplicatas 1042 a 1048"
              className={`${CAMPO} resize-y`}
            />
          </div>
        ))}
        {multiplas && (
          <button
            type="button"
            onClick={() => setLinhas(atuais => [...atuais, { chave: chave++, destinatarioId: null, texto: "" }])}
            className="inline-flex cursor-pointer items-center gap-1 text-xs font-medium text-[#612035] hover:underline dark:text-[#e8a3b6]"
          >
            <Icon name="add" size={14} /> pedir para mais alguém
          </button>
        )}
      </div>
    </Dialogo>
  );
}
