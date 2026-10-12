"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import Dica from "@/components/ui/Dica";
import Icon from "@/components/ui/Icon";
import { caixaTitulo, formatCnpj, formatData, formatMoeda, iniciais, tomDoAvatar } from "@/lib/formatadores";
import { TIPO_RESTRICAO, type TipoRestricao } from "@/types/pessoa-resumo";
import type { ClientProfile, CreditAnalysisData } from "@/types/company-detail";
import { CUSTO_CONSULTA_PJ, consultarEmpresa } from "./consultaEmpresa";

/** Colunas: abaixo de lg some "na carteira desde"; abaixo de md fica só a empresa. */
export const GRADE_EMPRESAS =
  "grid grid-cols-[minmax(0,1fr)_20px] items-center gap-4 " +
  "md:grid-cols-[minmax(0,2.4fr)_minmax(170px,1.4fr)_112px_20px] " +
  "lg:grid-cols-[minmax(0,2.6fr)_120px_minmax(190px,1.5fr)_112px_20px]";

/** Ocorrências por tipo, na mesma ordem e cor das restrições da pessoa física. */
function pendenciasPorTipo(analise: CreditAnalysisData) {
  const neg = analise.negativeSummary ?? {};
  const fatos = analise.inquiryHistory ?? {};
  const secoes: [TipoRestricao, { summary?: { count?: number; balance?: number } } | undefined][] = [
    ["PEFIN", neg.pefin],
    ["REFIN", neg.refin],
    ["CHEQUE", neg.check],
    ["PROTESTO", neg.notary],
    ["DIVIDA_VENCIDA", neg.collectionRecords],
    ["ACAO_JUDICIAL", fatos.judgementFilings],
    ["FALENCIA", fatos.bankrupts],
  ];
  return secoes
    .map(([tipo, secao]) => ({ tipo, quantidade: secao?.summary?.count ?? 0, valor: secao?.summary?.balance ?? 0 }))
    .filter(p => p.quantidade > 0);
}

export function CabecalhoEmpresas() {
  return (
    <div
      className={`${GRADE_EMPRESAS} border-b border-linha bg-[#FBFAF7] px-5 py-3 text-[11px] font-bold uppercase tracking-[0.1em] text-[#7F7C73] sm:px-6`}
    >
      <span>Empresa</span>
      <span className="hidden lg:block">Na carteira</span>
      <span className="hidden md:block">Pendências Serasa</span>
      <span className="hidden md:block">Visão Cedente</span>
      <span />
    </div>
  );
}

const VISAO_CEDENTE = {
  SIM: { rotulo: "Cedente", classe: "bg-[#E2F0E9] text-[#17583F]", ponto: "bg-ok" },
  NAO: { rotulo: "Não", classe: "bg-[#F8E1E6] text-[#8E1B37]", ponto: "bg-critico" },
  PENDENTE: { rotulo: "Pendente", classe: "bg-[#EFEDE6] text-[#5E5C55]", ponto: "bg-[#A8A49A]" },
} as const;

