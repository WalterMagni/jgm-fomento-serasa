"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import Icon from "@/components/ui/Icon";
import CampoData from "@/components/ui/CampoData";
import { exportarLiberacao, useArquivoLiberacao, useUsuarioAtual } from "@/hooks/useLiberacao";
import { DESFECHOS, ROTULO_DESFECHO, ROTULO_RESULTADO, type Desfecho, type LiberacaoCard } from "@/types/liberacao";
import CardDetalheModal from "./CardDetalheModal";
import { TOM_EMPRESA } from "./cores";
import { useMovimento } from "./useMovimento";
import { COR_DESFECHO, ICONE_DESFECHO, dataDoFim, desfecho, formatDataHora, formatDocumento, formatMoeda } from "./formatters";

type Periodo = { de: string; ate: string };

const doisDigitos = (n: number) => String(n).padStart(2, "0");
const isoDia = (data: Date) => `${data.getFullYear()}-${doisDigitos(data.getMonth() + 1)}-${doisDigitos(data.getDate())}`;

/** Atalhos de período. "Tudo" limpa: é o relatório geral. */
function atalhos(hoje = new Date()): { rotulo: string; periodo: Periodo }[] {
  const ano = hoje.getFullYear();
  const mes = hoje.getMonth();
  const noventa = new Date(hoje);
  noventa.setDate(hoje.getDate() - 90);
  return [
    { rotulo: "Este mês", periodo: { de: isoDia(new Date(ano, mes, 1)), ate: isoDia(hoje) } },
    { rotulo: "Mês passado", periodo: { de: isoDia(new Date(ano, mes - 1, 1)), ate: isoDia(new Date(ano, mes, 0)) } },
    { rotulo: "Últimos 90 dias", periodo: { de: isoDia(noventa), ate: isoDia(hoje) } },
    { rotulo: "Este ano", periodo: { de: isoDia(new Date(ano, 0, 1)), ate: isoDia(hoje) } },
    { rotulo: "Tudo", periodo: { de: "", ate: "" } },
  ];
}

/** Dentro do período pela data em que o card terminou (declínio ou decisão). */
function noPeriodo(card: LiberacaoCard, { de, ate }: Periodo) {
  const fim = dataDoFim(card);
  if (!fim) return !de && !ate;
  const dia = isoDia(new Date(fim));
  return (!de || dia >= de) && (!ate || dia <= ate);
}

function naBusca(card: LiberacaoCard, termo: string) {
  const texto = termo.trim().toLowerCase();
  if (!texto) return true;
  const digitos = texto.replace(/\D/g, "");
  return (
    card.cedenteNome.toLowerCase().includes(texto) ||
    (digitos.length >= 3 && card.cedenteCnpj.includes(digitos)) ||
    String(card.numero) === texto.replace("#", "") ||
    card.sacados.some(sacado => (sacado.nome ?? "").toLowerCase().includes(texto) || (digitos.length >= 3 && sacado.documento.includes(digitos)))
  );
}

function ChipDesfecho({ valor }: { valor: Desfecho }) {
  return (
    <span className={`inline-flex items-center gap-1 whitespace-nowrap rounded-md px-1.5 py-0.5 text-[11px] font-semibold ${COR_DESFECHO[valor]}`}>
      <Icon name={ICONE_DESFECHO[valor]} size={12} />
      {ROTULO_DESFECHO[valor]}
    </span>
  );
}

function Total({ rotulo, valor, detalhe }: { rotulo: string; valor: string; detalhe?: string }) {
  return (
    <div className="rounded-xl border border-slate-200/80 bg-white px-4 py-3 dark:border-slate-700 dark:bg-slate-800">
      <p className="text-[11px] font-bold uppercase tracking-[0.08em] text-slate-500">{rotulo}</p>
      <p className="font-display mt-1 text-xl font-semibold tabular-nums tracking-tight text-slate-900 dark:text-white">{valor}</p>
      {detalhe && <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{detalhe}</p>}
    </div>
  );
}

/**
 * Arquivo da esteira: tudo que saiu do quadro ao ser arquivado. Filtra pelo período em que o card
 * terminou e pelo desfecho, e gera o mesmo relatório Excel do quadro com o que está na tela —
 * sem período, é o relatório geral.
 *
 * <p>O card abre aqui mesmo, por cima da lista. Mandar para o quadro exigiria ligar o filtro
 * "com arquivados" lá, e o quadro inteiro se encheria de arquivados.</p>
 */
