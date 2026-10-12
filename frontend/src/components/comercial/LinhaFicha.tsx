"use client";

import Icon from "@/components/ui/Icon";
import { caixaTitulo, formatCnpj, iniciais, tempoRelativo, tomDoAvatar } from "@/lib/formatadores";
import { LIQUIDACAO, formatMoedaFicha, formatPct, type FichaComercial } from "@/types/informacao-comercial";

/**
 * Colunas da lista. Abaixo de lg somem parceiro e liquidação; abaixo de md fica só o cedente.
 */
export const GRADE_FICHAS =
  "grid grid-cols-[minmax(0,1fr)_20px] items-center gap-4 " +
  "md:grid-cols-[minmax(0,2fr)_minmax(140px,1.1fr)_minmax(120px,0.9fr)_112px_20px] " +
  "lg:grid-cols-[minmax(0,2fr)_minmax(0,1.1fr)_minmax(150px,1.1fr)_minmax(120px,0.9fr)_minmax(140px,1fr)_104px_20px]";

/** null: ficha sem data. */
export type FichaNaLista = FichaComercial & { dias: number | null };

/** Quanto do limite o risco ocupa; a cor esquenta perto do teto. */
export function usoDoLimite(ficha: FichaComercial) {
  if (ficha.limiteN <= 0) return null;
  const fracao = ficha.riscoN / ficha.limiteN;
  return { fracao, cor: fracao > 1 ? "#B42345" : fracao > 0.8 ? "#D1732C" : "#612035" };
}

/** Barra L1–L4 empilhada, cada fatia na proporção do total informado. */
export function BarraLiquidacao({ ficha, alta = false }: { ficha: FichaComercial; alta?: boolean }) {
  const total = ficha.liquidacao.reduce((s, l) => s + l.valor, 0);
  if (total <= 0) return null;
  return (
    <span
      role="img"
      aria-label={ficha.liquidacao.map(l => `${LIQUIDACAO[l.chave].rotulo} ${formatPct(l.valor)}`).join(", ")}
      className={`flex w-full gap-0.5 overflow-hidden rounded-full bg-[#F0EEE8] ${alta ? "h-2.5" : "h-1.5"}`}
    >
      {ficha.liquidacao
        .filter(l => l.valor > 0)
        .map(l => (
          <span
            key={l.chave}
            className="block h-full origin-left animate-crescer-x motion-reduce:animate-none"
            style={{ width: `${(l.valor / total) * 100}%`, background: LIQUIDACAO[l.chave].cor, animationDelay: "200ms" }}
          />
        ))}
    </span>
  );
}

export function CabecalhoFichas() {
  return (
    <div
      className={`${GRADE_FICHAS} border-b border-linha bg-[#FBFAF7] px-5 py-3 text-[11px] font-bold uppercase tracking-[0.1em] text-[#7F7C73] sm:px-6`}
    >
      <span>Cedente</span>
      <span className="hidden lg:block">Parceiro</span>
      <span className="hidden md:block">Risco no parceiro</span>
      <span className="hidden md:block">Vencido</span>
      <span className="hidden lg:block">Liquidação</span>
      <span className="hidden md:block">Ficha</span>
      <span />
    </div>
  );
}

