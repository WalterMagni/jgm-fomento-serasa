"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Icon from "@/components/ui/Icon";
import PageHeader from "@/components/ui/PageHeader";
import KpiCard, { Medidor } from "@/components/ui/KpiCard";
import Segmentos from "@/components/ui/Segmentos";
import EstadoVazio from "@/components/ui/EstadoVazio";
import Paginacao from "@/components/ui/Paginacao";
import { BOTAO } from "@/components/ui/botao";
import LinhaEmpresa, { CabecalhoEmpresas, EmpresasEsqueleto } from "@/components/carteira/LinhaEmpresa";
import NovoCliente from "@/components/carteira/NovoCliente";
import FiltroNota, { type FiltroDeNota } from "@/components/nota/FiltroNota";
import { useCompanyList } from "@/hooks/useCompanyList";
import { useDashboardMetrics } from "@/hooks/useDashboardMetrics";

type Cedente = "" | "SIM" | "NAO" | "PENDENTE";
type Origem = "" | "CARTEIRA" | "SACADO_PRACA" | "PROSPECCAO";
type Analise = "" | "ANALYZED" | "PENDING";
type Ordem = "recentes" | "nome";

const POR_PAGINA = [10, 50, 100, 200];

// A URL usa nomes curtos em português; a API, os valores do backend.
const CEDENTE_URL: Record<string, Cedente> = { sim: "SIM", nao: "NAO", pendente: "PENDENTE" };
const ORIGEM_URL: Record<string, Origem> = { carteira: "CARTEIRA", sacado: "SACADO_PRACA", prospeccao: "PROSPECCAO" };
const ANALISE_URL: Record<string, Analise> = { feita: "ANALYZED", pendente: "PENDING" };
const NOTA_URL: Record<string, FiltroDeNota> = { a: "A", b: "B", c: "C", d: "D", sem: "SEM" };
const paraUrl = <T extends string>(mapa: Record<string, T>, valor: T) =>
  Object.keys(mapa).find(chave => mapa[chave] === valor) ?? "";

export default function GestaoCarteiraPage() {
  // useSearchParams exige Suspense no App Router.
  return (
    <Suspense fallback={null}>
      <GestaoCarteira />
    </Suspense>
  );
}