export default function LinhaEmpresa({ perfil, atraso }: { perfil: ClientProfile; atraso: number }) {
  const empresa = perfil.companyDetail;
  const analise = perfil.creditAnalysis;
  const cnpj = (empresa?.documentNumber || perfil.client?.documentNumber || "").replace(/\D/g, "");
  const nome = empresa?.companyName || perfil.client?.name || "";
  const tom = tomDoAvatar(cnpj);
  // Matriz/filial: usa o head do CNPJ Já; sem enriquecimento, cai na regra da Receita (0001 = matriz).
  const matriz = empresa?.head ?? (cnpj.length === 14 ? cnpj.slice(8, 12) === "0001" : null);
  const soSacado = perfil.client?.origin === "SACADO_PRACA";
  const semCodigo = !perfil.client?.clientCode && !soSacado;
  const nadaConstaSinalizado = analise?.negativeSummary?.message?.trim().toUpperCase() === "NADA CONSTA";
  const local = [caixaTitulo(empresa?.city ?? null), empresa?.state].filter(Boolean).join(" · ");

  const pendencias = analise ? pendenciasPorTipo(analise) : [];
  const ocorrencias = pendencias.reduce((s, p) => s + p.quantidade, 0);
  const divida = pendencias.reduce((s, p) => s + p.valor, 0);
  const corPonto = !analise ? "#B5B1A6" : ocorrencias > 0 ? "#D1732C" : "#1F7A5C";
  const visao = VISAO_CEDENTE[(analise?.visaoCedente as keyof typeof VISAO_CEDENTE) ?? "PENDENTE"] ?? VISAO_CEDENTE.PENDENTE;

  return (
    <div
      className={`${GRADE_EMPRESAS} group relative border-b border-[#F0EEE8] bg-white px-5 py-3 transition-colors last:border-b-0 hover:bg-[#FBF7F1] sm:px-6 animate-subir motion-reduce:animate-none`}
      style={{ animationDelay: `${atraso}ms` }}
    >
      <span className="flex min-w-0 items-center gap-3">
        <span className="relative shrink-0">
          <span
            className="grid h-10 w-10 place-items-center rounded-xl text-[13.5px] font-bold transition-transform duration-300 ease-saida group-hover:-rotate-6 group-hover:scale-105 motion-reduce:group-hover:transform-none"
            style={{ background: tom.fundo, color: tom.texto }}
          >
            {iniciais(nome)}
          </span>
          <span className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full ring-2 ring-white" style={{ background: corPonto }} />
        </span>
        <span className="min-w-0">
          <span className="flex min-w-0 flex-wrap items-center gap-1.5">
            {/* Link "esticado": o ::after cobre a linha toda, e só a consulta rápida fica por cima. */}
            <Link
              href={`/clients/${cnpj}`}
              className="truncate text-[14.5px] font-semibold text-grafite after:absolute after:inset-0 after:content-[''] focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-primary"
            >
              {caixaTitulo(nome) || "Razão social não informada"}
            </Link>
            {nadaConstaSinalizado && (
              <span className="relative z-10">
                <Dica
                  conteudo={
                    <>
                      <b className="mb-1 block">Sinalização &quot;NADA CONSTA&quot;</b>
                      O Serasa retornou &quot;NADA CONSTA&quot;. Pode indicar restrição de exibição de ocorrências, não
                      necessariamente ausência de risco.
                    </>
                  }
                >
                  <span
                    tabIndex={0}
                    aria-label='Empresa com sinalização "NADA CONSTA"'
                    className="grid h-5 w-5 place-items-center rounded-full bg-[#F8E1E6] text-critico"
                  >
                    <Icon name="priority_high" size={13} />
                  </span>
                </Dica>
              </span>
            )}
            {matriz !== null && (
              <span className="rounded-md bg-[#F4F2EC] px-1.5 py-px text-[10px] font-bold uppercase tracking-wide text-[#5E5C55]">
                {matriz ? "matriz" : "filial"}
              </span>
            )}
            {soSacado && (
              <span className="rounded-md bg-[#2956E0]/10 px-1.5 py-px text-[10px] font-bold uppercase tracking-wide text-[#1f43b8]">
                só sacado
              </span>
            )}
            {semCodigo && (
              <span
                className="rounded-md bg-secondary/10 px-1.5 py-px text-[10px] font-bold text-laranja-texto"
                title="Empresa sem código de cliente (ERP) — não cruza na Praça de Pagamento"
              >
                sem código
              </span>
            )}
          </span>
          <span className="mt-0.5 block truncate font-mono text-xs text-suave">
            {formatCnpj(cnpj)}
            {local && ` · ${local}`}
          </span>
        </span>
      </span>

      <span className="hidden text-[13px] text-suave lg:block">
        {perfil.client?.createdAt ? formatData(perfil.client.createdAt) : "—"}
      </span>

      <span className="hidden md:block">
        {!analise ? (
          <span className="flex flex-wrap items-center gap-2">
            <span className="text-[12.5px] italic text-[#8A877E]">Sem análise</span>
            {cnpj.length === 14 && <ConsultaRapidaSerasa cnpj={cnpj} />}
          </span>
        ) : ocorrencias === 0 ? (
          <span className="inline-flex items-center gap-1.5 text-[12.5px] font-semibold text-ok">
            <Icon name="check_circle" size={17} />
            Nada consta
          </span>
        ) : (
          <>
            <span className="flex h-1.5 w-[110px] gap-0.5 overflow-hidden rounded-full bg-[#F0EEE8]">
              {pendencias.map(p => (
                <span
                  key={p.tipo}
                  className="block h-full"
                  style={{ width: `${(p.quantidade / ocorrencias) * 100}%`, background: TIPO_RESTRICAO[p.tipo].cor }}
                />
              ))}
            </span>
            <span className="mt-1.5 block text-[12.5px] font-semibold text-laranja-texto">
              {ocorrencias === 1 ? "1 ocorrência" : `${ocorrencias} ocorrências`} · {formatMoeda(divida)}
            </span>
          </>
        )}
      </span>

      <span className="hidden md:block">
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-bold ${visao.classe}`}>
          <span className={`h-1.5 w-1.5 rounded-full ${visao.ponto}`} />
          {visao.rotulo}
        </span>
      </span>

      <Icon
        name="chevron_right"
        size={18}
        className="text-[#B5B1A6] transition-[transform,color] duration-200 group-hover:translate-x-1 group-hover:text-primary"
      />
    </div>
  );
}

/**
 * Consulta Serasa direto da lista, só para empresa ainda sem análise. Custa dinheiro: o
 * primeiro clique pede confirmação, que expira em 4 segundos.
 */
function ConsultaRapidaSerasa({ cnpj }: { cnpj: string }) {
  const queryClient = useQueryClient();
  const [confirmando, setConfirmando] = useState(false);
  const [consultando, setConsultando] = useState(false);

  useEffect(() => {
    if (!confirmando) return;
    const prazo = setTimeout(() => setConfirmando(false), 4000);
    return () => clearTimeout(prazo);
  }, [confirmando]);

  const consultar = async () => {
    setConsultando(true);
    try {
      await consultarEmpresa("serasa", cnpj);
      toast.success("Consulta Serasa salva.");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["companyList"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboardMetrics"] }),
      ]);
    } catch (erro) {
      toast.error(erro instanceof Error ? erro.message : "Não foi possível consultar o Serasa.");
    } finally {
      setConsultando(false);
      setConfirmando(false);
    }
  };

  return (
    <button
      type="button"
      disabled={consultando}
      onClick={() => (confirmando ? consultar() : setConfirmando(true))}
      className={`relative z-10 inline-flex h-7 items-center gap-1 rounded-full px-2.5 text-[11.5px] font-bold transition-colors disabled:opacity-60 ${
        confirmando ? "bg-[#E4006F] text-white" : "border border-[#E4006F]/35 text-[#B8005A] hover:bg-[#E4006F]/10"
      }`}
    >
      {consultando && <Icon name="progress_activity" size={13} className="animate-spin" />}
      {consultando ? "Consultando…" : confirmando ? `Confirmar · ${CUSTO_CONSULTA_PJ}` : "Consultar Serasa"}
    </button>
  );
}

/** Linhas de mentira com brilho enquanto a página carrega. */
export function EmpresasEsqueleto({ quantidade = 8 }: { quantidade?: number }) {
  return (
    <div aria-hidden>
      {Array.from({ length: quantidade }, (_, i) => (
        <div key={i} className={`${GRADE_EMPRESAS} border-b border-[#F0EEE8] px-5 py-3.5 sm:px-6`}>
          <span className="flex items-center gap-3">
            <span className="esqueleto h-10 w-10 shrink-0 rounded-xl" />
            <span className="flex flex-col gap-2">
              <span className="esqueleto h-3.5 w-56" />
              <span className="esqueleto h-3 w-36" />
            </span>
          </span>
          <span className="esqueleto hidden h-3 w-20 lg:block" />
          <span className="esqueleto hidden h-3 w-28 md:block" />
          <span className="esqueleto hidden h-5 w-20 rounded-full md:block" />
          <span />
        </div>
      ))}
    </div>
  );
}
