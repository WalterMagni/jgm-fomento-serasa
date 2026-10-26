"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import Icon from "@/components/ui/Icon";
import Segmentos from "@/components/ui/Segmentos";
import DetalheProcessoGaveta from "@/components/judit/DetalheProcessoGaveta";
import { useJudit, useJuditProcessos } from "@/hooks/useJudit";
import {
  AlertaTituloContestado,
  Carimbo,
  PontosDeAtencao,
  ROTULO_JUSTICA,
  SelosJudit,
  VERDE,
} from "@/components/judit/JuditPecas";
import { PainelGeral } from "@/components/judit/painel/PainelGeral";
import { PainelJustica } from "@/components/judit/painel/PainelJustica";
import { PartesJudit } from "@/components/judit/painel/PartesJudit";
import { CardTotal, COR_POLO } from "@/components/judit/painel/Graficos";
import { ListaProcessos, type FiltroInicial } from "@/components/judit/painel/ListaProcessos";
import type { JuditJustica, JuditProcessosConsultados } from "@/types/judit";

type Aba = "dashboard" | "instancias" | "processos";
type SubAba = "dashboard" | "processos" | "partes" | "financeiro";

function formatarCnpj(d: string) {
  return d.length === 14 ? `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}` : d;
}

/**
 * Os processos da Judit de uma empresa, no desenho da aba Processos do Vadu (pedido do time,
 * 2026-10-26): Dashboard, Instâncias (por justiça: dashboard, processos, partes e segmento
 * financeiro) e a lista completa. Lê a última lista guardada — nada aqui chama a Judit nem cobra.
 *
 * <p>Na URL: `aba=processos` e `area=` abrem a lista filtrada (as lupas do card da Visão 360º);
 * `processo=<cnj>` abre a gaveta do detalhe (o aviso da Judit traz).</p>
 */
