"use client";

import { useEffect, useState, type KeyboardEvent } from "react";
import Icon from "@/components/ui/Icon";
import { CAMPO, ROTULO } from "@/components/liberacao/Dialogo";
import { useEmpresaBusca } from "@/hooks/useLiberacao";
import { diasDesde, formatData, tempoRelativo } from "@/lib/formatadores";
import { DIAS_CONSULTA_VELHA } from "@/types/pessoa-resumo";
import { ROTULO_ESTAGIO, type Prospeccao } from "@/types/prospeccao";
import { formatCnpj } from "./formatters";

export type EmpresaEscolhida =
  | { tipo: "existente"; cnpj: string; nome: string; consultaSerasaEm: string | null }
  | { tipo: "nova"; cnpj: string };

type Situacao = { tom: "ok" | "antiga" | "sem"; texto: string };

/** Como está a consulta Serasa da empresa: recente, antiga (mais de 90 dias) ou inexistente. */
export function situacaoSerasa(consultaEm: string | null | undefined): Situacao {
  if (!consultaEm) return { tom: "sem", texto: "Sem consulta Serasa" };
  const dias = diasDesde(consultaEm);
  const quando = `${formatData(consultaEm)}, ${tempoRelativo(dias)}`;
  return dias > DIAS_CONSULTA_VELHA
    ? { tom: "antiga", texto: `Consulta Serasa antiga (${quando})` }
    : { tom: "ok", texto: `Consulta Serasa de ${quando}` };
}

const CLASSE_SITUACAO: Record<Situacao["tom"], string> = {
  ok: "bg-emerald-50 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-200",
  antiga: "bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-100",
  sem: "bg-slate-100 text-slate-600 dark:bg-slate-700/60 dark:text-slate-200",
};

export function SeloSerasa({ consultaEm, curto = false }: { consultaEm: string | null | undefined; curto?: boolean }) {
  const situacao = situacaoSerasa(consultaEm);
  const texto = curto
    ? situacao.tom === "sem" ? "sem Serasa" : situacao.tom === "antiga" ? "consulta antiga" : `Serasa ${formatData(consultaEm!)}`
    : situacao.texto;
  return (
    <span
      title={situacao.texto}
      className={`inline-flex shrink-0 items-center gap-1 rounded-md px-1.5 py-0.5 text-[10.5px] font-semibold ${CLASSE_SITUACAO[situacao.tom]}`}
    >
      <Icon name={situacao.tom === "antiga" ? "schedule" : situacao.tom === "ok" ? "verified_user" : "help_outline"} size={11} />
      {texto}
    </span>
  );
}

type Opcao = { tipo: "existente"; cnpj: string; nome: string; detalhe: string; consultaSerasaEm: string | null } | { tipo: "nova"; cnpj: string };

/**
 * Busca da empresa por nome ou CNPJ, como no cedente da esteira de liberação.
 *
 * <p>Empresa que o portal já conhece aparece com a situação da consulta Serasa e, se já tiver card
 * aberto, com o estágio. CNPJ completo que não está na base vira a opção de consultar no CNPJ Já
 * e no Serasa.</p>
 */
