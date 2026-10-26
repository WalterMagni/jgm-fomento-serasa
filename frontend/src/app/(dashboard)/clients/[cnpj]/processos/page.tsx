"use client";

import { useMemo, useRef, useState } from "react";
import { flushSync } from "react-dom";
import Link from "next/link";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import Icon from "@/components/ui/Icon";
import Paginacao from "@/components/ui/Paginacao";
import Segmentos from "@/components/ui/Segmentos";
import DetalheProcessoGaveta from "@/components/judit/DetalheProcessoGaveta";
import { BOTAO } from "@/components/ui/botao";
import { exportarProcessosJudit, useJudit, useJuditProcessos } from "@/hooks/useJudit";
import {
  CabecalhoTabela,
  Carimbo,
  LinhaProcesso,
  ListaContagem,
  SelosJudit,
  TotaisDaLista,
  VERDE,
  normalizar,
  ordenar,
  type CampoOrdem,
  type Ordem,
} from "@/components/judit/JuditPecas";
import type { JuditProcesso, JuditSituacaoConsulta } from "@/types/judit";

const POR_PAGINA = 50;

type FiltroPolo = "TODOS" | "REU" | "AUTOR" | "OUTROS";

function formatarCnpj(d: string) {
  return d.length === 14 ? `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}` : d;
}

function casaPolo(p: JuditProcesso, filtro: FiltroPolo) {
  if (filtro === "TODOS") return true;
  if (filtro === "OUTROS") return p.polo !== "REU" && p.polo !== "AUTOR";
  return p.polo === filtro;
}

/** Foro: tribunal, órgão julgador (vara, câmara), cidade e UF. */
function casaForo(p: JuditProcesso, termo: string) {
  if (!termo) return true;
  return normalizar([p.tribunal, p.cidade, p.uf, ...p.orgaos].filter(Boolean).join(" ")).includes(termo);
}

/** Tipo: classe, assuntos e área — "execução", "duplicata", "cédula de crédito". */
function casaTipo(p: JuditProcesso, termo: string) {
  if (!termo) return true;
  return normalizar([p.classe, p.area, ...p.assuntos].filter(Boolean).join(" ")).includes(termo);
}

/** A ordem que o leitor já entrega: distribuição mais recente primeiro. */
const ORDEM_PADRAO: Ordem = { campo: "DISTRIBUICAO", decrescente: true };

/** Contraparte: todos os nomes do outro polo e a autuação ("BANCO X versus EMPRESA"). */
function casaContraparte(p: JuditProcesso, termo: string) {
  if (!termo) return true;
  return normalizar([p.autuacao, ...p.contrapartes].filter(Boolean).join(" ")).includes(termo);
}

/**
 * Lista completa dos processos da Judit de uma empresa. Lê a última busca guardada — nada aqui
 * chama a Judit nem cobra. Filtros de polo, tipo, foro e contraparte, e ordem por distribuição,
 * valor ou último andamento; a planilha e a impressão levam o que está na tela, na mesma ordem.
 */
