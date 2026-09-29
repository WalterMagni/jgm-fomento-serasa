"use client";

import { useMemo, useState } from "react";
import Icon from "@/components/ui/Icon";
import ProspeccaoColumn from "@/components/prospeccao/ProspeccaoColumn";
import ProspeccaoModal from "@/components/prospeccao/ProspeccaoModal";
import NovaProspeccaoDialog from "@/components/prospeccao/NovaProspeccaoDialog";
import BackfillDialog from "@/components/prospeccao/BackfillDialog";
import { COLUNAS, EstagioProspeccao, Prospeccao, ROTULO_ESTAGIO, ROTULO_MOTIVO } from "@/types/prospeccao";
import { Ordenacao, ordenarCards } from "@/components/prospeccao/formatters";
import {
  ConteudoExport,
  exportarEsteira,
  useEstagios,
  useProspeccaoResumo,
  useProspeccoes,
  useTransicionarProspeccao,
} from "@/hooks/useProspeccao";

/**
 * Esteira de prospecção — kanban da análise do cedente e da coleta documental.
 *
 * <p>Substitui as abas VISÃO CEDENTE e DOC'S PENDENTES da planilha de controle. É rota irmã do BI
 * em /reports/visao-cedente, não uma aba dele: esta tela é operacional, aberta muitas vezes por
 * dia e com escrita; o BI é leitura ocasional.</p>
 */
