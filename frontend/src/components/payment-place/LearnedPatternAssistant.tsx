"use client";

import { useEffect, useMemo, useState } from "react";
import Icon from "@/components/ui/Icon";
import { PaymentPlaceEntry } from "../../types/payment-place";

type Decision = "SACADO" | "CEDENTE" | "INCONCLUSIVO";

function formatCnpj(cnpj?: string | null) {
  if (!cnpj) return "";
  const d = cnpj.replace(/\D/g, "");
  if (d.length !== 14) return cnpj;
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12, 14)}`;
}

/**
 * Assistente flutuante de padrões aprendidos. Varre os lançamentos visíveis, separa os que
 * batem com um padrão (par cedente×sacado já decidido antes) e ainda estão pendentes, e
 * oferece aplicar a decisão sugerida em todos de uma vez. Tem uma fase de "pensando" para
 * dar a sensação de uma IA processando — a decisão continua sendo do analista (pode reabrir).
 */
export default function LearnedPatternAssistant({
  entries,
  onApply,
  isApplying,
}: {
  entries: PaymentPlaceEntry[];
  onApply: (decisions: { entryId: string; decision: Decision }[]) => void;
  isApplying: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [phase, setPhase] = useState<"thinking" | "ready">("thinking");

  const candidates = useMemo(
    () => entries.filter((e) => (e.learnedPatternDecision === "CEDENTE" || e.learnedPatternDecision === "SACADO") && !e.analystDecision),
    [entries],
  );
  const cedente = candidates.filter((e) => e.learnedPatternDecision === "CEDENTE");
  const sacado = candidates.filter((e) => e.learnedPatternDecision === "SACADO");
  const count = candidates.length;

  // Pequena fase de "processamento" ao abrir, para a sensação de IA pensando.
  useEffect(() => {
    if (!open) return;
    setPhase("thinking");
    const t = setTimeout(() => setPhase("ready"), 1100);
    return () => clearTimeout(t);
  }, [open]);

  const apply = () => {
    if (!count) return;
    onApply(candidates.map((e) => ({ entryId: e.id, decision: e.learnedPatternDecision as Decision })));
  };

  const thinking = phase === "thinking";

  return (
    <>
      {/* Painel */}
      {open ? (
        <div className="fixed bottom-24 right-6 z-50 w-[348px] max-w-[calc(100vw-3rem)] origin-bottom-right animate-[ppa-in_200ms_ease-out]">
          {/* Aura "pensando" — gradiente cônico girando atrás do card */}
          <div className="pointer-events-none absolute -inset-[1.5px] overflow-hidden rounded-[20px]">
            <div
              className="absolute left-1/2 top-1/2 h-[160%] w-[160%] -translate-x-1/2 -translate-y-1/2 animate-[ppa-aura_4s_linear_infinite] opacity-70"
              style={{ background: "conic-gradient(from 0deg, transparent 0deg, #34d39955 70deg, #61203500 140deg, #34d39955 210deg, transparent 300deg)" }}
            />
          </div>

          <div className="relative overflow-hidden rounded-[18px] border border-emerald-200/60 bg-white shadow-2xl ring-1 ring-black/5 dark:border-emerald-500/20 dark:bg-surface-dark">
            {/* Cabeçalho — cabeça pensando */}
            <div className="relative overflow-hidden bg-gradient-to-br from-[#54142b] via-[#612035] to-[#7d2a45] px-4 py-3.5 text-white">
              <div className="absolute -right-8 -top-10 h-28 w-28 animate-[ppa-float_4s_ease-in-out_infinite] rounded-full bg-emerald-400/25 blur-2xl" />
              <div className="absolute -left-6 bottom-0 h-20 w-20 rounded-full bg-fuchsia-400/10 blur-2xl" />
              <div className="relative flex items-center gap-3">
                {/* Cérebro com órbita de partículas */}
                <span className="relative flex h-11 w-11 items-center justify-center">
                  <span className="absolute inset-0 rounded-full bg-white/10 ring-1 ring-white/20" />
                  <span className="absolute inset-0 animate-ping rounded-full bg-emerald-400/20" />
                  <Icon name="psychology" size={24} className="relative text-emerald-300" />
                  <span className="absolute left-1/2 top-1/2 h-1.5 w-1.5 -translate-x-1/2 -translate-y-1/2 animate-[ppa-orbit_2.4s_linear_infinite] rounded-full bg-emerald-300 shadow-[0_0_6px_#34d399]" />
                  <span className="absolute left-1/2 top-1/2 h-1 w-1 -translate-x-1/2 -translate-y-1/2 animate-[ppa-orbit_3.2s_linear_infinite_reverse] rounded-full bg-white/80" />
                </span>
                <div className="min-w-0">
                  <p className="text-sm font-bold leading-tight">Assistente de padrões</p>
                  <p className="text-[11px] text-white/70">
                    {thinking ? (
                      <span className="inline-flex items-center gap-1">
                        Analisando lançamentos
                        <span className="inline-flex gap-[2px]">
                          <i className="h-1 w-1 animate-[ppa-think_1.2s_infinite] rounded-full bg-emerald-300 [animation-delay:0ms]" />
                          <i className="h-1 w-1 animate-[ppa-think_1.2s_infinite] rounded-full bg-emerald-300 [animation-delay:200ms]" />
                          <i className="h-1 w-1 animate-[ppa-think_1.2s_infinite] rounded-full bg-emerald-300 [animation-delay:400ms]" />
                        </span>
                      </span>
                    ) : (
                      "Aprende com suas decisões anteriores"
                    )}
                  </p>
                </div>
                <button
                  onClick={() => setOpen(false)}
                  className="ml-auto inline-flex h-7 w-7 items-center justify-center rounded-lg text-white/70 transition-colors hover:bg-white/10 hover:text-white"
                  aria-label="Fechar"
                >
                  <Icon name="close" size={16} />
                </button>
              </div>
            </div>

            {/* Corpo */}
            <div className="relative p-4">
              {thinking ? (
                <ThinkingBody />
              ) : count === 0 ? (
                <div className="py-6 text-center">
                  <Icon name="search_off" size={28} className="mx-auto text-gray-300" />
                  <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">
                    Nenhum lançamento pendente com padrão aprendido nesta lista.
                  </p>
                  <p className="mt-1 text-xs text-gray-400">
                    Decida títulos entre as mesmas empresas e eu passo a reconhecê-los.
                  </p>
                </div>
              ) : (
                <>
                  <p className="text-sm text-grafite dark:text-gray-200">
                    Reconheci <span className="font-bold text-emerald-600 dark:text-emerald-400">{count}</span>{" "}
                    lançamento{count === 1 ? "" : "s"} com padrão já decidido antes:
                  </p>

                  <div className="mt-3 flex gap-2">
                    <SummaryChip label="Cedente" value={cedente.length} color="#612035" />
                    <SummaryChip label="Sacado" value={sacado.length} color="#2956E0" />
                  </div>

                  {/* Lista com linha de varredura sobreposta */}
                  <div className="relative mt-3">
                    <span className="pointer-events-none absolute inset-x-0 top-0 z-10 h-8 animate-[ppa-scan_2.6s_ease-in-out_infinite] bg-gradient-to-b from-emerald-400/0 via-emerald-400/20 to-emerald-400/0" />
                    <ul className="max-h-[260px] space-y-1.5 overflow-y-auto pr-1">
                      {candidates.slice(0, 30).map((e) => (
                        <li
                          key={e.id}
                          className="rounded-lg border border-border-light bg-gray-50/60 px-2.5 py-2 text-xs dark:border-border-dark dark:bg-white/[0.03]"
                        >
                          <div className="flex items-center gap-2">
                            <span className="font-mono font-bold text-gray-500 dark:text-gray-400">{e.externalId}</span>
                            <span
                              className="ml-auto shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold"
                              style={{
                                color: e.learnedPatternDecision === "CEDENTE" ? "#612035" : "#2956E0",
                                background: e.learnedPatternDecision === "CEDENTE" ? "#6120351a" : "#2956E01a",
                              }}
                            >
                              {e.learnedPatternDecision === "CEDENTE" ? "Cedente" : "Sacado"} {e.learnedPatternCount}/{e.learnedPatternTotal}
                            </span>
                          </div>
                          <div className="mt-1 space-y-0.5">
                            <p className="flex gap-1.5 leading-snug">
                              <span className="shrink-0 font-bold text-[#612035] dark:text-[#d98aa3]">Ced:</span>
                              <span className="text-gray-700 dark:text-gray-200">{e.clientName || formatCnpj(e.clientDocument) || "—"}</span>
                            </p>
                            <p className="flex gap-1.5 leading-snug">
                              <span className="shrink-0 font-bold text-[#2956E0] dark:text-[#7da0f0]">Sac:</span>
                              <span className="text-gray-700 dark:text-gray-200">{e.payerName || formatCnpj(e.payerDocument) || "—"}</span>
                            </p>
                          </div>
                        </li>
                      ))}
                      {candidates.length > 30 ? (
                        <li className="px-1 pt-1 text-center text-[11px] text-gray-400">+{candidates.length - 30} outros…</li>
                      ) : null}
                    </ul>
                  </div>

                  <button
                    onClick={apply}
                    disabled={isApplying}
                    className="group mt-4 flex w-full items-center justify-center gap-2 overflow-hidden rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition-all hover:bg-emerald-700 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-80"
                  >
                    {isApplying ? (
                      <>
                        <span className="absolute inset-0 animate-[ppa-shimmer_1.2s_linear_infinite] bg-[linear-gradient(110deg,transparent_30%,rgba(255,255,255,0.25)_50%,transparent_70%)] bg-[length:200%_100%]" />
                        <Icon name="psychology" size={18} className="relative animate-pulse" />
                        <span className="relative">Processando…</span>
                      </>
                    ) : (
                      <>
                        <Icon name="auto_awesome" size={18} className="transition-transform group-hover:rotate-12" />
                        Analisar todos ({count})
                      </>
                    )}
                  </button>
                  <p className="mt-2 text-center text-[11px] text-gray-400">
                    Aplica a decisão sugerida. Você pode reabrir qualquer uma depois.
                  </p>
                </>
              )}
            </div>
          </div>
        </div>
      ) : null}

      {/* Botão flutuante */}
      <button
        onClick={() => setOpen((v) => !v)}
        aria-label="Assistente de padrões aprendidos"
        title="Assistente de padrões aprendidos"
        className="group fixed bottom-6 right-6 z-50 flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-[#612035] to-[#7d2a45] text-white shadow-xl shadow-[#612035]/30 transition-transform hover:scale-105 active:scale-95"
      >
        {count > 0 ? <span className="absolute inset-0 animate-ping rounded-full bg-emerald-400/40" /> : null}
        <Icon name="psychology" size={26} className="relative text-emerald-200 transition-transform group-hover:rotate-6" />
        {count > 0 ? (
          <span className="absolute -right-1 -top-1 flex h-6 min-w-6 items-center justify-center rounded-full border-2 border-white bg-emerald-500 px-1 text-xs font-bold text-white dark:border-surface-dark">
            {count > 99 ? "99+" : count}
          </span>
        ) : null}
      </button>
    </>
  );
}

/** Estado de "processando": skeleton pulsante + texto shimmer, simula a IA pensando. */
function ThinkingBody() {
  return (
    <div className="py-2">
      <p
        className="bg-[linear-gradient(110deg,#9ca3af_30%,#34d399_50%,#9ca3af_70%)] bg-[length:200%_100%] bg-clip-text text-sm font-semibold text-transparent"
        style={{ animation: "ppa-shimmer 1.6s linear infinite" }}
      >
        Cruzando com o histórico de decisões…
      </p>
      <div className="mt-3 space-y-2">
        {[0, 1, 2].map((i) => (
          <div
            key={i}
            className="flex items-center gap-2 rounded-lg border border-border-light bg-gray-50/60 px-2.5 py-2 dark:border-border-dark dark:bg-white/[0.03]"
            style={{ animation: "ppa-think 1.4s ease-in-out infinite", animationDelay: `${i * 180}ms` }}
          >
            <span className="h-3 w-12 rounded bg-gray-200 dark:bg-white/10" />
            <span className="h-3 flex-1 rounded bg-gray-200 dark:bg-white/10" />
            <span className="h-4 w-14 rounded-full bg-emerald-200/60 dark:bg-emerald-500/20" />
          </div>
        ))}
      </div>
    </div>
  );
}

function SummaryChip({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="flex flex-1 items-center gap-2 rounded-lg border border-border-light px-2.5 py-1.5 dark:border-border-dark" style={{ background: `${color}0a` }}>
      <span className="text-lg font-bold" style={{ color }}>{value}</span>
      <span className="text-[11px] font-bold uppercase tracking-wide text-gray-400">{label}</span>
    </div>
  );
}