export default function LinhaFicha({
  ficha,
  atraso,
  onAbrir,
}: {
  ficha: FichaNaLista;
  /** Atraso da entrada, em ms: as linhas chegam em cascata. */
  atraso: number;
  onAbrir: () => void;
}) {
  const tom = tomDoAvatar(ficha.cnpj ?? ficha.id);
  const nome = caixaTitulo(ficha.empresa ?? null) || "Empresa sem nome";
  const uso = usoDoLimite(ficha);
  const pontual = ficha.liquidacao.find(l => l.chave === "pontual")?.valor ?? 0;
  const temLiquidacao = ficha.liquidacao.some(l => l.valor > 0);

  return (
    <button
      type="button"
      onClick={onAbrir}
      aria-label={`Abrir ficha de ${nome}${ficha.parceiro ? ` no parceiro ${ficha.parceiro}` : ""}`}
      className={`${GRADE_FICHAS} group w-full border-b border-[#F0EEE8] bg-white px-5 py-3 text-left transition-colors last:border-b-0 hover:bg-[#FBF7F1] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary sm:px-6 animate-subir motion-reduce:animate-none`}
      style={{ animationDelay: `${atraso}ms` }}
    >
      <span className="flex min-w-0 items-center gap-3">
        <span
          className="grid h-10 w-10 shrink-0 place-items-center rounded-xl text-[13.5px] font-bold transition-transform duration-300 ease-saida group-hover:-rotate-6 group-hover:scale-105 motion-reduce:group-hover:transform-none"
          style={{ background: tom.fundo, color: tom.texto }}
        >
          {iniciais(ficha.empresa ?? null)}
        </span>
        <span className="min-w-0">
          <span className="block truncate text-[14.5px] font-semibold text-grafite">{nome}</span>
          <span className="mt-0.5 block truncate font-mono text-xs text-suave">
            {ficha.cnpj ? formatCnpj(ficha.cnpj) : "sem CNPJ"}
            {/* Parceiro vai para a linha do CNPJ quando a coluna dele some. */}
            <span className="font-sans lg:hidden">{ficha.parceiro && ` · ${ficha.parceiro}`}</span>
          </span>
        </span>
      </span>

      <span className="hidden min-w-0 lg:block">
        {ficha.parceiro ? (
          <span className="inline-flex h-7 max-w-full items-center gap-1.5 rounded-lg bg-[#F4F2EC] px-2.5 text-xs font-semibold text-[#45443F]">
            <Icon name="handshake" size={15} className="shrink-0 text-[#8A877E]" />
            <span className="truncate">{ficha.parceiro}</span>
          </span>
        ) : (
          <span className="text-xs italic text-[#8A877E]">não informado</span>
        )}
        {ficha.tipo && <span className="mt-1 block text-[11.5px] text-suave">por {ficha.tipo.toLowerCase()}</span>}
      </span>

      <span className="hidden md:block">
        <span className={`block text-sm font-semibold ${ficha.riscoN > 0 ? "text-grafite" : "text-[#B0ACA1]"}`}>
          {ficha.riscoN > 0 ? formatMoedaFicha(ficha.riscoN) : "—"}
        </span>
        {uso ? (
          <>
            <span className="mt-1.5 block h-1.5 w-full max-w-[140px] overflow-hidden rounded-full bg-[#F0EEE8]">
              <i
                className="block h-full origin-left rounded-full animate-crescer-x motion-reduce:animate-none"
                style={{ width: `${Math.min(100, uso.fracao * 100)}%`, background: uso.cor, animationDelay: "200ms" }}
              />
            </span>
            <span className="mt-1 block text-[11.5px] text-suave">{formatPct(Math.round(uso.fracao * 1000) / 10)} do limite</span>
          </>
        ) : (
          <span className="mt-0.5 block text-[11.5px] text-suave">sem limite informado</span>
        )}
      </span>

      <span className="hidden md:block">
        {ficha.vencidoN > 0 ? (
          <>
            <span className="block text-sm font-bold text-critico">{formatMoedaFicha(ficha.vencidoN)}</span>
            {ficha.vencidosData && <span className="mt-0.5 block text-[11.5px] text-suave">desde {ficha.vencidosData}</span>}
          </>
        ) : (
          <span className="inline-flex items-center gap-1.5 text-[12.5px] font-semibold text-ok">
            <Icon name="check_circle" size={17} />
            Nada vencido
          </span>
        )}
      </span>

      <span className="hidden lg:block">
        {temLiquidacao ? (
          <>
            <BarraLiquidacao ficha={ficha} />
            <span className="mt-1.5 block text-[12.5px] font-semibold text-grafite">
              {formatPct(pontual)} <span className="font-medium text-suave">pontual</span>
            </span>
          </>
        ) : (
          <span className="text-sm text-[#B0ACA1]">—</span>
        )}
      </span>

      <span className="hidden md:block">
        <span className="block text-[13px] font-semibold text-grafite">{ficha.dias != null ? tempoRelativo(ficha.dias) : "sem data"}</span>
        {ficha.data && <span className="mt-0.5 block text-[11.5px] text-suave">{ficha.data}</span>}
      </span>

      <Icon
        name="chevron_right"
        size={18}
        className="text-[#B5B1A6] transition-[transform,color] duration-200 group-hover:translate-x-1 group-hover:text-primary"
      />
    </button>
  );
}

/** Linhas de mentira com brilho, no formato da lista, enquanto as fichas chegam. */
export function LinhasEsqueletoFichas({ quantidade = 8 }: { quantidade?: number }) {
  return (
    <div aria-hidden>
      {Array.from({ length: quantidade }, (_, i) => (
        <div key={i} className={`${GRADE_FICHAS} border-b border-[#F0EEE8] px-5 py-3.5 sm:px-6`}>
          <span className="flex items-center gap-3">
            <span className="esqueleto h-10 w-10 shrink-0 rounded-xl" />
            <span className="flex flex-col gap-2">
              <span className="esqueleto h-3.5 w-44" />
              <span className="esqueleto h-3 w-28" />
            </span>
          </span>
          <span className="esqueleto hidden h-7 w-28 lg:block" />
          <span className="esqueleto hidden h-3 w-24 md:block" />
          <span className="esqueleto hidden h-3.5 w-20 md:block" />
          <span className="esqueleto hidden h-2 w-28 lg:block" />
          <span className="esqueleto hidden h-3.5 w-14 md:block" />
          <span />
        </div>
      ))}
    </div>
  );
}
