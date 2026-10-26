"use client";

import { useMemo, useState } from "react";
import Icon from "@/components/ui/Icon";
import Segmentos from "@/components/ui/Segmentos";
import CampoNumero from "@/components/ui/CampoNumero";
import { BOTAO } from "@/components/ui/botao";
import { parseMoeda } from "@/components/liberacao/formatters";
import { useJuditConsumo, useLiberarJudit } from "@/hooks/useJudit";
import { moeda } from "@/components/judit/JuditPecas";
import type { JuditConsultaDoPainel, JuditPainelConsumo } from "@/types/judit";

type Periodo = "7" | "15" | "30" | "60" | "90";

const ROTULO = "text-[11px] font-bold uppercase tracking-[0.1em] text-[#7F7C73]";

/**
 * Aba "Consumo Judit" das Configurações, no padrão do Consumo Serasa. A barra é sempre do mês do
 * calendário — franquia (já paga), excedente e limite; o período filtra só a tabela.
 */
export default function ConsumoJudit() {
  const [periodo, setPeriodo] = useState<Periodo>("30");
  const { data: painel, isLoading, isError } = useJuditConsumo(Number(periodo));

  const consultas = useMemo(() => painel?.consultas ?? [], [painel]);
  const de = (servico: JuditConsultaDoPainel["servico"]) => consultas.filter(c => c.servico === servico);
  const soma = (lista: JuditConsultaDoPainel[]) => lista.reduce((total, c) => total + c.custo, 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 font-sans text-base font-bold text-[#047857]">
          <Icon name="gavel" className="text-lg" /> Consumo da Judit
        </h2>
        <Segmentos
          rotulo="Período da tabela"
          valor={periodo}
          onChange={setPeriodo}
          opcoes={(["7", "15", "30", "60", "90"] as const).map(p => ({ valor: p, rotulo: `${p} dias` }))}
        />
      </div>

      {painel && <Franquia painel={painel} />}

      <section className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
        {[
          {
            rotulo: "Custo do período",
            valor: moeda(soma(consultas)),
            cor: "#047857",
            apoio: `${consultas.length.toLocaleString("pt-BR")} consultas nos últimos ${periodo} dias`,
          },
          ...(
            [
              ["Resumos", "RESUMO"],
              ["Listas de processos", "PROCESSOS"],
              ["Detalhes de processo", "DETALHE"],
            ] as const
          ).map(([rotulo, servico]) => ({
            rotulo,
            valor: de(servico).length.toLocaleString("pt-BR"),
            cor: "#262833",
            apoio: `${moeda(soma(de(servico)))} no período`,
          })),
        ].map(item => (
          <div key={item.rotulo} className="rounded-cartao border border-linha bg-white px-5 py-4">
            <p className="text-[12.5px] font-semibold text-suave">{item.rotulo}</p>
            <p className="mt-1.5 font-display text-[30px] font-semibold leading-tight tracking-[-0.015em]" style={{ color: item.cor }}>
              {item.valor}
            </p>
            <p className="mt-1 text-xs text-suave">{item.apoio}</p>
          </div>
        ))}
      </section>

      <section className="overflow-hidden rounded-painel border border-linha bg-white">
        <div className={`relative overflow-x-auto ${isLoading || isError ? "min-h-[250px]" : ""}`}>
          {isLoading && (
            <div className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-white/60 backdrop-blur-sm">
              <Icon name="progress_activity" size={32} weight="bold" className="mb-2 animate-spin text-[#047857]" />
              <p className="text-sm font-medium text-suave">Carregando consultas…</p>
            </div>
          )}
          {isError && !isLoading && (
            <div className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-white p-6 text-center">
              <p className="mb-1 font-medium text-critico">Não foi possível carregar o consumo da Judit.</p>
              <p className="text-sm text-suave">O servidor não respondeu agora. Tente de novo em instantes.</p>
            </div>
          )}
          <table className="w-full text-left text-sm">
            <thead className="border-b border-linha bg-[#FBFAF7]">
              <tr>
                <th className={`px-6 py-3.5 ${ROTULO}`}>Usuário</th>
                <th className={`px-6 py-3.5 ${ROTULO}`}>Consultado</th>
                <th className={`px-6 py-3.5 ${ROTULO}`}>Data/Hora</th>
                <th className={`px-6 py-3.5 ${ROTULO}`}>Tipo</th>
                <th className={`px-6 py-3.5 ${ROTULO}`}>Situação</th>
                <th className={`px-6 py-3.5 text-right ${ROTULO}`}>Custo</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#F0EEE8]">
              {!isLoading && !isError && consultas.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-10 text-center text-suave">
                    Nenhuma consulta neste período.
                  </td>
                </tr>
              ) : (
                consultas.map(c => <Linha key={c.id} consulta={c} />)
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function Linha({ consulta: c }: { consulta: JuditConsultaDoPainel }) {
  return (
    <tr className="transition-colors hover:bg-[#F3FBF6]">
      <td className="px-6 py-3.5 font-medium text-grafite">{c.usuarioNome ?? "—"}</td>
      <td className="px-6 py-3.5">
        <div className="text-grafite">{c.nomeEmpresa ?? "—"}</div>
        <div className="font-mono text-xs text-suave">
          {formatarCnpj(c.documento)}
          {c.cnj && ` · ${c.cnj}`}
        </div>
      </td>
      <td className="whitespace-nowrap px-6 py-3.5 text-suave">
        {new Date(c.criadoEm).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" })}
      </td>
      <td className="px-6 py-3.5">
        <span className="inline-flex items-center whitespace-nowrap rounded-md bg-[#E2F0E9] px-2 py-0.5 text-xs font-bold text-[#17583F]">
          {tipo(c)}
        </span>
      </td>
      <td className="px-6 py-3.5">
        <span
          className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-bold ${
            c.situacao === "PENDENTE"
              ? "bg-[#2956E0]/10 text-[#1f43b8]"
              : c.situacao === "FALHOU"
                ? "bg-red-50 text-red-700"
                : "bg-[#EFEDE6] text-[#5E5C55]"
          }`}
        >
          {c.situacao === "PENDENTE" ? "Pendente" : c.situacao === "FALHOU" ? "Falhou" : "Concluída"}
        </span>
      </td>
      <td className="whitespace-nowrap px-6 py-3.5 text-right font-semibold text-grafite">
        {moeda(c.custo)}
        {c.servico === "PROCESSOS" && c.totalProcessos !== null && (
          <div className="text-[11px] font-normal text-suave">{c.totalProcessos.toLocaleString("pt-BR")} registros</div>
        )}
      </td>
    </tr>
  );
}

const TIPO: Record<JuditConsultaDoPainel["servico"], string> = {
  RESUMO: "Resumo",
  PROCESSOS: "Lista de processos",
  DETALHE: "Detalhe do processo",
};

function tipo(c: JuditConsultaDoPainel) {
  return TIPO[c.servico];
}

function formatarCnpj(documento: string) {
  return documento.length === 14
    ? documento.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5")
    : documento;
}

/** A barra do mês: franquia já paga, excedente e o limite (com o que o admin liberou). */
function Franquia({ painel }: { painel: JuditPainelConsumo }) {
  const { gastoMes, franquia, limite, liberado } = painel.mes;
  const excedente = Math.max(0, gastoMes - franquia);
  const noLimite = gastoMes >= limite;
  const escala = Math.max(limite, gastoMes);
  const largura = (valor: number) => `${Math.min(100, (valor / escala) * 100)}%`;
  const extenso = new Date(`${painel.mesReferencia}T12:00:00`).toLocaleDateString("pt-BR", { month: "long", year: "numeric" });
  const mes = extenso.charAt(0).toUpperCase() + extenso.slice(1);

  return (
    <section className="rounded-cartao border border-linha bg-white p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-[12.5px] font-semibold text-suave">{mes}</p>
          <p
            className={`mt-1 font-display text-[30px] font-semibold leading-tight tracking-[-0.015em] ${
              noLimite ? "text-red-700" : excedente > 0 ? "text-[#a8551a]" : "text-[#047857]"
            }`}
          >
            {moeda(gastoMes)}
          </p>
          <p className="mt-1 text-xs text-suave">
            {noLimite
              ? `No limite do mês: o portal parou de consultar. ${painel.podeLiberar ? "Libere um valor a mais abaixo." : "Peça ao admin para liberar um valor a mais."}`
              : excedente > 0
                ? `${moeda(excedente)} de excedente, acima da franquia de ${moeda(franquia)}.`
                : `Dentro da franquia: ${moeda(franquia - gastoMes)} ainda disponíveis do que já está pago.`}
          </p>
        </div>
        {painel.podeLiberar && <Liberar />}
      </div>

      <div className="mt-5" role="img" aria-label={`Gasto de ${moeda(gastoMes)}; franquia de ${moeda(franquia)}; limite de ${moeda(limite)}`}>
        <div className="relative h-3 overflow-hidden rounded-full bg-[#EFEDE6]">
          <div className="absolute inset-y-0 left-0 bg-[#15BE62]" style={{ width: largura(Math.min(gastoMes, franquia)) }} />
          {excedente > 0 && (
            <div
              className={`absolute inset-y-0 ${noLimite ? "bg-red-500" : "bg-[#D1732C]"}`}
              style={{ left: largura(franquia), width: largura(Math.min(gastoMes, escala) - franquia) }}
            />
          )}
          <div className="absolute inset-y-0 w-0.5 bg-[#262833]/40" style={{ left: largura(franquia) }} aria-hidden />
        </div>
        <div className="mt-2 flex flex-wrap justify-between gap-2 text-xs text-suave">
          <span className="inline-flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-[#15BE62]" /> Franquia {moeda(franquia)} (já paga)
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-[#D1732C]" /> Excedente
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-red-500" /> Limite {moeda(limite)}
            {liberado > 0 && ` (inclui ${moeda(liberado)} liberados)`}
          </span>
        </div>
      </div>

      {painel.liberacoes.length > 0 && (
        <ul className="mt-4 space-y-1 border-t border-linha pt-3 text-xs text-suave">
          {painel.liberacoes.map(l => (
            <li key={l.id}>
              <span className="font-semibold text-grafite">+{moeda(l.valor)}</span> liberados por {l.adminNome ?? "admin"} em{" "}
              {new Date(l.criadoEm).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })}
              {l.motivo && ` — ${l.motivo}`}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** Só o admin vê. Soma ao limite do mês corrente; no mês seguinte volta ao configurado. */
function Liberar() {
  const [aberto, setAberto] = useState(false);
  const [valor, setValor] = useState("");
  const [motivo, setMotivo] = useState("");
  const liberar = useLiberarJudit();
  const numero = parseMoeda(valor) ?? 0;

  if (!aberto) {
    return (
      <button type="button" className={BOTAO.fantasma} onClick={() => setAberto(true)}>
        <Icon name="add_circle" size={16} /> Liberar valor no mês
      </button>
    );
  }
  return (
    <form
      className="flex flex-wrap items-end gap-2"
      onSubmit={event => {
        event.preventDefault();
        liberar.mutate(
          { valor: numero, motivo },
          {
            onSuccess: () => {
              setAberto(false);
              setValor("");
              setMotivo("");
            },
          },
        );
      }}
    >
      <label className="flex flex-col gap-1">
        <span className={ROTULO}>Valor a mais</span>
        <span className="relative">
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs text-suave">R$</span>
          <CampoNumero
            autoFocus
            valor={valor}
            onMudar={setValor}
            placeholder="500,00"
            className="w-36 rounded-[10px] border border-linha bg-white py-2.5 pl-8 pr-3 text-right text-sm tabular-nums outline-none focus:border-[#047857] focus:ring-4 focus:ring-[#15BE62]/15"
          />
        </span>
      </label>
      <label className="flex flex-col gap-1">
        <span className={ROTULO}>Motivo (opcional)</span>
        <input
          value={motivo}
          onChange={event => setMotivo(event.target.value)}
          placeholder="ex.: análises do fim do mês"
          className="w-60 rounded-[10px] border border-linha bg-white px-3 py-2.5 text-sm outline-none focus:border-[#047857] focus:ring-4 focus:ring-[#15BE62]/15"
        />
      </label>
      <button type="submit" className={BOTAO.judit} disabled={numero <= 0 || liberar.isPending}>
        {liberar.isPending ? <Icon name="progress_activity" size={16} className="animate-spin" /> : <Icon name="check" size={16} />}
        Liberar {numero > 0 ? moeda(numero) : ""}
      </button>
      <button type="button" className={BOTAO.fantasma} onClick={() => setAberto(false)}>
        Cancelar
      </button>
    </form>
  );
}
