"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { useDeclinarCard } from "@/hooks/useLiberacao";
import type { LiberacaoCard } from "@/types/liberacao";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "./Dialogo";

/**
 * O cliente declinou a operação. O card vai direto para Finalizados com a marca de declínio; o
 * motivo é opcional. Reabrir no Comitê desfaz.
 */
export default function DeclinarDialog({ card, onFechar }: { card: LiberacaoCard; onFechar: () => void }) {
  const declinar = useDeclinarCard();
  const [motivo, setMotivo] = useState("");

  return (
    <Dialogo
      titulo="Cliente declinou"
      subtitulo={`#${card.numero} · ${card.cedenteNome}`}
      onFechar={onFechar}
      largura="sm"
      rodape={
        <>
          <button type="button" onClick={onFechar} className={BOTAO_SECUNDARIO}>
            Cancelar
          </button>
          <button
            type="button"
            disabled={declinar.isPending}
            onClick={() => declinar.mutate({ card, motivo }, { onSuccess: onFechar })}
            className={BOTAO_PRIMARIO}
          >
            <Icon name="do_not_disturb_on" size={16} />
            {declinar.isPending ? "Declinando…" : "Declinar"}
          </button>
        </>
      }
    >
      <div className="space-y-4 px-5 py-5">
        <p className="text-sm leading-relaxed text-slate-600 dark:text-slate-300">
          O card vai direto para <strong className="font-semibold">Finalizados</strong>, marcado como declinado.
          {card.resultado && " A decisão do crédito continua registrada."} Se o cliente voltar atrás, dá para reabrir no Comitê.
        </p>
        <div>
          <label htmlFor="motivo-declinio" className={ROTULO}>
            Por que o cliente declinou? <span className="font-normal text-slate-400">(opcional)</span>
          </label>
          <textarea
            id="motivo-declinio"
            data-autofocus
            value={motivo}
            onChange={event => setMotivo(event.target.value)}
            rows={4}
            maxLength={5000}
            placeholder="Ex.: fechou com outro fundo, taxa acima do esperado, desistiu da operação."
            className={`${CAMPO} resize-y`}
          />
        </div>
      </div>
    </Dialogo>
  );
}
