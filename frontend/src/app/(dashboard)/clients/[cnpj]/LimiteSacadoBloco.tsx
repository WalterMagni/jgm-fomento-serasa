"use client";

import { useState } from "react";
import { toast } from "sonner";
import CampoNumero from "@/components/ui/CampoNumero";
import Icon from "@/components/ui/Icon";
import { moedaParaCampo, parseMoeda } from "@/components/liberacao/formatters";
import { useDefinirLimiteSacado, useLimiteSacado } from "@/hooks/useLimiteSacado";
import { formatData, formatMoeda } from "@/lib/formatadores";

/**
 * Limite da empresa como sacado (pedido do time em 2026-10-26), na faixa de resumo. Diferente dos
 * outros blocos, edita ali mesmo: qualquer pessoa define, troca ou tira. A liberação mostra o
 * limite em cada sacado. O histórico das trocas aparece passando o mouse no valor.
 */
export default function LimiteSacadoBloco({ cnpj, atraso }: { cnpj: string; atraso: number }) {
  const { data, isLoading } = useLimiteSacado(cnpj);
  const definir = useDefinirLimiteSacado(cnpj);
  const [editando, setEditando] = useState(false);
  const [valor, setValor] = useState("");
  const atual = data?.valor ?? null;

  function salvar(novo: number | null) {
    definir.mutate(novo, {
      onSuccess: () => {
        setEditando(false);
        toast.success(novo == null ? "Limite retirado" : "Limite salvo");
      },
      onError: erro => toast.error(erro.message),
    });
  }

  const historico = (data?.historico ?? [])
    .map(registro => `${formatData(registro.em)} · ${registro.por ?? "?"}: ${registro.valor == null ? "retirou" : formatMoeda(registro.valor)}`)
    .join("\n");

  return (
    <div
      className="flex flex-col gap-1.5 rounded-cartao border border-linha bg-white px-5 py-4 animate-subir motion-reduce:animate-none"
      style={{ animationDelay: `${atraso}ms` }}
    >
      <span className="flex items-center gap-2 text-[12.5px] font-semibold text-suave">
        <Icon name="payment" size={18} style={{ color: "#2956E0" }} />
        Limite de sacado
        {!editando && (
          <button
            type="button"
            onClick={() => {
              setValor(moedaParaCampo(atual));
              setEditando(true);
            }}
            aria-label={atual == null ? "Definir limite de sacado" : "Editar limite de sacado"}
            title={atual == null ? "Definir limite" : "Editar limite"}
            className="ml-auto inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-md text-[#8A877E] transition-colors hover:bg-[#F3F1EC] hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <Icon name="edit" size={15} />
          </button>
        )}
      </span>
      {editando ? (
        <form
          className="flex flex-col gap-2"
          onSubmit={event => {
            event.preventDefault();
            const numero = parseMoeda(valor);
            if (!numero || numero <= 0) {
              toast.error("Informe um limite maior que zero.");
              return;
            }
            salvar(numero);
          }}
        >
          <div className="relative">
            <span className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[13px] text-suave">R$</span>
            <CampoNumero
              autoFocus
              valor={valor}
              onMudar={setValor}
              placeholder="0,00"
              aria-label="Limite de sacado"
              onKeyDown={event => {
                if (event.key === "Escape") setEditando(false);
              }}
              className="w-full rounded-lg border border-linha bg-white py-1.5 pl-8 pr-2 text-right text-[15px] tabular-nums focus:border-primary focus:outline-none"
            />
          </div>
          <div className="flex flex-wrap items-center gap-1.5">
            <button
              type="submit"
              disabled={definir.isPending}
              className="h-8 cursor-pointer rounded-lg bg-primary px-3 text-[12.5px] font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
            >
              {definir.isPending ? "Salvando…" : "Salvar"}
            </button>
            <button
              type="button"
              onClick={() => setEditando(false)}
              className="h-8 cursor-pointer rounded-lg px-2.5 text-[12.5px] font-medium text-suave hover:bg-[#F3F1EC]"
            >
              Cancelar
            </button>
            {atual != null && (
              <button
                type="button"
                disabled={definir.isPending}
                onClick={() => salvar(null)}
                className="ml-auto h-8 cursor-pointer rounded-lg px-2 text-[12px] font-medium text-[#B42345] hover:bg-[#F8E1E6] disabled:opacity-60"
              >
                Tirar limite
              </button>
            )}
          </div>
        </form>
      ) : (
        <>
          <span
            className="font-display text-[26px] font-semibold leading-tight tracking-[-0.015em]"
            style={{ color: atual == null ? "#8A877E" : "#262833" }}
            title={historico || undefined}
          >
            {isLoading ? "…" : atual == null ? "Sem limite" : formatMoeda(atual)}
          </span>
          <span className="text-[12.5px] text-suave">
            {atual != null && data?.em
              ? `por ${data.por ?? "?"} · ${formatData(data.em)}`
              : "Clique no lápis para definir"}
          </span>
        </>
      )}
    </div>
  );
}
