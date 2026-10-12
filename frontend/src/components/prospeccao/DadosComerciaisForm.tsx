"use client";

import { useState } from "react";
import { CAMPO, ROTULO } from "@/components/liberacao/Dialogo";
import type { DadosComerciais } from "@/types/prospeccao";

/**
 * Os treze campos do perfil comercial, como o comercial preenchia no Portal Comercial.
 *
 * <p>Controlado por quem usa: a abertura do card guarda o rascunho no próprio diálogo, e a aba do
 * card salva no servidor. O formulário não sabe de onde veio nem para onde vai.</p>
 */
export default function DadosComerciaisForm({
  valor,
  onMudar,
  desabilitado = false,
}: {
  valor: DadosComerciais;
  onMudar: (proximo: DadosComerciais) => void;
  desabilitado?: boolean;
}) {
  function campo<K extends keyof DadosComerciais>(chave: K, novo: DadosComerciais[K]) {
    onMudar({ ...valor, [chave]: novo });
  }

  return (
    <div className="space-y-5">
      <Grupo titulo="Volume">
        <Moeda rotulo="Faturamento mensal" valor={valor.faturamentoMensal} onMudar={v => campo("faturamentoMensal", v)} desabilitado={desabilitado} />
        <Moeda rotulo="Ticket médio" valor={valor.ticketMedio} onMudar={v => campo("ticketMedio", v)} desabilitado={desabilitado} />
        <Moeda rotulo="Valor médio das operações" valor={valor.valorMedioOperacoes} onMudar={v => campo("valorMedioOperacoes", v)} desabilitado={desabilitado} />
      </Grupo>

      <Grupo titulo="Prazos médios (dias)">
        <Dias rotulo="Vendas" valor={valor.prazoMedioVendas} onMudar={v => campo("prazoMedioVendas", v)} desabilitado={desabilitado} />
        <Dias rotulo="Pagamento a fornecedor" valor={valor.prazoMedioPgtoFornecedor} onMudar={v => campo("prazoMedioPgtoFornecedor", v)} desabilitado={desabilitado} />
        <Dias rotulo="Entrega" valor={valor.prazoMedioEntrega} onMudar={v => campo("prazoMedioEntrega", v)} desabilitado={desabilitado} />
        <Dias rotulo="Guarda canhotos há" valor={valor.possuiCanhotosDias} onMudar={v => campo("possuiCanhotosDias", v)} desabilitado={desabilitado} />
      </Grupo>

      <Grupo titulo="Como opera">
        <SimNao rotulo="Mercadoria entregue" valor={valor.operaMercadoriaEntregue} onMudar={v => campo("operaMercadoriaEntregue", v)} desabilitado={desabilitado} />
        <SimNao rotulo="Mercadoria embarcada" valor={valor.operaMercadoriaEmbarcada} onMudar={v => campo("operaMercadoriaEmbarcada", v)} desabilitado={desabilitado} />
      </Grupo>

      <div className="grid gap-3 sm:grid-cols-2">
        <Texto rotulo="Região de atendimento" valor={valor.regiaoAtendimento} onMudar={v => campo("regiaoAtendimento", v)} desabilitado={desabilitado} />
        <Texto rotulo="Opera com quais produtos?" valor={valor.produtos} onMudar={v => campo("produtos", v)} desabilitado={desabilitado} />
        <Texto rotulo="Parceiros atuais" valor={valor.parceirosAtuais} onMudar={v => campo("parceirosAtuais", v)} desabilitado={desabilitado} />
        <Texto rotulo="Outros" valor={valor.outros} onMudar={v => campo("outros", v)} desabilitado={desabilitado} />
      </div>
    </div>
  );
}

/** Quantos campos estão preenchidos — vira o contador da aba e do resumo. */
export function camposPreenchidos(dados: DadosComerciais | null | undefined) {
  if (!dados) return 0;
  return Object.values(dados).filter(valor => valor !== null && valor !== "").length;
}

export const TOTAL_CAMPOS_COMERCIAIS = 13;

function Grupo({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <fieldset>
      <legend className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">{titulo}</legend>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{children}</div>
    </fieldset>
  );
}

