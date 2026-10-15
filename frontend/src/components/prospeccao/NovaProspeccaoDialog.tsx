"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import Icon from "@/components/ui/Icon";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "@/components/liberacao/Dialogo";
import { consultarEmpresa } from "@/components/carteira/consultaEmpresa";
import { useCriarProspeccao } from "@/hooks/useProspeccao";
import { useDiretorio, useEmpresaBusca } from "@/hooks/useLiberacao";
import { DADOS_COMERCIAIS_VAZIOS, ROTULO_ESTAGIO, ehTerminal, type DadosComerciais, type Prospeccao } from "@/types/prospeccao";
import DadosComerciaisForm, { camposPreenchidos } from "./DadosComerciaisForm";
import EscolhaEmpresa, { SeloSerasa, situacaoSerasa, type EmpresaEscolhida } from "./EscolhaEmpresa";
import { formatCnpj } from "./formatters";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

/** Sem comercial: cliente direto. Outro: nome digitado, para quem ainda não tem login. */
const DIRETO = "__direto__";
const OUTRO = "__outro__";

type Etapa = "cnpja" | "card";
type EstadoEtapa = "espera" | "rodando" | "ok" | "erro";

const ROTULO_ETAPA: Record<Etapa, string> = {
  cnpja: "CNPJ Já — cadastro e endereço (Receita Federal)",
  card: "Card aberto em Nova",
};

