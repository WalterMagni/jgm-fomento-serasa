"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { BOTAO_PRIMARIO, BOTAO_SECUNDARIO } from "@/components/liberacao/Dialogo";
import { useAtualizarDadosComerciais } from "@/hooks/useProspeccao";
import type { DadosComerciais } from "@/types/prospeccao";
import DadosComerciaisForm from "./DadosComerciaisForm";

/**
 * Aba de dados comerciais do card: o formulário com salvar e descartar.
 *
 * <p>O rascunho é local até salvar. Quem abre o card de outra analista não grava nada só por
 * olhar, e o refetch do detalhe não apaga o que está sendo digitado: o formulário só volta ao
 * valor do servidor ao descartar ou depois de salvar.</p>
 */
export default function DadosComerciaisAba({
  cardId,
  dados,
  somenteLeitura,
  exigidos,
}: {
  cardId: string;
  dados: DadosComerciais;
  somenteLeitura: boolean;
  /** Campos pedidos por pendência aberta. */
  exigidos?: Set<string>;
}) {
  const salvar = useAtualizarDadosComerciais();
  const [rascunho, setRascunho] = useState<DadosComerciais | null>(null);
  const valor = rascunho ?? dados;
  const alterado = rascunho !== null && JSON.stringify(rascunho) !== JSON.stringify(dados);

  return (
    <div className="space-y-4">
      {somenteLeitura && (
        <p className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">
          <Icon name="lock" size={14} />
          Card encerrado: reabra para editar.
        </p>
      )}
      {exigidos && exigidos.size > 0 && (
        <p className="flex items-center gap-2 rounded-lg bg-[#D1732C]/10 px-3 py-2 text-xs text-[#a8551a] dark:text-[#f0a46b]">
          <Icon name="hourglass_top" size={14} />
          Campos com * foram pedidos em pendência aberta.
        </p>
      )}
      <DadosComerciaisForm valor={valor} onMudar={setRascunho} desabilitado={somenteLeitura || salvar.isPending} exigidos={exigidos} />
      {!somenteLeitura && (
        <div className="sticky bottom-0 -mx-1 flex items-center justify-end gap-2 bg-gradient-to-t from-white via-white px-1 pt-4
          dark:from-slate-900 dark:via-slate-900">
          {alterado && <span className="mr-auto text-xs text-[#D1732C]">Alterações não salvas</span>}
          <button type="button" onClick={() => setRascunho(null)} disabled={!alterado || salvar.isPending} className={BOTAO_SECUNDARIO}>
            Descartar
          </button>
          <button
            type="button"
            disabled={!alterado || salvar.isPending}
            onClick={() => salvar.mutate({ id: cardId, dados: valor }, { onSuccess: () => setRascunho(null) })}
            className={BOTAO_PRIMARIO}
          >
            <Icon name="save" size={16} />
            {salvar.isPending ? "Salvando…" : "Salvar"}
          </button>
        </div>
      )}
    </div>
  );
}
