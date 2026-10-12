"use client";

import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import Icon from "@/components/ui/Icon";
import PageHeader from "@/components/ui/PageHeader";
import KpiCard, { Medidor, MiniBarras } from "@/components/ui/KpiCard";
import Segmentos from "@/components/ui/Segmentos";
import EstadoVazio from "@/components/ui/EstadoVazio";
import Paginacao from "@/components/ui/Paginacao";
import { BOTAO } from "@/components/ui/botao";
import LinhaPessoa, { CabecalhoPessoas, LinhasEsqueleto, type PessoaNaLista } from "@/components/pessoas/LinhaPessoa";
import GavetaPessoa from "@/components/pessoas/GavetaPessoa";
import NovaConsultaPF from "@/components/pessoas/NovaConsultaPF";
import { diasDesde, normalizar } from "@/lib/formatadores";
import { usePessoasResumo } from "@/hooks/usePersonProfile";
import { SITUACAO, type SituacaoPessoa } from "@/types/pessoa-resumo";

type Filtro = "TODOS" | SituacaoPessoa;
type Ordem = "recentes" | "az" | "restricoes" | "divida" | "antigas";

const POR_PAGINA = 15;
const FILTROS: Filtro[] = ["TODOS", "LIMPO", "COM_RESTRICAO", "CRITICO"];
const ORDENS: Record<Ordem, { rotulo: string; comparar: (a: PessoaNaLista, b: PessoaNaLista) => number }> = {
  recentes: { rotulo: "Mais recentes", comparar: (a, b) => a.dias - b.dias },
  az: { rotulo: "Nome A–Z", comparar: (a, b) => (a.nome ?? "").localeCompare(b.nome ?? "", "pt-BR") },
  restricoes: { rotulo: "Mais restrições", comparar: (a, b) => b.totalOcorrencias - a.totalOcorrencias || b.dividaTotal - a.dividaTotal },
  divida: { rotulo: "Maior dívida", comparar: (a, b) => b.dividaTotal - a.dividaTotal },
  antigas: { rotulo: "Consulta mais antiga", comparar: (a, b) => b.dias - a.dias },
};

function lerFiltro(valor: string | null): Filtro {
  const f = (valor ?? "").toUpperCase() as Filtro;
  return FILTROS.includes(f) ? f : "TODOS";
}

function lerOrdem(valor: string | null): Ordem {
  return valor && valor in ORDENS ? (valor as Ordem) : "recentes";
}

/** Consultas por mês de calendário, do mais antigo (11 meses atrás) ao atual. */
function consultasPorMes(pessoas: PessoaResumoComData[], hoje: Date) {
  const meses = new Array(12).fill(0);
  const atual = hoje.getFullYear() * 12 + hoje.getMonth();
  for (const p of pessoas) {
    const d = new Date(p.consultaEm);
    const distancia = atual - (d.getFullYear() * 12 + d.getMonth());
    if (distancia >= 0 && distancia < 12) meses[11 - distancia]++;
  }
  return meses;
}

type PessoaResumoComData = PessoaNaLista & { chave: string };

export default function PessoasFisicasPage() {
  // useSearchParams exige Suspense no App Router.
  return (
    <Suspense fallback={null}>
      <PessoasFisicas />
    </Suspense>
  );
}