function GestaoCarteira() {
  const params = useSearchParams();
  const [busca, setBusca] = useState(() => params.get("q") ?? "");
  const [buscaEnviada, setBuscaEnviada] = useState(busca);
  const [cedente, setCedente] = useState<Cedente>(() => CEDENTE_URL[params.get("cedente") ?? ""] ?? "");
  const [origem, setOrigem] = useState<Origem>(() => ORIGEM_URL[params.get("origem") ?? ""] ?? "");
  const [analise, setAnalise] = useState<Analise>(() => ANALISE_URL[params.get("analise") ?? ""] ?? "");
  const [nota, setNota] = useState<FiltroDeNota>(() => NOTA_URL[params.get("nota") ?? ""] ?? "");
  const [ordem, setOrdem] = useState<Ordem>(() => (params.get("ordem") === "nome" ? "nome" : "recentes"));
  const [pagina, setPagina] = useState(() => Math.max(0, Number(params.get("pagina") ?? "1") - 1) || 0);
  const [porPagina, setPorPagina] = useState(() => {
    const n = Number(params.get("por"));
    return POR_PAGINA.includes(n) ? n : 50;
  });
  const [incluindo, setIncluindo] = useState(false);

  // A busca vai ao servidor: espera a pessoa parar de digitar.
  useEffect(() => {
    const espera = setTimeout(() => setBuscaEnviada(busca.trim()), 350);
    return () => clearTimeout(espera);
  }, [busca]);

  // A URL espelha os filtros: voltar da Visão 360º devolve a lista como estava.
  useEffect(() => {
    const p = new URLSearchParams();
    if (buscaEnviada) p.set("q", buscaEnviada);
    if (cedente) p.set("cedente", paraUrl(CEDENTE_URL, cedente));
    if (origem) p.set("origem", paraUrl(ORIGEM_URL, origem));
    if (analise) p.set("analise", paraUrl(ANALISE_URL, analise));
    if (nota) p.set("nota", paraUrl(NOTA_URL, nota));
    if (ordem !== "recentes") p.set("ordem", ordem);
    if (pagina > 0) p.set("pagina", String(pagina + 1));
    if (porPagina !== 50) p.set("por", String(porPagina));
    const qs = p.toString();
    window.history.replaceState(null, "", qs ? `?${qs}` : window.location.pathname);
  }, [buscaEnviada, cedente, origem, analise, nota, ordem, pagina, porPagina]);

  const { data: metricas, isLoading: carregandoMetricas } = useDashboardMetrics();
  const { companies, pageData, isLoading, isError, refetch, isPlaceholderData } = useCompanyList(
    pagina,
    porPagina,
    buscaEnviada,
    cedente,
    analise,
    origem,
    ordem === "nome" ? "nome" : "",
    nota,
  );

  const total = metricas?.totalClients ?? 0;
  const analisadas = metricas?.analyzedClients ?? 0;
  const cedentes = metricas?.cedenteSimCount ?? 0;
  const aguardando = Math.max(total - analisadas, 0);
  const fracao = (n: number, de = total) => (de ? n / de : 0);
  const pct = (n: number, de = total) => `${Math.round(fracao(n, de) * 100)}%`;

  const totalFiltrado = pageData?.totalElements ?? 0;
  const totalPaginas = pageData?.totalPages ?? 1;
  const algumFiltro = !!(buscaEnviada || cedente || origem || analise || nota);

  const mudar = <T,>(definir: (v: T) => void) => (valor: T) => {
    definir(valor);
    setPagina(0);
  };
  const alternarAnalise = (valor: Analise) => mudar(setAnalise)(analise === valor ? "" : valor);
  const limparFiltros = () => {
    setBusca("");
    setBuscaEnviada("");
    setCedente("");
    setOrigem("");
    setAnalise("");
    setNota("");
    setPagina(0);
  };

  return (
    // Largura total, como era antes da repaginação: a carteira é a tela de trabalho e usa o espaço todo.
    <div className="flex flex-col gap-6 pb-10">
      <PageHeader
        rotulo="Carteira"
        titulo="Gestão de Carteira"
        resumo={
          carregandoMetricas ? (
            "Carregando carteira…"
          ) : (
            <>
              <b className="text-grafite">{total.toLocaleString("pt-BR")}</b> empresas ·{" "}
              <b className="text-grafite">{analisadas.toLocaleString("pt-BR")}</b> analisadas no Serasa
            </>
          )
        }
        acoes={
          <button type="button" onClick={() => setIncluindo(v => !v)} aria-expanded={incluindo} className={BOTAO.primario}>
            <Icon name="add" />
            Novo cliente
          </button>
        }
      />

      {incluindo && (
        <NovoCliente
          onFechar={() => setIncluindo(false)}
          onIncluido={cnpj => {
            // Mostra a empresa recém-incluída: busca por ela, sem os outros filtros.
            setCedente("");
            setOrigem("");
            setAnalise("");
            setNota("");
            setBusca(cnpj);
            setBuscaEnviada(cnpj);
            setPagina(0);
            setIncluindo(false);
          }}
        />
      )}

      <section className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          icone="business"
          cor="#612035"
          rotulo="Empresas na carteira"
          valor={total}
          atraso={60}
          rodape={`${(metricas?.cnpjaEnrichedCount ?? 0).toLocaleString("pt-BR")} com cadastro do CNPJ Já`}
        >
          <Medidor fracao={fracao(metricas?.cnpjaEnrichedCount ?? 0)} cor="#2956E0" />
        </KpiCard>
        <KpiCard
          icone="verified_user"
          cor="#E4006F"
          rotulo="Analisadas no Serasa"
          valor={analisadas}
          atraso={120}
          rodape={`${pct(analisadas)} da carteira`}
          onClick={() => alternarAnalise("ANALYZED")}
          ativo={analise === "ANALYZED"}
        >
          <Medidor fracao={fracao(analisadas)} cor="#E4006F" />
        </KpiCard>
        <KpiCard
          icone="swap_horiz"
          cor="#1F7A5C"
          rotulo="Visão Cedente"
          valor={cedentes}
          atraso={180}
          rodape={`${pct(cedentes, analisadas)} das analisadas operam como cedente`}
          onClick={() => mudar(setCedente)(cedente === "SIM" ? "" : "SIM")}
          ativo={cedente === "SIM"}
        >
          <Medidor fracao={fracao(cedentes, analisadas)} cor="#1F7A5C" />
        </KpiCard>
        <KpiCard
          icone="hourglass_empty"
          cor="#D1732C"
          rotulo="Aguardando análise"
          valor={aguardando}
          atraso={240}
          rodape={`${pct(aguardando)} nunca consultadas no Serasa`}
          onClick={() => alternarAnalise("PENDING")}
          ativo={analise === "PENDING"}
        >
          <Medidor fracao={fracao(aguardando)} cor="#D1732C" />
        </KpiCard>
      </section>

      <section className="flex flex-wrap items-center gap-3 animate-subir motion-reduce:animate-none" style={{ animationDelay: "280ms" }}>
        <label className="relative min-w-0 flex-1 basis-[260px]">
          <span className="sr-only">Buscar por nome ou CNPJ</span>
          <Icon name="search" size={20} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#8A877E]" />
          <input
            type="search"
            value={busca}
            onChange={e => {
              setBusca(e.target.value);
              setPagina(0);
            }}
            placeholder="Buscar por nome ou CNPJ"
            className="h-[46px] w-full rounded-[13px] border border-linha bg-white pl-11 pr-3.5 text-[14.5px] font-medium text-grafite outline-none transition-[border-color,box-shadow] placeholder:text-[#9A978E] focus:border-primary focus:ring-4 focus:ring-primary/10"
          />
        </label>
        <Segmentos
          rotulo="Filtrar por Visão Cedente"
          valor={cedente}
          onChange={mudar(setCedente)}
          opcoes={[
            { valor: "", rotulo: "Todas" },
            { valor: "SIM", rotulo: "Cedente", cor: "#1F7A5C" },
            { valor: "NAO", rotulo: "Não", cor: "#B42345" },
            { valor: "PENDENTE", rotulo: "Pendente", cor: "#A8A49A" },
          ]}
        />
        <Segmentos
          rotulo="Filtrar por origem"
          valor={origem}
          onChange={mudar(setOrigem)}
          opcoes={[
            { valor: "", rotulo: "Toda origem" },
            { valor: "CARTEIRA", rotulo: "Carteira" },
            { valor: "SACADO_PRACA", rotulo: "Só sacado" },
            { valor: "PROSPECCAO", rotulo: "Prospecção" },
          ]}
        />
        <FiltroNota valor={nota} onChange={mudar(setNota)} />
        <label className="shrink-0">
          <span className="sr-only">Ordenar</span>
          <select
            value={ordem}
            onChange={e => mudar(setOrdem)(e.target.value as Ordem)}
            className="h-[46px] rounded-[13px] border border-linha bg-white px-3 text-[13.5px] font-semibold text-grafite outline-none focus:border-primary"
          >
            <option value="recentes">Mais recentes</option>
            <option value="nome">Nome A–Z</option>
          </select>
        </label>
      </section>

      <section
        className="overflow-hidden rounded-painel border border-linha bg-white animate-subir motion-reduce:animate-none"
        style={{ animationDelay: "340ms" }}
        aria-busy={isLoading || isPlaceholderData}
      >
        <CabecalhoEmpresas />

        {isLoading ? (
          <EmpresasEsqueleto />
        ) : isError ? (
          <EstadoVazio
            icone="error_outline"
            titulo="Não foi possível carregar a carteira"
            descricao="O servidor não respondeu agora. O resto do portal continua funcionando."
            acoes={
              <button type="button" onClick={() => refetch()} className={BOTAO.primario}>
                Tentar de novo
              </button>
            }
          />
        ) : companies.length === 0 ? (
          <EstadoVazio
            icone="search_off"
            titulo={algumFiltro ? "Nenhuma empresa com esses filtros" : "A carteira está vazia"}
            descricao={
              algumFiltro
                ? "Confira a digitação ou limpe os filtros. Se a empresa ainda não está na carteira, inclua pelo CNPJ."
                : "Inclua a primeira empresa pelo CNPJ."
            }
            acoes={
              <>
                {algumFiltro && (
                  <button type="button" onClick={limparFiltros} className={BOTAO.fantasma}>
                    Limpar filtros
                  </button>
                )}
                <button type="button" onClick={() => setIncluindo(true)} className={BOTAO.primario}>
                  Novo cliente
                </button>
              </>
            }
          />
        ) : (
          // A chave refaz a cascata de entrada a cada página ou filtro novo.
          <div
            key={`${pagina}-${porPagina}-${cedente}-${origem}-${analise}-${nota}-${ordem}-${buscaEnviada}`}
            className={`transition-opacity duration-200 ${isPlaceholderData ? "opacity-60" : ""}`}
          >
            {companies.map((perfil, i) => (
              <LinhaEmpresa
                key={perfil.client?.id ?? perfil.companyDetail?.id ?? i}
                perfil={perfil}
                atraso={Math.min(i, 14) * 24}
              />
            ))}
          </div>
        )}

        {!isLoading && !isError && companies.length > 0 && (
          <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-[#F0EEE8] bg-[#FBFAF7] px-5 py-3 text-[13px] text-suave sm:px-6">
            <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <span>
                Mostrando{" "}
                <b className="text-grafite">
                  {(pagina * porPagina + 1).toLocaleString("pt-BR")}–
                  {Math.min(totalFiltrado, (pagina + 1) * porPagina).toLocaleString("pt-BR")}
                </b>{" "}
                de <b className="text-grafite">{totalFiltrado.toLocaleString("pt-BR")}</b>
              </span>
              <label className="inline-flex items-center gap-1.5">
                <select
                  value={porPagina}
                  onChange={e => mudar(setPorPagina)(Number(e.target.value))}
                  className="h-9 rounded-[10px] border border-linha bg-white px-2 text-[13px] font-semibold text-grafite outline-none focus:border-primary"
                >
                  {POR_PAGINA.map(n => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
                por página
              </label>
            </span>
            <Paginacao pagina={pagina} totalPaginas={totalPaginas} onIr={setPagina} />
          </footer>
        )}
      </section>
    </div>
  );
}
