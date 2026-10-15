"use client";

import { useEffect, useId, useState } from "react";
import Icon from "@/components/ui/Icon";
import { useSacadoBusca } from "@/hooks/useLiberacao";
import type { SacadoEncontrado } from "@/types/liberacao";
import { formatDocumento, mascararDocumento } from "./formatters";

type Props = {
  /** Documento da linha, já com máscara. */
  documento: string;
  onDocumento: (documento: string) => void;
  /** Escolheu um sacado da busca: documento com máscara e o nome da base. */
  onEscolher: (escolhido: { documento: string; nome: string | null }) => void;
  /** Colou uma lista de documentos: devolve true se tratou (vira várias linhas). */
  onColar: (texto: string) => boolean;
  /** Enter sem sugestão aberta: nova linha. */
  onNovaLinha: () => void;
  rotulo: string;
};

/**
 * Campo do sacado: CNPJ, CPF ou nome. Começou com número, é documento e ganha a máscara; começou
 * com letra, é nome e abre a busca nas empresas e pessoas físicas que o portal conhece, como no
 * cedente. Escolher preenche o documento e o nome.
 *
 * <p>As sugestões aparecem na própria linha, e não flutuando: a lista de sacados corta o que passa
 * da borda.</p>
 */
export default function SacadoBusca({ documento, onDocumento, onEscolher, onColar, onNovaLinha, rotulo }: Props) {
  // Nome em digitação; nulo quando o campo mostra o documento da linha.
  const [nome, setNome] = useState<string | null>(null);
  const [termo, setTermo] = useState("");
  const [focado, setFocado] = useState(false);
  const [destaque, setDestaque] = useState(0);
  const idLista = useId();
  const texto = nome ?? documento;

  // Espera a pessoa parar de digitar antes de ir ao servidor.
  useEffect(() => {
    const timer = setTimeout(() => setTermo(texto), 250);
    return () => clearTimeout(timer);
  }, [texto]);

  const { data: resultados = [], isFetching } = useSacadoBusca(termo);
  const digitos = texto.replace(/\D/g, "");
  // Documento completo que não está na base não precisa de sugestão: segue digitado.
  const documentoCompleto = nome === null && (digitos.length === 11 || digitos.length === 14);
  const opcoes = resultados.filter(item => item.documento !== digitos).slice(0, 8);
  const buscandoNome = nome !== null && termo.trim().length >= 2 && termo === texto;
  const aberto = focado && termo.trim().length >= 2 && (opcoes.length > 0 || (buscandoNome && !isFetching));

  function escolher(item: SacadoEncontrado) {
    setNome(null);
    setFocado(false);
    onEscolher({ documento: mascararDocumento(item.documento), nome: item.nome });
  }

  return (
    <div>
      <div className="relative">
        <input
          value={texto}
          onChange={event => {
            const novo = event.target.value;
            setDestaque(0);
            // Começou com número: é documento, aplica a máscara. Senão é nome e fica como está.
            if (/^\d/.test(novo.trim()) || novo.trim() === "") {
              setNome(null);
              onDocumento(mascararDocumento(novo));
            } else {
              setNome(novo);
              if (documento) onDocumento("");
            }
          }}
          onPaste={event => {
            if (onColar(event.clipboardData.getData("text"))) {
              event.preventDefault();
              setNome(null);
            }
          }}
          onFocus={() => setFocado(true)}
          onBlur={() => setFocado(false)}
          onKeyDown={event => {
            if (aberto && opcoes.length > 0 && event.key === "ArrowDown") {
              event.preventDefault();
              setDestaque(atual => Math.min(atual + 1, opcoes.length - 1));
            } else if (aberto && opcoes.length > 0 && event.key === "ArrowUp") {
              event.preventDefault();
              setDestaque(atual => Math.max(atual - 1, 0));
            } else if (event.key === "Enter") {
              event.preventDefault();
              if (aberto && opcoes[destaque]) escolher(opcoes[destaque]);
              else if (!nome) onNovaLinha();
            } else if (event.key === "Escape" && aberto) {
              event.stopPropagation();
              setFocado(false);
            }
          }}
          placeholder="CNPJ, CPF ou nome do sacado"
          aria-label={rotulo}
          autoComplete="off"
          role="combobox"
          aria-controls={idLista}
          aria-expanded={aberto}
          className={`w-full rounded-md bg-transparent px-2 py-1.5 text-sm text-slate-800 placeholder:font-sans placeholder:text-slate-400
            focus:bg-slate-50 focus:outline-none dark:text-slate-100 dark:focus:bg-slate-800 ${nome === null ? "font-mono" : ""}`}
        />
        {isFetching && focado && !documentoCompleto && (
          <span className="absolute right-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 animate-spin rounded-full border-2 border-slate-300 border-t-[#612035]" />
        )}
      </div>

      {aberto && (
        <div id={idLista} role="listbox" aria-label={`Sugestões para ${rotulo}`} className="mx-1 mb-1 mt-0.5 overflow-hidden rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
          {opcoes.map((item, indice) => (
            <button
              key={item.documento}
              type="button"
              role="option"
              aria-selected={indice === destaque}
              // mousedown, e não click: o campo perde o foco antes do click e a lista sumiria.
              onMouseDown={event => {
                event.preventDefault();
                escolher(item);
              }}
              onMouseEnter={() => setDestaque(indice)}
              className={`flex w-full cursor-pointer items-center gap-2.5 px-2.5 py-1.5 text-left ${
                indice === destaque ? "bg-slate-50 dark:bg-slate-700" : ""
              }`}
            >
              <Icon name={item.pessoaFisica ? "person" : "business"} size={14} className="shrink-0 text-slate-400" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[13px] font-medium text-slate-800 dark:text-slate-100">
                  {item.nome ?? <span className="italic text-slate-400">sem nome na base</span>}
                </span>
                <span className="block truncate text-[11px] text-slate-500">
                  <span className="font-mono">{formatDocumento(item.documento)}</span>
                  {item.praca && ` · ${item.praca}`}
                </span>
              </span>
              {item.pessoaFisica && (
                <span className="shrink-0 rounded bg-slate-100 px-1.5 py-px text-[10px] font-semibold text-slate-500 dark:bg-slate-700">PF</span>
              )}
            </button>
          ))}
          {opcoes.length === 0 && (
            <p className="px-2.5 py-2 text-[11px] text-slate-500">
              Ninguém com esse nome no portal. Para sacado novo, digite o CNPJ ou o CPF.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