function PessoasFisicas() {
  const params = useSearchParams();
  const [busca, setBusca] = useState(() => params.get("q") ?? "");
  const [filtro, setFiltro] = useState<Filtro>(() => lerFiltro(params.get("situacao")));
  const [ordem, setOrdem] = useState<Ordem>(() => lerOrdem(params.get("ordem")));
  const [pagina, setPagina] = useState(() => Math.max(0, Number(params.get("pagina") ?? "1") - 1) || 0);
  const [selecionado, setSelecionado] = useState<string | null>(null);
  const [consultando, setConsultando] = useState(false);
  const [hoje] = useState(() => new Date());

  const { data, isLoading, isError, refetch, isFetching } = usePessoasResumo();

  // A URL espelha os filtros: voltar da análise completa devolve a lista como estava.
  useEffect(() => {
    const p = new URLSearchParams();
    if (busca.trim()) p.set("q", busca.trim());
    if (filtro !== "TODOS") p.set("situacao", filtro.toLowerCase());
    if (ordem !== "recentes") p.set("ordem", ordem);
    if (pagina > 0) p.set("pagina", String(pagina + 1));
    const qs = p.toString();
    window.history.replaceState(null, "", qs ? `?${qs}` : window.location.pathname);
  }, [busca, filtro, ordem, pagina]);

  const pessoas = useMemo<PessoaResumoComData[]>(
    () => (data ?? []).map(p => ({ ...p, dias: diasDesde(p.consultaEm, hoje), chave: normalizar(p.nome ?? "") })),
    [data, hoje],
  );

  const totais = useMemo(() => {
    const t = { LIMPO: 0, COM_RESTRICAO: 0, CRITICO: 0 };
    pessoas.forEach(p => t[p.situacao]++);
    return t;
  }, [pessoas]);

  const porBusca = useMemo(() => {
    const termo = normalizar(busca.trim());
    const digitos = busca.replace(/\D/g, "");
    if (!termo) return pessoas;
    return pessoas.filter(p => p.chave.includes(termo) || (digitos.length >= 3 && p.cpf.includes(digitos)));
  }, [pessoas, busca]);

  const contagens = useMemo(() => {
    const c: Record<Filtro, number> = { TODOS: porBusca.length, LIMPO: 0, COM_RESTRICAO: 0, CRITICO: 0 };
    porBusca.forEach(p => c[p.situacao]++);
    return c;
  }, [porBusca]);

  const lista = useMemo(
    () => (filtro === "TODOS" ? [...porBusca] : porBusca.filter(p => p.situacao === filtro)).sort(ORDENS[ordem].comparar),
    [porBusca, filtro, ordem],
  );

  const totalPaginas = Math.max(1, Math.ceil(lista.length / POR_PAGINA));
  const paginaAtual = Math.min(pagina, totalPaginas - 1);
  const visiveis = lista.slice(paginaAtual * POR_PAGINA, paginaAtual * POR_PAGINA + POR_PAGINA);
  const pessoaAberta = selecionado ? pessoas.find(p => p.cpf === selecionado) : undefined;
  const fecharGaveta = useCallback(() => setSelecionado(null), []);

  const total = pessoas.length;
  const comRestricao = totais.COM_RESTRICAO + totais.CRITICO;
  const fracao = (n: number) => (total ? n / total : 0);
  const pct = (n: number) => `${Math.round(fracao(n) * 100)}%`;

  const limparFiltros = () => {
    setBusca("");
    setFiltro("TODOS");
    setPagina(0);
  };

  return (
    <div className="mx-auto flex max-w-[1180px] flex-col gap-6 pb-10">
      <PageHeader
        rotulo="Gestão de Carteira"
        titulo="Pessoas Físicas"
        resumo={
          isLoading ? (
            "Carregando consultas…"
          ) : (
            <>
              <b className="text-grafite">{total.toLocaleString("pt-BR")}</b> CPFs consultados no Serasa ·{" "}
              <b className="text-grafite">{comRestricao.toLocaleString("pt-BR")}</b> com alguma restrição
            </>
          )
        }
        acoes={
          <button type="button" onClick={() => setConsultando(c => !c)} className={BOTAO.primario} aria-expanded={consultando}>
            <Icon name="add" />
            Nova consulta PF
          </button>
        }
      />

      {consultando && <NovaConsultaPF onFechar={() => setConsultando(false)} />}

      <section className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          icone="person_search"
          cor="#612035"
          rotulo="Consultados"
          valor={total}
          atraso={60}
          rodape={
            <>
              <b className="text-grafite">{pessoas.filter(p => p.dias < 30).length}</b> nos últimos 30 dias
            </>
          }
        >
          <MiniBarras valores={consultasPorMes(pessoas, hoje)} rotulo="Consultas por mês nos últimos 12 meses" />
        </KpiCard>
        <KpiCard icone="check_circle" cor={SITUACAO.LIMPO.cor} rotulo="Sem restrição" valor={totais.LIMPO} atraso={120} rodape={`${pct(totais.LIMPO)} da base`}>
          <Medidor fracao={fracao(totais.LIMPO)} cor={SITUACAO.LIMPO.cor} />
        </KpiCard>
        <KpiCard icone="warning" cor="#D1732C" rotulo="Com restrição" valor={totais.COM_RESTRICAO} atraso={180} rodape="1 ou 2 ocorrências">
          <Medidor fracao={fracao(totais.COM_RESTRICAO)} cor="#D1732C" />
        </KpiCard>
        <KpiCard
          icone="warning"
          cor={SITUACAO.CRITICO.cor}
          rotulo="Críticos"
          valor={totais.CRITICO}
          atraso={240}
          rodape="3+ ocorrências ou dívida acima de R$ 50 mil"
        >
          <Medidor fracao={fracao(totais.CRITICO)} cor={SITUACAO.CRITICO.cor} />
        </KpiCard>
      </section>

      <section className="flex flex-wrap items-center gap-3 animate-subir motion-reduce:animate-none" style={{ animationDelay: "280ms" }}>
        <label className="relative min-w-0 flex-1 basis-[280px]">
          <span className="sr-only">Buscar por nome ou CPF</span>
          <Icon name="search" size={20} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#8A877E]" />
          <input
            type="search"
            value={busca}
            onChange={e => {
              setBusca(e.target.value);
              setPagina(0);
            }}
            placeholder="Buscar por nome ou CPF"
            className="h-[46px] w-full rounded-[13px] border border-linha bg-white pl-11 pr-3.5 text-[14.5px] font-medium text-grafite outline-none transition-[border-color,box-shadow] placeholder:text-[#9A978E] focus:border-primary focus:ring-4 focus:ring-primary/10"
          />
        </label>
        <Segmentos
          rotulo="Filtrar por situação"
          valor={filtro}
          onChange={f => {
            setFiltro(f);
            setPagina(0);
          }}
          opcoes={[
            { valor: "TODOS", rotulo: "Todos", contagem: contagens.TODOS },
            { valor: "LIMPO", rotulo: "Limpos", contagem: contagens.LIMPO, cor: SITUACAO.LIMPO.cor },
            { valor: "COM_RESTRICAO", rotulo: "Com restrição", contagem: contagens.COM_RESTRICAO, cor: "#D1732C" },
            { valor: "CRITICO", rotulo: "Críticos", contagem: contagens.CRITICO, cor: SITUACAO.CRITICO.cor },
          ]}
        />
        <label className="shrink-0">
          <span className="sr-only">Ordenar</span>
          <select
            value={ordem}
            onChange={e => {
              setOrdem(e.target.value as Ordem);
              setPagina(0);
            }}
            className="h-[46px] rounded-[13px] border border-linha bg-white px-3 text-[13.5px] font-semibold text-grafite outline-none focus:border-primary"
          >
            {(Object.keys(ORDENS) as Ordem[]).map(o => (
              <option key={o} value={o}>
                {ORDENS[o].rotulo}
              </option>
            ))}
          </select>
        </label>
      </section>

      <section
        className="overflow-hidden rounded-painel border border-linha bg-white animate-subir motion-reduce:animate-none"
        style={{ animationDelay: "340ms" }}
        aria-busy={isLoading}
      >
        <CabecalhoPessoas />

        {isLoading ? (
          <LinhasEsqueleto />
        ) : isError ? (
          <EstadoVazio
            icone="error_outline"
            titulo="Não foi possível carregar a lista"
            descricao="O servidor não respondeu agora. O resto do portal continua funcionando."
            acoes={
              <button type="button" onClick={() => refetch()} disabled={isFetching} className={BOTAO.primario}>
                {isFetching ? "Tentando…" : "Tentar de novo"}
              </button>
            }
          />
        ) : visiveis.length === 0 ? (
          total === 0 ? (
            <EstadoVazio
              icone="person_search"
              titulo="Nenhuma pessoa consultada ainda"
              descricao="Consulte um CPF aqui ou a partir dos sócios na página de uma empresa."
              acoes={
                <button type="button" onClick={() => setConsultando(true)} className={BOTAO.primario}>
                  Nova consulta PF
                </button>
              }
            />
          ) : (
            <EstadoVazio
              icone="search_off"
              titulo={busca.trim() ? "Ninguém com esse nome ou CPF" : "Ninguém nesta situação"}
              descricao="Confira a digitação, troque o filtro ou consulte o CPF agora no Serasa."
              acoes={
                <>
                  <button type="button" onClick={limparFiltros} className={BOTAO.fantasma}>
                    Limpar filtros
                  </button>
                  <button type="button" onClick={() => setConsultando(true)} className={BOTAO.primario}>
                    Nova consulta PF
                  </button>
                </>
              }
            />
          )
        ) : (
          // A chave refaz a cascata de entrada ao trocar filtro, ordem ou página — não a cada tecla da busca.
          <div key={`${filtro}-${ordem}-${paginaAtual}`}>
            {visiveis.map((p, i) => (
              <LinhaPessoa key={p.cpf} pessoa={p} atraso={i * 28} onAbrir={() => setSelecionado(p.cpf)} />
            ))}
          </div>
        )}

        {!isLoading && !isError && lista.length > 0 && (
          <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-[#F0EEE8] bg-[#FBFAF7] px-5 py-3 text-[13px] text-suave sm:px-6">
            <span>
              Mostrando{" "}
              <b className="text-grafite">
                {paginaAtual * POR_PAGINA + 1}–{Math.min(lista.length, (paginaAtual + 1) * POR_PAGINA)}
              </b>{" "}
              de <b className="text-grafite">{lista.length.toLocaleString("pt-BR")}</b>
            </span>
            <Paginacao pagina={paginaAtual} totalPaginas={totalPaginas} onIr={setPagina} />
          </footer>
        )}
      </section>

      {pessoaAberta && <GavetaPessoa key={pessoaAberta.cpf} pessoa={pessoaAberta} onFechar={fecharGaveta} />}
    </div>
  );
}
