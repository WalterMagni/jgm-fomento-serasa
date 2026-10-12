"use client";

import { caixaTitulo, formatCnpj } from "@/lib/formatadores";
import { formatPct, numeroBr } from "@/types/informacao-comercial";
import type { FichaNaLista } from "./LinhaFicha";

const vazio = (v?: string | null) => (v && v.trim() ? v : "—");
const pct = (v?: string | null) => (v && v.trim() ? formatPct(numeroBr(v)) : "—");

/** Mesmas colunas da planilha, para quem prefere ver tudo lado a lado. */
const COLUNAS: { rotulo: string; valor: (f: FichaNaLista) => string; alinhar?: "direita"; vermelho?: (f: FichaNaLista) => boolean }[] = [
  { rotulo: "CNPJ", valor: f => (f.cnpj ? formatCnpj(f.cnpj) : "—") },
  { rotulo: "Data", valor: f => vazio(f.data) },
  { rotulo: "Tipo", valor: f => vazio(f.tipo) },
  { rotulo: "Parceiro", valor: f => vazio(f.parceiro) },
  { rotulo: "Desde", valor: f => vazio(f.clienteDesde) },
  { rotulo: "Última operação", valor: f => vazio(f.ultimaOperacaoData) },
  { rotulo: "Valor", valor: f => vazio(f.ultimaOperacaoValor), alinhar: "direita" },
  { rotulo: "Limite", valor: f => vazio(f.limite), alinhar: "direita" },
  { rotulo: "Risco dupl.", valor: f => vazio(f.riscoDuplicata), alinhar: "direita" },
  { rotulo: "Risco cheque", valor: f => vazio(f.riscoCheque), alinhar: "direita" },
  { rotulo: "Risco comiss.", valor: f => vazio(f.riscoComissaria), alinhar: "direita" },
  { rotulo: "Venc. últ. dupl.", valor: f => vazio(f.vencidosValor) },
  { rotulo: "Valor vencido", valor: f => vazio(f.vencidosValorMonetario), alinhar: "direita", vermelho: f => f.vencidoN > 0 },
  { rotulo: "Data vencido", valor: f => vazio(f.vencidosData) },
  { rotulo: "VOP", valor: f => vazio(f.vop), alinhar: "direita" },
  { rotulo: "L1 pontual", valor: f => pct(f.pontual), alinhar: "direita" },
  { rotulo: "L2 atraso", valor: f => pct(f.atraso), alinhar: "direita" },
  { rotulo: "L3 cartório", valor: f => pct(f.cartorio), alinhar: "direita" },
  { rotulo: "L4 recompra", valor: f => pct(f.recompra), alinhar: "direita" },
];

export default function TabelaFichas({ fichas, onAbrir }: { fichas: FichaNaLista[]; onAbrir: (id: string) => void }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[1500px] border-collapse text-left text-[13px] tabular-nums">
        <thead>
          <tr className="border-b border-linha bg-[#FBFAF7]">
            {/* Cedente fica parado ao rolar para o lado. */}
            <th className="sticky left-0 z-10 bg-[#FBFAF7] px-4 py-3 text-[11px] font-bold uppercase tracking-[0.1em] text-[#7F7C73] shadow-[1px_0_0_#E4E1D8]">
              Cedente
            </th>
            {COLUNAS.map(c => (
              <th
                key={c.rotulo}
                className={`whitespace-nowrap px-3 py-3 text-[11px] font-bold uppercase tracking-[0.1em] text-[#7F7C73] ${c.alinhar === "direita" ? "text-right" : ""}`}
              >
                {c.rotulo}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {fichas.map(f => (
            <tr key={f.id} className="group border-b border-[#F0EEE8] last:border-b-0 hover:bg-[#FBF7F1]">
              <td className="sticky left-0 z-10 max-w-[280px] bg-white px-4 py-2.5 shadow-[1px_0_0_#E4E1D8] group-hover:bg-[#FBF7F1]">
                <button
                  type="button"
                  onClick={() => onAbrir(f.id)}
                  className="block max-w-full truncate text-left font-semibold text-grafite underline-offset-2 hover:text-primary hover:underline"
                >
                  {caixaTitulo(f.empresa ?? null) || "Empresa sem nome"}
                </button>
              </td>
              {COLUNAS.map(c => (
                <td
                  key={c.rotulo}
                  className={`whitespace-nowrap px-3 py-2.5 ${c.alinhar === "direita" ? "text-right" : ""} ${
                    c.vermelho?.(f) ? "font-bold text-critico" : c.rotulo === "CNPJ" ? "font-mono text-xs text-suave" : "text-grafite"
                  }`}
                >
                  {c.valor(f)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
