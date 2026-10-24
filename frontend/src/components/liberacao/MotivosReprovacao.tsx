"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { useMotivosReprovacao, useSalvarMotivoReprovacao } from "@/hooks/useLiberacao";
import type { MotivoReprovacao } from "@/types/liberacao";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "./Dialogo";

/**
 * Os motivos da lista, para marcar ao reprovar. Um ou mais; reprovar pede ao menos um, e a
 * observação continua livre, embaixo.
 */
export function EscolhaMotivosReprovacao({ marcados, onMudar }: { marcados: string[]; onMudar: (ids: string[]) => void }) {
  const { data: motivos = [], isLoading, error } = useMotivosReprovacao();
  const ativos = motivos.filter(motivo => motivo.ativo);

  function alternar(id: string) {
    onMudar(marcados.includes(id) ? marcados.filter(marcado => marcado !== id) : [...marcados, id]);
  }

  return (
    <fieldset>
      <legend className={ROTULO}>
        Motivos da reprovação <span className="font-normal text-slate-400">(marque ao menos um)</span>
      </legend>
      {isLoading ? (
        <div className="h-24 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
      ) : error ? (
        <p className="text-xs text-rose-700 dark:text-rose-300">{error.message}</p>
      ) : ativos.length === 0 ? (
        <p className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">
          Nenhum motivo na lista. Peça ao administrador para cadastrar em Motivos, no topo da esteira.
        </p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {ativos.map(motivo => {
            const marcado = marcados.includes(motivo.id);
            return (
              <button
                key={motivo.id}
                type="button"
                role="checkbox"
                aria-checked={marcado}
                onClick={() => alternar(motivo.id)}
                className={`inline-flex min-h-8 cursor-pointer items-center gap-1 rounded-full border px-2.5 text-xs font-medium transition-colors
                  focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-600 focus-visible:ring-offset-1 ${
                    marcado
                      ? "border-rose-600 bg-rose-600 text-white hover:bg-rose-700"
                      : "border-slate-200 bg-white text-slate-600 hover:border-rose-300 hover:text-rose-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
                  }`}
              >
                {marcado && <Icon name="check" size={12} />}
                {motivo.nome}
              </button>
            );
          })}
        </div>
      )}
    </fieldset>
  );
}

/**
 * A lista de motivos de reprovação.
 *
 * <p>Todo mundo vê; só o admin altera. Motivo não se apaga — desativado, ele some da escolha, e o
 * card reprovado com ele continua legível, porque guarda o nome como texto.</p>
 */
export default function MotivosReprovacaoDialog({ podeEditar, onFechar }: { podeEditar: boolean; onFechar: () => void }) {
  const { data: motivos = [], isLoading } = useMotivosReprovacao();
  const salvar = useSalvarMotivoReprovacao();
  const [novo, setNovo] = useState("");

  function adicionar() {
    const nome = novo.trim();
    if (!nome || salvar.isPending) return;
    const ultima = motivos.reduce((maior, motivo) => Math.max(maior, motivo.ordem), 0);
    salvar.mutate({ nome, ordem: ultima + 1 }, { onSuccess: () => setNovo("") });
  }

  return (
    <Dialogo
      titulo="Motivos de reprovação"
      subtitulo="O que se marca ao reprovar um card"
      onFechar={onFechar}
      largura="sm"
      rodape={<button type="button" onClick={onFechar} className={BOTAO_SECUNDARIO}>Fechar</button>}
    >
      <div className="space-y-4 px-5 py-4">
        {!podeEditar && (
          <p className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">
            <Icon name="lock" size={14} />
            Só o administrador altera a lista.
          </p>
        )}
        {isLoading ? (
          <div className="h-32 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
        ) : (
          <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-700">
            {motivos.map(motivo => (
              <LinhaMotivo key={motivo.id} motivo={motivo} podeEditar={podeEditar} />
            ))}
          </ul>
        )}
        {podeEditar && (
          <form
            className="flex gap-2"
            onSubmit={event => {
              event.preventDefault();
              adicionar();
            }}
          >
            <input
              value={novo}
              onChange={event => setNovo(event.target.value)}
              placeholder="Novo motivo (ex.: Sacado em Recuperação)"
              aria-label="Nome do novo motivo"
              maxLength={120}
              className={CAMPO}
            />
            <button type="submit" disabled={!novo.trim() || salvar.isPending} className={BOTAO_PRIMARIO}>
              <Icon name="add" size={16} /> Adicionar
            </button>
          </form>
        )}
      </div>
    </Dialogo>
  );
}

function LinhaMotivo({ motivo, podeEditar }: { motivo: MotivoReprovacao; podeEditar: boolean }) {
  const salvar = useSalvarMotivoReprovacao();
  const [editando, setEditando] = useState(false);
  const [nome, setNome] = useState(motivo.nome);

  return (
    <li className="flex min-h-11 items-center gap-2 px-3">
      {editando ? (
        <form
          className="flex flex-1 gap-2 py-1.5"
          onSubmit={event => {
            event.preventDefault();
            if (!nome.trim()) return;
            salvar.mutate({ id: motivo.id, nome: nome.trim() }, { onSuccess: () => setEditando(false) });
          }}
        >
          <input value={nome} onChange={event => setNome(event.target.value)} autoFocus maxLength={120} aria-label="Nome do motivo" className={CAMPO} />
          <button type="submit" disabled={salvar.isPending} className={BOTAO_PRIMARIO}>Salvar</button>
        </form>
      ) : (
        <>
          <span className={`flex-1 text-sm ${motivo.ativo ? "text-slate-800 dark:text-slate-100" : "text-slate-400 line-through"}`}>
            {motivo.nome}
          </span>
          {podeEditar && (
            <>
              <button
                type="button"
                onClick={() => setEditando(true)}
                aria-label={`Renomear ${motivo.nome}`}
                className="inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800"
              >
                <Icon name="edit" size={14} />
              </button>
              <button
                type="button"
                disabled={salvar.isPending}
                onClick={() => salvar.mutate({ id: motivo.id, nome: motivo.nome, ativo: !motivo.ativo })}
                className="min-h-8 cursor-pointer rounded-lg px-2 text-xs font-medium text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
              >
                {motivo.ativo ? "desativar" : "reativar"}
              </button>
            </>
          )}
        </>
      )}
    </li>
  );
}