export default function ArquivoLiberacao() {
  const { data: cards = [], isLoading, error } = useArquivoLiberacao();
  const { data: eu } = useUsuarioAtual();
  const movimento = useMovimento(eu?.id);
  const [cardAberto, setCardAberto] = useState<string | null>(null);
  const [periodo, setPeriodo] = useState<Periodo>({ de: "", ate: "" });
  const [desfechos, setDesfechos] = useState<Desfecho[]>([]);
  const [busca, setBusca] = useState("");
  const [exportando, setExportando] = useState(false);

  // Contagem por desfecho respeita período e busca, mas não o próprio filtro de desfecho.
  const noRecorte = useMemo(() => cards.filter(card => noPeriodo(card, periodo) && naBusca(card, busca)), [cards, periodo, busca]);
  const contagem = useMemo(() => {
    const mapa = new Map<Desfecho, number>();
    noRecorte.forEach(card => {
      const fim = desfecho(card);
      if (fim) mapa.set(fim, (mapa.get(fim) ?? 0) + 1);
    });
    return mapa;
  }, [noRecorte]);
  const lista = useMemo(
    () => noRecorte.filter(card => desfechos.length === 0 || desfechos.includes(desfecho(card) as Desfecho)),
    [noRecorte, desfechos],
  );

  const totais = useMemo(() => {
    const valor = lista.reduce((soma, card) => soma + (card.valor ?? 0), 0);
    const aprovado = lista.filter(card => !card.declinadoEm).reduce((soma, card) => soma + (card.valorAprovado ?? 0), 0);
    const declinados = lista.filter(card => card.declinadoEm);
    return { valor, aprovado, declinados: declinados.length, valorDeclinado: declinados.reduce((soma, card) => soma + (card.valor ?? 0), 0) };
  }, [lista]);

  const comPeriodo = Boolean(periodo.de || periodo.ate);
  const atalhoAtivo = atalhos().find(atalho => atalho.periodo.de === periodo.de && atalho.periodo.ate === periodo.ate)?.rotulo;

  async function gerarRelatorio() {
    setExportando(true);
    try {
      await exportarLiberacao(lista.map(card => card.id));
      toast.success(`Relatório com ${lista.length} card${lista.length === 1 ? "" : "s"} baixado`);
    } catch (erro) {
      toast.error((erro as Error).message);
    } finally {
      setExportando(false);
    }
  }

  const abrir = (card: LiberacaoCard) => setCardAberto(card.id);

  return (
    <div className="space-y-5">
      {/* ------------------------------------------------------------ cabeçalho */}
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <Link
            href="/liberacao"
            className="inline-flex items-center gap-1 text-xs font-medium text-slate-500 hover:text-[#612035] dark:text-slate-400 dark:hover:text-[#e8a3b6]"
          >
            <Icon name="arrow_back" size={13} /> Esteira de Liberação
          </Link>
          <h1 className="font-display mt-1 text-2xl font-semibold text-slate-900 dark:text-white">Arquivo</h1>
          <p className="text-xs text-slate-500 dark:text-slate-400">Cards finalizados e arquivados: aprovados, parciais, reprovados e declinados</p>
        </div>
        <button
          type="button"
          onClick={gerarRelatorio}
          disabled={exportando || lista.length === 0}
          className="inline-flex min-h-10 cursor-pointer items-center gap-1.5 rounded-lg bg-[#612035] px-4 text-sm font-medium text-white shadow-sm
            transition-colors hover:bg-[#4e1a2a] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] focus-visible:ring-offset-2
            disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Icon name="download" size={16} />
          {exportando ? "Gerando…" : comPeriodo || desfechos.length > 0 || busca.trim() ? `Relatório do filtro (${lista.length})` : `Relatório geral (${lista.length})`}
        </button>
      </header>

      {/* -------------------------------------------------------------- filtros */}
      <section className="space-y-3 rounded-xl border border-slate-200/80 bg-white p-4 dark:border-slate-700 dark:bg-slate-800">
        <div className="flex flex-wrap items-end gap-3">
          <label className="min-w-0 flex-1 basis-56">
            <span className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-300">Buscar</span>
            <span className="relative block">
              <Icon name="search" size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                value={busca}
                onChange={event => setBusca(event.target.value)}
                placeholder="Cedente, CNPJ, sacado ou nº do card"
                className="min-h-10 w-full rounded-lg border border-slate-200 bg-white pl-9 pr-3 text-sm text-slate-900 placeholder:text-slate-400
                  focus:border-[#612035] focus:outline-none focus:ring-2 focus:ring-[#612035]/20 dark:border-slate-700 dark:bg-slate-900 dark:text-white"
              />
            </span>
          </label>
          <div>
            <span className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-300">Finalizado de</span>
            <CampoData value={periodo.de} onChange={de => setPeriodo(atual => ({ ...atual, de }))} aria-label="Finalizado a partir de" />
          </div>
          <div>
            <span className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-300">até</span>
            <CampoData value={periodo.ate} onChange={ate => setPeriodo(atual => ({ ...atual, ate }))} aria-label="Finalizado até" />
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          {atalhos().map(atalho => (
            <button
              key={atalho.rotulo}
              type="button"
              aria-pressed={atalhoAtivo === atalho.rotulo}
              onClick={() => setPeriodo(atalho.periodo)}
              className={`min-h-8 cursor-pointer rounded-lg border px-2.5 text-xs font-medium transition-colors focus:outline-none focus-visible:ring-2
                focus-visible:ring-[#612035] ${
                  atalhoAtivo === atalho.rotulo
                    ? "border-[#612035] bg-[#612035] text-white"
                    : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300"
                }`}
            >
              {atalho.rotulo}
            </button>
          ))}
          <span className="mx-1 hidden h-5 w-px bg-slate-200 sm:block dark:bg-slate-700" />
          {DESFECHOS.map(valor => {
            const ativo = desfechos.includes(valor);
            return (
              <button
                key={valor}
                type="button"
                aria-pressed={ativo}
                onClick={() => setDesfechos(atual => (ativo ? atual.filter(item => item !== valor) : [...atual, valor]))}
                className={`inline-flex min-h-8 cursor-pointer items-center gap-1 rounded-lg px-2.5 text-xs font-semibold transition-opacity focus:outline-none
                  focus-visible:ring-2 focus-visible:ring-[#612035] ${COR_DESFECHO[valor]} ${
                    ativo ? "ring-2 ring-slate-900 ring-offset-1 dark:ring-white dark:ring-offset-slate-800" : desfechos.length > 0 ? "opacity-60 hover:opacity-100" : ""
                  }`}
              >
                <Icon name={ICONE_DESFECHO[valor]} size={13} />
                {ROTULO_DESFECHO[valor]}
                <span className="font-normal tabular-nums">· {contagem.get(valor) ?? 0}</span>
              </button>
            );
          })}
        </div>
      </section>

      {/* --------------------------------------------------------------- totais */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Total rotulo="Cards" valor={String(lista.length)} detalhe={comPeriodo ? "no período" : "no arquivo todo"} />
        <Total rotulo="Valor das operações" valor={formatMoeda(totais.valor)} />
        <Total rotulo="Valor aprovado" valor={formatMoeda(totais.aprovado)} detalhe="sem os declinados" />
        <Total rotulo="Declinados" valor={String(totais.declinados)} detalhe={totais.declinados > 0 ? formatMoeda(totais.valorDeclinado) : undefined} />
      </div>

      {/* ---------------------------------------------------------------- lista */}
      <section className="overflow-hidden rounded-xl border border-slate-200/80 bg-white dark:border-slate-700 dark:bg-slate-800">
        {isLoading ? (
          <div className="flex min-h-40 items-center justify-center">
            <span className="h-6 w-6 animate-spin rounded-full border-2 border-slate-300 border-t-[#612035]" />
          </div>
        ) : error ? (
          <p className="px-4 py-10 text-center text-sm text-rose-700">{(error as Error).message}</p>
        ) : lista.length === 0 ? (
          <div className="px-4 py-12 text-center">
            <Icon name="inventory_2" size={28} className="mx-auto text-slate-300" />
            <p className="mt-2 text-sm font-medium text-slate-700 dark:text-slate-200">
              {cards.length === 0 ? "Nenhum card arquivado ainda." : "Nenhum card neste filtro."}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              {cards.length === 0 ? "Finalizados entram aqui quando alguém os arquiva no quadro." : "Mude o período ou o desfecho."}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold uppercase tracking-[0.06em] text-slate-500 dark:border-slate-700 dark:bg-slate-900/60">
                <tr>
                  <th className="px-4 py-2.5">Card</th>
                  <th className="px-4 py-2.5">Cedente</th>
                  <th className="px-4 py-2.5 text-right">Valor</th>
                  <th className="px-4 py-2.5">Desfecho</th>
                  <th className="px-4 py-2.5">Finalizado em</th>
                  <th className="px-4 py-2.5">Arquivado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-700/70">
                {lista.map(card => {
                  const fim = desfecho(card);
                  return (
                    <tr
                      key={card.id}
                      onClick={() => abrir(card)}
                      className="cursor-pointer transition-colors hover:bg-slate-50 dark:hover:bg-slate-900/40"
                    >
                      <td className="px-4 py-3 align-top">
                        <span className="font-mono text-xs text-slate-400">#{card.numero}</span>
                        <span className="mt-1 flex flex-wrap gap-1">
                          {card.tipoOperacao && (
                            <span className="rounded bg-slate-100 px-1.5 py-px text-[10px] font-medium uppercase tracking-wide text-slate-600 dark:bg-slate-700 dark:text-slate-300">
                              {card.tipoOperacao}
                            </span>
                          )}
                          {card.empresa && (
                            <span className={`rounded px-1.5 py-px text-[10px] font-bold tracking-wide ${TOM_EMPRESA[card.empresa]}`}>{card.empresa}</span>
                          )}
                        </span>
                      </td>
                      <td className="max-w-[22rem] px-4 py-3 align-top">
                        <button
                          type="button"
                          onClick={event => {
                            event.stopPropagation();
                            abrir(card);
                          }}
                          className="cursor-pointer text-left font-semibold text-slate-800 hover:text-[#612035] hover:underline focus:outline-none
                            focus-visible:ring-2 focus-visible:ring-[#612035] dark:text-slate-100 dark:hover:text-[#e8a3b6]"
                        >
                          {card.cedenteNome}
                        </button>
                        <span className="block font-mono text-[11px] text-slate-500">{formatDocumento(card.cedenteCnpj)}</span>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-right align-top tabular-nums text-slate-800 dark:text-slate-100">
                        {formatMoeda(card.valor)}
                        {fim === "PARCIAL" && card.valorAprovado != null && (
                          <span className="block text-[11px] text-slate-500">{formatMoeda(card.valorAprovado)} aprovados</span>
                        )}
                      </td>
                      <td className="px-4 py-3 align-top">
                        {fim ? <ChipDesfecho valor={fim} /> : <span className="text-slate-400">—</span>}
                        {card.declinadoEm && card.resultado && (
                          <span className="mt-1 block text-[11px] text-slate-500">crédito: {ROTULO_RESULTADO[card.resultado].toLowerCase()}</span>
                        )}
                        {card.declinadoEm && card.motivoDeclinio && (
                          <span className="mt-1 block max-w-[16rem] truncate text-[11px] text-slate-500" title={card.motivoDeclinio}>
                            {card.motivoDeclinio}
                          </span>
                        )}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 align-top text-slate-600 dark:text-slate-300">{formatDataHora(dataDoFim(card))}</td>
                      <td className="whitespace-nowrap px-4 py-3 align-top text-xs text-slate-500">
                        {formatDataHora(card.arquivadoEm)}
                        {card.arquivadoPorNome && <span className="block">por {card.arquivadoPorNome.split(" ")[0]}</span>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {cardAberto && (
        <CardDetalheModal
          cardId={cardAberto}
          euId={eu?.id}
          ehAnalista={eu?.analista ?? false}
          onFechar={() => setCardAberto(null)}
          onMover={(card, para) => movimento.pedir(card, para)}
        />
      )}
      {/* Depois do detalhe: a confirmação de mover (reabrir no Comitê) fica por cima dele. */}
      {movimento.dialogo}
    </div>
  );
}