const MOEDA = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * Valor em reais digitado como no Excel: "250.000,00" ou "250000".
 *
 * <p>O texto digitado vive no estado local enquanto o campo tem foco; só ao sair dele vira número
 * e volta formatado. Formatar a cada tecla moveria o cursor para o fim no meio da digitação.</p>
 */
function Moeda({ rotulo, valor, onMudar, desabilitado }: {
  rotulo: string;
  valor: number | null;
  onMudar: (v: number | null) => void;
  desabilitado: boolean;
}) {
  const [rascunho, setRascunho] = useState<string | null>(null);
  const exibido = rascunho ?? (valor == null ? "" : MOEDA.format(valor));

  return (
    <label className="block">
      <span className={ROTULO}>{rotulo}</span>
      <div className="relative">
        <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs text-slate-400">R$</span>
        <input
          inputMode="decimal"
          value={exibido}
          disabled={desabilitado}
          onFocus={() => setRascunho(valor == null ? "" : MOEDA.format(valor))}
          onChange={event => setRascunho(event.target.value.replace(/[^\d.,]/g, ""))}
          onBlur={() => {
            onMudar(paraNumero(rascunho ?? ""));
            setRascunho(null);
          }}
          placeholder="0,00"
          className={`${CAMPO} pl-9 text-right tabular-nums`}
        />
      </div>
    </label>
  );
}

/** "1.234,56" → 1234.56. Ponto é milhar, vírgula é decimal, como se digita no Brasil. */
function paraNumero(texto: string): number | null {
  const limpo = texto.trim();
  if (!limpo) return null;
  const numero = Number(limpo.replace(/\./g, "").replace(",", "."));
  return Number.isFinite(numero) && numero >= 0 ? numero : null;
}

function Dias({ rotulo, valor, onMudar, desabilitado }: {
  rotulo: string;
  valor: number | null;
  onMudar: (v: number | null) => void;
  desabilitado: boolean;
}) {
  return (
    <label className="block">
      <span className={ROTULO}>{rotulo}</span>
      <div className="relative">
        <input
          inputMode="numeric"
          value={valor ?? ""}
          disabled={desabilitado}
          onChange={event => {
            const digitos = event.target.value.replace(/\D/g, "").slice(0, 4);
            onMudar(digitos ? Number(digitos) : null);
          }}
          className={`${CAMPO} pr-12 tabular-nums`}
        />
        <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-xs text-slate-400">dias</span>
      </div>
    </label>
  );
}

/** Três estados: sim, não e não informado — "não" respondido é diferente de não perguntado. */
function SimNao({ rotulo, valor, onMudar, desabilitado }: {
  rotulo: string;
  valor: boolean | null;
  onMudar: (v: boolean | null) => void;
  desabilitado: boolean;
}) {
  const opcoes: [boolean | null, string][] = [[true, "Sim"], [false, "Não"], [null, "—"]];
  return (
    <div>
      <span className={ROTULO}>{rotulo}</span>
      <div role="radiogroup" aria-label={rotulo} className="inline-flex rounded-lg border border-slate-200 p-0.5 dark:border-slate-700">
        {opcoes.map(([opcao, texto]) => (
          <button
            key={texto}
            type="button"
            role="radio"
            aria-checked={valor === opcao}
            disabled={desabilitado}
            onClick={() => onMudar(opcao)}
            className={`min-h-8 min-w-12 cursor-pointer rounded-md px-3 text-xs font-medium transition-colors
              focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] disabled:cursor-not-allowed ${
                valor === opcao
                  ? "bg-[#612035] text-white dark:bg-[#e8a3b6] dark:text-slate-900"
                  : "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
              }`}
          >
            {texto}
          </button>
        ))}
      </div>
    </div>
  );
}

function Texto({ rotulo, valor, onMudar, desabilitado }: {
  rotulo: string;
  valor: string | null;
  onMudar: (v: string | null) => void;
  desabilitado: boolean;
}) {
  return (
    <label className="block">
      <span className={ROTULO}>{rotulo}</span>
      <textarea
        rows={2}
        value={valor ?? ""}
        disabled={desabilitado}
        onChange={event => onMudar(event.target.value === "" ? null : event.target.value)}
        className={`${CAMPO} resize-y`}
      />
    </label>
  );
}
