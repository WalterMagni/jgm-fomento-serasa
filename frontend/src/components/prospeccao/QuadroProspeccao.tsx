"use client";

import { useCallback, useMemo, useState, useSyncExternalStore } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  closestCenter,
  DndContext,
  DragOverlay,
  KeyboardSensor,
  pointerWithin,
  PointerSensor,
  TouchSensor,
  useSensor,
  useSensors,
  type CollisionDetection,
  type DragEndEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import Icon from "@/components/ui/Icon";
import BackfillDialog from "./BackfillDialog";
import ManualDrawer from "./ManualDrawer";
import NovaProspeccaoDialog from "./NovaProspeccaoDialog";
import ProspeccaoColumn from "./ProspeccaoColumn";
import ProspeccaoModal, { RESPOSTA_DO_DESTINO, RESULTADO_DO_DESTINO, type AcaoAberta } from "./ProspeccaoModal";
import { CardFace } from "./ProspeccaoCard";
import { acaoPropria, confirmarExclusao, ordenarCards, type Ordenacao } from "./formatters";
import {
  COLUNAS,
  ROTULO_ESTAGIO,
  ROTULO_MOTIVO,
  TERMINAIS,
  type EstagioProspeccao,
  type Prospeccao,
} from "@/types/prospeccao";
import {
  exportarEsteira,
  useAssumirProspeccao,
  useEnviarComite,
  useEstagios,
  useExcluirProspeccoes,
  useProspeccaoResumo,
  useProspeccoes,
  useTransicionarProspeccao,
  type ConteudoExport,
} from "@/hooks/useProspeccao";

/**
 * Onde o card cai: na coluna em que está o ponteiro, e não na que a cópia arrastada encosta.
 * Pelo teclado não há ponteiro, e vale a coluna mais próxima. Mesma regra da liberação.
 */
const detectarColisao: CollisionDetection = args =>
  args.pointerCoordinates ? pointerWithin(args) : closestCenter(args);

const CELULAR = "(max-width: 767px)";

/**
 * Celular ou não, lido do navegador. Os dois layouts não podem coexistir escondidos por CSS: cada
 * card registraria dois arrastáveis com o mesmo id no dnd-kit.
 */
function useEhCelular() {
  return useSyncExternalStore(
    avisar => {
      const consulta = window.matchMedia(CELULAR);
      consulta.addEventListener("change", avisar);
      return () => consulta.removeEventListener("change", avisar);
    },
    () => window.matchMedia(CELULAR).matches,
    () => false,
  );
}

const FILTRO =
  "min-h-9 cursor-pointer rounded-lg border border-slate-200 bg-white px-2.5 text-xs text-slate-700 focus:outline-none " +
  "focus-visible:ring-2 focus-visible:ring-[#612035] dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100";

const BOTAO_TOPO =
  "inline-flex min-h-9 cursor-pointer items-center gap-1.5 rounded-lg px-3 text-xs font-medium transition-colors " +
  "focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]";

/**
 * Esteira de prospecção unificada — da abertura até o cedente habilitado.
 *
 * <p>Junta a esteira original (análise e documentação) com o fluxo do Portal Comercial (decisão
 * com limite, aceite do cliente, comitê e contrato). O card aberto mora na URL ({@code ?card=}):
 * a página da empresa e as notificações apontam direto para ele.</p>
 */