/** Razão social que o CNPJ Já acabou de gravar, pela mesma busca do seletor. */
async function nomeCadastrado(cnpj: string) {
  try {
    const token = localStorage.getItem("serasa_token");
    const res = await fetch(`${API_BASE_URL}/company/busca?q=${cnpj}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) return null;
    const lista: { cnpj: string; nome: string }[] = await res.json();
    return lista.find(empresa => empresa.cnpj === cnpj)?.nome ?? null;
  } catch {
    return null;
  }
}

/**
 * Abertura de card, pelo quadro ou pela página da empresa.
 *
 * <p>Busca a empresa por nome ou CNPJ, como no cedente da esteira de liberação. Empresa que o
 * portal já conhece abre sem custo, com o aviso de consulta Serasa antiga (mais de 90 dias) quando
 * for o caso. CNPJ novo é consultado só no CNPJ Já — os dados da Receita Federal — e o card abre.
 * O Serasa ficou de fora para cortar custo (pedido do time em 2026-10-14): quem analisa consulta
 * pela página da empresa, quando precisar.</p>
 *
 * <p>Sem o CNPJ Já não há perfil — mesma regra da página da empresa —, então o card não abre e a
 * tela pede para tentar depois.</p>
 */
export default function NovaProspeccaoDialog({
  onFechar,
  onCriado,
  inicial,
  cardsAbertos = [],
}: {
  onFechar: () => void;
  onCriado?: (card: Prospeccao) => void;
  /** Vindo da página da empresa: CNPJ e nome já conhecidos. */
  inicial?: { cnpj: string; razaoSocial?: string };
  /** Cards do quadro: avisa quem já está na esteira em vez de deixar o servidor recusar. */
  cardsAbertos?: Prospeccao[];
}) {
  const criar = useCriarProspeccao();
  const queryClient = useQueryClient();
  const { data: diretorio = [] } = useDiretorio();
  const [empresa, setEmpresa] = useState<EmpresaEscolhida | null>(() =>
    inicial
      ? { tipo: "existente", cnpj: inicial.cnpj.replace(/\D/g, ""), nome: inicial.razaoSocial ?? formatCnpj(inicial.cnpj), consultaSerasaEm: null }
      : null,
  );
  const [comercial, setComercial] = useState<string>(DIRETO);
  const [comercialNome, setComercialNome] = useState("");
  const [dados, setDados] = useState<DadosComerciais>(DADOS_COMERCIAIS_VAZIOS);
  const [etapas, setEtapas] = useState<Partial<Record<Etapa, EstadoEtapa>> | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  // Vindo da página da empresa, a data da consulta Serasa vem da mesma busca.
  const buscaInicial = useEmpresaBusca(inicial ? inicial.cnpj.replace(/\D/g, "") : "");
  const daBusca = buscaInicial.data ?? [];
  const conferindoSerasa = Boolean(inicial) && buscaInicial.isLoading;
  const consultaSerasaEm =
    empresa?.tipo === "existente" ? (empresa.consultaSerasaEm ?? daBusca.find(e => e.cnpj === empresa.cnpj)?.consultaSerasaEm ?? null) : null;

  const abertos = useMemo(
    () => new Map(cardsAbertos.filter(card => !ehTerminal(card.estagio)).map(card => [card.cnpj, card])),
    [cardsAbertos],
  );
  const cardExistente = empresa ? abertos.get(empresa.cnpj) : undefined;

  const comercialValido = comercial !== OUTRO || comercialNome.trim().length > 0;
  const preenchidos = camposPreenchidos(dados);
  const rodando = etapas !== null && Object.values(etapas).includes("rodando");
  const ocupado = criar.isPending || rodando;

  const corpoDoCard = (cnpj: string, razaoSocial: string) => ({
    cnpj,
    razaoSocial,
    comercialId: comercial !== DIRETO && comercial !== OUTRO ? comercial : undefined,
    comercialNome: comercial === OUTRO ? comercialNome.trim() : undefined,
    dadosComerciais: preenchidos > 0 ? dados : undefined,
  });

  function concluir(card: Prospeccao) {
    onFechar();
    onCriado?.(card);
  }

  /** Empresa já cadastrada: abre o card sem consulta paga. */
  function abrirExistente() {
    if (empresa?.tipo !== "existente" || !comercialValido || ocupado) return;
    criar.mutate(corpoDoCard(empresa.cnpj, empresa.nome), { onSuccess: concluir });
  }

  /** CNPJ novo: CNPJ Já e card, nessa ordem. Sem Serasa. */
  async function consultarEAbrir() {
    if (empresa?.tipo !== "nova" || !comercialValido || ocupado) return;
    const cnpj = empresa.cnpj;
    setErro(null);
    setEtapas({ cnpja: "rodando" });
    try {
      await consultarEmpresa("cnpja", cnpj);
    } catch (falha) {
      setEtapas({ cnpja: "erro" });
      setErro(`${falha instanceof Error ? falha.message : "O CNPJ Já não respondeu."} O card não foi aberto: tente de novo em alguns minutos.`);
      return;
    }

    setEtapas({ cnpja: "ok", card: "rodando" });
    let card: Prospeccao;
    try {
      card = await criar.mutateAsync(corpoDoCard(cnpj, (await nomeCadastrado(cnpj)) ?? formatCnpj(cnpj)));
    } catch {
      // O hook já mostrou o motivo (CNPJ com card aberto, sem permissão…).
      setEtapas({ cnpja: "ok", card: "erro" });
      return;
    }
    setEtapas({ cnpja: "ok", card: "ok" });
    queryClient.invalidateQueries({ queryKey: ["companyList"] });
    concluir(card);
  }

  const situacao = empresa?.tipo === "existente" && !conferindoSerasa ? situacaoSerasa(consultaSerasaEm) : null;

  const botaoPrincipal = cardExistente ? (
    <button type="button" onClick={() => concluir(cardExistente)} className={BOTAO_PRIMARIO}>
      <Icon name="open_in_new" size={16} />
      Abrir o card existente
    </button>
  ) : empresa?.tipo === "nova" ? (
    <button type="button" onClick={consultarEAbrir} disabled={!comercialValido || ocupado} className={BOTAO_PRIMARIO}>
      <Icon name={rodando ? "progress_activity" : "travel_explore"} size={16} className={rodando ? "animate-spin" : ""} />
      {rodando ? "Consultando…" : "Consultar CNPJ Já e abrir"}
    </button>
  ) : (
    <button type="button" onClick={abrirExistente} disabled={!empresa || !comercialValido || ocupado} className={BOTAO_PRIMARIO}>
      <Icon name="add" size={16} />
      {criar.isPending ? "Abrindo…" : "Abrir prospecção"}
    </button>
  );

  return (
    <Dialogo
      titulo="Nova prospecção"
      subtitulo="Busque a empresa pelo CNPJ ou pelo nome. O card entra em Nova, com o checklist de documentos já montado."
      onFechar={onFechar}
      largura="lg"
      rodape={
        <>
          <button type="button" onClick={onFechar} disabled={rodando} className={BOTAO_SECUNDARIO}>
            Cancelar
          </button>
          {botaoPrincipal}
        </>
      }
    >
      <div className="space-y-4 px-5 py-4">
        {!empresa ? (
          <EscolhaEmpresa onEscolher={setEmpresa} cardsAbertos={abertos} />
        ) : (
          <div>
            <span className={ROTULO}>Empresa</span>
            <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 dark:border-slate-700 dark:bg-slate-800/60">
              <Icon
                name={empresa.tipo === "nova" ? "travel_explore" : "business"}
                size={18}
                className="mt-0.5 shrink-0 text-[#612035] dark:text-[#e8a3b6]"
              />
              <div className="min-w-0 flex-1">
                {empresa.tipo === "existente" ? (
                  <>
                    <p className="truncate text-sm font-semibold text-slate-900 dark:text-white">{empresa.nome}</p>
                    <p className="font-mono text-[11px] text-slate-500">{formatCnpj(empresa.cnpj)}</p>
                    <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                      {conferindoSerasa ? (
                        <span className="text-[11px] text-slate-400">conferindo a consulta Serasa…</span>
                      ) : (
                        <SeloSerasa consultaEm={consultaSerasaEm} />
                      )}
                      <Link href={`/clients/${empresa.cnpj}`} target="_blank" className="text-[11px] font-semibold text-[#2956E0] hover:underline">
                        ver empresa
                      </Link>
                    </div>
                    {situacao?.tom === "antiga" && (
                      <p className="mt-1.5 text-[11px] text-amber-800 dark:text-amber-200">
                        A consulta tem mais de 90 dias. A analista pode atualizar o Serasa pela página da empresa.
                      </p>
                    )}
                  </>
                ) : (
                  <>
                    <p className="text-sm font-semibold text-slate-900 dark:text-white">Empresa nova: {formatCnpj(empresa.cnpj)}</p>
                    <p className="mt-0.5 text-[11px] text-slate-500">
                      Não está no portal. Vamos consultar o CNPJ Já (dados da Receita Federal) e abrir o card. O Serasa
                      fica para depois, pela página da empresa.
                    </p>
                  </>
                )}
              </div>
              {!etapas && (
                <button
                  type="button"
                  onClick={() => {
                    setEmpresa(null);
                    setErro(null);
                  }}
                  className="shrink-0 cursor-pointer rounded-md px-2 py-1 text-xs text-slate-500 hover:bg-white hover:text-slate-800 dark:hover:bg-slate-700"
                >
                  trocar
                </button>
              )}
            </div>
            {cardExistente && (
              <p className="mt-2 flex items-center gap-2 rounded-lg bg-[#612035]/8 px-3 py-2 text-xs text-[#612035] dark:text-[#f2c4d1]">
                <Icon name="conveyor_belt" size={14} />
                Esta empresa já está na esteira, em {ROTULO_ESTAGIO[cardExistente.estagio]}. Abra o card existente.
              </p>
            )}
          </div>
        )}

        {etapas && (
          <ol className="space-y-1.5 rounded-xl border border-slate-200 px-3 py-2.5 dark:border-slate-700" aria-live="polite">
            {(["cnpja", "card"] as Etapa[]).map(etapa => {
              const estado = etapas[etapa] ?? "espera";
              return (
                <li key={etapa} className="flex items-center gap-2 text-xs">
                  <Icon
                    name={estado === "ok" ? "check_circle" : estado === "erro" ? "error" : estado === "rodando" ? "progress_activity" : "radio_button_unchecked"}
                    size={15}
                    weight={estado === "espera" ? "regular" : undefined}
                    className={
                      estado === "ok" ? "text-emerald-600" : estado === "erro" ? "text-rose-600" : estado === "rodando" ? "animate-spin text-[#612035]" : "text-slate-300"
                    }
                  />
                  <span className={estado === "espera" ? "text-slate-400" : "text-slate-700 dark:text-slate-200"}>{ROTULO_ETAPA[etapa]}</span>
                </li>
              );
            })}
          </ol>
        )}
        {erro && (
          <p role="alert" className="flex gap-2 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-800 dark:bg-rose-950/40 dark:text-rose-200">
            <Icon name="error_outline" size={14} className="mt-px shrink-0" />
            {erro}
          </p>
        )}

        {!cardExistente && (
          <>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block">
                <span className={ROTULO}>Comercial</span>
                <select value={comercial} onChange={event => setComercial(event.target.value)} disabled={rodando} className={`${CAMPO} cursor-pointer`}>
                  <option value={DIRETO}>Sem comercial (cliente direto)</option>
                  {diretorio.map(pessoa => (
                    <option key={pessoa.id} value={pessoa.id}>{pessoa.nome}</option>
                  ))}
                  <option value={OUTRO}>Outro — digitar o nome</option>
                </select>
              </label>
              {comercial === OUTRO && (
                <label className="block">
                  <span className={ROTULO}>Nome do comercial</span>
                  <input
                    value={comercialNome}
                    onChange={event => setComercialNome(event.target.value)}
                    placeholder="quem trouxe a empresa"
                    className={CAMPO}
                  />
                </label>
              )}
            </div>

            <details className="group rounded-xl border border-slate-200 dark:border-slate-700">
              <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-2 px-4 text-sm font-medium
                text-slate-700 dark:text-slate-200 [&::-webkit-details-marker]:hidden">
                <span className="inline-flex items-center gap-2">
                  <Icon name="storefront" size={16} className="text-slate-400" />
                  Dados comerciais
                  <span className="text-xs font-normal text-slate-400">
                    {preenchidos > 0 ? `${preenchidos} preenchido${preenchidos > 1 ? "s" : ""}` : "opcional — dá para completar depois"}
                  </span>
                </span>
                <Icon name="expand_more" size={16} className="text-slate-400 transition-transform group-open:rotate-180" />
              </summary>
              <div className="border-t border-slate-100 px-4 py-4 dark:border-slate-800">
                <DadosComerciaisForm valor={dados} onMudar={setDados} />
              </div>
            </details>
          </>
        )}
      </div>
    </Dialogo>
  );
}