export default function EscolhaEmpresa({
  onEscolher,
  cardsAbertos,
}: {
  onEscolher: (empresa: EmpresaEscolhida) => void;
  /** Cards abertos no quadro, para dizer na lista quem já está na esteira. */
  cardsAbertos: Map<string, Prospeccao>;
}) {
  const [texto, setTexto] = useState("");
  const [termo, setTermo] = useState("");
  const [destaque, setDestaque] = useState(0);

  // Espera a pessoa parar de digitar antes de ir ao servidor.
  useEffect(() => {
    const timer = setTimeout(() => setTermo(texto), 250);
    return () => clearTimeout(timer);
  }, [texto]);

  const { data: resultados = [], isFetching } = useEmpresaBusca(termo);
  const digitos = texto.replace(/\D/g, "");
  const pronto = termo === texto && !isFetching;
  const cnpjNovo = digitos.length === 14 && pronto && !resultados.some(empresa => empresa.cnpj === digitos);

  const opcoes: Opcao[] = [
    ...resultados.map(empresa => ({
      tipo: "existente" as const,
      cnpj: empresa.cnpj,
      nome: empresa.nome,
      detalhe: [formatCnpj(empresa.cnpj), [empresa.cidade, empresa.uf].filter(Boolean).join("/")].filter(Boolean).join(" · "),
      consultaSerasaEm: empresa.consultaSerasaEm,
    })),
    ...(cnpjNovo ? [{ tipo: "nova" as const, cnpj: digitos }] : []),
  ];
  const ativo = Math.min(destaque, Math.max(0, opcoes.length - 1));

  function escolher(opcao: Opcao) {
    onEscolher(
      opcao.tipo === "nova"
        ? opcao
        : { tipo: "existente", cnpj: opcao.cnpj, nome: opcao.nome, consultaSerasaEm: opcao.consultaSerasaEm },
    );
  }

  function aoTeclar(event: KeyboardEvent<HTMLInputElement>) {
    if (!opcoes.length) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setDestaque((ativo + 1) % opcoes.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setDestaque((ativo - 1 + opcoes.length) % opcoes.length);
    } else if (event.key === "Enter") {
      event.preventDefault();
      escolher(opcoes[ativo]);
    }
  }

  const mostraLista = termo.trim().length >= 2;

  return (
    <div>
      <label className="block">
        <span className={ROTULO}>Empresa</span>
        <span className="relative block">
          <Icon name="search" size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            data-autofocus
            value={texto}
            onChange={event => {
              setTexto(event.target.value);
              setDestaque(0);
            }}
            onKeyDown={aoTeclar}
            placeholder="CNPJ ou nome da empresa"
            aria-label="CNPJ ou nome da empresa"
            autoComplete="off"
            className={`${CAMPO} pl-9`}
          />
        </span>
      </label>
      {digitos.length > 0 && digitos.length < 14 && /^[\d.\-/\s]+$/.test(texto.trim()) && (
        <p className="mt-1 text-[11px] text-[#D1732C]">Faltam {14 - digitos.length} dígito(s) do CNPJ.</p>
      )}

      {mostraLista && (
        <ul role="listbox" aria-label="Empresas encontradas" className="mt-2 max-h-72 overflow-y-auto rounded-xl border border-slate-200 dark:border-slate-700">
          {opcoes.length === 0 && (
            <li className="px-3 py-3 text-xs text-slate-500">
              {!pronto ? "Buscando…" : digitos.length > 0 && digitos.length < 14 ? "Termine de digitar o CNPJ." : "Nenhuma empresa no portal com esse nome. Digite o CNPJ para consultar."}
            </li>
          )}
          {opcoes.map((opcao, i) => {
            const card = cardsAbertos.get(opcao.cnpj);
            return (
              <li key={opcao.tipo + opcao.cnpj} role="option" aria-selected={i === ativo}>
                <button
                  type="button"
                  onClick={() => escolher(opcao)}
                  onMouseEnter={() => setDestaque(i)}
                  className={`flex w-full items-center gap-3 border-b border-slate-100 px-3 py-2.5 text-left last:border-b-0 dark:border-slate-800 ${
                    i === ativo ? "bg-[#612035]/5 dark:bg-slate-800" : ""
                  }`}
                >
                  <span
                    className={`grid h-8 w-8 shrink-0 place-items-center rounded-lg ${
                      opcao.tipo === "nova" ? "bg-[#2956E0]/10 text-[#2956E0]" : "bg-slate-100 text-[#612035] dark:bg-slate-700 dark:text-[#e8a3b6]"
                    }`}
                  >
                    <Icon name={opcao.tipo === "nova" ? "travel_explore" : "business"} size={16} />
                  </span>
                  <span className="min-w-0 flex-1">
                    {opcao.tipo === "nova" ? (
                      <>
                        <span className="block text-sm font-semibold text-slate-900 dark:text-white">Empresa nova: {formatCnpj(opcao.cnpj)}</span>
                        <span className="block text-[11px] text-slate-500">Não está no portal. Consultar no CNPJ Já e no Serasa.</span>
                      </>
                    ) : (
                      <>
                        <span className="block truncate text-sm font-semibold text-slate-900 dark:text-white">{opcao.nome}</span>
                        <span className="block truncate font-mono text-[11px] text-slate-500">{opcao.detalhe}</span>
                      </>
                    )}
                  </span>
                  {opcao.tipo === "existente" && (
                    <span className="flex shrink-0 flex-col items-end gap-1">
                      <SeloSerasa consultaEm={opcao.consultaSerasaEm} curto />
                      {card && (
                        <span className="rounded-md bg-[#612035]/10 px-1.5 py-0.5 text-[10.5px] font-semibold text-[#612035] dark:text-[#e8a3b6]">
                          na esteira: {ROTULO_ESTAGIO[card.estagio]}
                        </span>
                      )}
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