export default function ProcessosJuditPage() {
  const params = useParams<{ cnpj: string }>();
  const cnpj = (params.cnpj ?? "").replace(/\D/g, "");
  const parametros = useSearchParams();
  const nome = parametros.get("nome");
  // Processo aberto na gaveta. Vai na URL: a notificação da Judit traz ?processo=<cnj>.
  const processoAberto = parametros.get("processo");
  const router = useRouter();
  const caminho = usePathname();
  const { data: consulta, isLoading, isError, error } = useJuditProcessos(cnpj);
  const { data: situacao } = useJudit(cnpj);

  const [polo, setPolo] = useState<FiltroPolo | null>(null);
  const [foro, setForo] = useState("");
  const [contraparte, setContraparte] = useState("");
  const [tipo, setTipo] = useState("");
  const [ordem, setOrdem] = useState<Ordem>(ORDEM_PADRAO);
  const filtros = useRef<HTMLDivElement>(null);
  const [pagina, setPagina] = useState(0);
  const [imprimindo, setImprimindo] = useState(false);
  const [exportando, setExportando] = useState(false);

  // Empresa acima de 1.000 só tem a lista de ré; abre já nesse filtro.
  const poloAtual: FiltroPolo = polo ?? (consulta?.somenteReu ? "REU" : "TODOS");
  const termoForo = normalizar(foro.trim());
  const termoContraparte = normalizar(contraparte.trim());
  const termoTipo = normalizar(tipo.trim());

  // Os textos filtram primeiro; as contagens do polo saem daí, para cada botão dizer quantos sobram.
  const porTexto = useMemo(
    () =>
      (consulta?.dados.lista ?? []).filter(
        p => casaTipo(p, termoTipo) && casaForo(p, termoForo) && casaContraparte(p, termoContraparte),
      ),
    [consulta, termoTipo, termoForo, termoContraparte],
  );
  const filtrados = useMemo(
    () => ordenar(porTexto.filter(p => casaPolo(p, poloAtual)), ordem),
    [porTexto, poloAtual, ordem],
  );
  const contagem = (filtro: FiltroPolo) => porTexto.filter(p => casaPolo(p, filtro)).length;

  const totalPaginas = Math.ceil(filtrados.length / POR_PAGINA);
  const paginaAtual = Math.min(pagina, Math.max(0, totalPaginas - 1));
  const visiveis = imprimindo ? filtrados : filtrados.slice(paginaAtual * POR_PAGINA, (paginaAtual + 1) * POR_PAGINA);
  const filtrando = poloAtual !== "TODOS" || termoTipo !== "" || termoForo !== "" || termoContraparte !== "";
  const ordemPadrao = ordem.campo === ORDEM_PADRAO.campo && ordem.decrescente === ORDEM_PADRAO.decrescente;

  function abrirDetalhe(cnj: string | null) {
    const novos = new URLSearchParams(parametros.toString());
    if (cnj) novos.set("processo", cnj);
    else novos.delete("processo");
    const consulta = novos.toString();
    router.replace(consulta ? `${caminho}?${consulta}` : caminho, { scroll: false });
  }

  function mudar<T>(setter: (valor: T) => void) {
    return (valor: T) => {
      setter(valor);
      setPagina(0);
    };
  }

  function limpar() {
    setPolo("TODOS");
    setTipo("");
    setForo("");
    setContraparte("");
    setPagina(0);
  }

  /** Clique num assunto, classe ou tribunal dos totais: preenche o filtro e leva até a lista. */
  function filtrarPor(setter: (valor: string) => void) {
    return (valor: string) => {
      setter(valor);
      setPagina(0);
      filtros.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    };
  }

  /** Mesma coluna inverte o sentido; coluna nova começa do maior (valor) ou do mais recente (datas). */
  function ordenarPor(campo: CampoOrdem) {
    setOrdem(atual => (atual.campo === campo ? { campo, decrescente: !atual.decrescente } : { campo, decrescente: true }));
    setPagina(0);
  }

  /** Imprime todos os filtrados, não só a página: renderiza tudo antes de abrir o diálogo. */
  function imprimir() {
    flushSync(() => setImprimindo(true));
    window.print();
    setImprimindo(false);
  }

  async function exportar() {
    setExportando(true);
    try {
      // Lista inteira na ordem de sempre: não precisa mandar os números. Senão, vão na ordem da tela.
      const cnjs = filtrando || !ordemPadrao ? filtrados.map(p => p.cnj).filter((c): c is string => Boolean(c)) : [];
      await exportarProcessosJudit(cnpj, cnjs);
    } finally {
      setExportando(false);
    }
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
          Os processos desta empresa ainda não foram consultados — ou a consulta ainda está nos tribunais. Consulte pelo card
          Processos judiciais na Visão 360º; o sino avisa quando ficar pronto.
        </p>
      </div>
    );
  }

  const resumo = situacao?.resumo ?? null;

  return (
    <div className="space-y-5">
      <header className="space-y-3">
        {voltar}
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0">
            <p className="flex flex-wrap items-center gap-2 text-xs font-bold uppercase tracking-[0.14em] text-[#047857] dark:text-[#15BE62]">
              <Icon name="gavel" size={14} /> Processos judiciais <SelosJudit />
            </p>
            <h1 className="mt-2 text-3xl font-semibold leading-tight tracking-[-0.02em] text-grafite dark:text-white">{titulo}</h1>
            <p className="mt-1 text-sm text-suave">
              {nome && `${formatarCnpj(cnpj)} · `}
              {consulta.onDemand ? "On Demand: base histórica + tribunais" : "base histórica da Judit (Data Lake)"} ·{" "}
              <Carimbo
                rotulo={consulta.onDemand ? "consultado" : "lista consultada"}
                {...consulta}
                validadeDias={situacao?.validadeDias ?? 30}
              />
            </p>
          </div>
          <div className="flex flex-wrap gap-2 print:hidden">
            <button type="button" className={BOTAO.fantasma} onClick={imprimir}>
              <Icon name="picture_as_pdf" size={16} /> Imprimir / PDF
            </button>
            <button type="button" className={BOTAO.judit} onClick={exportar} disabled={exportando || filtrados.length === 0}>
              <Icon name={exportando ? "progress_activity" : "table_chart"} size={16} className={exportando ? "animate-spin" : ""} />
              {filtrando ? `Exportar ${filtrados.length} filtrados` : "Exportar planilha"}
            </button>
          </div>
        </div>
      </header>

      {resumo && resumo.dados.total > 0 && (
        <section className="break-inside-avoid rounded-xl border border-border-light bg-surface-light p-5 dark:border-border-dark dark:bg-surface-dark">
          <h2 className="text-sm font-bold text-[#047857] dark:text-[#15BE62]">Distribuição pela pré-consulta da Judit</h2>
          <p className="mb-4 text-[11px] text-gray-500">
            {resumo.dados.total} registros — a Judit conta cada instância, por isso os números são maiores que os da lista.
            Clique num item para filtrar a lista.
          </p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <ListaContagem titulo="Assuntos" itens={resumo.dados.assuntos} onEscolher={filtrarPor(setTipo)} />
            <ListaContagem titulo="Classes" itens={resumo.dados.classes} onEscolher={filtrarPor(setTipo)} />
            <ListaContagem titulo="Tribunais" itens={resumo.dados.tribunais} onEscolher={filtrarPor(setForo)} />
            <ListaContagem titulo="Áreas" itens={resumo.dados.areas} onEscolher={filtrarPor(setTipo)} />
          </div>
        </section>
      )}

      <section className="overflow-hidden rounded-xl border border-[#15BE62]/50 bg-surface-light shadow-sm dark:border-[#15BE62]/40 dark:bg-surface-dark">
        <div className="h-1 print:hidden" style={{ backgroundColor: VERDE }} aria-hidden />
        <div className="p-5">
          <TotaisDaLista dados={consulta.dados} totalNoResumo={consulta.totalNoResumo} onAssunto={filtrarPor(setTipo)} />
          {consulta.somenteReu && (
            <p className="mt-3 text-[11px] text-gray-500">
              {consulta.onDemand
                ? "Só os processos em que a empresa é ré. Para ver também como autora, use “Buscar também como autora” no card da Visão 360º."
                : "A empresa passa de 1.000 registros na Judit, que corta nesse número; a busca pediu só os processos em que ela é ré."}
            </p>
          )}
        </div>
      </section>

      <section className="space-y-3">
        <div ref={filtros} className="flex scroll-mt-20 flex-wrap items-center gap-3 print:hidden">
          <Segmentos<FiltroPolo>
            rotulo="Filtrar processos pelo polo da empresa"
            valor={poloAtual}
            onChange={mudar(setPolo)}
            opcoes={[
              { valor: "TODOS", rotulo: "Todos", contagem: porTexto.length },
              { valor: "REU", rotulo: "Ré", contagem: contagem("REU") },
              { valor: "AUTOR", rotulo: "Autora", contagem: contagem("AUTOR") },
              ...(contagem("OUTROS") > 0 ? [{ valor: "OUTROS" as const, rotulo: "Outros", contagem: contagem("OUTROS") }] : []),
            ]}
          />
          <CampoBusca
            rotulo="Tipo"
            valor={tipo}
            onChange={mudar(setTipo)}
            placeholder="Tipo: assunto ou classe"
          />
          <CampoBusca
            rotulo="Foro"
            valor={foro}
            onChange={mudar(setForo)}
            placeholder="Foro: tribunal, vara, cidade ou UF"
          />
          <CampoBusca
            rotulo="Contraparte"
            valor={contraparte}
            onChange={mudar(setContraparte)}
            placeholder="Contraparte: nome"
          />
          {filtrando && (
            <button type="button" onClick={limpar} className="text-xs font-semibold text-primary hover:underline">
              Limpar filtros
            </button>
          )}
          <span className="ml-auto text-xs text-gray-500">
            {filtrados.length === consulta.dados.quantidade
              ? `${filtrados.length} processos`
              : `${filtrados.length} de ${consulta.dados.quantidade} processos`}
          </span>
        </div>

        {/* Na impressão, os filtros viram uma linha de texto. */}
        {filtrando && (
          <p className="hidden text-xs text-gray-600 print:block">
            Filtros: polo {poloAtual === "TODOS" ? "todos" : poloAtual.toLowerCase()}
            {tipo && ` · tipo "${tipo}"`}
            {foro && ` · foro "${foro}"`}
            {contraparte && ` · contraparte "${contraparte}"`} — {filtrados.length} de {consulta.dados.quantidade} processos
          </p>
        )}

        {filtrados.length === 0 ? (
          <p className="rounded-lg border border-border-light px-4 py-6 text-center text-sm text-gray-500 dark:border-border-dark">
            Nenhum processo com esses filtros.{" "}
            <button type="button" onClick={limpar} className="font-semibold text-primary hover:underline">
              Limpar filtros
            </button>
          </p>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-border-light bg-surface-light dark:border-border-dark dark:bg-surface-dark">
            <table className="w-full text-xs">
              <CabecalhoTabela ordem={ordem} onOrdenar={ordenarPor} comAcao />
              <tbody className="divide-y divide-[#F0EEE8] dark:divide-white/10">
                {visiveis.map((p, i) => (
                  <LinhaProcesso
                    key={p.cnj ?? `sem-numero-${i}`}
                    processo={p}
                    acao={p.cnj ? <BotaoDetalhe situacao={consulta.detalhes[p.cnj]} onAbrir={() => abrirDetalhe(p.cnj)} /> : null}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}

        {!imprimindo && (
          <div className="print:hidden">
            <Paginacao pagina={paginaAtual} totalPaginas={totalPaginas} onIr={setPagina} />
          </div>
        )}
      </section>


      {processoAberto && (() => {
        const processo = consulta.dados.lista.find(p => p.cnj === processoAberto);
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

function CampoBusca({
  rotulo,
  valor,
  onChange,
  placeholder,
}: {
  rotulo: string;
  valor: string;
  onChange: (valor: string) => void;
  placeholder: string;
}) {
  return (
    <label className="relative block min-w-[220px] flex-1 sm:max-w-xs">
      <span className="sr-only">{rotulo}</span>
      <Icon name="search" size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#8A877E]" />
      <input
        type="search"
        value={valor}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        className="h-10 w-full rounded-[11px] border border-linha bg-white pl-9 pr-3 text-[13px] font-medium text-grafite outline-none transition-[border-color,box-shadow] placeholder:text-[#9A978E] focus:border-[#15BE62] focus:ring-4 focus:ring-[#15BE62]/15 dark:bg-transparent dark:text-white"
      />
    </label>
  );
}

/** O botão de cada linha: diz se o processo já foi detalhado. Abrir não cobra — a gaveta pede confirmação. */
function BotaoDetalhe({ situacao, onAbrir }: { situacao: JuditSituacaoConsulta | undefined; onAbrir: () => void }) {
  const base = "inline-flex h-8 items-center gap-1.5 rounded-lg px-2.5 text-[11px] font-semibold transition-colors";
  if (situacao === "CONCLUIDA") {
    return (
      <button type="button" onClick={onAbrir} className={`${base} bg-[#15BE62]/15 text-[#047857] hover:bg-[#15BE62]/25 dark:text-[#15BE62]`}>
        <Icon name="auto_awesome" size={13} /> Ver detalhe
      </button>
    );
  }
  if (situacao === "PENDENTE") {
    return (
      <button type="button" onClick={onAbrir} className={`${base} border border-[#C6F1D6] text-[#047857] dark:text-[#15BE62]`}>
        <Icon name="progress_activity" size={13} className="animate-spin" /> Detalhando…
      </button>
    );
  }
  if (situacao === "FALHOU") {
    return (
      <button type="button" onClick={onAbrir} className={`${base} border border-amber-200 bg-amber-50 text-amber-800`}>
        <Icon name="warning" size={13} /> Falhou
      </button>
    );
  }
  return (
    <button
      type="button"
      onClick={onAbrir}
      title="Status, fase, todas as movimentações e resumo da IA, direto do tribunal"
      className={`${base} border border-linha text-gray-500 hover:border-[#15BE62] hover:text-[#047857]`}
    >
      <Icon name="gavel" size={13} /> Detalhar
    </button>
  );
}
