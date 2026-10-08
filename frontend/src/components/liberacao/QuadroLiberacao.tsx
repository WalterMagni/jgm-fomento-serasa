"use client";

import { useCallback, useMemo, useState, useSyncExternalStore } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  closestCenter,
  DndContext,
  pointerWithin,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  TouchSensor,
  useSensor,
  useSensors,
  type CollisionDetection,
  type DragEndEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import { toast } from "sonner";
import Icon from "@/components/ui/Icon";
import ManualDrawer from "@/components/prospeccao/ManualDrawer";
import { MANUAL_LIBERACAO } from "@/content/manual-liberacao";
import BarraFiltros from "@/components/liberacao/BarraFiltros";
import { aplicarFiltros, escreverFiltros, finalizadosDesde, lerFiltros, ordenarCards, type Filtros } from "@/components/liberacao/filtros";
import CardDetalheModal from "@/components/liberacao/CardDetalheModal";
import LiberacaoColumn from "@/components/liberacao/LiberacaoColumn";
import { CardFace } from "@/components/liberacao/LiberacaoCardItem";
import NovoCardDialog from "@/components/liberacao/NovoCardDialog";
import { COR_ETAPA, aguardando } from "@/components/liberacao/formatters";
import { useMovimento } from "@/components/liberacao/useMovimento";
import { useTempoReal } from "@/components/notificacoes/NotificacoesProvider";
import { exportarLiberacao, useLiberacaoCards, useLiberacaoResumo, useUsuarioAtual } from "@/hooks/useLiberacao";
import { ETAPAS, ROTULO_ETAPA_CURTO, type EtapaLiberacao, type LiberacaoCard } from "@/types/liberacao";

/**
 * Onde o card cai: na coluna em que está o ponteiro, e não na que a cópia arrastada encosta.
 *
 * <p>A cópia que segue o mouse pode ser mais larga que a coluna e encostar na vizinha. Com a
 * regra padrão (sobreposição de retângulos), um clique com o mouse mexendo poucos pixels movia o
 * card da Origem para o Comitê sem ninguém ter arrastado de verdade. Pelo teclado não há
 * ponteiro, e vale a coluna mais próxima.</p>
 */
const detectarColisao: CollisionDetection = args =>
  args.pointerCoordinates ? pointerWithin(args) : closestCenter(args);

const CELULAR = "(max-width: 767px)";