export default function ProcessosJuditPage() {
  const params = useParams<{ cnpj: string }>();
  const cnpj = (params.cnpj ?? "").replace(/\D/g, "");
  const parametros = useSearchParams();
  const nome = parametros.get("nome");
  const processoAberto = parametros.get("processo");
  const router = useRouter();
  const caminho = usePathname();
  const { data: consulta, isLoading, isError, error } = useJuditProcessos(cnpj);
  const { data: situacao } = useJudit(cnpj);

  const areaDaUrl = parametros.has("area") ? parametros.get("area") || null : undefined;
  const [aba, setAba] = useState<Aba>(parametros.get("aba") === "processos" ? "processos" : "dashboard");
  const [filtro, setFiltro] = useState<{ inicial: FiltroInicial; chave: number }>({
    inicial: areaDaUrl === undefined ? {} : { area: areaDaUrl },
    chave: 0,
  });

  function abrirDetalhe(cnj: string | null) {
    const novos = new URLSearchParams(parametros.toString());
    if (cnj) novos.set("processo", cnj);
    else novos.delete("processo");
    const busca = novos.toString();
    router.replace(busca ? `${caminho}?${busca}` : caminho, { scroll: false });
  }

  function verLista(inicial: FiltroInicial) {
    setFiltro(atual => ({ inicial, chave: atual.chave + 1 }));
    setAba("processos");
  }

  const titulo = nome || formatarCnpj(cnpj);
  const voltar = (
    <Link href={`/clients/${cnpj}`} className="inline-flex items-center gap-1 text-[13px] font-medium text-suave transition-colors hover:text-primary print:hidden">
      <Icon name="arrow_back" size={14} /> Voltar para {nome || "a Visão 360º"}
    </Link>
  );

  if (isLoading) {
    return <p className="p-6 text-sm text-gray-500">Carregando processos…</p>;
  }
  if (isError) {
    return (
      <div className="space-y-3 p-6">
        {voltar}
        <p className="text-sm text-gray-500">{(error as Error)?.message ?? "Não foi possível carregar os processos."}</p>
      </div>
    );
  }
  if (!consulta) {
    return (
      <div className="space-y-3 p-6">
        {voltar}
        <p className="text-sm text-gray-500">
          A lista de processos desta empresa ainda não foi buscada. Busque pelo card Processos judiciais na Visão 360º.
        </p>
      </div>
    );
  }

  const resumo = situacao?.resumo ?? null;
  const lista = consulta.dados.lista;

  return (
    <div className="space-y-5">
      <header className="space-y-3">
        {voltar}
        <div className="min-w-0">
          <p className="flex flex-wrap items-center gap-2 text-xs font-bold uppercase tracking-[0.14em] text-[#047857] dark:text-[#15BE62]">
            <Icon name="gavel" size={14} /> Processos judiciais <SelosJudit />
          </p>
          <h1 className="mt-2 text-3xl font-semibold leading-tight tracking-[-0.02em] text-grafite dark:text-white">{titulo}</h1>
          <p className="mt-1 text-sm text-suave">
            {nome && `${formatarCnpj(cnpj)} · `}base histórica da Judit (Data Lake) ·{" "}
            <Carimbo rotulo="lista consultada" {...consulta} validadeDias={situacao?.validadeDias ?? 30} />
          </p>
        </div>
      </header>

      <div className="print:hidden">
        <Segmentos<Aba>
          rotulo="Seção dos processos"
          valor={aba}
          onChange={setAba}
          opcoes={[
            { valor: "dashboard", rotulo: "Dashboard" },
            { valor: "instancias", rotulo: "Instâncias" },
            { valor: "processos", rotulo: "Processos", contagem: lista.length },
          ]}
        />
      </div>

      <section className="overflow-hidden rounded-xl border border-[#15BE62]/50 bg-surface-light shadow-sm dark:border-[#15BE62]/40 dark:bg-surface-dark">
        <div className="h-1 print:hidden" style={{ backgroundColor: VERDE }} aria-hidden />
        <div className="space-y-5 p-5">
          {aba === "dashboard" && (
            <>
              <AlertaTituloContestado quantidade={consulta.dados.tituloContestadoComoReu} />
              {resumo && resumo.dados.total > 0 && <PontosDeAtencao pontos={resumo.pontosDeAtencao} />}
              <PainelGeral
                bloco={consulta.painel.geral}
                onArea={area => verLista({ area })}
                onProcesso={cnj => abrirDetalhe(cnj)}
              />
              {consulta.somenteReu && (
                <p className="text-[11px] text-gray-500">
                  A empresa passa de 1.000 registros na Judit, que corta nesse número; a busca pediu só os processos em que
                  ela é ré.
                </p>
              )}
            </>
          )}

          {aba === "instancias" && <Instancias cnpj={cnpj} consulta={consulta} onDetalhe={abrirDetalhe} />}

          {aba === "processos" && (
            <ListaProcessos
              key={`todos-${filtro.chave}`}
              cnpj={cnpj}
              base={lista}
              completa
              detalhes={consulta.detalhes}
              onDetalhe={abrirDetalhe}
              inicial={filtro.inicial}
            />
          )}
        </div>
      </section>

      {processoAberto &&
        (() => {
          const processo = lista.find(p => p.cnj === processoAberto);
          return processo ? (
            <DetalheProcessoGaveta
              cnpj={cnpj}
              processo={processo}
              nomeEmpresa={nome}
              situacao={situacao}
              onFechar={() => abrirDetalhe(null)}
            />
          ) : null;
        })()}
    </div>
  );
}

