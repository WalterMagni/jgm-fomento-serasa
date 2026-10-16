"use client";

import { useEffect, useMemo, useState, type ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import Gaveta from "@/components/ui/Gaveta";
import Icon from "@/components/ui/Icon";
import { BOTAO } from "@/components/ui/botao";
import { useDetalharProcesso, useJuditDetalhe, useVerificarDetalhe } from "@/hooks/useJudit";
import {
  BotaoCobrado,
  SelosJudit,
  VERDE,
  data,
  moeda,
  motivoBloqueio,
  normalizar,
} from "@/components/judit/JuditPecas";
import type { JuditDetalheConsultado, JuditParte, JuditProcesso, JuditProcessoDetalhado, JuditSituacao } from "@/types/judit";

/** Quantas movimentações aparecem antes do "mostrar todas". O teste trouxe 259. */
const PREVIA_ANDAMENTOS = 30;

/** Depois disso sem resposta, oferece "verificar agora" (o servidor pode ter reiniciado no meio). */
const MINUTOS_PARA_VERIFICAR = 5;

const ROTULO_PARTE: Record<JuditParte["polo"], string> = {
  ATIVO: "Polo ativo",
  PASSIVO: "Polo passivo",
  INTERESSADO: "Interessados",
  OUTRO: "Outros",
};

function hora(valor: string | null | undefined) {
  if (!valor) return "";
  return valor.slice(11, 16);
}

/**
 * Detalhe de um processo pela Judit: direto no tribunal, com status, fase, todas as movimentações e
 * o resumo da IA deles. Cobra por processo e leva ~1 minuto — o pedido fica pendente e o sino avisa.
 * Abrir a gaveta não cobra; só o botão de detalhar, com confirmação.
 */
export default function DetalheProcessoGaveta({
  cnpj,
  processo,
  nomeEmpresa,
  situacao,
  onFechar,
}: {
  cnpj: string;
  processo: JuditProcesso;
  nomeEmpresa: string | null;
  situacao: JuditSituacao | undefined;
  onFechar: () => void;
}) {
  const cnj = processo.cnj!;
  const { data: detalhe, isLoading, isError, error } = useJuditDetalhe(cnpj, cnj);
  const detalhar = useDetalharProcesso(cnpj);
  const verificar = useVerificarDetalhe(cnpj);
  const preco = situacao?.precos.detalhe ?? 0;
  const bloqueio = situacao ? motivoBloqueio(situacao, preco) : "Carregando…";

  const botaoDetalhar = (rotulo: string, discreto = false) => (
    <BotaoCobrado
      rotulo={rotulo}
      preco={preco}
      bloqueio={bloqueio}
      pendente={detalhar.isPending}
      discreto={discreto}
      onConfirmar={() => detalhar.mutate({ cnj, nomeEmpresa })}
    />
  );

  return (
    <Gaveta rotulo={`Detalhe do processo ${cnj}`} onFechar={onFechar} larga>
      <header className="-mx-7 -mt-6 border-b border-[#C6F1D6] dark:border-[#15BE62]/25">
        <div className="h-1" style={{ backgroundColor: VERDE }} aria-hidden />
        <div className="flex items-start gap-3 px-7 py-5">
          <div className="min-w-0 flex-1">
            <p className="flex flex-wrap items-center gap-2 text-[11px] font-bold uppercase tracking-[0.14em] text-[#047857] dark:text-[#15BE62]">
              <Icon name="gavel" size={13} /> Detalhe do processo <SelosJudit />
            </p>
            <h2 className="mt-2 font-mono text-lg font-semibold text-grafite dark:text-white">{cnj}</h2>
            <p className="text-sm font-semibold text-grafite dark:text-gray-200">{processo.classe ?? "—"}</p>
            {processo.autuacao && <p className="text-xs text-gray-500">{processo.autuacao}</p>}
          </div>
          <button
            type="button"
            onClick={onFechar}
            aria-label="Fechar"
            className="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-black/5 hover:text-grafite dark:hover:bg-white/10"
          >
            <Icon name="close" size={18} />
          </button>
        </div>
      </header>

      {isLoading ? (
        <p className="text-sm text-gray-500">Carregando…</p>
      ) : isError ? (
        <p className="text-sm text-gray-500">{(error as Error)?.message ?? "Não foi possível carregar o detalhe."}</p>
      ) : !detalhe ? (
        <section className="space-y-4">
          <p className="text-sm leading-relaxed text-gray-600 dark:text-gray-300">
            Busca o processo <strong>direto no tribunal</strong>: status, fase, todas as partes, <strong>todas as
            movimentações</strong> (a lista só traz a última) e um <strong>resumo escrito pela IA da Judit</strong>.
          </p>
          <p className="text-xs text-gray-500">
            Leva cerca de 1 minuto. Pode fechar esta janela: o sino avisa quando ficar pronto.
          </p>
          {botaoDetalhar("Detalhar processo")}
        </section>
      ) : detalhe.situacao === "PENDENTE" ? (
        <Pendente
          detalhe={detalhe}
          verificando={verificar.isPending}
          onVerificar={() => verificar.mutate({ cnj, nomeEmpresa })}
        />
      ) : detalhe.situacao === "FALHOU" ? (
        <section className="space-y-4">
          <div className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100">
            <Icon name="warning" size={18} className="mt-0.5 shrink-0" />
            <span>
              A Judit não conseguiu detalhar este processo.
              {detalhe.erro && <span className="mt-1 block text-xs">{detalhe.erro}</span>}
            </span>
          </div>
          <p className="text-xs text-gray-500">
            Pedido em {data(detalhe.pedidoEm)} às {hora(detalhe.pedidoEm)}
            {detalhe.pedidoPor && ` por ${detalhe.pedidoPor}`} · {moeda(detalhe.custo)} (a Judit cobra o pedido).
          </p>
          {botaoDetalhar("Tentar de novo")}
        </section>
      ) : (
        <Concluido detalhe={detalhe} validadeDias={situacao?.validadeDias ?? 30} atualizar={botaoDetalhar("Atualizar", true)} />
      )}
    </Gaveta>
  );
}

function Pendente({
  detalhe,
  verificando,
  onVerificar,
}: {
  detalhe: JuditDetalheConsultado;
  verificando: boolean;
  onVerificar: () => void;
}) {
  // Relógio próprio: a regra de pureza do React não deixa ler a hora durante a renderização.
  const [agora, setAgora] = useState(() => Date.now());
  useEffect(() => {
    const relogio = setInterval(() => setAgora(Date.now()), 30000);
    return () => clearInterval(relogio);
  }, []);
  const minutos = (agora - new Date(detalhe.pedidoEm).getTime()) / 60000;
  return (
    <section className="space-y-4">
      <div className="flex gap-3 rounded-xl border border-[#15BE62]/50 bg-[#15BE62]/[0.06] px-4 py-4">
        <Icon name="progress_activity" size={20} className="mt-0.5 shrink-0 animate-spin text-[#047857] dark:text-[#15BE62]" />
        <div className="text-sm text-grafite dark:text-gray-200">
          <p className="font-semibold">A Judit está buscando o processo no tribunal…</p>
          <p className="mt-1 text-xs text-gray-500">
            Pedido em {data(detalhe.pedidoEm)} às {hora(detalhe.pedidoEm)}
            {detalhe.pedidoPor && ` por ${detalhe.pedidoPor}`} · {moeda(detalhe.custo)}. Costuma levar cerca de 1
            minuto. Pode fechar esta janela: o sino avisa quando ficar pronto.
          </p>
        </div>
      </div>
      {minutos > MINUTOS_PARA_VERIFICAR && (
        <div className="flex flex-wrap items-center gap-3 text-xs text-gray-500">
          Está demorando mais que o normal.
          <button type="button" className={BOTAO.fantasma} disabled={verificando} onClick={onVerificar}>
            <Icon name="refresh" size={16} /> Verificar agora (sem custo)
          </button>
        </div>
      )}
    </section>
  );
}

function Concluido({
  detalhe,
  validadeDias,
  atualizar,
}: {
  detalhe: JuditDetalheConsultado;
  validadeDias: number;
  atualizar: ReactNode;
}) {
  const processo = detalhe.dados?.processo ?? null;
  const resumo = detalhe.dados?.resumoIa ?? null;
  return (
    <>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <span className="text-[11px] text-gray-500">
          Detalhado em {data(detalhe.pedidoEm)}
          {detalhe.pedidoPor && ` por ${detalhe.pedidoPor}`} · {moeda(detalhe.custo)}
        </span>
        {detalhe.desatualizado && (
          <span
            className="rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-800 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200"
            title={`Detalhado há mais de ${validadeDias} dias. Não atualiza sozinho.`}
          >
            desatualizado
          </span>
        )}
        <div className="ml-auto">{atualizar}</div>
      </div>

      {!processo ? (
        <p className="text-sm text-gray-500">A Judit não encontrou o processo no tribunal.</p>
      ) : (
        <Ficha processo={processo} />
      )}

      {resumo && (
        <section>
          <h3 className="mb-1 flex items-center gap-2 text-sm font-bold text-[#047857] dark:text-[#15BE62]">
            <Icon name="auto_awesome" size={15} /> Resumo da IA da Judit
          </h3>
          <p className="mb-3 text-[11px] text-gray-400">
            Escrito pela IA da Judit (em beta) a partir das movimentações. Apoio à leitura — confira nos andamentos.
          </p>
          <div className="rounded-xl border border-[#C6F1D6] bg-white px-5 py-4 dark:border-[#15BE62]/25 dark:bg-white/5">
            <ResumoMarkdown texto={resumo} />
          </div>
        </section>
      )}

      {processo && <Partes partes={processo.partes} />}
      {processo && <Movimentacoes movimentacoes={processo.movimentacoes} />}
    </>
  );
}

function Ficha({ processo }: { processo: JuditProcessoDetalhado }) {
  const selos = [processo.status, processo.fase, processo.situacao].filter((v): v is string => Boolean(v));
  const linhas: [string, string | null][] = [
    ["Valor da causa", processo.valor !== null ? moeda(processo.valor) : null],
    ["Distribuído em", processo.distribuidoEm ? data(processo.distribuidoEm) : null],
    ["Foro", [processo.tribunal, ...processo.orgaos].filter(Boolean).join(" · ") || null],
    ["Juiz", processo.juiz],
    ["Assuntos", processo.assuntos.join(" · ") || null],
    ["Instâncias", processo.instancias.map(i => `${i}ª`).join(", ") || null],
    ["Anexos", processo.anexos > 0 ? `${processo.anexos} no tribunal (não baixados — têm cobrança à parte)` : null],
  ];
  return (
    <section className="space-y-3">
      {selos.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {selos.map(selo => (
            <span
              key={selo}
              className="rounded-full border border-[#C6F1D6] bg-[#15BE62]/10 px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wide text-[#047857] dark:border-[#15BE62]/30 dark:text-[#15BE62]"
            >
              {selo}
            </span>
          ))}
        </div>
      )}
      <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1.5 text-xs">
        {linhas
          .filter(([, valor]) => valor)
          .map(([rotulo, valor]) => (
            <div key={rotulo} className="contents">
              <dt className="font-semibold text-gray-400">{rotulo}</dt>
              <dd className="text-grafite dark:text-gray-200">{valor}</dd>
            </div>
          ))}
      </dl>
    </section>
  );
}

