"use client";

import { useMemo, useState } from "react";
import { flushSync } from "react-dom";
import Icon from "@/components/ui/Icon";
import Paginacao from "@/components/ui/Paginacao";
import Segmentos from "@/components/ui/Segmentos";
import { BOTAO } from "@/components/ui/botao";
import { exportarProcessosJudit } from "@/hooks/useJudit";
import {
  AVISO_SITUACAO,
  CabecalhoTabela,
  LinhaProcesso,
  normalizar,
  ordenar,
  type CampoOrdem,
  type Ordem,
} from "@/components/judit/JuditPecas";
import type { JuditProcesso, JuditSituacaoConsulta, JuditSituacaoProcesso } from "@/types/judit";

const POR_PAGINA = 50;

export type FiltroPolo = "TODOS" | "REU" | "AUTOR" | "OUTROS";
export type FiltroSituacao = "TODAS" | JuditSituacaoProcesso | "SEM_ANDAMENTO";

/** O que uma lupa do painel pede: polo, situação e área. Área `null` é a não informada. */
export type FiltroInicial = { polo?: FiltroPolo; situacao?: FiltroSituacao; area?: string | null };

function casaPolo(p: JuditProcesso, filtro: FiltroPolo) {
  if (filtro === "TODOS") return true;
  if (filtro === "OUTROS") return p.polo !== "REU" && p.polo !== "AUTOR";
  return p.polo === filtro;
}

