"use client";

import Icon from "@/components/ui/Icon";
import MentionTextarea from "@/components/ui/mencao/MentionTextarea";
import { ROTULO_POSICAO, type PosicaoParecer } from "@/types/liberacao";
import { COR_POSICAO, ICONE_POSICAO } from "./formatters";

/**
 * A posição (favorável, com ressalvas, desfavorável) e o texto do parecer da origem. O mesmo no
 * formulário do card e na edição só do parecer, no detalhe. Clicar de novo na posição marcada a tira.
 */
export default function ParecerOrigemCampos({
  id,
  posicao,
  onPosicao,
  parecer,
  onParecer,
}: {
  id: string;
  posicao: PosicaoParecer | null;
  onPosicao: (posicao: PosicaoParecer | null) => void;
  parecer: string;
  onParecer: (parecer: string) => void;
}) {
  return (
    <>
      <div role="radiogroup" aria-label="Posição da origem" className="mb-2 grid grid-cols-3 gap-1.5">
        {(Object.keys(ROTULO_POSICAO) as PosicaoParecer[]).map(opcao => (
          <button
            key={opcao}
            type="button"
            role="radio"
            aria-checked={posicao === opcao}
            onClick={() => onPosicao(posicao === opcao ? null : opcao)}
            className={`flex min-h-9 cursor-pointer items-center justify-center gap-1 rounded-lg border px-2 text-xs font-medium transition-colors
              focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] ${
                posicao === opcao
                  ? `${COR_POSICAO[opcao]} border-transparent`
                  : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
              }`}
          >
            <Icon name={ICONE_POSICAO[opcao]} size={13} />
            {ROTULO_POSICAO[opcao]}
          </button>
        ))}
      </div>
      <MentionTextarea id={id} value={parecer} onChange={onParecer} rows={5} placeholder="Use @ para marcar pessoas ou empresas." />
    </>
  );
}
