"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import Icon from "@/components/ui/Icon";
import { useDiretorio, useEtiquetas } from "@/hooks/useLiberacao";
import { ROTULO_TIPO, TIPOS_OPERACAO, type TipoOperacao } from "@/types/liberacao";
import Avatar from "./Avatar";
import { TOM } from "./cores";
import {
  ROTULO_FINALIZADOS,
  ROTULO_ORDEM,
  ROTULO_PRAZO,
  contarAtivos,
  type Filtros,
  type Finalizados,
  type FiltroPrazo,
  type Ordem,
} from "./filtros";

type Props = {
  filtros: Filtros;
  onMudar: (filtros: Filtros) => void;
  visiveis: number;
  total: number;
  onExportar: () => void;
  exportando: boolean;
};

const CONTROLE =
  "inline-flex min-h-10 cursor-pointer items-center gap-1.5 rounded-lg border px-3 text-xs font-medium transition-colors focus:outline-none " +
  "focus-visible:ring-2 focus-visible:ring-[#612035]";
const NEUTRO = "border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700";
const LIGADO = "border-[#612035] bg-[#612035] text-white";

function Chip({ ativo, onClick, children }: { ativo: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={ativo}
      onClick={onClick}
      className={`inline-flex min-h-8 cursor-pointer items-center gap-1 rounded-lg border px-2.5 text-[11px] font-medium transition-colors ${
        ativo ? LIGADO : NEUTRO
      }`}
    >
      {children}
    </button>
  );
}

function alternar<T>(lista: T[], item: T) {
  return lista.includes(item) ? lista.filter(atual => atual !== item) : [...lista, item];
}

/**
 * Barra de filtros do quadro.
 *
 * <p>O que se usa todo dia fica à vista — busca, "só os meus", finalizados e ordenação. O resto
 * (membro, etiqueta, tipo, quem criou, período, prazo) mora no painel Filtros, e o que estiver
 * ligado aparece como chip removível embaixo, para ninguém esquecer um filtro escondendo card.</p>
 */
