"use client";

import { createContext, useContext, useState, type ReactNode } from "react";
import Icon from "@/components/ui/Icon";
import { useCadastrarEmpresa, useDiretorio, useUsuarioAtual } from "@/hooks/useLiberacao";
import { segmentar } from "./mencoes";

type Contexto = {
  /** CNPJ → tem página de empresa. Ausente = não sabemos, trata como cadastrada. */
  empresas: Record<string, boolean>;
  /** Card a recarregar depois de cadastrar uma empresa citada. */
  cardId?: string;
};

const MencoesContexto = createContext<Contexto>({ empresas: {} });

/** Quem sabe quais empresas existem (o detalhe do card) envolve os textos com isto. */
export function MencoesProvider({ empresas, cardId, children }: Contexto & { children: ReactNode }) {
  return <MencoesContexto.Provider value={{ empresas, cardId }}>{children}</MencoesContexto.Provider>;
}

function formatCnpj(cnpj: string) {
  return cnpj.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}

function ChipPessoa({ id, rotulo }: { id: string; rotulo: string }) {
  const { data: pessoas } = useDiretorio();
  const { data: eu } = useUsuarioAtual();
  // O nome vem do id, não do rótulo salvo: sobrevive à troca de nome e não aceita rótulo forjado.
  const nome = pessoas?.find(pessoa => pessoa.id === id)?.nome ?? rotulo;
  const souEu = eu?.id === id;
  return (
    <span
      className={`rounded-[4px] px-1 py-px font-medium ${
        souEu
          ? "bg-[#612035] text-white dark:bg-[#e8a3b6] dark:text-[#3b1220]"
          : "bg-[#612035]/10 text-[#612035] dark:bg-[#e8a3b6]/20 dark:text-[#f2c4d1]"
      }`}
      title={souEu ? "Você foi mencionado" : nome}
    >
      @{nome}
    </span>
  );
}

function ChipEmpresa({ cnpj, rotulo }: { cnpj: string; rotulo: string | null }) {
  const { empresas, cardId } = useContext(MencoesContexto);
  const cadastrar = useCadastrarEmpresa();
  const [aberto, setAberto] = useState(false);
  const texto = rotulo ?? formatCnpj(cnpj);
  const cadastrada = empresas[cnpj] !== false;

  if (cadastrada) {
    return (
      <a
        href={`/clients/${cnpj}`}
        target="_blank"
        rel="noopener noreferrer"
        title={`Abrir ${formatCnpj(cnpj)} em nova aba`}
        className="inline-flex items-baseline gap-0.5 rounded-[4px] bg-[#2956E0]/10 px-1 py-px font-medium text-[#1f45b8] underline-offset-2
          hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-[#2956E0] dark:bg-[#7ea0ff]/20 dark:text-[#b8caff]"
      >
        {rotulo ? `@${rotulo}` : texto}
        <Icon name="open_in_new" size={10} className="self-center" />
      </a>
    );
  }

  return (
    <span className="relative inline-block">
      <button
        type="button"
        onClick={() => setAberto(atual => !atual)}
        aria-expanded={aberto}
        title="Empresa não cadastrada"
        className="rounded-[4px] border border-dashed border-amber-500 px-1 font-medium text-amber-800 hover:bg-amber-50
          focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:text-amber-200 dark:hover:bg-amber-900/30"
      >
        {rotulo ? `@${rotulo}` : texto}
      </button>
      {aberto && (
        <>
          <span className="fixed inset-0 z-30" onClick={() => setAberto(false)} />
          <span
            role="dialog"
            className="absolute left-0 top-full z-40 mt-1 block w-64 rounded-xl border border-slate-200 bg-white p-3 text-left text-xs
              font-normal text-slate-600 shadow-xl dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
          >
            <span className="flex items-center gap-1.5 font-semibold text-slate-800 dark:text-slate-100">
              <Icon name="warning" size={13} className="text-amber-500" /> Empresa não cadastrada
            </span>
            <span className="mt-1 block font-mono text-[11px]">{formatCnpj(cnpj)}</span>
            <span className="mt-1 block">Cadastre pelo CNPJ Já para abrir a página da empresa daqui.</span>
            <button
              type="button"
              disabled={cadastrar.isPending}
              onClick={() => cadastrar.mutate({ cnpj, cardId }, { onSuccess: () => setAberto(false) })}
              className="mt-2 inline-flex min-h-8 w-full cursor-pointer items-center justify-center gap-1 rounded-lg bg-[#2956E0] px-3 text-xs
                font-medium text-white hover:bg-[#1f45b8] disabled:opacity-60"
            >
              {cadastrar.isPending ? "Cadastrando…" : "Cadastrar agora"}
            </button>
          </span>
        </>
      )}
    </span>
  );
}

/**
 * Texto salvo com marcação, desenhado com as menções como chips.
 *
 * <p>Pessoa vira chip bordô (mais forte quando é você). Empresa cadastrada abre a página dela em
 * nova aba; não cadastrada fica tracejada e oferece cadastrar. CNPJ escrito solto, com dígito
 * verificador válido, ganha o mesmo tratamento.</p>
 */
export default function TextoRico({ texto, className = "" }: { texto: string | null | undefined; className?: string }) {
  if (!texto) return null;
  return (
    <span className={`whitespace-pre-wrap break-words ${className}`}>
      {segmentar(texto).map((segmento, indice) => {
        if (segmento.tipo === "user") return <ChipPessoa key={indice} id={segmento.id} rotulo={segmento.rotulo} />;
        if (segmento.tipo === "cnpj") return <ChipEmpresa key={indice} cnpj={segmento.cnpj} rotulo={segmento.rotulo} />;
        return <span key={indice}>{segmento.texto}</span>;
      })}
    </span>
  );
}