export default function ProspeccaoPage() {
  const [apenasAtrasados, setApenasAtrasados] = useState(false);
  const [cardAberto, setCardAberto] = useState<string | null>(null);
  const [novaAberta, setNovaAberta] = useState(false);
  const [arrastando, setArrastando] = useState<string | null>(null);
  const [exportAberto, setExportAberto] = useState(false);
  const [backfillAberto, setBackfillAberto] = useState(false);
  const [busca, setBusca] = useState("");
  const [responsavel, setResponsavel] = useState("");
  const [origem, setOrigem] = useState<"" | "MANUAL" | "AUTOMATICA">("");
  // Ordenação por coluna: cada uma tem um volume e um uso diferente. Triagem enche depois de
  // puxar visão cedente e é onde ordenar por nome mais ajuda.
  const [ordenacoes, setOrdenacoes] = useState<Record<string, Ordenacao>>({});

  const { data: cards = [], isLoading, error } = useProspeccoes({ apenasAtrasados });
  const { data: resumo } = useProspeccaoResumo();
  const { data: transicoes } = useEstagios();
  const transicionar = useTransicionarProspeccao();

  // O filtro não persiste entre sessões de propósito: ler localStorage no mount exigiria
  // setState em efeito, que a regra react-hooks/set-state-in-effect proíbe neste projeto, e
  // a alternativa sancionada (useSyncExternalStore com emissor próprio, porque localStorage
  // não notifica a própria aba) seria maquinaria demais para um botão de filtro.

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
   * responde a cada tecla. O CNPJ é comparado só por dígitos, para achar tanto quem digita com
   * pontuação quanto sem.
   */
  const filtrados = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    const digitos = busca.replace(/\D/g, "");

    return cards.filter(card => {
      if (responsavel && card.analistaNome !== responsavel && card.comercialNome !== responsavel) {
        return false;
      }
      if (origem && card.origem !== origem) return false;
      if (!termo) return true;
      if (digitos.length >= 2 && card.cnpj.includes(digitos)) return true;
      return card.razaoSocial.toLowerCase().includes(termo);
    });
  }, [cards, busca, responsavel, origem]);

  const temFiltro = Boolean(busca.trim() || responsavel || origem || apenasAtrasados);

  const porEstagio = useMemo(() => {
    const mapa = new Map<EstagioProspeccao, Prospeccao[]>();
    COLUNAS.forEach(coluna => mapa.set(coluna, []));
    filtrados.forEach(card => mapa.get(card.estagio)?.push(card));
    COLUNAS.forEach(coluna =>
      mapa.set(coluna, ordenarCards(mapa.get(coluna) ?? [], ordenacoes[coluna] ?? "urgencia")),
    );
    return mapa;
  }, [filtrados, ordenacoes]);

  const terminais = useMemo(
    () => filtrados.filter(card => card.estagio === "REPROVADO" || card.estagio === "REMOVIDO_RADAR"),
    [filtrados],
  );

  const noQuadro = COLUNAS.reduce((soma, coluna) => soma + (porEstagio.get(coluna)?.length ?? 0), 0);

  const cardEmArraste = arrastando ? filtrados.find(card => card.id === arrastando) : undefined;

  function aceitaDrop(destino: EstagioProspeccao) {
    if (!cardEmArraste || !transicoes) return false;
    return (transicoes[cardEmArraste.estagio] ?? []).includes(destino);
  }

  /**
   * Move por arraste. Não faz atualização otimista: aprovar materializa o checklist e fechar a
   * documentação pode ser recusado com a lista do que falta, então o estado real só se conhece
   * depois da resposta. O card fica esmaecido enquanto a requisição corre.
   */
  function soltar(id: string, destino: EstagioProspeccao) {
    setArrastando(null);
    const card = filtrados.find(item => item.id === id);
    if (!card || card.estagio === destino) return;

    const exigeMotivo = destino === "REPROVADO" || destino === "REMOVIDO_RADAR";
    if (exigeMotivo) {
      // Motivo estruturado é pedido no painel, que tem a lista completa.
      setCardAberto(id);
      return;
    }
    transicionar.mutate({ id, estagio: destino });
  }

  function moverPorMenu(card: Prospeccao) {
    const destinos = transicoes?.[card.estagio] ?? [];
    if (destinos.length === 0) {
      setCardAberto(card.id);
      return;
    }
    const escolha = window.prompt(
      `Mover "${card.razaoSocial}" para:\n\n${destinos.map((destino, i) => `${i + 1}. ${ROTULO_ESTAGIO[destino]}`).join("\n")}\n\nDigite o número:`,
    );
    const indice = Number(escolha) - 1;
    if (!escolha || Number.isNaN(indice) || indice < 0 || indice >= destinos.length) return;
    soltar(card.id, destinos[indice]);
  }

  return (
    <div className="flex h-full flex-col gap-3 p-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Esteira de prospecção</h1>
            <span
              className="rounded border border-[#D1732C] px-1.5 py-px text-[10px] font-bold uppercase
                tracking-wider text-[#D1732C]"
              title="Em avaliação: o fluxo ainda pode mudar e os dados podem ser reiniciados"
            >
              beta
            </span>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Da análise do cedente até a documentação completa
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {resumo && (
            <div className="flex items-center gap-3 rounded-lg bg-white px-3 py-1.5 text-xs shadow-sm dark:bg-slate-800">
              <span className="text-slate-600 dark:text-slate-300">{resumo.total} em aberto</span>
              {resumo.slaEmAtencao > 0 && (
                <span className="text-[#D1732C]">{resumo.slaEmAtencao} perto do prazo</span>
              )}
              {resumo.slaEstourado > 0 && (
                <span className="font-semibold text-red-600 dark:text-red-400">
                  {resumo.slaEstourado} atrasado{resumo.slaEstourado > 1 ? "s" : ""}
                </span>
              )}
              {resumo.silencioProlongado > 0 && (
                <span className="text-red-600 dark:text-red-400">{resumo.silencioProlongado} sem resposta</span>
              )}
            </div>
          )}

          <button
            type="button"
            onClick={() => setApenasAtrasados(valor => !valor)}
            className={`inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs shadow-sm ${
              apenasAtrasados
                ? "bg-red-600 text-white"
                : "bg-white text-slate-600 dark:bg-slate-800 dark:text-slate-300"
            }`}
          >
            <Icon name="filter_alt" className="text-[15px]" />
            {apenasAtrasados ? "só atrasados" : "todos"}
          </button>

          <div className="relative">
            <button
              type="button"
              onClick={() => setExportAberto(aberto => !aberto)}
              className="inline-flex items-center gap-1 rounded-lg bg-white px-2.5 py-1.5 text-xs text-slate-600
                shadow-sm dark:bg-slate-800 dark:text-slate-300"
            >
              <Icon name="table_chart" className="text-[15px]" />
              Exportar
            </button>
            {exportAberto && (
              <>
                {/* Fecha ao clicar fora, sem prender o foco da página. */}
                <div className="fixed inset-0 z-10" onClick={() => setExportAberto(false)} />
                <div className="absolute right-0 z-20 mt-1 w-60 rounded-lg bg-white py-1 text-xs shadow-lg
                  dark:bg-slate-800">
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
                      className="block w-full px-3 py-1.5 text-left hover:bg-slate-50 dark:hover:bg-slate-700"
                    >
                      <span className="block text-slate-700 dark:text-slate-200">{titulo}</span>
                      <span className="block text-[10px] text-slate-400">{ajuda}</span>
                    </button>
                  ))}
                  <p className="border-t border-slate-100 px-3 pt-1.5 text-[10px] text-slate-400 dark:border-slate-700">
                    CSV que o Excel abre. Respeita o filtro atual.
                  </p>
                </div>
              </>
            )}
          </div>

          <button
            type="button"
            onClick={() => setBackfillAberto(true)}
            title="Traz para a esteira as análises com visão cedente SIM que já existiam"
            className="inline-flex cursor-pointer items-center gap-1 rounded-lg border border-slate-200
              bg-white px-2.5 py-1.5 text-xs text-slate-600 shadow-sm transition-colors hover:bg-slate-50
              focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
              dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
          >
            <Icon name="download" className="text-[15px]" />
            puxar visão cedente
          </button>

          <button
            type="button"
            onClick={() => setNovaAberta(true)}
            className="inline-flex items-center gap-1 rounded-lg bg-[#612035] px-3 py-1.5 text-xs text-white shadow-sm"
          >
            <Icon name="add" className="text-[15px]" />
            Nova prospecção
          </button>
        </div>
      </header>

      <div className="flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2
        shadow-sm dark:border-slate-700 dark:bg-slate-800">
        <div className="relative min-w-[16rem] flex-1">
          <Icon
            name="search"
            className="pointer-events-none absolute left-2 top-1/2 -translate-y-1/2 text-[16px] text-slate-400"
          />
          <input
            type="search"
            value={busca}
            onChange={event => setBusca(event.target.value)}
            placeholder="Buscar por razão social ou CNPJ…"
            aria-label="Buscar por razão social ou CNPJ"
            className="w-full rounded-md border border-slate-300 py-1.5 pl-8 pr-2 text-xs
              focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
              dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
          />
        </div>

        <label className="flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400">
          Responsável
          <select
            value={responsavel}
            onChange={event => setResponsavel(event.target.value)}
            className="cursor-pointer rounded-md border border-slate-300 px-2 py-1.5 text-xs
              focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
              dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
          >
            <option value="">todos</option>
            {responsaveis.map(nome => (
              <option key={nome} value={nome}>{nome}</option>
            ))}
          </select>
        </label>

        <label className="flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400">
          Entrada
          <select
            value={origem}
            onChange={event => setOrigem(event.target.value as "" | "MANUAL" | "AUTOMATICA")}
            className="cursor-pointer rounded-md border border-slate-300 px-2 py-1.5 text-xs
              focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
              dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
          >
            <option value="">todas</option>
            <option value="MANUAL">manual</option>
            <option value="AUTOMATICA">visão cedente</option>
          </select>
        </label>

        {temFiltro && (
          <>
            <span className="text-xs text-slate-400">
              {noQuadro + terminais.length} de {cards.length}
            </span>
            <button
              type="button"
              onClick={() => {
                setBusca("");
                setResponsavel("");
                setOrigem("");
                setApenasAtrasados(false);
              }}
              className="cursor-pointer rounded px-2 py-1 text-xs text-slate-500 transition-colors
                hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus-visible:ring-2
                focus-visible:ring-[#612035] dark:text-slate-400 dark:hover:bg-slate-700"
            >
              limpar
            </button>
          </>
        )}
      </div>

      {isLoading && <p className="p-8 text-center text-sm text-slate-500">Carregando a esteira…</p>}
      {error && <p className="p-8 text-center text-sm text-red-600">{error.message}</p>}

      {!isLoading && !error && noQuadro === 0 && terminais.length === 0 && temFiltro && (
        <div className="esteira-fade-in flex flex-1 flex-col items-center justify-center gap-2 rounded-lg
          border border-dashed border-slate-300 py-16 dark:border-slate-700">
          <Icon name="search_off" className="text-[28px] text-slate-300" />
          <p className="text-sm text-slate-500 dark:text-slate-400">Nenhum card com esse filtro.</p>
          <button
            type="button"
            onClick={() => {
              setBusca("");
              setResponsavel("");
              setOrigem("");
              setApenasAtrasados(false);
            }}
            className="cursor-pointer rounded border border-slate-300 px-3 py-1 text-xs text-slate-600
              transition-colors hover:bg-slate-50 focus:outline-none focus-visible:ring-2
              focus-visible:ring-[#612035] dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
          >
            Limpar filtros
          </button>
        </div>
      )}

      {!isLoading && !error && (noQuadro > 0 || terminais.length > 0 || !temFiltro) && (
        <div className="flex flex-1 gap-3 overflow-x-auto pb-2">
          {COLUNAS.map(coluna => (
            <ProspeccaoColumn
              key={coluna}
              estagio={coluna}
              cards={porEstagio.get(coluna) ?? []}
              cardArrastando={arrastando}
              aceitaDrop={aceitaDrop(coluna)}
              onDragStart={setArrastando}
              onDragEnd={() => setArrastando(null)}
              onDrop={soltar}
              onAbrir={setCardAberto}
              onMover={moverPorMenu}
              ordenacao={ordenacoes[coluna] ?? "urgencia"}
              onOrdenar={proxima => setOrdenacoes(atual => ({ ...atual, [coluna]: proxima }))}
            />
          ))}
        </div>
      )}

      {terminais.length > 0 && (
        <details className="rounded-lg bg-white px-3 py-2 text-xs shadow-sm dark:bg-slate-800">
          <summary className="cursor-pointer text-slate-600 dark:text-slate-300">
            Reprovados e removidos do radar ({terminais.length})
          </summary>
          <ul className="mt-2 space-y-1">
            {terminais.map(card => (
              <li key={card.id}>
                <button
                  type="button"
                  onClick={() => setCardAberto(card.id)}
                  className="text-left text-slate-600 hover:underline dark:text-slate-300"
                >
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

      {cardAberto && <ProspeccaoModal cardId={cardAberto} onFechar={() => setCardAberto(null)} />}
      {novaAberta && <NovaProspeccaoDialog onFechar={() => setNovaAberta(false)} />}
      {backfillAberto && <BackfillDialog onFechar={() => setBackfillAberto(false)} />}
    </div>
  );
}