/**
 * Celular ou não, lido do navegador.
 *
 * <p>Os dois layouts não podem coexistir escondidos por CSS: cada card registraria dois
 * arrastáveis com o mesmo id no dnd-kit, e soltar um card movia outro junto.</p>
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

export default function QuadroLiberacao() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const cardAberto = params.get("card");

  const { data: eu } = useUsuarioAtual();
  const tempoReal = useTempoReal();
  const filtros = useMemo(() => lerFiltros(new URLSearchParams(params.toString())), [params]);
  const { data: cards = [], isLoading, error } = useLiberacaoCards(finalizadosDesde(filtros.finalizados), tempoReal);
  const { data: resumo } = useLiberacaoResumo();
  const movimento = useMovimento(eu?.id);
  const ehCelular = useEhCelular();

  const [manualAberto, setManualAberto] = useState(false);
  const [exportando, setExportando] = useState(false);
  const [novoAberto, setNovoAberto] = useState(false);
  const [arrastando, setArrastando] = useState<LiberacaoCard | null>(null);
  const [larguraArrasto, setLarguraArrasto] = useState<number | null>(null);
  const [abaCelular, setAbaCelular] = useState<EtapaLiberacao>("ORIGEM");

  // O card aberto mora na URL: o link do card pode ser mandado para a colega, e a notificação
  // (fase 3) só precisa apontar para ?card=<id>.
  const abrir = useCallback(
    (id: string | null) => {
      const novos = new URLSearchParams(params.toString());
      if (id) novos.set("card", id);
      else novos.delete("card");
      router.replace(`${pathname}${novos.size ? `?${novos}` : ""}`, { scroll: false });
    },
    [params, pathname, router],
  );

  const mudarFiltros = useCallback(
    (novos: Filtros) => {
      const escritos = escreverFiltros(new URLSearchParams(params.toString()), novos);
      router.replace(`${pathname}${escritos.size ? `?${escritos}` : ""}`, { scroll: false });
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

  const filtrados = useMemo(() => aplicarFiltros(cards, filtros, eu?.id), [cards, filtros, eu?.id]);

  // "Ocultar finalizados" tira a coluna de decididos e dá o espaço às três de trabalho.
  const etapas = useMemo(
    () => (filtros.finalizados === "ocultar" ? ETAPAS.filter(etapa => etapa !== "FINALIZADO") : ETAPAS),
    [filtros.finalizados],
  );

  // Aba do celular numa coluna que acabou de ser ocultada volta para a Origem.
  const abaVisivel = etapas.includes(abaCelular) ? abaCelular : "ORIGEM";

  const porEtapa = useMemo(() => {
    const mapa = new Map<EtapaLiberacao, LiberacaoCard[]>(ETAPAS.map(etapa => [etapa, []]));
    filtrados.forEach(card => mapa.get(card.etapa)?.push(card));
    ETAPAS.forEach(etapa => mapa.set(etapa, ordenarCards(mapa.get(etapa) ?? [], filtros.ordem)));
    return mapa;
  }, [filtrados, filtros.ordem]);

  async function exportar() {
    setExportando(true);
    try {
      await exportarLiberacao(filtrados.map(card => card.id));
      toast.success(`Relatório com ${filtrados.length} card${filtrados.length === 1 ? "" : "s"} baixado`);
    } catch (erro) {
      toast.error((erro as Error).message);
    } finally {
      setExportando(false);
    }
  }

  const meusPareceres = useMemo(
    () => cards.filter(card => card.etapa === "COMITE" && aguardando(card).some(parecer => parecer.usuarioId === eu?.id)),
    [cards, eu?.id],
  );

  function alvo(etapa: EtapaLiberacao) {
    if (!arrastando) return null;
    if (etapa === arrastando.etapa) return { permitido: false, motivo: null, origem: true };
    const destino = arrastando.destinos.find(item => item.etapa === etapa);
    return {
      permitido: destino?.permitido ?? false,
      motivo: destino ? destino.motivo : null,
      origem: false,
    };
  }

  function aoComecar(event: DragStartEvent) {
    setArrastando((event.active.data.current?.card as LiberacaoCard) ?? null);
    // A cópia tem a largura do card de origem, para não invadir a coluna vizinha.
    setLarguraArrasto(event.active.rect.current.initial?.width ?? null);
  }

  function aoSoltar(event: DragEndEvent) {
    const card = event.active.data.current?.card as LiberacaoCard | undefined;
    setArrastando(null);
    const destino = event.over?.id as EtapaLiberacao | undefined;
    if (card && destino && destino !== card.etapa) movimento.pedir(card, destino);
  }

  return (
    <div className="flex h-[calc(100dvh-7rem)] flex-col gap-4 lg:h-[calc(100dvh-8rem)]">
      {/* ------------------------------------------------------------ cabeçalho */}
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="font-display text-2xl font-semibold text-slate-900 dark:text-white">Esteira de Liberação</h1>
            <span className="rounded border border-slate-300 px-1 py-px text-[9px] font-bold uppercase tracking-wider text-slate-500 dark:border-slate-600">
              beta
            </span>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400">Da origem da análise à decisão do Comitê</p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setManualAberto(true)}
            className="inline-flex min-h-10 cursor-pointer items-center gap-1.5 rounded-lg px-3 text-sm text-slate-600 transition-colors hover:bg-white
              focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] dark:text-slate-300 dark:hover:bg-slate-800"
          >
            <Icon name="help" size={16} /> Ajuda
          </button>
          <button
            type="button"
            onClick={() => setNovoAberto(true)}
            className="inline-flex min-h-10 cursor-pointer items-center gap-1.5 rounded-lg bg-[#612035] px-4 text-sm font-medium text-white shadow-sm
              transition-colors hover:bg-[#4e1a2a] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] focus-visible:ring-offset-2"
          >
            <Icon name="add" size={16} /> Nova análise
          </button>
        </div>
      </header>

      {/* -------------------------------------------------------- pede atenção */}
      {resumo && (resumo.total > 0 || resumo.atrasados > 0) && (
        <div className="esteira-fade-in flex flex-wrap items-center gap-x-4 gap-y-2 rounded-xl border border-[#612035]/15 bg-gradient-to-r
          from-[#612035]/[0.07] via-[#612035]/[0.03] to-transparent px-4 py-2.5 text-sm dark:border-[#e8a3b6]/20 dark:from-[#612035]/30">
          <span className="inline-flex items-center gap-2 font-semibold text-[#612035] dark:text-[#e8a3b6]">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[#612035] opacity-60 motion-reduce:hidden dark:bg-[#e8a3b6]" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-[#612035] dark:bg-[#e8a3b6]" />
            </span>
            Pede atenção
          </span>
          {resumo.pareceresAguardando > 0 && (
            <button
              type="button"
              onClick={() => meusPareceres[0] && abrir(meusPareceres[0].id)}
              className="cursor-pointer text-slate-700 underline-offset-2 hover:underline dark:text-slate-200"
            >
              {resumo.pareceresAguardando === 1 ? "1 parecer seu no Comitê" : `${resumo.pareceresAguardando} pareceres seus no Comitê`}
            </button>
          )}
          {resumo.pendenciasParaMim > 0 && (
            <span className="text-slate-700 dark:text-slate-200">
              {resumo.pendenciasParaMim} pendência{resumo.pendenciasParaMim > 1 ? "s" : ""} para você responder
            </span>
          )}
          {resumo.atrasados > 0 && (
            <button
              type="button"
              onClick={() => mudarFiltros({ ...filtros, prazo: "vencido" })}
              title="Mostrar só os cards com prazo vencido"
              className="inline-flex cursor-pointer items-center gap-1 font-medium text-rose-700 underline-offset-2 hover:underline dark:text-rose-300"
            >
              <Icon name="schedule" size={13} />
              {resumo.atrasados === 1 ? "1 card com prazo vencido" : `${resumo.atrasados} cards com prazo vencido`}
            </button>
          )}
        </div>
      )}

      <BarraFiltros
        filtros={filtros}
        onMudar={mudarFiltros}
        visiveis={filtrados.length}
        total={cards.length}
        onExportar={exportar}
        exportando={exportando}
      />

      {/* ------------------------------------------------------ abas do celular */}
      {ehCelular && (
        <nav className="-mx-1 flex gap-1 overflow-x-auto px-1" aria-label="Etapas">
          {etapas.map(etapa => (
            <button
              key={etapa}
              type="button"
              onClick={() => setAbaCelular(etapa)}
              aria-current={abaVisivel === etapa ? "true" : undefined}
              className={`inline-flex min-h-10 shrink-0 cursor-pointer items-center gap-1.5 rounded-lg px-3 text-xs font-semibold transition-colors ${
                abaVisivel === etapa
                  ? "bg-white text-slate-900 shadow-sm dark:bg-slate-800 dark:text-white"
                  : "text-slate-500 hover:bg-white/60 dark:hover:bg-slate-800/60"
              }`}
            >
              <span className={`h-2 w-2 rounded-full ${COR_ETAPA[etapa].faixa}`} />
              {ROTULO_ETAPA_CURTO[etapa]}
              <span className="tabular-nums text-slate-400">{porEtapa.get(etapa)?.length ?? 0}</span>
            </button>
          ))}
        </nav>
      )}

      {/* ---------------------------------------------------------------- quadro */}
      {error ? (
        <div className="rounded-xl bg-rose-50 p-6 text-sm text-rose-700 dark:bg-rose-900/30 dark:text-rose-200">{(error as Error).message}</div>
      ) : isLoading ? (
        <div className="flex gap-3 overflow-hidden">
          {ETAPAS.map(etapa => (
            <div key={etapa} className="h-96 w-[17.5rem] shrink-0 animate-pulse rounded-2xl bg-slate-200/60 dark:bg-slate-800/60" />
          ))}
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
              <LiberacaoColumn
                etapa={abaVisivel}
                cards={porEtapa.get(abaVisivel) ?? []}
                alvo={null}
                onAbrir={abrir}
                onNovo={abaVisivel === "ORIGEM" ? () => setNovoAberto(true) : undefined}
                larguraTotal
              />
            </div>
          ) : (
            // Desktop/tablet: as cinco colunas lado a lado, rolando na horizontal se faltar espaço.
            <div
              className="flex min-h-0 flex-1 gap-2.5 overflow-x-auto pb-2
                [background-image:radial-gradient(circle,rgb(148_163_184/0.25)_1px,transparent_1px)] [background-size:18px_18px]"
            >
              {etapas.map(etapa => (
                <LiberacaoColumn
                  key={etapa}
                  etapa={etapa}
                  cards={porEtapa.get(etapa) ?? []}
                  alvo={alvo(etapa)}
                  onAbrir={abrir}
                  onNovo={etapa === "ORIGEM" ? () => setNovoAberto(true) : undefined}
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

      {novoAberto && (
        <NovoCardDialog
          onFechar={() => setNovoAberto(false)}
          onCriado={card => {
            setNovoAberto(false);
            abrir(card.id);
          }}
        />
      )}

      {cardAberto && (
        <CardDetalheModal
          cardId={cardAberto}
          euId={eu?.id}
          ehAnalista={eu?.analista ?? false}
          onFechar={() => abrir(null)}
          onMover={(card, para) => movimento.pedir(card, para)}
        />
      )}

      {/* Depois do detalhe: a confirmação de mover precisa ficar por cima dele. */}
      {movimento.dialogo}

      {manualAberto && <ManualDrawer conteudo={MANUAL_LIBERACAO} rotulo="Manual de uso da esteira de liberação" onFechar={() => setManualAberto(false)} />}
    </div>
  );
}
