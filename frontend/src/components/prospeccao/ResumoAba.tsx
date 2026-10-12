"use client";

import Link from "next/link";
import Icon from "@/components/ui/Icon";
import { ROTULO_MOTIVO, type ProspeccaoDetalhe } from "@/types/prospeccao";
import { TOTAL_CAMPOS_COMERCIAIS, camposPreenchidos } from "./DadosComerciaisForm";
import { formatDate, formatMoeda } from "./formatters";

/**
 * Visão de bolso do card: o que a analista precisa saber antes de abrir as outras abas.
 *
 * <p>Cada bloco aponta para a aba onde o assunto é tratado, em vez de repetir a edição aqui.</p>
 */
export default function ResumoAba({
  detalhe,
  onIrPara,
}: {
  detalhe: ProspeccaoDetalhe;
  onIrPara: (aba: "dados" | "documentos") => void;
}) {
  const { card, dadosComerciais } = detalhe;
  const preenchidos = camposPreenchidos(dadosComerciais);
  const progresso = card.documentosTotal > 0 ? Math.round((card.documentosResolvidos / card.documentosTotal) * 100) : 0;

  return (
    <div className="space-y-3">
      {card.legado && (
        <div className="flex gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900
          dark:border-amber-900/50 dark:bg-amber-950/30 dark:text-amber-100">
          <Icon name="history" size={18} className="mt-0.5 shrink-0" />
          <div>
            <p className="font-medium">Card da esteira antiga</p>
            <p className="mt-0.5 text-xs opacity-80">
              Passou da aprovação antes de a decisão com limite existir. Revise com o time: limite, condições e aceite do cliente.
            </p>
          </div>
        </div>
      )}

      {card.motivoRecusa && (
        <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-900 dark:border-rose-900/50
          dark:bg-rose-950/30 dark:text-rose-100">
          <p className="font-medium">{ROTULO_MOTIVO[card.motivoRecusa]}</p>
          {card.observacao && <p className="mt-0.5 text-xs opacity-80">{card.observacao}</p>}
        </div>
      )}
      {!card.motivoRecusa && card.observacao && card.estagio === "RECUSADO_CLIENTE" && (
        <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-900 dark:border-rose-900/50
          dark:bg-rose-950/30 dark:text-rose-100">
          <p className="font-medium">O cliente recusou</p>
          <p className="mt-0.5 text-xs opacity-80">{card.observacao}</p>
        </div>
      )}

      <div className="grid gap-3 sm:grid-cols-2">
        <Bloco icone="analytics" titulo="Análise de crédito">
          <p className="text-sm text-slate-600 dark:text-slate-300">
            Rating, restrições e QSA ficam na ficha da empresa.
          </p>
          <Link
            href={`/clients/${card.cnpj}`}
            className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-[#2956E0] hover:underline dark:text-sky-400"
          >
            Abrir ficha da empresa <Icon name="open_in_new" size={14} />
          </Link>
        </Bloco>

        <Bloco icone="person" titulo="Pessoas">
          <dl className="space-y-1 text-sm">
            <Linha rotulo="Comercial" valor={card.comercialNome ?? "Sem comercial (direto)"} />
            <Linha rotulo="Analista" valor={card.analistaNome ?? "ninguém assumiu"} />
            <Linha rotulo="Entrada" valor={card.origem === "AUTOMATICA" ? "Visão cedente" : "Manual"} />
            <Linha rotulo="Aberto em" valor={formatDate(card.createdAt)} />
          </dl>
        </Bloco>

        <Bloco icone="folder_open" titulo="Documentos" acao={{ rotulo: "Ver checklist", onClick: () => onIrPara("documentos") }}>
          {card.documentosTotal === 0 ? (
            <p className="text-sm text-slate-500">Nenhum item obrigatório no checklist.</p>
          ) : (
            <>
              <p className="text-sm text-slate-600 dark:text-slate-300">
                <span className="font-semibold tabular-nums text-slate-900 dark:text-white">{card.documentosResolvidos}</span> de{" "}
                <span className="tabular-nums">{card.documentosTotal}</span> resolvidos
              </p>
              <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                <div className="h-full rounded-full bg-[#612035] transition-[width] dark:bg-[#e8a3b6]" style={{ width: `${progresso}%` }} />
              </div>
            </>
          )}
        </Bloco>

        <Bloco icone="storefront" titulo="Dados comerciais" acao={{ rotulo: preenchidos ? "Editar" : "Preencher", onClick: () => onIrPara("dados") }}>
          {preenchidos === 0 ? (
            <p className="text-sm text-slate-500">Nada preenchido ainda.</p>
          ) : (
            <dl className="space-y-1 text-sm">
              {dadosComerciais.faturamentoMensal != null && <Linha rotulo="Faturamento/mês" valor={formatMoeda(dadosComerciais.faturamentoMensal)} />}
              {dadosComerciais.ticketMedio != null && <Linha rotulo="Ticket médio" valor={formatMoeda(dadosComerciais.ticketMedio)} />}
              {dadosComerciais.prazoMedioVendas != null && <Linha rotulo="Prazo de vendas" valor={`${dadosComerciais.prazoMedioVendas} dias`} />}
              <p className="pt-1 text-xs text-slate-400">{preenchidos} de {TOTAL_CAMPOS_COMERCIAIS} campos</p>
            </dl>
          )}
        </Bloco>
      </div>
    </div>
  );
}

function Bloco({ icone, titulo, acao, children }: {
  icone: string;
  titulo: string;
  acao?: { rotulo: string; onClick: () => void };
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-xl border border-slate-200 p-4 dark:border-slate-700">
      <header className="mb-2 flex items-center justify-between gap-2">
        <h3 className="inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
          <Icon name={icone} size={14} />
          {titulo}
        </h3>
        {acao && (
          <button
            type="button"
            onClick={acao.onClick}
            className="cursor-pointer rounded text-xs font-medium text-[#612035] hover:underline focus:outline-none focus-visible:ring-2
              focus-visible:ring-[#612035] dark:text-[#e8a3b6]"
          >
            {acao.rotulo}
          </button>
        )}
      </header>
      {children}
    </section>
  );
}

function Linha({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-slate-500 dark:text-slate-400">{rotulo}</dt>
      <dd className="truncate text-right text-slate-800 dark:text-slate-100">{valor}</dd>
    </div>
  );
}