function casaSituacao(p: JuditProcesso, filtro: FiltroSituacao) {
  if (filtro === "TODAS") return true;
  if (filtro === "SEM_ANDAMENTO") return p.situacao === null;
  return p.situacao === filtro;
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

/** Contraparte: todos os nomes do outro polo e a autuação ("BANCO X versus EMPRESA"). */
function casaContraparte(p: JuditProcesso, termo: string) {
  if (!termo) return true;
  return normalizar([p.autuacao, ...p.contrapartes].filter(Boolean).join(" ")).includes(termo);
}

/** A ordem que o leitor já entrega: distribuição mais recente primeiro. */
const ORDEM_PADRAO: Ordem = { campo: "DISTRIBUICAO", decrescente: true };

/**
 * A lista de processos com filtros de polo, situação, tipo, foro e contraparte, ordem por
 * distribuição, valor ou último andamento, planilha e impressão do que está na tela. Lê a lista
 * guardada — nada aqui chama a Judit nem cobra.
 *
 * <p>Quem abre por uma lupa do painel passa o filtro inicial e uma `key` nova, para a lista
 * recomeçar com ele.</p>
 */
export function ListaProcessos({
  cnpj,
  base,
  detalhes,
  onDetalhe,
  inicial,
  completa = false,
}: {
  cnpj: string;
  /** Os processos do recorte: todos, de uma justiça ou do segmento financeiro. */
  base: JuditProcesso[];
  /** A base é a lista inteira da empresa: sem filtro, a planilha não precisa mandar os números. */
  completa?: boolean;
  detalhes: Record<string, JuditSituacaoConsulta>;
  onDetalhe: (cnj: string) => void;
  inicial?: FiltroInicial;
}) {
  const [polo, setPolo] = useState<FiltroPolo>(inicial?.polo ?? "TODOS");
  const [situacao, setSituacao] = useState<FiltroSituacao>(inicial?.situacao ?? "TODAS");
  const [area, setArea] = useState<string | null | undefined>(inicial?.area);
  const [foro, setForo] = useState("");
  const [contraparte, setContraparte] = useState("");
  const [tipo, setTipo] = useState("");
  const [ordem, setOrdem] = useState<Ordem>(ORDEM_PADRAO);
  const [pagina, setPagina] = useState(0);
  const [imprimindo, setImprimindo] = useState(false);
  const [exportando, setExportando] = useState(false);

  const termoForo = normalizar(foro.trim());
  const termoContraparte = normalizar(contraparte.trim());
  const termoTipo = normalizar(tipo.trim());

  // Os textos e a área filtram primeiro; as contagens de polo e situação saem daí.
  const porTexto = useMemo(
    () =>
      base.filter(
        p =>
          (area === undefined || p.area === area) &&
          casaTipo(p, termoTipo) &&
          casaForo(p, termoForo) &&
          casaContraparte(p, termoContraparte),
      ),
    [base, area, termoTipo, termoForo, termoContraparte],
  );
  const filtrados = useMemo(
    () => ordenar(porTexto.filter(p => casaPolo(p, polo) && casaSituacao(p, situacao)), ordem),
    [porTexto, polo, situacao, ordem],
  );
  const contaPolo = (f: FiltroPolo) => porTexto.filter(p => casaPolo(p, f) && casaSituacao(p, situacao)).length;
  const contaSituacao = (f: FiltroSituacao) => porTexto.filter(p => casaPolo(p, polo) && casaSituacao(p, f)).length;

  const totalPaginas = Math.ceil(filtrados.length / POR_PAGINA);
  const paginaAtual = Math.min(pagina, Math.max(0, totalPaginas - 1));
  const visiveis = imprimindo ? filtrados : filtrados.slice(paginaAtual * POR_PAGINA, (paginaAtual + 1) * POR_PAGINA);
  const filtrando =
    polo !== "TODOS" || situacao !== "TODAS" || area !== undefined || termoTipo !== "" || termoForo !== "" || termoContraparte !== "";
  const ordemPadrao = ordem.campo === ORDEM_PADRAO.campo && ordem.decrescente === ORDEM_PADRAO.decrescente;
  const recortado = filtrando || !completa || !ordemPadrao;

  function mudar<T>(setter: (valor: T) => void) {
    return (valor: T) => {
      setter(valor);
      setPagina(0);
    };
  }

  function limpar() {
    setPolo("TODOS");
    setSituacao("TODAS");
    setArea(undefined);
    setTipo("");
    setForo("");
    setContraparte("");
    setPagina(0);
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
      // Recorte, filtro ou outra ordem: vão os números na ordem da tela. Senão, a lista inteira.
      const cnjs = recortado ? filtrados.map(p => p.cnj).filter((c): c is string => Boolean(c)) : [];
      await exportarProcessosJudit(cnpj, cnjs);
    } finally {
      setExportando(false);
    }
  }

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center gap-3 print:hidden">
        <Segmentos<FiltroPolo>
          rotulo="Filtrar processos pelo polo da empresa"
          valor={polo}
          onChange={mudar(setPolo)}
          opcoes={[
            { valor: "TODOS", rotulo: "Todos", contagem: contaPolo("TODOS") },
            { valor: "REU", rotulo: "Ré", contagem: contaPolo("REU") },
            { valor: "AUTOR", rotulo: "Autora", contagem: contaPolo("AUTOR") },
            ...(contaPolo("OUTROS") > 0 ? [{ valor: "OUTROS" as const, rotulo: "Outros", contagem: contaPolo("OUTROS") }] : []),
          ]}
        />
        <Segmentos<FiltroSituacao>
          rotulo="Filtrar pela situação provável"
          valor={situacao}
          onChange={mudar(setSituacao)}
          opcoes={[
            { valor: "TODAS", rotulo: "Todas", contagem: contaSituacao("TODAS") },
            { valor: "EM_TRAMITACAO", rotulo: "Em tramitação", contagem: contaSituacao("EM_TRAMITACAO") },
            { valor: "SUSPENSO", rotulo: "Suspenso", contagem: contaSituacao("SUSPENSO") },
            { valor: "ARQUIVADO", rotulo: "Arquivado", contagem: contaSituacao("ARQUIVADO") },
            ...(contaSituacao("SEM_ANDAMENTO") > 0
              ? [{ valor: "SEM_ANDAMENTO" as const, rotulo: "Sem andamento", contagem: contaSituacao("SEM_ANDAMENTO") }]
              : []),
          ]}
        />
        <div className="ml-auto flex flex-wrap gap-2">
          <button type="button" className={BOTAO.fantasma} onClick={imprimir}>
            <Icon name="picture_as_pdf" size={16} /> Imprimir / PDF
          </button>
          <button type="button" className={BOTAO.judit} onClick={exportar} disabled={exportando || filtrados.length === 0}>
            <Icon name={exportando ? "progress_activity" : "table_chart"} size={16} className={exportando ? "animate-spin" : ""} />
            {filtrando ? `Exportar ${filtrados.length} filtrados` : "Exportar planilha"}
          </button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3 print:hidden">
        {area !== undefined && (
          <button
            type="button"
            onClick={() => {
              setArea(undefined);
              setPagina(0);
            }}
            className="inline-flex h-10 items-center gap-1.5 rounded-[11px] border border-[#15BE62]/50 bg-[#15BE62]/10 px-3 text-xs font-semibold text-[#047857]"
            title="Tirar o filtro de área"
          >
            Área: {area ?? "não informada"} <Icon name="close" size={14} />
          </button>
        )}
        <CampoBusca rotulo="Tipo" valor={tipo} onChange={mudar(setTipo)} placeholder="Tipo: assunto ou classe" />
        <CampoBusca rotulo="Foro" valor={foro} onChange={mudar(setForo)} placeholder="Foro: tribunal, vara, cidade ou UF" />
        <CampoBusca rotulo="Contraparte" valor={contraparte} onChange={mudar(setContraparte)} placeholder="Contraparte: nome" />
        {filtrando && (
          <button type="button" onClick={limpar} className="text-xs font-semibold text-primary hover:underline">
            Limpar filtros
          </button>
        )}
        <span className="ml-auto text-xs text-gray-500">
          {filtrados.length === base.length ? `${filtrados.length} processos` : `${filtrados.length} de ${base.length} processos`}
        </span>
      </div>

      {/* Na impressão, os filtros viram uma linha de texto. */}
      {filtrando && (
        <p className="hidden text-xs text-gray-600 print:block">
          Filtros: polo {polo === "TODOS" ? "todos" : polo.toLowerCase()}
          {situacao !== "TODAS" && ` · situação ${situacao.toLowerCase().replace("_", " ")}`}
          {area !== undefined && ` · área "${area ?? "não informada"}"`}
          {tipo && ` · tipo "${tipo}"`}
          {foro && ` · foro "${foro}"`}
          {contraparte && ` · contraparte "${contraparte}"`} — {filtrados.length} de {base.length} processos
        </p>
      )}

      {filtrados.length === 0 ? (
        <p className="rounded-lg border border-border-light px-4 py-6 text-center text-sm text-gray-500 dark:border-border-dark">
          Nenhum processo com esses filtros.{" "}
          {filtrando && (
            <button type="button" onClick={limpar} className="font-semibold text-primary hover:underline">
              Limpar filtros
            </button>
          )}
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
                  acao={p.cnj ? <BotaoDetalhe situacao={detalhes[p.cnj]} onAbrir={() => onDetalhe(p.cnj!)} /> : null}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="text-[11px] text-gray-400">{AVISO_SITUACAO}</p>

      {!imprimindo && (
        <div className="print:hidden">
          <Paginacao pagina={paginaAtual} totalPaginas={totalPaginas} onIr={setPagina} />
        </div>
      )}
    </section>
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
