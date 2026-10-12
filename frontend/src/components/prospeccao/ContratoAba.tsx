"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "@/components/liberacao/Dialogo";
import { useConcluirProspeccao, useSalvarContrato } from "@/hooks/useProspeccao";
import type { ContratoContato, EstagioProspeccao } from "@/types/prospeccao";

/**
 * Aba Contrato: quem assina pelo cliente e a conclusão do card.
 *
 * <p>Nome, telefone e e-mail são exigidos para concluir, como no Portal Comercial. Concluir leva
 * o card a Habilitado; dali em diante as operações do cedente correm na esteira de liberação.</p>
 */
export default function ContratoAba({
  cardId,
  contrato,
  estagio,
  podeConcluir,
}: {
  cardId: string;
  contrato: ContratoContato;
  estagio: EstagioProspeccao;
  podeConcluir: boolean;
}) {
  const salvar = useSalvarContrato();
  const concluir = useConcluirProspeccao();
  const [rascunho, setRascunho] = useState<ContratoContato | null>(null);
  const valor = rascunho ?? contrato;
  const alterado = rascunho !== null && JSON.stringify(rascunho) !== JSON.stringify(contrato);
  const completo = Boolean(contrato.nome && contrato.telefone && contrato.email);
  const editavel = estagio !== "HABILITADO" && estagio !== "REPROVADO" && estagio !== "RECUSADO_CLIENTE" && estagio !== "INERTE";

  function campo(chave: keyof ContratoContato, texto: string) {
    setRascunho({ ...valor, [chave]: texto === "" ? null : texto });
  }

  return (
    <div className="space-y-5">
      <section className="space-y-3">
        <h3 className="text-sm font-semibold text-slate-900 dark:text-white">Quem assina pelo cliente</h3>
        <div className="grid gap-3 sm:grid-cols-3">
          <label className="block">
            <span className={ROTULO}>Nome</span>
            <input value={valor.nome ?? ""} disabled={!editavel} onChange={event => campo("nome", event.target.value)} className={CAMPO} />
          </label>
          <label className="block">
            <span className={ROTULO}>Telefone</span>
            <input
              value={valor.telefone ?? ""}
              disabled={!editavel}
              inputMode="tel"
              onChange={event => campo("telefone", event.target.value)}
              className={CAMPO}
            />
          </label>
          <label className="block">
            <span className={ROTULO}>E-mail</span>
            <input
              value={valor.email ?? ""}
              disabled={!editavel}
              type="email"
              onChange={event => campo("email", event.target.value)}
              className={CAMPO}
            />
          </label>
        </div>
        {editavel && (
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setRascunho(null)} disabled={!alterado || salvar.isPending} className={BOTAO_SECUNDARIO}>
              Descartar
            </button>
            <button
              type="button"
              disabled={!alterado || salvar.isPending}
              onClick={() => salvar.mutate({ id: cardId, contrato: valor }, { onSuccess: () => setRascunho(null) })}
              className={BOTAO_PRIMARIO}
            >
              {salvar.isPending ? "Salvando…" : "Salvar contato"}
            </button>
          </div>
        )}
      </section>

      {estagio === "CONTRATO" && (
        <section className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-emerald-200 bg-emerald-50/60 px-4 py-3
          dark:border-emerald-900/50 dark:bg-emerald-950/20">
          <p className="text-sm text-slate-700 dark:text-slate-200">
            {completo ? "Contrato pronto para concluir. O card vai para Habilitado." : "Preencha e salve nome, telefone e e-mail para concluir."}
          </p>
          <button
            type="button"
            disabled={!completo || alterado || !podeConcluir || concluir.isPending}
            title={!podeConcluir ? "Só a analista dona do card conclui" : undefined}
            onClick={() => concluir.mutate({ id: cardId })}
            className={BOTAO_PRIMARIO}
          >
            <Icon name="verified" size={16} />
            {concluir.isPending ? "Concluindo…" : "Concluir: habilitado"}
          </button>
        </section>
      )}
      {estagio === "HABILITADO" && (
        <p className="flex items-center gap-2 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-200">
          <Icon name="verified" size={16} />
          Cedente habilitado. As operações seguem na esteira de liberação.
        </p>
      )}
    </div>
  );
}
