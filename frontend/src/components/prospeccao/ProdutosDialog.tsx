"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO } from "@/components/liberacao/Dialogo";
import { useProdutos, useSalvarProduto } from "@/hooks/useProspeccao";
import type { ProdutoCatalogo } from "@/types/prospeccao";

/**
 * Catálogo de produtos das condições de crédito.
 *
 * <p>Todo mundo vê; só o admin altera. Produto não se apaga — desativado, ele some da lista de
 * escolha, e a decisão antiga que o usou continua legível, porque guarda o nome como texto.</p>
 */
export default function ProdutosDialog({ podeEditar, onFechar }: { podeEditar: boolean; onFechar: () => void }) {
  const { data: produtos = [], isLoading } = useProdutos();
  const salvar = useSalvarProduto();
  const [novo, setNovo] = useState("");

  function adicionar() {
    const nome = novo.trim();
    if (!nome || salvar.isPending) return;
    salvar.mutate({ nome, ordem: produtos.length + 1 }, { onSuccess: () => setNovo("") });
  }

  return (
    <Dialogo
      titulo="Catálogo de produtos"
      subtitulo="Produtos que entram nas condições de crédito da decisão"
      onFechar={onFechar}
      largura="sm"
      rodape={<button type="button" onClick={onFechar} className={BOTAO_SECUNDARIO}>Fechar</button>}
    >
      <div className="space-y-4 px-5 py-4">
        {!podeEditar && (
          <p className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">
            <Icon name="lock" size={14} />
            Só o administrador altera o catálogo.
          </p>
        )}
        {isLoading ? (
          <div className="h-32 animate-pulse rounded-lg bg-slate-100 dark:bg-slate-800" />
        ) : (
          <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-700">
            {produtos.map(produto => (
              <LinhaProduto key={produto.id} produto={produto} podeEditar={podeEditar} />
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
              placeholder="Novo produto (ex.: CCB)"
              aria-label="Nome do novo produto"
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

function LinhaProduto({ produto, podeEditar }: { produto: ProdutoCatalogo; podeEditar: boolean }) {
  const salvar = useSalvarProduto();
  const [editando, setEditando] = useState(false);
  const [nome, setNome] = useState(produto.nome);

  return (
    <li className="flex min-h-11 items-center gap-2 px-3">
      {editando ? (
        <form
          className="flex flex-1 gap-2 py-1.5"
          onSubmit={event => {
            event.preventDefault();
            if (!nome.trim()) return;
            salvar.mutate({ id: produto.id, nome: nome.trim() }, { onSuccess: () => setEditando(false) });
          }}
        >
          <input value={nome} onChange={event => setNome(event.target.value)} autoFocus aria-label="Nome do produto" className={CAMPO} />
          <button type="submit" disabled={salvar.isPending} className={BOTAO_PRIMARIO}>Salvar</button>
        </form>
      ) : (
        <>
          <span className={`flex-1 text-sm ${produto.ativo ? "text-slate-800 dark:text-slate-100" : "text-slate-400 line-through"}`}>
            {produto.nome}
          </span>
          {podeEditar && (
            <>
              <button
                type="button"
                onClick={() => setEditando(true)}
                aria-label={`Renomear ${produto.nome}`}
                className="inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800"
              >
                <Icon name="edit" size={14} />
              </button>
              <button
                type="button"
                disabled={salvar.isPending}
                onClick={() => salvar.mutate({ id: produto.id, nome: produto.nome, ativo: !produto.ativo })}
                className="min-h-8 cursor-pointer rounded-lg px-2 text-xs font-medium text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
              >
                {produto.ativo ? "desativar" : "reativar"}
              </button>
            </>
          )}
        </>
      )}
    </li>
  );
}
