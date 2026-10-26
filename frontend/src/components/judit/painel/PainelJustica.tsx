"use client";

import { AVISO_SITUACAO, moeda, rotuloSituacao } from "@/components/judit/JuditPecas";
import { BarrasHorizontais, BarrasPorPolo, COR_POLO, CardTotal, Colunas, Secao } from "@/components/judit/painel/Graficos";
import { Lupa } from "@/components/judit/painel/PainelGeral";
import type { JuditBloco, JuditGrupoPolo, JuditPorPolo, JuditSituacaoProcesso } from "@/types/judit";

const LIMITE_BARRAS = 8;

const POLOS: { polo: JuditGrupoPolo; titulo: string; subtitulo: string }[] = [
  { polo: "REU", titulo: "Parte passiva", subtitulo: "Processos movidos contra a empresa" },
  { polo: "AUTOR", titulo: "Parte ativa", subtitulo: "Processos movidos pela empresa" },
  { polo: "OUTROS", titulo: "Outros e terceiros", subtitulo: "Terceiro interessado ou polo não identificado" },
];

/** "2026-08" → "08/2026". */
const mes = (periodo: string) => `${periodo.slice(5, 7)}/${periodo.slice(0, 4)}`;

/**
 * O Dashboard de uma justiça, na aba Instâncias do Vadu: cards por polo, área e, para cada polo, o
 * total por situação (deduzida), as classes e os assuntos; depois os processos novos dos últimos 12
 * meses, por ano e por Estado.
 */
export function PainelJustica({
  bloco,
  nome,
  onSituacao,
}: {
  bloco: JuditBloco;
  nome: string;
  /** Lupa do totalizador: a lista só com aquele polo e aquela situação. */
  onSituacao?: (polo: JuditGrupoPolo, situacao: JuditSituacaoProcesso | null) => void;
}) {
  if (bloco.total.quantidade === 0) {
    return (
      <p className="rounded-lg border border-border-light px-4 py-6 text-center text-sm text-gray-500 dark:border-border-dark">
        Nenhum processo na Justiça {nome}.
      </p>
    );
  }
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <CardTotal rotulo="Parte ativa" soma={bloco.comoAutor} apoio="Processos movidos" destaque={COR_POLO.AUTOR} />
        <CardTotal rotulo="Parte passiva" soma={bloco.comoReu} apoio="Processos contra" destaque={COR_POLO.REU} />
        <CardTotal rotulo="Total" soma={bloco.total} />
        <CardTotal rotulo="Outros e terceiros" soma={bloco.outros} apoio="Processos outros" />
      </div>

      <Secao icone="balance" titulo="Área">
        <BarrasHorizontais itens={bloco.areas.map(a => ({ rotulo: a.area ?? "Não informada", valor: a.quantidade }))} />
      </Secao>

      {POLOS.filter(p => bloco.porPolo[p.polo].soma.quantidade > 0).map(p => (
        <Polo
          key={p.polo}
          titulo={p.titulo}
          subtitulo={p.subtitulo}
          dados={bloco.porPolo[p.polo]}
          onSituacao={onSituacao ? situacao => onSituacao(p.polo, situacao) : undefined}
        />
      ))}

      <Secao icone="calendar_today" titulo="Processos novos nos últimos 12 meses">
        <BarrasPorPolo periodos={bloco.ultimos12Meses} rotulo={mes} vazio="Nenhum processo distribuído nos últimos 12 meses." />
      </Secao>

      <Secao icone="calendar_today" titulo="Qtd. de processos por ano">
        <BarrasPorPolo periodos={bloco.porAno} />
      </Secao>

      <Secao icone="map" titulo="Estado">
        <Colunas itens={bloco.porUf.map(u => ({ rotulo: u.valor ?? "—", valor: u.quantidade }))} />
      </Secao>
    </div>
  );
}

function Polo({
  titulo,
  subtitulo,
  dados,
  onSituacao,
}: {
  titulo: string;
  subtitulo: string;
  dados: JuditPorPolo;
  onSituacao?: (situacao: JuditSituacaoProcesso | null) => void;
}) {
  return (
    <Secao icone="person" titulo={titulo} extra={<span className="text-[11px] text-gray-500">{subtitulo}</span>}>
      <div className="space-y-1.5">
        <div className="text-[11px] font-bold uppercase tracking-wide text-gray-400">Totalizador por situação</div>
        <div className="overflow-x-auto rounded-lg border border-border-light dark:border-border-dark">
          <table className="w-full text-xs">
            <thead className="bg-[#FBFAF7] text-left text-[11px] text-gray-500 dark:bg-white/5">
              <tr>
                {onSituacao && <th className="w-14 px-3 py-2" />}
                <th className="px-3 py-2">Situação</th>
                <th className="px-3 py-2 text-right">Qtd</th>
                <th className="px-3 py-2 text-right">Valor</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#F0EEE8] dark:divide-white/10">
              {dados.situacoes.map(s => (
                <tr key={s.situacao ?? "—"}>
                  {onSituacao && (
                    <td className="px-3 py-2">
                      <Lupa rotulo={`Ver os processos ${rotuloSituacao(s.situacao).toLowerCase()}`} onClick={() => onSituacao(s.situacao)} />
                    </td>
                  )}
                  <td className="px-3 py-2 text-grafite dark:text-gray-200">{rotuloSituacao(s.situacao)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{s.quantidade}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{moeda(s.valor)}</td>
                </tr>
              ))}
              <tr className="bg-[#FBFAF7] font-semibold dark:bg-white/5">
                {onSituacao && <td />}
                <td className="px-3 py-2">Total</td>
                <td className="px-3 py-2 text-right tabular-nums">{dados.soma.quantidade}</td>
                <td className="px-3 py-2 text-right tabular-nums">{moeda(dados.soma.valor)}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p className="text-[11px] text-gray-400">{AVISO_SITUACAO}</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="space-y-1.5">
          <div className="text-[11px] font-bold uppercase tracking-wide text-gray-400">Classe</div>
          <BarrasHorizontais
            itens={dados.classes.slice(0, LIMITE_BARRAS).map(c => ({ rotulo: c.valor ?? "—", valor: c.quantidade }))}
          />
        </div>
        <div className="space-y-1.5">
          <div className="text-[11px] font-bold uppercase tracking-wide text-gray-400">Assunto</div>
          <BarrasHorizontais
            itens={dados.assuntos.slice(0, LIMITE_BARRAS).map(c => ({ rotulo: c.valor ?? "—", valor: c.quantidade }))}
          />
        </div>
      </div>
    </Secao>
  );
}