export default function QuadroProspeccao() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const cardAberto = params.get("card");

  const [apenasAtrasados, setApenasAtrasados] = useState(false);
  const [novaAberta, setNovaAberta] = useState(false);
  const [exportAberto, setExportAberto] = useState(false);
  const [backfillAberto, setBackfillAberto] = useState(false);
  const [manualAberto, setManualAberto] = useState(false);
  const [busca, setBusca] = useState("");
  const [responsavel, setResponsavel] = useState("");
  const [origem, setOrigem] = useState<"" | "MANUAL" | "AUTOMATICA">("");
  const [ordenacoes, setOrdenacoes] = useState<Record<string, Ordenacao>>({});
  const [selecionando, setSelecionando] = useState(false);
  const [selecionados, setSelecionados] = useState<Set<string>>(new Set());
  const [arrastando, setArrastando] = useState<Prospeccao | null>(null);
  const [larguraArrasto, setLarguraArrasto] = useState<number | null>(null);
  const [abaCelular, setAbaCelular] = useState<EstagioProspeccao>("NOVA");
  // Arrastar para um estágio que exige decisão ou resposta do cliente abre o card com o diálogo.
  const [acaoInicial, setAcaoInicial] = useState<AcaoAberta | null>(null);
  // Conta os arrastes com ação: soltar de novo o mesmo card remonta o detalhe e reabre o diálogo.
  const [arrastesComAcao, setArrastesComAcao] = useState(0);

  const { data: cards = [], isLoading, error } = useProspeccoes({ apenasAtrasados });
  const { data: resumo } = useProspeccaoResumo();
  const { data: transicoes } = useEstagios();
  const transicionar = useTransicionarProspeccao();
  const assumir = useAssumirProspeccao();
  const enviarComite = useEnviarComite();
  const excluir = useExcluirProspeccoes();
  const ehCelular = useEhCelular();

  const abrir = useCallback(
    (id: string | null) => {
      const novos = new URLSearchParams(params.toString());
      if (id) novos.set("card", id);
      else novos.delete("card");
      router.replace(`${pathname}${novos.size ? `?${novos}` : ""}`, { scroll: false });
    },
    [params, pathname, router],
  );

  const sensors = useSensors(
    // Distância mínima: um clique simples abre o card em vez de começar um arraste.
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    // No toque, segurar um instante: sem isso rolar a coluna com o dedo arrastaria o card.
    useSensor(TouchSensor, { activationConstraint: { delay: 220, tolerance: 6 } }),
    // Espaço pega e solta; Enter fica para abrir o card.
    useSensor(KeyboardSensor, { keyboardCodes: { start: ["Space"], cancel: ["Escape"], end: ["Space", "Enter"] } }),
  );

  /** Nomes que aparecem no filtro de responsável: quem realmente tem card na esteira. */
  const responsaveis = useMemo(() => {
    const nomes = new Set<string>();
    cards.forEach(card => {
      if (card.analistaNome) nomes.add(card.analistaNome);
      if (card.comercialNome) nomes.add(card.comercialNome);
    });
    return Array.from(nomes).sort((a, b) => a.localeCompare(b, "pt-BR"));
  }, [cards]);

  /**
   * Busca por nome ou CNPJ, sem ir ao servidor: a esteira aberta cabe em memória, e filtrar aqui
   * responde a cada tecla. O CNPJ é comparado só por dígitos.
   */
  const filtrados = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    const digitos = busca.replace(/\D/g, "");
    return cards.filter(card => {
      if (responsavel && card.analistaNome !== responsavel && card.comercialNome !== responsavel) return false;
      if (origem && card.origem !== origem) return false;
      if (!termo) return true;
      if (digitos.length >= 2 && card.cnpj.includes(digitos)) return true;
      return card.razaoSocial.toLowerCase().includes(termo);
    });
  }, [cards, busca, responsavel, origem]);

  const temFiltro = Boolean(busca.trim() || responsavel || origem || apenasAtrasados);

  function limparFiltros() {
    setBusca("");
    setResponsavel("");
    setOrigem("");
    setApenasAtrasados(false);
  }

  const porEstagio = useMemo(() => {
    const mapa = new Map<EstagioProspeccao, Prospeccao[]>(COLUNAS.map(coluna => [coluna, []]));
    filtrados.forEach(card => mapa.get(card.estagio)?.push(card));
    COLUNAS.forEach(coluna => mapa.set(coluna, ordenarCards(mapa.get(coluna) ?? [], ordenacoes[coluna] ?? "urgencia")));
    return mapa;
  }, [filtrados, ordenacoes]);

  const terminais = useMemo(() => filtrados.filter(card => TERMINAIS.includes(card.estagio)), [filtrados]);
  const noQuadro = COLUNAS.reduce((soma, coluna) => soma + (porEstagio.get(coluna)?.length ?? 0), 0);

  // Só conta o que está visível: card marcado e depois escondido pelo filtro não é apagado sem
  // que a pessoa o veja.
  const marcadosVisiveis = useMemo(() => filtrados.filter(card => selecionados.has(card.id)), [filtrados, selecionados]);

  function alternarSelecao(id: string) {
    setSelecionados(atual => {
      const proximo = new Set(atual);
      if (proximo.has(id)) proximo.delete(id);
      else proximo.add(id);
      return proximo;
    });
  }

  function selecionarColuna(ids: string[], marcar: boolean) {
    setSelecionados(atual => {
      const proximo = new Set(atual);
      ids.forEach(id => (marcar ? proximo.add(id) : proximo.delete(id)));
      return proximo;
    });
  }

  function sairDaSelecao() {
    setSelecionando(false);
    setSelecionados(new Set());
  }

  function apagarSelecionados() {
    if (!confirmarExclusao(marcadosVisiveis)) return;
    excluir.mutate(marcadosVisiveis.map(card => card.id), { onSuccess: sairDaSelecao });
  }

  function alvo(coluna: EstagioProspeccao) {
    if (!arrastando) return null;
    if (coluna === arrastando.estagio) return { permitido: false, origem: true };
    return { permitido: (transicoes?.[arrastando.estagio] ?? []).includes(coluna), origem: false };
  }

  function aoComecar(event: DragStartEvent) {
    setArrastando((event.active.data.current?.card as Prospeccao) ?? null);
    setLarguraArrasto(event.active.rect.current.initial?.width ?? null);
  }

  /**
   * Move por arraste. Não faz atualização otimista: a passagem para o comitê pode ser recusada com
   * a lista de documentos que faltam, então o estado real só se conhece depois da resposta.
   * Soltar em Em análise a partir de Nova é assumir, que tem endpoint próprio contra corrida.
   */
  function aoSoltar(event: DragEndEvent) {
    const card = event.active.data.current?.card as Prospeccao | undefined;
    setArrastando(null);
    const destino = event.over?.id as EstagioProspeccao | undefined;
    if (!card || !destino || destino === card.estagio) return;
    if (card.estagio === "NOVA" && destino === "EM_ANALISE") {
      assumir.mutate(card.id);
      return;
    }
    const acao = acaoPropria(card.estagio, destino);
    if (acao === "comite") {
      // Enviar ao comitê não pede nada além do clique: convoca os membros direto.
      enviarComite.mutate({ id: card.id });
      return;
    }
    if (acao) {
      setAcaoInicial(
        acao === "decidir"
          ? { tipo: "decidir" }
          : acao === "aceite"
            ? { tipo: "aceite", resposta: RESPOSTA_DO_DESTINO[destino] ?? "ACEITO" }
            : acao === "resultado"
              ? { tipo: "resultado", resultado: RESULTADO_DO_DESTINO[destino] ?? "APROVADO" }
              : { tipo: "contrato" },
      );
      setArrastesComAcao(n => n + 1);
      abrir(card.id);
      return;
    }
    transicionar.mutate({ id: card.id, estagio: destino });
  }

  const abaVisivel = COLUNAS.includes(abaCelular) ? abaCelular : "NOVA";

  return (
    // Altura presa à janela só a partir do tablet, onde as colunas rolam por dentro. No celular a
    // página rola inteira: com cabeçalho e filtros empilhados, a coluna presa mostraria um card só.
    <div className="flex flex-col gap-3 md:h-[calc(100dvh-7rem)] lg:h-[calc(100dvh-8rem)]">
      {/* ------------------------------------------------------------ cabeçalho */}
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="font-display text-2xl font-semibold text-slate-900 dark:text-white">Esteira de Prospecção</h1>
            <span
              className="rounded border border-[#D1732C] px-1 py-px text-[9px] font-bold uppercase tracking-wider text-[#D1732C]"
              title="Em avaliação: o fluxo ainda pode mudar"
            >
              beta
            </span>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400">Da abertura ao cedente habilitado</p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => setManualAberto(true)}
            className={`${BOTAO_TOPO} text-slate-600 hover:bg-white dark:text-slate-300 dark:hover:bg-slate-800`}
          >
            <Icon name="help" size={15} /> Ajuda
          </button>
          <button
            type="button"
            onClick={() => setBackfillAberto(true)}
            title="Traz para a esteira as análises com visão cedente SIM que já existiam"
            className={`${BOTAO_TOPO} border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700`}
          >
            <Icon name="download" size={15} /> Puxar visão cedente
          </button>
          <button
            type="button"
            onClick={() => setNovaAberta(true)}
            className={`${BOTAO_TOPO} bg-[#612035] text-white shadow-sm hover:bg-[#4e1a2a] focus-visible:ring-offset-2`}
          >
            <Icon name="add" size={15} /> Nova prospecção
          </button>
        </div>
      </header>

      {/* -------------------------------------------------------- pede atenção */}
      {resumo && (resumo.slaEstourado > 0 || resumo.slaEmAtencao > 0 || resumo.silencioProlongado > 0) && (
        <div className="esteira-fade-in flex flex-wrap items-center gap-x-4 gap-y-1 rounded-xl border border-[#612035]/15 bg-gradient-to-r
          from-[#612035]/[0.07] via-[#612035]/[0.03] to-transparent px-4 py-2 text-sm dark:border-[#e8a3b6]/20 dark:from-[#612035]/30">
          <span className="font-semibold text-[#612035] dark:text-[#e8a3b6]">{resumo.total} em aberto</span>
          {resumo.slaEstourado > 0 && (
            <button
              type="button"
              onClick={() => setApenasAtrasados(true)}
              className="cursor-pointer font-medium text-rose-700 underline-offset-2 hover:underline dark:text-rose-300"
            >
              {resumo.slaEstourado} atrasado{resumo.slaEstourado > 1 ? "s" : ""}
            </button>
          )}
          {resumo.slaEmAtencao > 0 && <span className="text-[#D1732C]">{resumo.slaEmAtencao} perto do prazo</span>}
          {resumo.silencioProlongado > 0 && (
            <span className="text-rose-700 dark:text-rose-300">{resumo.silencioProlongado} sem resposta do cliente</span>
          )}
        </div>
      )}

      {/* ------------------------------------------------------------- filtros */}
      <div className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 shadow-sm dark:border-slate-700 dark:bg-slate-800">
        <div className="relative min-w-[14rem] flex-1">
          <Icon name="search" size={15} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            value={busca}
            onChange={event => setBusca(event.target.value)}
            placeholder="Buscar por razão social ou CNPJ…"
            aria-label="Buscar por razão social ou CNPJ"
            className={`${FILTRO} w-full cursor-text pl-8`}
          />
        </div>
        <select value={responsavel} onChange={event => setResponsavel(event.target.value)} aria-label="Responsável" className={FILTRO}>
          <option value="">Todos os responsáveis</option>
          {responsaveis.map(nome => (
            <option key={nome} value={nome}>{nome}</option>
          ))}
        </select>
        <select
          value={origem}
          onChange={event => setOrigem(event.target.value as "" | "MANUAL" | "AUTOMATICA")}
          aria-label="Entrada"
          className={FILTRO}
        >
          <option value="">Toda entrada</option>
          <option value="MANUAL">Manual</option>
          <option value="AUTOMATICA">Visão cedente</option>
        </select>
        <button
          type="button"
          onClick={() => setApenasAtrasados(valor => !valor)}
          aria-pressed={apenasAtrasados}
          className={`${BOTAO_TOPO} ${apenasAtrasados ? "bg-rose-600 text-white" : "border border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-700"}`}
        >
          <Icon name="schedule" size={14} /> Só atrasados
        </button>

        <div className="relative">
          <button
            type="button"
            onClick={() => setExportAberto(aberto => !aberto)}
            className={`${BOTAO_TOPO} border border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-700`}
          >
            <Icon name="table_chart" size={14} /> Exportar
          </button>
          {exportAberto && (
            <>
              <div className="fixed inset-0 z-10" onClick={() => setExportAberto(false)} />
              <div className="absolute right-0 z-20 mt-1 w-60 rounded-lg bg-white py-1 text-xs shadow-lg dark:bg-slate-800">
                {(
                  [
                    ["CARDS", "Cards da esteira", "uma linha por empresa"],
                    ["DOCUMENTOS", "Checklist item a item", "qual documento mais trava"],
                    ["EVENTOS", "Histórico de cobranças", "canal, autor e data"],
                  ] as [ConteudoExport, string, string][]
                ).map(([conteudo, titulo, ajuda]) => (
                  <button
                    key={conteudo}
                    type="button"
                    onClick={() => {
                      setExportAberto(false);
                      exportarEsteira(conteudo, { apenasAtrasados });
                    }}
                    className="block w-full cursor-pointer px-3 py-1.5 text-left hover:bg-slate-50 dark:hover:bg-slate-700"
                  >
                    <span className="block text-slate-700 dark:text-slate-200">{titulo}</span>
                    <span className="block text-[10px] text-slate-400">{ajuda}</span>
                  </button>
                ))}
              </div>
            </>
          )}
        </div>

        <button
          type="button"
          onClick={() => (selecionando ? sairDaSelecao() : setSelecionando(true))}
          title="Marcar cards para apagar de uma vez"
          className={`${BOTAO_TOPO} ${selecionando ? "bg-[#612035] text-white" : "border border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-700"}`}
        >
          <Icon name="checklist" size={14} /> {selecionando ? "Sair da seleção" : "Selecionar"}
        </button>

        {temFiltro && (
          <>
            <span className="text-xs tabular-nums text-slate-400">{noQuadro + terminais.length} de {cards.length}</span>
            <button type="button" onClick={limparFiltros} className={`${BOTAO_TOPO} text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-700`}>
              Limpar
            </button>
          </>
        )}
      </div>

      {/* ------------------------------------------------------ abas do celular */}
      {ehCelular && (
        <nav className="-mx-1 flex gap-1 overflow-x-auto px-1" aria-label="Estágios">
          {COLUNAS.map(coluna => (
            <button
              key={coluna}
              type="button"
              onClick={() => setAbaCelular(coluna)}
              aria-current={abaVisivel === coluna ? "true" : undefined}
              className={`inline-flex min-h-10 shrink-0 cursor-pointer items-center gap-1.5 rounded-lg px-3 text-xs font-semibold transition-colors ${
                abaVisivel === coluna
                  ? "bg-white text-slate-900 shadow-sm dark:bg-slate-800 dark:text-white"
                  : "text-slate-500 hover:bg-white/60 dark:hover:bg-slate-800/60"
              }`}
            >
              {ROTULO_ESTAGIO[coluna]}
              <span className="tabular-nums text-slate-400">{porEstagio.get(coluna)?.length ?? 0}</span>
            </button>
          ))}
        </nav>
      )}

      {/* ---------------------------------------------------------------- quadro */}
      {error ? (
        <div className="rounded-xl bg-rose-50 p-6 text-sm text-rose-700 dark:bg-rose-900/30 dark:text-rose-200">{(error as Error).message}</div>
      ) : isLoading ? (
        <div className="flex gap-2.5 overflow-hidden">
          {COLUNAS.map(coluna => (
            <div key={coluna} className="h-96 min-w-[12.5rem] flex-1 animate-pulse rounded-2xl bg-slate-200/60 dark:bg-slate-800/60" />
          ))}
        </div>
      ) : noQuadro === 0 && terminais.length === 0 && temFiltro ? (
        <div className="esteira-fade-in flex flex-1 flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-slate-300 py-16 dark:border-slate-700">
          <Icon name="search_off" size={28} className="text-slate-300" />
          <p className="text-sm text-slate-500 dark:text-slate-400">Nenhum card com esse filtro.</p>
          <button type="button" onClick={limparFiltros} className={`${BOTAO_TOPO} border border-slate-300 text-slate-600 dark:border-slate-600 dark:text-slate-300`}>
            Limpar filtros
          </button>
        </div>
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={detectarColisao}
          onDragStart={aoComecar}
          onDragEnd={aoSoltar}
          onDragCancel={() => setArrastando(null)}
        >
          {ehCelular ? (
            // Celular: uma coluna por vez. Mover pelo detalhe do card.
            <div className="flex min-h-0 flex-1">
              <ProspeccaoColumn
                estagio={abaVisivel}
                cards={porEstagio.get(abaVisivel) ?? []}
                alvo={null}
                onAbrir={abrir}
                ordenacao={ordenacoes[abaVisivel] ?? "urgencia"}
                onOrdenar={proxima => setOrdenacoes(atual => ({ ...atual, [abaVisivel]: proxima }))}
                selecionando={selecionando}
                selecionados={selecionados}
                onAlternarSelecao={alternarSelecao}
                onSelecionarColuna={selecionarColuna}
                onNovo={abaVisivel === "NOVA" ? () => setNovaAberta(true) : undefined}
                larguraTotal
              />
            </div>
          ) : (
            <div
              className="flex min-h-0 flex-1 gap-2.5 overflow-x-auto pb-2
                [background-image:radial-gradient(circle,rgb(148_163_184/0.25)_1px,transparent_1px)] [background-size:18px_18px]"
            >
              {COLUNAS.map(coluna => (
                <ProspeccaoColumn
                  key={coluna}
                  estagio={coluna}
                  cards={porEstagio.get(coluna) ?? []}
                  alvo={alvo(coluna)}
                  onAbrir={abrir}
                  ordenacao={ordenacoes[coluna] ?? "urgencia"}
                  onOrdenar={proxima => setOrdenacoes(atual => ({ ...atual, [coluna]: proxima }))}
                  selecionando={selecionando}
                  selecionados={selecionados}
                  onAlternarSelecao={alternarSelecao}
                  onSelecionarColuna={selecionarColuna}
                  onNovo={coluna === "NOVA" ? () => setNovaAberta(true) : undefined}
                />
              ))}
            </div>
          )}

          <DragOverlay dropAnimation={{ duration: 180, easing: "cubic-bezier(0.2, 0, 0, 1)" }}>
            {arrastando ? (
              <div className="cursor-grabbing" style={{ width: larguraArrasto ?? 240 }}>
                <CardFace card={arrastando} sobreposicao />
              </div>
            ) : null}
          </DragOverlay>
        </DndContext>
      )}

      {/* ------------------------------------------------------------ desfechos */}
      {terminais.length > 0 && (
        <details className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <summary className="flex min-h-8 cursor-pointer items-center gap-3 text-slate-600 dark:text-slate-300">
            <span className="font-medium">Desfechos dos últimos 90 dias</span>
            {TERMINAIS.map(estagio => {
              const total = terminais.filter(card => card.estagio === estagio).length;
              return total > 0 ? (
                <span key={estagio} className="text-slate-400">{ROTULO_ESTAGIO[estagio]} {total}</span>
              ) : null;
            })}
          </summary>
          <ul className="mt-2 grid gap-1 sm:grid-cols-2">
            {terminais.map(card => (
              <li key={card.id}>
                <button type="button" onClick={() => abrir(card.id)} className="cursor-pointer text-left text-slate-600 hover:underline dark:text-slate-300">
                  {card.razaoSocial}
                  <span className="ml-2 text-slate-400">
                    {ROTULO_ESTAGIO[card.estagio]}
                    {card.motivoRecusa ? ` · ${ROTULO_MOTIVO[card.motivoRecusa]}` : ""}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </details>
      )}

      {selecionando && (
        <div className="esteira-fade-in sticky bottom-2 z-30 mx-auto flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-2
          text-xs shadow-lg dark:border-slate-700 dark:bg-slate-800">
          <span className="text-slate-600 dark:text-slate-300">
            {marcadosVisiveis.length === 0
              ? "Clique nos cards para marcar"
              : `${marcadosVisiveis.length} selecionado${marcadosVisiveis.length > 1 ? "s" : ""}`}
          </span>
          <button
            type="button"
            onClick={apagarSelecionados}
            disabled={marcadosVisiveis.length === 0 || excluir.isPending}
            className={`${BOTAO_TOPO} bg-rose-600 text-white hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-50`}
          >
            <Icon name="delete" size={14} />
            {excluir.isPending ? "Apagando…" : "Apagar"}
          </button>
          <button type="button" onClick={sairDaSelecao} className={`${BOTAO_TOPO} text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-700`}>
            Cancelar
          </button>
        </div>
      )}

      {cardAberto && (
        <ProspeccaoModal
          // A chave remonta o detalhe quando a ação muda: o diálogo nasce aberto sem efeito de sincronização.
          key={`${cardAberto}-${arrastesComAcao}`}
          cardId={cardAberto}
          acaoInicial={acaoInicial}
          onFechar={() => {
            setAcaoInicial(null);
            abrir(null);
          }}
        />
      )}
      {novaAberta && <NovaProspeccaoDialog onFechar={() => setNovaAberta(false)} onCriado={card => abrir(card.id)} />}
      {backfillAberto && <BackfillDialog onFechar={() => setBackfillAberto(false)} />}
      {manualAberto && <ManualDrawer onFechar={() => setManualAberto(false)} />}
    </div>
  );
}
