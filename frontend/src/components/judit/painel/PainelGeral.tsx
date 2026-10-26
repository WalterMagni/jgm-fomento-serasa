"use client";

import Icon from "@/components/ui/Icon";
import { SeloSituacao, data } from "@/components/judit/JuditPecas";
import {
  BarrasHorizontais,
  BarrasPorPolo,
  COR_POLO,
  CardTotal,
  Colunas,
  Pizza,
  Secao,
} from "@/components/judit/painel/Graficos";
import type { JuditBloco } from "@/types/judit";

const SOMA_ZERO = { quantidade: 0, valor: 0 };

/**
 * O Dashboard da aba Processos do Vadu, com os dados da Judit: cards por justiça, processos por área,
 * polo, quantidade por ano, Estado e últimos processos. Usado no card da Visão 360º e na página de
 * processos. As lupas levam à lista filtrada ou ao detalhe do processo.
 */
export function PainelGeral({
  bloco,
  onArea,
  onProcesso,
}: {
  bloco: JuditBloco;
  /** Lupa da área: a lista só com ela. `null` é a área não informada. */
  onArea?: (area: string | null) => void;
  onProcesso?: (cnj: string) => void;
}) {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <CardTotal rotulo="Total" soma={bloco.total} />
        <CardTotal rotulo="Trabalhista" soma={bloco.porJustica.TRABALHO ?? SOMA_ZERO} />
        <CardTotal rotulo="Estadual" soma={bloco.porJustica.ESTADUAL ?? SOMA_ZERO} />
        <CardTotal rotulo="Federal" soma={bloco.porJustica.FEDERAL ?? SOMA_ZERO} />
      </div>

      <div className="space-y-2">
        <h3 className="text-xs font-bold text-grafite dark:text-white">Processos por área</h3>
        <div className="overflow-x-auto rounded-lg border border-border-light dark:border-border-dark">
          <table className="w-full text-xs">
            <thead className="bg-[#FBFAF7] text-left text-[11px] text-gray-500 dark:bg-white/5">
              <tr>
                {onArea && <th className="w-14 px-3 py-2" />}
                <th className="px-3 py-2">Área</th>
                <th className="px-3 py-2 text-right">Qtd</th>
                <th className="w-10 px-3 py-2" />
                <th className="px-3 py-2 text-right">%</th>
                <th className="px-3 py-2">Última movimentação</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#F0EEE8] dark:divide-white/10">
              {bloco.areas.map(a => (
                <tr key={a.area ?? "—"} className="align-top">
                  {onArea && (
                    <td className="px-3 py-2">
                      <Lupa rotulo={`Ver os processos de ${a.area ?? "área não informada"}`} onClick={() => onArea(a.area)} />
                    </td>
                  )}
                  <td className="px-3 py-2">
                    <div className={a.area ? "text-grafite dark:text-gray-200" : "italic text-gray-400"}>{a.area ?? "Não informada"}</div>
                    <div className="text-[11px] text-gray-400">1º processo {data(a.primeiroProcesso)}</div>
                  </td>
                  <td className="px-3 py-2 text-right font-semibold tabular-nums">{a.quantidade}</td>
                  <td className="px-3 py-2">
                    <Pizza porcentagem={a.porcentagem} />
                  </td>
                  <td className="px-3 py-2 text-right font-semibold tabular-nums">{Math.round(a.porcentagem)}%</td>
                  <td className="px-3 py-2 text-gray-500">{data(a.ultimaMovimentacao)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Secao icone="balance" titulo="Status jurídico">
          <BarrasHorizontais
            itens={[
              { rotulo: "Outros", valor: bloco.outros.quantidade, cor: COR_POLO.OUTROS },
              { rotulo: "Como autora (ativo)", valor: bloco.comoAutor.quantidade, cor: COR_POLO.AUTOR },
              { rotulo: "Como ré (passivo)", valor: bloco.comoReu.quantidade, cor: COR_POLO.REU },
            ]}
          />
        </Secao>
        <Secao icone="map" titulo="Estado">
          <Colunas itens={bloco.porUf.map(u => ({ rotulo: u.valor ?? "—", valor: u.quantidade }))} />
        </Secao>
      </div>

      <Secao icone="calendar_today" titulo="Qtd. de processos por ano">
        <BarrasPorPolo periodos={bloco.porAno} />
      </Secao>

      <UltimosProcessos bloco={bloco} onProcesso={onProcesso} />
    </div>
  );
}

export function UltimosProcessos({ bloco, onProcesso }: { bloco: JuditBloco; onProcesso?: (cnj: string) => void }) {
  if (bloco.ultimos.length === 0) return null;
  return (
    <div className="space-y-2">
      <h3 className="text-xs font-bold text-grafite dark:text-white">Últimos processos</h3>
      <div className="overflow-x-auto rounded-lg border border-border-light dark:border-border-dark">
        <table className="w-full text-xs">
          <thead className="bg-[#FBFAF7] text-left text-[11px] text-gray-500 dark:bg-white/5">
            <tr>
              {onProcesso && <th className="w-14 px-3 py-2" />}
              <th className="px-3 py-2">Tribunal</th>
              <th className="px-3 py-2">UF</th>
              <th className="px-3 py-2">Situação</th>
              <th className="px-3 py-2">Movimentação</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#F0EEE8] dark:divide-white/10">
            {bloco.ultimos.map((u, i) => (
              <tr key={u.cnj ?? i} className="align-top">
                {onProcesso && (
                  <td className="px-3 py-2">
                    {u.cnj && <Lupa rotulo={`Abrir o processo ${u.cnj}`} onClick={() => onProcesso(u.cnj!)} />}
                  </td>
                )}
                <td className="px-3 py-2">
                  <div className="text-grafite dark:text-gray-200">
                    {u.tribunal ?? "—"}
                    {u.classe && <span className="text-gray-500"> · {u.classe}</span>}
                  </div>
                  <div className="font-mono text-[11px] text-gray-400">{u.cnj ?? "sem número"}</div>
                </td>
                <td className="px-3 py-2 text-gray-500">{u.uf ?? "—"}</td>
                <td className="px-3 py-2">
                  <SeloSituacao situacao={u.situacao} />
                </td>
                <td className="whitespace-nowrap px-3 py-2 text-gray-500">
                  {data(u.distribuidoEm)} – {data(u.ultimaMovimentacao)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function Lupa({ rotulo, onClick }: { rotulo: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={rotulo}
      aria-label={rotulo}
      className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-border-light text-gray-500 transition-colors hover:border-[#15BE62] hover:text-[#047857] dark:border-border-dark"
    >
      <Icon name="search" size={14} />
    </button>
  );
}