/** Instâncias do Vadu: uma aba por justiça e, dentro, dashboard, processos, partes e segmento financeiro. */
function Instancias({
  cnpj,
  consulta,
  onDetalhe,
}: {
  cnpj: string;
  consulta: JuditProcessosConsultados;
  onDetalhe: (cnj: string) => void;
}) {
  const justicas = Object.keys(consulta.painel.porJustica) as JuditJustica[];
  const comProcesso = justicas.find(j => (consulta.painel.porJustica[j]?.total.quantidade ?? 0) > 0);
  const [justica, setJustica] = useState<JuditJustica>(comProcesso ?? "ESTADUAL");
  const [sub, setSub] = useState<SubAba>("dashboard");
  const [filtro, setFiltro] = useState<{ inicial: FiltroInicial; chave: number }>({ inicial: {}, chave: 0 });

  const bloco = consulta.painel.porJustica[justica];
  const daJustica = consulta.dados.lista.filter(p => p.justica === justica);
  const financeiro = daJustica.filter(p => p.segmentoFinanceiro);
  const nome = ROTULO_JUSTICA[justica];

  function trocarJustica(nova: JuditJustica) {
    setJustica(nova);
    setSub("dashboard");
    setFiltro(atual => ({ inicial: {}, chave: atual.chave + 1 }));
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 print:hidden">
        <Segmentos<JuditJustica>
          rotulo="Justiça"
          valor={justica}
          onChange={trocarJustica}
          opcoes={justicas.map(j => ({
            valor: j,
            rotulo: ROTULO_JUSTICA[j],
            contagem: consulta.painel.porJustica[j]?.total.quantidade ?? 0,
          }))}
        />
        <Segmentos<SubAba>
          rotulo={`O que ver na Justiça ${nome}`}
          valor={sub}
          onChange={nova => {
            setSub(nova);
            setFiltro(atual => ({ inicial: {}, chave: atual.chave + 1 }));
          }}
          opcoes={[
            { valor: "dashboard", rotulo: "Dashboard" },
            { valor: "processos", rotulo: "Processos", contagem: daJustica.length },
            { valor: "partes", rotulo: "Partes", contagem: bloco?.partes.length ?? 0 },
            { valor: "financeiro", rotulo: "Seg. financeiro", contagem: financeiro.length },
          ]}
        />
      </div>

      {!bloco ? null : sub === "dashboard" ? (
        <PainelJustica
          bloco={bloco}
          nome={nome}
          onSituacao={(polo, situacao) => {
            setFiltro(atual => ({ inicial: { polo, situacao: situacao ?? "SEM_ANDAMENTO" }, chave: atual.chave + 1 }));
            setSub("processos");
          }}
        />
      ) : sub === "processos" ? (
        <ListaProcessos
          key={`${justica}-${filtro.chave}`}
          cnpj={cnpj}
          base={daJustica}
          detalhes={consulta.detalhes}
          onDetalhe={onDetalhe}
          inicial={filtro.inicial}
        />
      ) : sub === "partes" ? (
        <PartesJudit partes={bloco.partes} />
      ) : (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <CardTotal rotulo="Segmento financeiro" soma={bloco.segmentoFinanceiro} apoio="Processos com banco ou financeira" />
            <CardTotal
              rotulo="Empresa ré"
              soma={somar(financeiro.filter(p => p.polo === "REU"))}
              apoio="Movidos por banco ou financeira"
              destaque={COR_POLO.REU}
            />
            <CardTotal
              rotulo="Empresa autora"
              soma={somar(financeiro.filter(p => p.polo === "AUTOR"))}
              apoio="Contra banco ou financeira"
              destaque={COR_POLO.AUTOR}
            />
          </div>
          <p className="text-[11px] text-gray-500">
            Processos em que a outra parte é banco, financeira, FIDC, securitizadora ou cooperativa de crédito — pela
            etiqueta da Judit ou pelo nome.
          </p>
          <ListaProcessos
            key={`fin-${justica}-${filtro.chave}`}
            cnpj={cnpj}
            base={financeiro}
            detalhes={consulta.detalhes}
            onDetalhe={onDetalhe}
          />
        </div>
      )}
    </div>
  );
}

function somar(lista: { valor: number | null }[]) {
  return { quantidade: lista.length, valor: lista.reduce((total, p) => total + (p.valor ?? 0), 0) };
}