export default function BarraFiltros({ filtros, onMudar, visiveis, total, onExportar, exportando }: Props) {
  const { data: pessoas = [] } = useDiretorio();
  const { data: etiquetas = [] } = useEtiquetas();
  const [painel, setPainel] = useState(false);
  const caixa = useRef<HTMLDivElement>(null);
  const [busca, setBusca] = useState(filtros.busca);
  const ativos = contarAtivos(filtros);
  const mudar = (parcial: Partial<Filtros>) => onMudar({ ...filtros, ...parcial });

  // A busca vai para a URL depois que a pessoa para de digitar, e não a cada tecla.
  const mudarRef = useRef(onMudar);
  const filtrosRef = useRef(filtros);
  useEffect(() => {
    mudarRef.current = onMudar;
    filtrosRef.current = filtros;
  });
  useEffect(() => {
    if (busca === filtrosRef.current.busca) return;
    const timer = setTimeout(() => mudarRef.current({ ...filtrosRef.current, busca }), 250);
    return () => clearTimeout(timer);
  }, [busca]);

  useEffect(() => {
    if (!painel) return;
    function fora(event: MouseEvent) {
      if (caixa.current && !caixa.current.contains(event.target as Node)) setPainel(false);
    }
    function esc(event: KeyboardEvent) {
      if (event.key === "Escape") setPainel(false);
    }
    document.addEventListener("mousedown", fora);
    document.addEventListener("keydown", esc);
    return () => {
      document.removeEventListener("mousedown", fora);
      document.removeEventListener("keydown", esc);
    };
  }, [painel]);

  const nome = (id: string) => pessoas.find(pessoa => pessoa.id === id)?.nome ?? "…";
  const chipsAtivos: { chave: string; rotulo: ReactNode; tirar: () => void }[] = [
    ...filtros.membros.map(id => ({ chave: `m-${id}`, rotulo: <>membro: {nome(id)}</>, tirar: () => mudar({ membros: filtros.membros.filter(item => item !== id) }) })),
    ...filtros.etiquetas.map(id => {
      const etiqueta = etiquetas.find(item => item.id === id);
      return { chave: `e-${id}`, rotulo: <>etiqueta: {etiqueta?.nome ?? "…"}</>, tirar: () => mudar({ etiquetas: filtros.etiquetas.filter(item => item !== id) }) };
    }),
    ...filtros.tipos.map(tipo => ({ chave: `t-${tipo}`, rotulo: <>{ROTULO_TIPO[tipo]}</>, tirar: () => mudar({ tipos: filtros.tipos.filter(item => item !== tipo) }) })),
    ...(filtros.criador ? [{ chave: "c", rotulo: <>criado por {nome(filtros.criador)}</>, tirar: () => mudar({ criador: null }) }] : []),
    ...(filtros.de || filtros.ate
      ? [{
          chave: "p",
          rotulo: <>criado {filtros.de ? `de ${filtros.de.split("-").reverse().join("/")}` : ""} {filtros.ate ? `até ${filtros.ate.split("-").reverse().join("/")}` : ""}</>,
          tirar: () => mudar({ de: null, ate: null }),
        }]
      : []),
    ...(filtros.prazo ? [{ chave: "z", rotulo: <>{ROTULO_PRAZO[filtros.prazo]}</>, tirar: () => mudar({ prazo: null }) }] : []),
  ];

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-0 flex-1 sm:max-w-xs">
          <Icon name="search" size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={busca}
            onChange={event => setBusca(event.target.value)}
            placeholder="Cedente, sacado, CNPJ, #número"
            aria-label="Buscar card"
            className="min-h-10 w-full rounded-lg border border-slate-200 bg-white pl-9 pr-8 text-sm text-slate-800 placeholder:text-slate-400
              focus:border-[#612035] focus:outline-none focus:ring-2 focus:ring-[#612035]/20 dark:border-slate-700 dark:bg-slate-800 dark:text-white"
          />
          {busca && (
            <button
              type="button"
              aria-label="Limpar busca"
              onClick={() => setBusca("")}
              className="absolute right-2 top-1/2 flex h-6 w-6 -translate-y-1/2 cursor-pointer items-center justify-center rounded text-slate-400 hover:text-slate-700"
            >
              <Icon name="close" size={13} />
            </button>
          )}
        </div>

        <button type="button" aria-pressed={filtros.meus} onClick={() => mudar({ meus: !filtros.meus })} className={`${CONTROLE} ${filtros.meus ? LIGADO : NEUTRO}`}>
          <Icon name="person" size={14} /> Só os meus
        </button>

        <div className="relative" ref={caixa}>
          <button
            type="button"
            aria-expanded={painel}
            onClick={() => setPainel(atual => !atual)}
            className={`${CONTROLE} ${ativos > 0 ? "border-[#612035] text-[#612035] dark:border-[#e8a3b6] dark:text-[#e8a3b6]" : ""} ${ativos > 0 ? "bg-white dark:bg-slate-800" : NEUTRO}`}
          >
            <Icon name="filter_alt" size={14} /> Filtros
            {ativos > 0 && <span className="rounded-full bg-[#612035] px-1.5 text-[10px] font-bold text-white">{ativos}</span>}
          </button>
          {painel && (
            <div
              role="dialog"
              aria-label="Filtros"
              className="esteira-modal-in absolute left-0 z-30 mt-1 w-[min(22rem,calc(100vw-2rem))] space-y-4 rounded-2xl border border-slate-200 bg-white p-4
                shadow-2xl dark:border-slate-700 dark:bg-slate-900"
            >
              <section>
                <h4 className="mb-1.5 text-[11px] font-bold uppercase tracking-[0.08em] text-slate-500">Membro</h4>
                <div className="flex max-h-36 flex-wrap gap-1.5 overflow-y-auto">
                  {pessoas.map(pessoa => (
                    <Chip key={pessoa.id} ativo={filtros.membros.includes(pessoa.id)} onClick={() => mudar({ membros: alternar(filtros.membros, pessoa.id) })}>
                      <Avatar nome={pessoa.nome} iniciais={pessoa.iniciais} tamanho="xs" className="-ml-1 !h-5 !w-5 !text-[9px] !ring-0" />
                      {pessoa.nome.split(" ")[0]}
                    </Chip>
                  ))}
                </div>
              </section>
              {etiquetas.length > 0 && (
                <section>
                  <h4 className="mb-1.5 text-[11px] font-bold uppercase tracking-[0.08em] text-slate-500">Etiqueta</h4>
                  <div className="flex flex-wrap gap-1.5">
                    {etiquetas.map(etiqueta => (
                      <button
                        key={etiqueta.id}
                        type="button"
                        aria-pressed={filtros.etiquetas.includes(etiqueta.id)}
                        onClick={() => mudar({ etiquetas: alternar(filtros.etiquetas, etiqueta.id) })}
                        className={`min-h-8 cursor-pointer rounded-lg px-2.5 text-[11px] font-semibold ${TOM[etiqueta.cor].chip} ${
                          filtros.etiquetas.includes(etiqueta.id) ? "ring-2 ring-slate-900 ring-offset-1 dark:ring-white dark:ring-offset-slate-900" : "opacity-80 hover:opacity-100"
                        }`}
                      >
                        {etiqueta.nome}
                      </button>
                    ))}
                  </div>
                </section>
              )}
              <section>
                <h4 className="mb-1.5 text-[11px] font-bold uppercase tracking-[0.08em] text-slate-500">Tipo de operação</h4>
                <div className="flex flex-wrap gap-1.5">
                  {TIPOS_OPERACAO.map(tipo => (
                    <Chip key={tipo} ativo={filtros.tipos.includes(tipo)} onClick={() => mudar({ tipos: alternar<TipoOperacao>(filtros.tipos, tipo) })}>
                      {ROTULO_TIPO[tipo]}
                    </Chip>
                  ))}
                </div>
              </section>
              <section>
                <h4 className="mb-1.5 text-[11px] font-bold uppercase tracking-[0.08em] text-slate-500">Prazo</h4>
                <div className="flex flex-wrap gap-1.5">
                  {(Object.keys(ROTULO_PRAZO) as FiltroPrazo[]).map(prazo => (
                    <Chip key={prazo} ativo={filtros.prazo === prazo} onClick={() => mudar({ prazo: filtros.prazo === prazo ? null : prazo })}>
                      {ROTULO_PRAZO[prazo]}
                    </Chip>
                  ))}
                </div>
              </section>
              <section className="grid grid-cols-2 gap-2">
                <h4 className="col-span-2 text-[11px] font-bold uppercase tracking-[0.08em] text-slate-500">Criado</h4>
                <label className="text-[11px] text-slate-500">
                  de
                  <input
                    type="date"
                    value={filtros.de ?? ""}
                    onChange={event => mudar({ de: event.target.value || null })}
                    className="mt-0.5 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                  />
                </label>
                <label className="text-[11px] text-slate-500">
                  até
                  <input
                    type="date"
                    value={filtros.ate ?? ""}
                    onChange={event => mudar({ ate: event.target.value || null })}
                    className="mt-0.5 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                  />
                </label>
                <label className="col-span-2 text-[11px] text-slate-500">
                  por
                  <select
                    value={filtros.criador ?? ""}
                    onChange={event => mudar({ criador: event.target.value || null })}
                    className="mt-0.5 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs dark:border-slate-700 dark:bg-slate-800 dark:text-white"
                  >
                    <option value="">qualquer pessoa</option>
                    {pessoas.map(pessoa => (
                      <option key={pessoa.id} value={pessoa.id}>
                        {pessoa.nome}
                      </option>
                    ))}
                  </select>
                </label>
              </section>
              {ativos > 0 && (
                <button
                  type="button"
                  onClick={() => mudar({ membros: [], etiquetas: [], tipos: [], criador: null, de: null, ate: null, prazo: null })}
                  className="text-xs font-medium text-[#612035] hover:underline dark:text-[#e8a3b6]"
                >
                  limpar filtros
                </button>
              )}
            </div>
          )}
        </div>

        <label className={`${CONTROLE} ${NEUTRO} relative`}>
          <Icon name="task_alt" size={14} />
          <span className="hidden sm:inline">Finalizados:</span>
          <select
            value={filtros.finalizados}
            onChange={event => mudar({ finalizados: event.target.value as Finalizados })}
            aria-label="Finalizados"
            className="cursor-pointer bg-transparent pr-1 text-xs font-semibold text-slate-800 focus:outline-none dark:text-slate-100"
          >
            {(Object.keys(ROTULO_FINALIZADOS) as Finalizados[]).map(opcao => (
              <option key={opcao} value={opcao}>
                {ROTULO_FINALIZADOS[opcao]}
              </option>
            ))}
          </select>
        </label>

        <label className={`${CONTROLE} ${NEUTRO}`}>
          <Icon name="sort" size={14} />
          <span className="hidden sm:inline">Ordenar:</span>
          <select
            value={filtros.ordem}
            onChange={event => mudar({ ordem: event.target.value as Ordem })}
            aria-label="Ordenar"
            className="cursor-pointer bg-transparent pr-1 text-xs font-semibold text-slate-800 focus:outline-none dark:text-slate-100"
          >
            {(Object.keys(ROTULO_ORDEM) as Ordem[]).map(opcao => (
              <option key={opcao} value={opcao}>
                {ROTULO_ORDEM[opcao]}
              </option>
            ))}
          </select>
        </label>

        <button
          type="button"
          onClick={onExportar}
          disabled={exportando || visiveis === 0}
          title="Planilha .xlsx com os cards visíveis, respeitando os filtros"
          className={`${CONTROLE} ${NEUTRO} sm:ml-auto disabled:cursor-not-allowed disabled:opacity-50`}
        >
          {exportando ? <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-slate-300 border-t-[#612035]" /> : <Icon name="table_chart" size={14} />}
          Relatório XLSX
        </button>
      </div>

      {(chipsAtivos.length > 0 || filtros.busca || filtros.meus) && (
        <div className="flex flex-wrap items-center gap-1.5 text-[11px]">
          <span className="text-slate-500">
            {visiveis} de {total} card{total === 1 ? "" : "s"}
          </span>
          {chipsAtivos.map(chip => (
            <span key={chip.chave} className="inline-flex items-center gap-1 rounded-full bg-[#612035]/8 py-0.5 pl-2.5 pr-1 text-[#612035] dark:bg-[#612035]/30 dark:text-[#f2c4d1]">
              {chip.rotulo}
              <button type="button" aria-label="Remover filtro" onClick={chip.tirar} className="flex h-5 w-5 cursor-pointer items-center justify-center rounded-full hover:bg-[#612035]/15">
                <Icon name="close" size={10} />
              </button>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
