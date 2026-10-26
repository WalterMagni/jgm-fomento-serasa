"use client";

import { useMemo, useState } from "react";
import Icon from "@/components/ui/Icon";
import { moeda, normalizar } from "@/components/judit/JuditPecas";
import type { JuditParteResumo } from "@/types/judit";

const PRIMEIRAS = 50;

/**
 * As contrapartes da empresa, agrupadas pelo nome: em quantos processos aparecem, com a empresa como
 * ré ou como autora, e o valor das causas somado. Advogados, peritos e testemunhas já saíram na leitura.
 */
export function PartesJudit({ partes }: { partes: JuditParteResumo[] }) {
  const [busca, setBusca] = useState("");
  const [todas, setTodas] = useState(false);
  const termo = normalizar(busca.trim());
  const filtradas = useMemo(() => partes.filter(p => !termo || normalizar(p.nome).includes(termo)), [partes, termo]);
  const visiveis = todas ? filtradas : filtradas.slice(0, PRIMEIRAS);

  if (partes.length === 0) {
    return (
      <p className="rounded-lg border border-border-light px-4 py-6 text-center text-sm text-gray-500 dark:border-border-dark">
        Nenhuma contraparte identificada.
      </p>
    );
  }
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <label className="relative block min-w-[240px] flex-1 sm:max-w-sm">
          <span className="sr-only">Buscar parte</span>
          <Icon name="search" size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#8A877E]" />
          <input
            type="search"
            value={busca}
            onChange={e => setBusca(e.target.value)}
            placeholder="Buscar parte pelo nome"
            className="h-10 w-full rounded-[11px] border border-linha bg-white pl-9 pr-3 text-[13px] font-medium text-grafite outline-none focus:border-[#15BE62] focus:ring-4 focus:ring-[#15BE62]/15 dark:bg-transparent dark:text-white"
          />
        </label>
        <span className="ml-auto text-xs text-gray-500">{filtradas.length} partes</span>
      </div>
      <div className="overflow-x-auto rounded-lg border border-border-light dark:border-border-dark">
        <table className="w-full text-xs">
          <thead className="bg-[#FBFAF7] text-left text-[11px] uppercase tracking-wide text-gray-400 dark:bg-white/5">
            <tr>
              <th className="px-3 py-2">Parte</th>
              <th className="px-3 py-2 text-right">Processos</th>
              <th className="px-3 py-2 text-right">Empresa ré</th>
              <th className="px-3 py-2 text-right">Empresa autora</th>
              <th className="px-3 py-2 text-right">Valor das causas</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#F0EEE8] dark:divide-white/10">
            {visiveis.map(p => (
              <tr key={p.nome}>
                <td className="px-3 py-2 text-grafite dark:text-gray-200">{p.nome}</td>
                <td className="px-3 py-2 text-right font-semibold tabular-nums">{p.quantidade}</td>
                <td className="px-3 py-2 text-right tabular-nums text-primary">{p.empresaRe || "—"}</td>
                <td className="px-3 py-2 text-right tabular-nums text-[#2956E0]">{p.empresaAutora || "—"}</td>
                <td className="whitespace-nowrap px-3 py-2 text-right tabular-nums">{p.valor > 0 ? moeda(p.valor) : "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!todas && filtradas.length > PRIMEIRAS && (
        <button type="button" onClick={() => setTodas(true)} className="text-xs font-semibold text-primary hover:underline">
          Mostrar as {filtradas.length} partes
        </button>
      )}
    </div>
  );
}
