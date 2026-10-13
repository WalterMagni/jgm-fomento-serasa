"use client";

import { NOTA, NOTAS, type NotaEmpresa } from "@/types/nota-empresa";

export type FiltroDeNota = "" | NotaEmpresa | "SEM";

const BOTAO =
  "inline-flex h-10 items-center gap-2 rounded-[10px] px-3 text-[13px] font-semibold transition-[background-color,color,box-shadow] duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary";
const ATIVO = "bg-white text-grafite shadow-[0_1px_2px_rgba(38,40,51,0.08),0_6px_14px_-8px_rgba(38,40,51,0.3)]";
const INATIVO = "text-[#5E5C55] hover:text-grafite";

/** Filtro da carteira pela nota da equipe: as quatro letras nas cores do selo, mais "sem score". */
export default function FiltroNota({ valor, onChange }: { valor: FiltroDeNota; onChange: (valor: FiltroDeNota) => void }) {
  return (
    <div role="group" aria-label="Filtrar pelo score" className="inline-flex flex-wrap gap-0.5 rounded-[13px] bg-[#ECE9E1] p-[3px]">
      <button type="button" aria-pressed={valor === ""} onClick={() => onChange("")} className={`${BOTAO} ${valor === "" ? ATIVO : INATIVO}`}>
        Todo score
      </button>
      {NOTAS.map(letra => {
        const ativo = valor === letra;
        return (
          <button
            key={letra}
            type="button"
            aria-pressed={ativo}
            aria-label={`Score ${letra}: ${NOTA[letra].rotulo}`}
            title={`${letra} · ${NOTA[letra].rotulo}`}
            onClick={() => onChange(ativo ? "" : letra)}
            className={`${BOTAO} !px-2 ${ativo ? ATIVO : INATIVO}`}
          >
            <span
              aria-hidden
              className={`grid h-6 w-6 place-items-center rounded-[7px] font-display text-[13px] font-semibold text-white transition-transform duration-200 ${
                ativo ? "scale-110" : ""
              }`}
              style={{ background: NOTA[letra].cor, textShadow: "0 1px 1px rgba(0,0,0,0.3)" }}
            >
              {letra}
            </span>
          </button>
        );
      })}
      <button
        type="button"
        aria-pressed={valor === "SEM"}
        onClick={() => onChange(valor === "SEM" ? "" : "SEM")}
        className={`${BOTAO} ${valor === "SEM" ? ATIVO : INATIVO}`}
      >
        Sem score
      </button>
    </div>
  );
}