function Partes({ partes }: { partes: JuditParte[] }) {
  const principais = partes.filter(p => !p.auxiliar);
  const auxiliares = partes.filter(p => p.auxiliar);
  const porPolo = (["ATIVO", "PASSIVO", "INTERESSADO", "OUTRO"] as const)
    .map(polo => [polo, principais.filter(p => p.polo === polo)] as const)
    .filter(([, lista]) => lista.length > 0);
  if (partes.length === 0) return null;
  return (
    <section>
      <h3 className="mb-2 text-sm font-bold text-[#047857] dark:text-[#15BE62]">Partes</h3>
      <div className="grid gap-3 sm:grid-cols-2">
        {porPolo.map(([polo, lista]) => (
          <div key={polo}>
            <div className="mb-1 text-[11px] font-bold uppercase tracking-wide text-gray-400">{ROTULO_PARTE[polo]}</div>
            <ul className="space-y-1 text-xs">
              {lista.map(p => (
                <li key={p.nome + p.polo}>
                  <span className="font-semibold text-grafite dark:text-gray-200">{p.nome}</span>
                  {p.tipo && <span className="ml-1 text-gray-400">· {p.tipo.toLowerCase()}</span>}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      {auxiliares.length > 0 && (
        <p className="mt-2 text-[11px] text-gray-400">
          Advogados e auxiliares: {auxiliares.map(p => p.nome).join(", ")}
        </p>
      )}
    </section>
  );
}

function Movimentacoes({ movimentacoes }: { movimentacoes: JuditProcessoDetalhado["movimentacoes"] }) {
  const [busca, setBusca] = useState("");
  const [todas, setTodas] = useState(false);
  const termo = normalizar(busca.trim());
  const filtradas = useMemo(
    () => (termo ? movimentacoes.filter(m => normalizar(m.texto ?? "").includes(termo)) : movimentacoes),
    [movimentacoes, termo],
  );
  const visiveis = todas || termo ? filtradas : filtradas.slice(0, PREVIA_ANDAMENTOS);
  if (movimentacoes.length === 0) return null;
  return (
    <section>
      <div className="mb-2 flex flex-wrap items-center gap-3">
        <h3 className="text-sm font-bold text-[#047857] dark:text-[#15BE62]">
          Movimentações <span className="font-normal text-gray-400">· {movimentacoes.length}</span>
        </h3>
        <label className="relative ml-auto block w-full sm:w-64">
          <span className="sr-only">Buscar nas movimentações</span>
          <Icon name="search" size={16} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[#8A877E]" />
          <input
            type="search"
            value={busca}
            onChange={e => setBusca(e.target.value)}
            placeholder="Buscar: penhora, citação, sentença…"
            className="h-9 w-full rounded-[10px] border border-linha bg-white pl-8 pr-3 text-xs text-grafite outline-none focus:border-[#15BE62] focus:ring-4 focus:ring-[#15BE62]/15 dark:bg-transparent dark:text-white"
          />
        </label>
      </div>
      {filtradas.length === 0 ? (
        <p className="text-xs text-gray-500">Nenhuma movimentação com esse termo.</p>
      ) : (
        <ol className="relative space-y-2 border-l-2 border-[#C6F1D6] pl-4 dark:border-[#15BE62]/30">
          {visiveis.map((m, i) => (
            <li key={`${m.data}-${i}`} className="relative text-xs">
              <span className="absolute -left-[21px] top-1.5 h-2 w-2 rounded-full bg-[#15BE62]" aria-hidden />
              <span className="font-semibold tabular-nums text-gray-500">{data(m.data)}</span>
              <p className="text-grafite dark:text-gray-200">{m.texto ?? "—"}</p>
            </li>
          ))}
        </ol>
      )}
      {!termo && filtradas.length > PREVIA_ANDAMENTOS && (
        <button type="button" onClick={() => setTodas(!todas)} className="mt-2 text-xs font-semibold text-[#047857] hover:underline">
          {todas ? "Mostrar menos" : `Mostrar todas as ${filtradas.length}`}
        </button>
      )}
    </section>
  );
}

/** O resumo já vem limpo do backend (markdown, sem HTML); o react-markdown não executa HTML de qualquer jeito. */
function ResumoMarkdown({ texto }: { texto: string }) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        h2: ({ children }) => (
          <h4 className="mb-2 mt-4 text-[11px] font-bold uppercase tracking-[0.12em] text-[#047857] first:mt-0 dark:text-[#15BE62]">
            {children}
          </h4>
        ),
        p: ({ children }) => <p className="mb-3 text-[13px] leading-relaxed text-gray-700 dark:text-gray-300">{children}</p>,
        ul: ({ children }) => (
          <ul className="mb-3 list-disc space-y-1.5 pl-5 text-[13px] leading-relaxed text-gray-700 dark:text-gray-300">{children}</ul>
        ),
        strong: ({ children }) => <strong className="font-semibold text-grafite dark:text-white">{children}</strong>,
        em: ({ children }) => <em className="not-italic text-gray-400">{children}</em>,
        hr: () => <hr className="my-4 border-[#C6F1D6] dark:border-[#15BE62]/25" />,
        a: ({ children }) => <span>{children}</span>,
        img: () => null,
      }}
    >
      {texto}
    </ReactMarkdown>
  );
}

