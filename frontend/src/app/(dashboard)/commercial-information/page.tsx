"use client";

import { Suspense, useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import Icon from "@/components/ui/Icon";
import PageHeader from "@/components/ui/PageHeader";
import KpiCard, { Medidor, MiniBarras } from "@/components/ui/KpiCard";
import Segmentos from "@/components/ui/Segmentos";
import EstadoVazio from "@/components/ui/EstadoVazio";
import Paginacao from "@/components/ui/Paginacao";
import { BOTAO } from "@/components/ui/botao";
import LinhaFicha, { CabecalhoFichas, LinhasEsqueletoFichas, type FichaNaLista } from "@/components/comercial/LinhaFicha";
import GavetaFicha from "@/components/comercial/GavetaFicha";
import TabelaFichas from "@/components/comercial/TabelaFichas";
import { diasDesde, formatCnpj, formatMoeda, normalizar } from "@/lib/formatadores";
import { prepararFicha, type FichaComercialApi } from "@/types/informacao-comercial";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
const POR_PAGINA = 20;

type Ordem = "recentes" | "risco" | "vencido" | "nome";
type Visao = "fichas" | "tabela";

const ORDENS: Record<Ordem, { rotulo: string; comparar: (a: FichaNaLista, b: FichaNaLista) => number }> = {
  recentes: { rotulo: "Mais recentes", comparar: (a, b) => b.dataT - a.dataT || (b.createdAt ?? "").localeCompare(a.createdAt ?? "") },
  risco: { rotulo: "Maior risco", comparar: (a, b) => b.riscoN - a.riscoN },
  vencido: { rotulo: "Maior vencido", comparar: (a, b) => b.vencidoN - a.vencidoN || b.dataT - a.dataT },
  nome: { rotulo: "Empresa A–Z", comparar: (a, b) => (a.empresa ?? "").localeCompare(b.empresa ?? "", "pt-BR") },
};

function lerOrdem(valor: string | null): Ordem {
  return valor && valor in ORDENS ? (valor as Ordem) : "recentes";
}

/** "2026-05-08" do campo de data → início do dia local. */
function dataDoFiltro(valor: string) {
  if (!valor) return 0;
  const [ano, mes, dia] = valor.split("-").map(Number);
  return new Date(ano, mes - 1, dia).getTime();
}

// Fichas ou tabela: preferência de cada pessoa, neste navegador. useSyncExternalStore evita
// que o servidor e o navegador desenhem visões diferentes na primeira pintura.
const CHAVE_VISAO = "jgm_comercial_visao";
const ouvintesVisao = new Set<() => void>();
function lerVisao(): Visao {
  try {
    return localStorage.getItem(CHAVE_VISAO) === "tabela" ? "tabela" : "fichas";
  } catch {
    return "fichas";
  }
}
function gravarVisao(visao: Visao) {
  try {
    localStorage.setItem(CHAVE_VISAO, visao);
  } catch {
    // Sem armazenamento (aba anônima, bloqueio): a escolha vale só até recarregar.
  }
  ouvintesVisao.forEach(avisar => avisar());
}
function useVisao(): [Visao, (v: Visao) => void] {
  const visao = useSyncExternalStore(
    avisar => {
      ouvintesVisao.add(avisar);
      return () => ouvintesVisao.delete(avisar);
    },
    lerVisao,
    () => "fichas" as Visao,
  );
  return [visao, gravarVisao];
}

function escapeHtml(value: string) {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function exportXls(records: FichaComercialApi[]) {
  const clean = (value: string | null | undefined) => (value && value.trim() ? value : "-");
  const headers = [
    "Cedente",
    "CNPJ",
    "Data",
    "Tipo",
    "Parceiro",
    "Desde",
    "Ultima operacao data",
    "Ultima operacao valor",
    "Risco duplicata",
    "Risco cheque",
    "Risco comissaria",
    "Ultimo vencimento duplicata",
    "Valor vencido",
    "Limite",
    "VOP",
    "L1 pontual",
    "L2 atraso",
    "L3 cartorio",
    "L4 recompra",
    "Observacao",
  ];

  const rows = records.map((record) => [
    clean(record.empresa),
    record.cnpj ? formatCnpj(record.cnpj) : "-",
    clean(record.data),
    clean(record.tipo),
    clean(record.parceiro),
    clean(record.clienteDesde),
    clean(record.ultimaOperacaoData),
    clean(record.ultimaOperacaoValor),
    clean(record.riscoDuplicata),
    clean(record.riscoCheque),
    clean(record.riscoComissaria),
    clean(record.vencidosValor),
    clean(record.vencidosValorMonetario),
    clean(record.limite),
    clean(record.vop),
    clean(record.pontual),
    clean(record.atraso),
    clean(record.cartorio),
    clean(record.recompra),
    clean(record.observacao),
  ]);

  const table = `
    <html>
      <head><meta charset="UTF-8" /></head>
      <body>
        <table border="1">
          <thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join("")}</tr></thead>
          <tbody>${rows.map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join("")}</tr>`).join("")}</tbody>
        </table>
      </body>
    </html>
  `;

  const blob = new Blob([table], { type: "application/vnd.ms-excel;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `informacoes-comerciais-carteira-${new Date().toISOString().slice(0, 10)}.xls`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function useCommercialInformation() {
  return useQuery<FichaComercialApi[]>({
    queryKey: ["allCommercialInformation"],
    queryFn: async () => {
      const token = localStorage.getItem("serasa_token");
      const response = await fetch(`${API_BASE_URL}/company/commercial-information`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!response.ok) throw new Error("Erro ao carregar informações comerciais");
      return response.json();
    },
    staleTime: 60 * 1000,
  });
}

/** Fichas por mês nos últimos 12 meses, o mês atual por último. */
function fichasPorMes(fichas: FichaNaLista[], hoje: Date) {
  const meses = new Array(12).fill(0);
  const atual = hoje.getFullYear() * 12 + hoje.getMonth();
  for (const f of fichas) {
    if (!f.dataT) continue;
    const d = new Date(f.dataT);
    const distancia = atual - (d.getFullYear() * 12 + d.getMonth());
    if (distancia >= 0 && distancia < 12) meses[11 - distancia]++;
  }
  return meses;
}

export default function InformacoesComerciaisPage() {
  // useSearchParams exige Suspense no App Router.
  return (
    <Suspense fallback={null}>
      <InformacoesComerciais />
    </Suspense>
  );
}

function InformacoesComerciais() {
  const params = useSearchParams();
  const [busca, setBusca] = useState(() => params.get("q") ?? "");
  const [parceiro, setParceiro] = useState(() => params.get("parceiro") ?? "");
  const [tipo, setTipo] = useState(() => params.get("tipo") ?? "");
  const [de, setDe] = useState(() => params.get("de") ?? "");
  const [ate, setAte] = useState(() => params.get("ate") ?? "");
  const [soVencido, setSoVencido] = useState(() => params.get("vencido") === "1");
  const [ordem, setOrdem] = useState<Ordem>(() => lerOrdem(params.get("ordem")));
  const [pagina, setPagina] = useState(() => Math.max(0, Number(params.get("pagina") ?? "1") - 1) || 0);
  const [selecionada, setSelecionada] = useState<string | null>(null);
  const [visao, setVisao] = useVisao();
  const [hoje] = useState(() => new Date());

  const { data, isLoading, isError, refetch, isFetching } = useCommercialInformation();

  // A URL espelha os filtros: voltar da Visão 360º devolve a lista como estava.
  useEffect(() => {
    const p = new URLSearchParams();
    if (busca.trim()) p.set("q", busca.trim());
    if (parceiro) p.set("parceiro", parceiro);
    if (tipo) p.set("tipo", tipo);
    if (de) p.set("de", de);
    if (ate) p.set("ate", ate);
    if (soVencido) p.set("vencido", "1");
    if (ordem !== "recentes") p.set("ordem", ordem);
    if (pagina > 0) p.set("pagina", String(pagina + 1));
    const qs = p.toString();
    window.history.replaceState(null, "", qs ? `?${qs}` : window.location.pathname);
  }, [busca, parceiro, tipo, de, ate, soVencido, ordem, pagina]);

  const fichas = useMemo<FichaNaLista[]>(
    () =>
      (data ?? []).map(f => {
        const ficha = prepararFicha(f, normalizar);
        return { ...ficha, dias: ficha.dataT ? diasDesde(new Date(ficha.dataT).toISOString(), hoje) : null };
      }),
    [data, hoje],
  );

  const resumo = useMemo(() => {
    const porEmpresa = new Map<string, Set<string>>();
    const porParceiro = new Map<string, number>();
    let comVencido = 0;
    let totalVencido = 0;
    for (const f of fichas) {
      const empresa = f.cnpj || f.empresa || f.id;
      if (!porEmpresa.has(empresa)) porEmpresa.set(empresa, new Set());
      if (f.parceiro) {
        porEmpresa.get(empresa)!.add(f.parceiro);
        porParceiro.set(f.parceiro, (porParceiro.get(f.parceiro) ?? 0) + 1);
      }
      if (f.vencidoN > 0) {
        comVencido++;
        totalVencido += f.vencidoN;
      }
    }
    const [topo] = [...porParceiro.entries()].sort((a, b) => b[1] - a[1]);
    return {
      empresas: porEmpresa.size,
      variosParceiros: [...porEmpresa.values()].filter(p => p.size > 1).length,
      parceiros: [...porParceiro.keys()].sort((a, b) => a.localeCompare(b, "pt-BR")),
      topo,
      comVencido,
      totalVencido,
    };
  }, [fichas]);

  const tipos = useMemo(
    () => [...new Set(fichas.map(f => f.tipo).filter(Boolean) as string[])].sort((a, b) => a.localeCompare(b, "pt-BR")),
    [fichas],
  );

  const lista = useMemo(() => {
    const termo = normalizar(busca.trim());
    const digitos = busca.replace(/\D/g, "");
    const desde = dataDoFiltro(de);
    const ateDia = dataDoFiltro(ate);
    return fichas
      .filter(
        f =>
          (!termo || f.chave.includes(termo) || (digitos.length >= 3 && (f.cnpj ?? "").includes(digitos))) &&
          (!parceiro || f.parceiro === parceiro) &&
          (!tipo || f.tipo === tipo) &&
          (!desde || (f.dataT && f.dataT >= desde)) &&
          (!ateDia || (f.dataT && f.dataT <= ateDia)) &&
          (!soVencido || f.vencidoN > 0),
      )
      .sort(ORDENS[ordem].comparar);
  }, [fichas, busca, parceiro, tipo, de, ate, soVencido, ordem]);

  const totalPaginas = Math.max(1, Math.ceil(lista.length / POR_PAGINA));
  const paginaAtual = Math.min(pagina, totalPaginas - 1);
  const visiveis = lista.slice(paginaAtual * POR_PAGINA, paginaAtual * POR_PAGINA + POR_PAGINA);
  const aberta = selecionada ? fichas.find(f => f.id === selecionada) : undefined;
  const outras = aberta?.cnpj
    ? fichas.filter(f => f.cnpj === aberta.cnpj && f.id !== aberta.id).sort(ORDENS.recentes.comparar)
    : [];
  const fecharGaveta = useCallback(() => setSelecionada(null), []);

  const total = fichas.length;
  const algumFiltro = !!(busca.trim() || parceiro || tipo || de || ate || soVencido);
  const mudar = <T,>(definir: (v: T) => void) => (v: T) => {
    definir(v);
    setPagina(0);
  };
  const limparFiltros = () => {
    setBusca("");
    setParceiro("");
    setTipo("");
    setDe("");
    setAte("");
    setSoVencido(false);
    setPagina(0);
  };

  const CAMPO =
    "h-[46px] rounded-[13px] border border-linha bg-white px-3 text-[13.5px] font-semibold text-grafite outline-none transition-[border-color,box-shadow] focus:border-primary focus:ring-4 focus:ring-primary/10";

  return (
    <div className="flex flex-col gap-6 pb-10">
      <PageHeader
        rotulo="Gestão de Carteira"
        titulo="Informações Comerciais"
        resumo={
          isLoading ? (
            "Carregando fichas…"
          ) : (
            <>
              <b className="text-grafite">{total.toLocaleString("pt-BR")}</b> {total === 1 ? "ficha" : "fichas"} de{" "}
              <b className="text-grafite">{resumo.empresas.toLocaleString("pt-BR")}</b> {resumo.empresas === 1 ? "empresa" : "empresas"},
              informadas por <b className="text-grafite">{resumo.parceiros.length.toLocaleString("pt-BR")}</b>{" "}
              {resumo.parceiros.length === 1 ? "parceiro" : "parceiros"}
            </>
          )
        }
        acoes={
          <button
            type="button"
            onClick={() => exportXls(lista)}
            disabled={lista.length === 0}
            title={algumFiltro ? "Exporta só as fichas filtradas" : "Exporta todas as fichas"}
            className={BOTAO.fantasma}
          >
            <Icon name="download" />
            {algumFiltro ? `Exportar ${lista.length} em XLS` : "Exportar XLS"}
          </button>
        }
      />

      <section className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          icone="request_quote"
          cor="#612035"
          rotulo="Fichas"
          valor={total}
          atraso={60}
          rodape={
            <>
              <b className="text-grafite">{fichas.filter(f => f.dias != null && f.dias < 30).length}</b> nos últimos 30 dias
            </>
          }
        >
          <MiniBarras valores={fichasPorMes(fichas, hoje)} rotulo="Fichas por mês nos últimos 12 meses" />
        </KpiCard>
        <KpiCard
          icone="business"
          cor="#612035"
          rotulo="Empresas"
          valor={resumo.empresas}
          atraso={120}
          rodape={
            resumo.variosParceiros === 1
              ? "1 com ficha de mais de um parceiro"
              : `${resumo.variosParceiros} com fichas de mais de um parceiro`
          }
        >
          <Medidor fracao={resumo.empresas ? resumo.variosParceiros / resumo.empresas : 0} cor="#612035" />
        </KpiCard>
        <KpiCard
          icone="handshake"
          cor="#D1732C"
          rotulo="Parceiros"
          valor={resumo.parceiros.length}
          atraso={180}
          rodape={resumo.topo ? `Mais fichas: ${resumo.topo[0]} (${resumo.topo[1]})` : "Nenhum parceiro informado"}
        />
        <KpiCard
          icone="warning"
          cor="#B42345"
          rotulo="Com vencido"
          valor={resumo.comVencido}
          atraso={240}
          onClick={() => {
            setSoVencido(v => !v);
            setPagina(0);
          }}
          ativo={soVencido}
          rodape={resumo.comVencido > 0 ? `${formatMoeda(resumo.totalVencido)} vencidos nos parceiros` : "Nenhum vencido informado"}
        >
          <Medidor fracao={total ? resumo.comVencido / total : 0} cor="#B42345" />
        </KpiCard>
      </section>

      <section className="flex flex-wrap items-center gap-3 animate-subir motion-reduce:animate-none" style={{ animationDelay: "280ms" }}>
        <label className="relative min-w-0 flex-1 basis-[260px]">
          <span className="sr-only">Buscar por empresa ou CNPJ</span>
          <Icon name="search" size={20} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#8A877E]" />
          <input
            type="search"
            value={busca}
            onChange={e => mudar(setBusca)(e.target.value)}
            placeholder="Buscar por empresa ou CNPJ"
            className="h-[46px] w-full rounded-[13px] border border-linha bg-white pl-11 pr-3.5 text-[14.5px] font-medium text-grafite outline-none transition-[border-color,box-shadow] placeholder:text-[#9A978E] focus:border-primary focus:ring-4 focus:ring-primary/10"
          />
        </label>
        <label className="shrink-0">
          <span className="sr-only">Parceiro</span>
          <select value={parceiro} onChange={e => mudar(setParceiro)(e.target.value)} className={`${CAMPO} max-w-[220px]`}>
            <option value="">Todos os parceiros</option>
            {resumo.parceiros.map(p => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </label>
        {tipos.length > 1 && (
          <label className="shrink-0">
            <span className="sr-only">Tipo</span>
            <select value={tipo} onChange={e => mudar(setTipo)(e.target.value)} className={CAMPO}>
              <option value="">Todos os tipos</option>
              {tipos.map(t => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </label>
        )}
        <div className="flex h-[46px] shrink-0 items-center gap-1.5 rounded-[13px] border border-linha bg-white pl-3 pr-1.5 focus-within:border-primary focus-within:ring-4 focus-within:ring-primary/10">
          <Icon name="event" size={18} className="text-[#8A877E]" />
          <span className="sr-only">Período da ficha</span>
          <input
            type="date"
            aria-label="Fichas a partir de"
            value={de}
            max={ate || undefined}
            onChange={e => mudar(setDe)(e.target.value)}
            className="h-9 rounded-lg px-1.5 text-[13px] font-semibold text-grafite outline-none"
          />
          <span className="text-[#B5B1A6]">até</span>
          <input
            type="date"
            aria-label="Fichas até"
            value={ate}
            min={de || undefined}
            onChange={e => mudar(setAte)(e.target.value)}
            className="h-9 rounded-lg px-1.5 text-[13px] font-semibold text-grafite outline-none"
          />
        </div>
        <label className="shrink-0">
          <span className="sr-only">Ordenar</span>
          <select value={ordem} onChange={e => mudar(setOrdem)(e.target.value as Ordem)} className={CAMPO}>
            {(Object.keys(ORDENS) as Ordem[]).map(o => (
              <option key={o} value={o}>
                {ORDENS[o].rotulo}
              </option>
            ))}
          </select>
        </label>
        <Segmentos
          rotulo="Ver como"
          valor={visao}
          onChange={setVisao}
          opcoes={[
            { valor: "fichas", rotulo: "Fichas" },
            { valor: "tabela", rotulo: "Tabela" },
          ]}
        />
        {algumFiltro && (
          <button type="button" onClick={limparFiltros} className="h-[46px] shrink-0 px-2 text-[13px] font-bold text-primary hover:underline">
            Limpar filtros
          </button>
        )}
      </section>

      <section
        className="overflow-hidden rounded-painel border border-linha bg-white animate-subir motion-reduce:animate-none"
        style={{ animationDelay: "340ms" }}
        aria-busy={isLoading}
      >
        {visao === "fichas" && <CabecalhoFichas />}

        {isLoading ? (
          <LinhasEsqueletoFichas />
        ) : isError ? (
          <EstadoVazio
            icone="error_outline"
            titulo="Não foi possível carregar as fichas"
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
              icone="request_quote"
              titulo="Nenhuma ficha comercial ainda"
              descricao="As fichas são cadastradas na Visão 360º de cada empresa, na seção Informações comerciais."
            />
          ) : (
            <EstadoVazio
              icone="search_off"
              titulo="Nenhuma ficha com esses filtros"
              descricao="Confira a digitação, troque o parceiro ou amplie o período."
              acoes={
                <button type="button" onClick={limparFiltros} className={BOTAO.fantasma}>
                  Limpar filtros
                </button>
              }
            />
          )
        ) : visao === "tabela" ? (
          <TabelaFichas fichas={visiveis} onAbrir={setSelecionada} />
        ) : (
          // A chave refaz a cascata de entrada ao trocar filtro, ordem ou página — não a cada tecla da busca.
          <div key={`${parceiro}-${tipo}-${soVencido}-${ordem}-${paginaAtual}`}>
            {visiveis.map((f, i) => (
              <LinhaFicha key={f.id} ficha={f} atraso={i * 28} onAbrir={() => setSelecionada(f.id)} />
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

      {aberta && (
        <GavetaFicha key={aberta.id} ficha={aberta} outras={outras} onAbrirOutra={setSelecionada} onFechar={fecharGaveta} />
      )}
    </div>
  );
}
