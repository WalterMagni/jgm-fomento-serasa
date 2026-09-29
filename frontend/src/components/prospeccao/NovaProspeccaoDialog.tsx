"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { useCriarProspeccao } from "@/hooks/useProspeccao";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

/**
 * Abertura de card. O CNPJ é obrigatório: é a chave que amarra esteira, análise, partes ligadas
 * e filiais, e é o nome da pasta no compartilhamento.
 */
export default function NovaProspeccaoDialog({ onFechar }: { onFechar: () => void }) {
  const criar = useCriarProspeccao();
  const [cnpj, setCnpj] = useState("");
  const [razaoSocial, setRazaoSocial] = useState("");
  const [buscando, setBuscando] = useState(false);

  const digitos = cnpj.replace(/\D/g, "");

  /** Puxa a razão social do que o portal já conhece, para não digitar o nome à mão. */
  async function buscarNome() {
    if (digitos.length !== 14) return;
    setBuscando(true);
    try {
      const token = typeof window === "undefined" ? null : localStorage.getItem("serasa_token");
      const res = await fetch(`${API_BASE_URL}/company/${digitos}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (res.ok) {
        const json = await res.json();
        const nome = json.companyName || json.tradeName;
        if (nome) setRazaoSocial(nome);
      }
    } catch {
      // Empresa fora da base ainda pode entrar na esteira; o nome vai à mão.
    } finally {
      setBuscando(false);
    }
  }

  return (
    <div
      className="esteira-fade-in fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4
        backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label="Nova prospecção"
      onClick={event => {
        if (event.target === event.currentTarget) onFechar();
      }}
    >
      <div className="esteira-modal-in w-full max-w-md rounded-xl border border-slate-200 bg-white p-5
        shadow-2xl dark:border-slate-700 dark:bg-slate-800">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-base font-semibold text-slate-800 dark:text-slate-100">Nova prospecção</h2>
          <button
            type="button"
            onClick={onFechar}
            aria-label="Fechar"
            className="cursor-pointer rounded p-1 text-slate-400 transition-colors hover:bg-slate-100
              hover:text-slate-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
              dark:hover:bg-slate-700"
          >
            <Icon name="close" />
          </button>
        </div>

        <label className="block text-xs font-medium text-slate-600 dark:text-slate-300">
          CNPJ
          <input
            autoFocus
            value={cnpj}
            onChange={event => setCnpj(event.target.value)}
            onBlur={buscarNome}
            placeholder="00.000.000/0000-00"
            className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 font-mono text-sm
              dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
          />
        </label>
        {digitos.length > 0 && digitos.length !== 14 && (
          <p className="mt-1 text-[11px] text-[#D1732C]">Faltam {14 - digitos.length} dígito(s).</p>
        )}

        <label className="mt-3 block text-xs font-medium text-slate-600 dark:text-slate-300">
          Razão social
          <input
            value={razaoSocial}
            onChange={event => setRazaoSocial(event.target.value)}
            placeholder={buscando ? "buscando…" : "nome da empresa"}
            className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 text-sm
              dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
          />
        </label>

        <div className="mt-4 flex justify-end gap-2">
          <button type="button" onClick={onFechar} className="rounded px-3 py-1.5 text-xs text-slate-600 dark:text-slate-300">
            Cancelar
          </button>
          <button
            type="button"
            disabled={digitos.length !== 14 || !razaoSocial.trim() || criar.isPending}
            onClick={() =>
              criar.mutate(
                { cnpj: digitos, razaoSocial: razaoSocial.trim() },
                { onSuccess: onFechar },
              )
            }
            className="cursor-pointer rounded bg-[#612035] px-3 py-1.5 text-xs text-white transition-colors
              hover:bg-[#4d1a2a] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
              focus-visible:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-40"
          >
            {criar.isPending ? "abrindo…" : "Abrir na triagem"}
          </button>
        </div>
      </div>
    </div>
  );
}
