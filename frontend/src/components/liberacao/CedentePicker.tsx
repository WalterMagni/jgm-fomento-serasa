"use client";

import { useEffect, useState } from "react";
import Icon from "@/components/ui/Icon";
import { useEmpresaBusca } from "@/hooks/useLiberacao";
import { CAMPO, ROTULO } from "./Dialogo";
import { formatDocumento, mascararDocumento } from "./formatters";

export type CedenteEscolhido = { cnpj: string; nome: string; cadastrado: boolean };

type Props = {
  valor: CedenteEscolhido | null;
  onMudar: (valor: CedenteEscolhido | null) => void;
};

/**
 * Escolha do cedente: busca na base por nome ou CNPJ.
 *
 * <p>CNPJ fora da base continua aceito — a operação não espera o cadastro. Nesse caso pede a
 * razão social, que vira o título do card, e o detalhe oferece cadastrar pelo CNPJ Já depois.</p>
 */
export default function CedentePicker({ valor, onMudar }: Props) {
  const [texto, setTexto] = useState("");
  const [termo, setTermo] = useState("");
  const [aberto, setAberto] = useState(false);
  const [destaque, setDestaque] = useState(0);

  // Espera a pessoa parar de digitar antes de ir ao servidor.
  useEffect(() => {
    const timer = setTimeout(() => setTermo(texto), 250);
    return () => clearTimeout(timer);
  }, [texto]);

  const { data: resultados = [], isFetching } = useEmpresaBusca(termo);
  const digitos = texto.replace(/\D/g, "");
  const cnpjCompletoSemCadastro =
    digitos.length === 14 && !isFetching && termo === texto && !resultados.some(empresa => empresa.cnpj === digitos);

  if (valor) {
    return (
      <div>
        <span className={ROTULO}>Cedente</span>
        <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-700 dark:bg-slate-800/60">
          <Icon name="business" size={18} className="shrink-0 text-[#612035] dark:text-[#e8a3b6]" />
          <div className="min-w-0 flex-1">
            {valor.cadastrado ? (
              <p className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">{valor.nome}</p>
            ) : (
              <input
                value={valor.nome}
                onChange={event => onMudar({ ...valor, nome: event.target.value })}
                placeholder="Razão social do cedente"
                aria-label="Razão social do cedente"
                className="w-full bg-transparent text-sm font-semibold text-slate-800 placeholder:font-normal placeholder:text-amber-700
                  focus:outline-none dark:text-slate-100"
                autoFocus
              />
            )}
            <p className="font-mono text-[11px] text-slate-500">{formatDocumento(valor.cnpj)}</p>
          </div>
          {!valor.cadastrado && (
            <span className="shrink-0 rounded-md bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium text-amber-800 dark:bg-amber-900/40 dark:text-amber-200">
              não cadastrada
            </span>
          )}
          <button
            type="button"
            onClick={() => {
              onMudar(null);
              setTexto("");
            }}
            className="shrink-0 cursor-pointer rounded-md px-2 py-1 text-xs text-slate-500 hover:bg-white hover:text-slate-800
              focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] dark:hover:bg-slate-700"
          >
            trocar
          </button>
        </div>
      </div>
    );
  }

  const opcoes = resultados.slice(0, 8);

  function escolher(indice: number) {
    const empresa = opcoes[indice];
    if (empresa) {
      onMudar({ cnpj: empresa.cnpj, nome: empresa.nome, cadastrado: true });
      setAberto(false);
    }
  }

  return (
    <div className="relative">
      <label htmlFor="cedente-busca" className={ROTULO}>
        Cedente
      </label>
      <div className="relative">
        <Icon name="search" size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          id="cedente-busca"
          data-autofocus
          value={texto}
          onChange={event => {
            const novo = event.target.value;
            // Começou com número: é CNPJ, aplica a máscara. Senão é nome, deixa como está.
            setTexto(/^\d/.test(novo.trim()) ? mascararDocumento(novo) : novo);
            setAberto(true);
            setDestaque(0);
          }}
          onFocus={() => setAberto(true)}
          onKeyDown={event => {
            if (event.key === "ArrowDown") {
              event.preventDefault();
              setDestaque(atual => Math.min(atual + 1, opcoes.length - 1));
            } else if (event.key === "ArrowUp") {
              event.preventDefault();
              setDestaque(atual => Math.max(atual - 1, 0));
            } else if (event.key === "Enter") {
              event.preventDefault();
              if (opcoes.length > 0) escolher(destaque);
              else if (cnpjCompletoSemCadastro) onMudar({ cnpj: digitos, nome: "", cadastrado: false });
            } else if (event.key === "Escape" && aberto) {
              event.stopPropagation();
              setAberto(false);
            }
          }}
          placeholder="Nome da empresa ou CNPJ"
          autoComplete="off"
          role="combobox"
          aria-expanded={aberto}
          aria-controls="cedente-opcoes"
          className={`${CAMPO} pl-9`}
        />
        {isFetching && (
          <span className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin rounded-full border-2 border-slate-300 border-t-[#612035]" />
        )}
      </div>

      {aberto && termo.trim().length >= 2 && (
        <div
          id="cedente-opcoes"
          role="listbox"
          className="absolute left-0 right-0 z-20 mt-1 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl
            dark:border-slate-700 dark:bg-slate-800"
        >
          {opcoes.map((empresa, indice) => (
            <button
              key={empresa.cnpj}
              type="button"
              role="option"
              aria-selected={indice === destaque}
              onMouseEnter={() => setDestaque(indice)}
              onClick={() => escolher(indice)}
              className={`flex w-full cursor-pointer items-center gap-3 px-3 py-2 text-left ${
                indice === destaque ? "bg-slate-50 dark:bg-slate-700" : ""
              }`}
            >
              <Icon name="business" size={16} className="shrink-0 text-slate-400" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium text-slate-800 dark:text-slate-100">{empresa.nome}</span>
                <span className="block truncate text-[11px] text-slate-500">
                  <span className="font-mono">{formatDocumento(empresa.cnpj)}</span>
                  {empresa.cidade && ` · ${empresa.cidade}/${empresa.uf}`}
                </span>
              </span>
            </button>
          ))}
          {opcoes.length === 0 && !isFetching && (
            <div className="px-3 py-3 text-xs text-slate-500">
              {cnpjCompletoSemCadastro ? (
                <button
                  type="button"
                  onClick={() => onMudar({ cnpj: digitos, nome: "", cadastrado: false })}
                  className="flex w-full cursor-pointer items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-left text-amber-900
                    hover:bg-amber-100 dark:bg-amber-900/30 dark:text-amber-100"
                >
                  <Icon name="add_business" size={16} />
                  <span>
                    <strong>{formatDocumento(digitos)}</strong> não está cadastrada. Usar mesmo assim e informar a razão social.
                  </span>
                </button>
              ) : digitos.length > 0 && digitos.length < 14 ? (
                "Continue digitando o CNPJ (14 dígitos)."
              ) : (
                "Nenhuma empresa cadastrada com esse nome. Para empresa nova, digite o CNPJ."
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
