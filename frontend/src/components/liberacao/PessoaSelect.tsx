"use client";

import { useMemo, useState } from "react";
import Icon from "@/components/ui/Icon";
import { useDiretorio } from "@/hooks/useLiberacao";
import Avatar from "./Avatar";
import { CAMPO } from "./Dialogo";

type Props = {
  valor: string | null;
  onEscolher: (id: string) => void;
  /** Não oferece quem está pedindo: pendência para si mesmo não faz sentido. */
  excluir?: string;
  id?: string;
};

/** Escolha de pessoa do portal com busca por nome. */
export default function PessoaSelect({ valor, onEscolher, excluir, id }: Props) {
  const { data: pessoas = [], isLoading } = useDiretorio();
  const [busca, setBusca] = useState("");
  const [aberto, setAberto] = useState(false);
  const escolhida = pessoas.find(pessoa => pessoa.id === valor);

  const filtradas = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    return pessoas
      .filter(pessoa => pessoa.id !== excluir)
      .filter(pessoa => !termo || pessoa.nome.toLowerCase().includes(termo));
  }, [pessoas, busca, excluir]);

  return (
    <div className="relative">
      <button
        id={id}
        type="button"
        onClick={() => setAberto(valor => !valor)}
        aria-haspopup="listbox"
        aria-expanded={aberto}
        className={`${CAMPO} flex min-h-10 cursor-pointer items-center gap-2 text-left`}
      >
        {escolhida ? (
          <>
            <Avatar nome={escolhida.nome} iniciais={escolhida.iniciais} tamanho="xs" />
            <span className="truncate">{escolhida.nome}</span>
          </>
        ) : (
          <span className="text-slate-400">{isLoading ? "Carregando…" : "Escolha a pessoa"}</span>
        )}
        <Icon name="expand_more" size={16} className="ml-auto shrink-0 text-slate-400" />
      </button>

      {aberto && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setAberto(false)} />
          <div className="absolute left-0 right-0 z-20 mt-1 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl
            dark:border-slate-700 dark:bg-slate-800">
            <div className="border-b border-slate-100 p-2 dark:border-slate-700">
              <input
                autoFocus
                value={busca}
                onChange={event => setBusca(event.target.value)}
                placeholder="Buscar pelo nome"
                className={CAMPO}
                onKeyDown={event => {
                  if (event.key === "Enter" && filtradas[0]) {
                    event.preventDefault();
                    onEscolher(filtradas[0].id);
                    setAberto(false);
                  }
                }}
              />
            </div>
            <ul role="listbox" className="max-h-60 overflow-y-auto py-1">
              {filtradas.map(pessoa => (
                <li key={pessoa.id}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={pessoa.id === valor}
                    onClick={() => {
                      onEscolher(pessoa.id);
                      setAberto(false);
                      setBusca("");
                    }}
                    className="flex min-h-10 w-full cursor-pointer items-center gap-2 px-3 py-1.5 text-left text-sm text-slate-700
                      hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-700"
                  >
                    <Avatar nome={pessoa.nome} iniciais={pessoa.iniciais} tamanho="xs" />
                    <span className="truncate">{pessoa.nome}</span>
                    {pessoa.analista && (
                      <span className="ml-auto shrink-0 rounded bg-[#612035]/10 px-1.5 py-0.5 text-[10px] font-medium text-[#612035]
                        dark:bg-[#612035]/40 dark:text-[#e8a3b6]">
                        {pessoa.comite ? "Comitê" : "Analista"}
                      </span>
                    )}
                  </button>
                </li>
              ))}
              {filtradas.length === 0 && <li className="px-3 py-3 text-center text-xs text-slate-400">Ninguém com esse nome</li>}
            </ul>
          </div>
        </>
      )}
    </div>
  );
}
