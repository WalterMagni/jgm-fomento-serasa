"use client";

import React, { useState, useCallback } from "react";
import Icon from "@/components/ui/Icon";

// ─── Types ────────────────────────────────────────────────────────────────────

interface AiAnalysisData {
  available: boolean;
  parecer?: string;
  visaoCedente?: string;
  nivelRisco?: string;
  recomendacao?: string;
  pontosFortes?: string[];
  pontosAtencao?: string[];
  errorMessage?: string;
  analysisDate?: string;
}

interface Props {
  cnpj: string;
  hasSerasaData: boolean;
  initialData?: AiAnalysisData | null;
  aiAnalysisDate?: string | null;
}

// ─── Risk colour map ──────────────────────────────────────────────────────────

const RISCO_CONFIG: Record<string, { label: string; bg: string; text: string; border: string; dot: string }> = {
  BAIXO:     { label: "Baixo Risco",       bg: "bg-emerald-50 dark:bg-emerald-900/20",  text: "text-emerald-700 dark:text-emerald-300",  border: "border-emerald-300 dark:border-emerald-700",  dot: "bg-emerald-500" },
  MODERADO:  { label: "Risco Moderado",    bg: "bg-amber-50 dark:bg-amber-900/20",      text: "text-amber-700 dark:text-amber-300",      border: "border-amber-300 dark:border-amber-700",      dot: "bg-amber-500" },
  ALTO:      { label: "Alto Risco",        bg: "bg-orange-50 dark:bg-orange-900/20",    text: "text-orange-700 dark:text-orange-300",    border: "border-orange-300 dark:border-orange-700",    dot: "bg-orange-500" },
  MUITO_ALTO:{ label: "Muito Alto Risco",  bg: "bg-red-50 dark:bg-red-900/20",          text: "text-red-700 dark:text-red-300",          border: "border-red-300 dark:border-red-700",          dot: "bg-red-500" },
};

const RECOMENDACAO_CONFIG: Record<string, { label: string; icon: string; color: string }> = {
  APROVADO:    { label: "Aprovado",    icon: "check_circle",  color: "text-emerald-600 dark:text-emerald-400" },
  CONDICIONAL: { label: "Condicional", icon: "pending",       color: "text-amber-600 dark:text-amber-400" },
  NEGADO:      { label: "Negado",      icon: "cancel",        color: "text-red-600 dark:text-red-400" },
};

function ShimmerLine({ w = "w-full", h = "h-4" }: { w?: string; h?: string }) {
  return (
    <div className={`${w} ${h} rounded-md bg-gradient-to-r from-sky-100 via-sky-50 to-sky-100 dark:from-sky-900/30 dark:via-sky-800/20 dark:to-sky-900/30 bg-[length:200%_100%] animate-[shimmer_1.6s_ease-in-out_infinite]`} />
  );
}

function AiOrb({ isLoading }: { isLoading: boolean }) {
  return (
    <div className="relative flex-shrink-0 w-12 h-12">
      {isLoading && (
        <>
          <span className="absolute inset-0 rounded-full bg-sky-400/20 animate-ping" />
          <span className="absolute inset-1 rounded-full bg-sky-400/15 animate-ping [animation-delay:0.3s]" />
        </>
      )}
      <div className={`relative w-12 h-12 rounded-full flex items-center justify-center bg-gradient-to-br from-sky-400 to-blue-600 shadow-[0_0_20px_rgba(56,189,248,0.5)] ${isLoading ? "animate-[pulse_2s_ease-in-out_infinite]" : ""}`}>
        <Icon name={isLoading ? "psychology" : "auto_awesome"} className="text-white text-xl" />
      </div>
    </div>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────

export function AiAnalysisCard({ cnpj, hasSerasaData, initialData, aiAnalysisDate }: Props) {
  const [data, setData] = useState<AiAnalysisData | null>(initialData ?? null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExpanded, setIsExpanded] = useState(true);

  const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

  const refreshAnalysis = useCallback(async () => {
    setIsLoading(true);
    try {
      const token = localStorage.getItem("serasa_token");
      const headers: HeadersInit = {};
      if (token) headers["Authorization"] = `Bearer ${token}`;
      const res = await fetch(`${apiUrl}/company/${cnpj}/ai-analysis/refresh`, { method: "POST", headers });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json: AiAnalysisData = await res.json();
      setData(json);
    } catch {
      setData({ available: false, errorMessage: "Não foi possível gerar a análise de IA." });
    } finally {
      setIsLoading(false);
    }
  }, [cnpj, apiUrl]);

  const risco = data?.nivelRisco ? RISCO_CONFIG[data.nivelRisco] ?? RISCO_CONFIG.MODERADO : null;
  const rec = data?.recomendacao ? RECOMENDACAO_CONFIG[data.recomendacao] ?? RECOMENDACAO_CONFIG.CONDICIONAL : null;

  const displayDate = data?.analysisDate || aiAnalysisDate;

  return (
    <>
      <style>{`
        @keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
        @keyframes fadeInUp { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
        .ai-fade-in { animation: fadeInUp 0.4s ease both; }
      `}</style>

      <div className="relative mb-6 rounded-2xl overflow-hidden border border-sky-200/70 dark:border-sky-700/50 shadow-[0_4px_24px_rgba(56,189,248,0.12),0_1px_4px_rgba(56,189,248,0.08)] print:hidden">
        {/* Gradient background */}
        <div className="absolute inset-0 pointer-events-none" aria-hidden style={{ background: "linear-gradient(135deg, rgba(224,242,254,0.95) 0%, rgba(186,230,253,0.6) 40%, rgba(255,255,255,0.85) 100%)" }} />
        <div className="absolute inset-0 dark:block hidden pointer-events-none" aria-hidden style={{ background: "linear-gradient(135deg, rgba(12,74,110,0.55) 0%, rgba(7,89,133,0.3) 40%, rgba(15,23,42,0.0) 100%)" }} />
        <div className="absolute -top-10 -right-10 w-48 h-48 rounded-full bg-sky-300/20 blur-3xl pointer-events-none" aria-hidden />
        <div className="absolute -bottom-8 -left-8 w-36 h-36 rounded-full bg-blue-400/15 blur-2xl pointer-events-none" aria-hidden />

        <div className="relative p-6">
          {/* Header row */}
          <div className="flex items-center gap-3 mb-4">
            <AiOrb isLoading={isLoading} />
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h2 className="font-sans font-bold text-base text-sky-900 dark:text-sky-100">Análise de Crédito com IA</h2>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-sky-500/15 text-sky-700 dark:text-sky-300 text-[10px] font-sans font-bold uppercase tracking-wider border border-sky-400/30">
                  <span className="w-1.5 h-1.5 rounded-full bg-sky-500 animate-pulse inline-block" />
                  Gemini 2.5 Flash
                </span>
              </div>
              <p className="text-xs text-sky-700/70 dark:text-sky-400/80 font-sans mt-0.5">
                Visão cedente para factoring · análise sob demanda com dados Serasa
                {displayDate && (
                  <span className="ml-2 text-sky-600/60">
                    · Gerada em {new Date(displayDate).toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" })}
                  </span>
                )}
              </p>
            </div>

            <div className="flex items-center gap-2 flex-shrink-0">
              {hasSerasaData && !isLoading && (
                <button
                  onClick={refreshAnalysis}
                  title={data?.available ? "Reanalisar" : "Gerar Análise"}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-sky-600 hover:bg-sky-700 active:scale-95 text-white text-xs font-sans font-medium transition-all duration-150 shadow-sm"
                >
                  <Icon name={data?.available ? "refresh" : "auto_awesome"} className="text-sm" />
                  {data?.available ? "Reanalisar" : "Gerar Análise"}
                </button>
              )}
              {!hasSerasaData && !isLoading && (
                <span className="text-xs text-sky-600/70 dark:text-sky-400/60 font-sans">Aguardando consulta Serasa</span>
              )}
              <button onClick={() => setIsExpanded(v => !v)} className="p-1.5 rounded-lg hover:bg-sky-200/50 dark:hover:bg-sky-800/30 transition-colors">
                <Icon name={isExpanded ? "expand_less" : "expand_more"} className="text-base text-sky-600 dark:text-sky-400" />
              </button>
            </div>
          </div>

          {isExpanded && (
            <>
              {/* Loading state */}
              {isLoading && (
                <div className="space-y-3 ai-fade-in">
                  <div className="flex items-center gap-2 text-sky-600 dark:text-sky-400 text-sm font-sans mb-4">
                    <Icon name="sync" className="text-base animate-spin" />
                    Processando dados com Gemini AI...
                  </div>
                  <ShimmerLine w="w-full" h="h-4" />
                  <ShimmerLine w="w-5/6" h="h-4" />
                  <ShimmerLine w="w-4/6" h="h-4" />
                  <div className="grid grid-cols-2 gap-3 mt-4">
                    <ShimmerLine h="h-16" />
                    <ShimmerLine h="h-16" />
                  </div>
                </div>
              )}

              {/* No analysis yet */}
              {!isLoading && (!data || (!data.available && !data.errorMessage?.includes("Não foi possível"))) && hasSerasaData && (
                <div className="ai-fade-in flex items-start gap-3 p-4 rounded-xl bg-sky-100/60 dark:bg-sky-900/20 border border-sky-200 dark:border-sky-800">
                  <Icon name="auto_awesome" className="text-sky-500 text-xl flex-shrink-0" />
                  <div>
                    <p className="text-sm font-sans font-medium text-sky-800 dark:text-sky-200">Análise não gerada ainda.</p>
                    <p className="text-xs text-sky-600 dark:text-sky-400 mt-0.5">Clique em &quot;Gerar Análise&quot; para criar o parecer com IA.</p>
                  </div>
                </div>
              )}

              {/* Error */}
              {!isLoading && data && !data.available && data.errorMessage?.includes("Não foi possível") && (
                <div className="ai-fade-in flex items-start gap-3 p-4 rounded-xl bg-sky-100/60 dark:bg-sky-900/20 border border-sky-200 dark:border-sky-800">
                  <Icon name="info" className="text-sky-500 text-xl flex-shrink-0" />
                  <p className="text-sm font-sans text-sky-800 dark:text-sky-200">{data.errorMessage}</p>
                </div>
              )}

              {/* Result */}
              {!isLoading && data?.available && (
                <div className="space-y-5 ai-fade-in">
                  {/* Disclaimer */}
                  <div className="flex items-start gap-2.5 p-3 rounded-xl bg-amber-50 dark:bg-amber-900/15 border border-amber-200 dark:border-amber-800/50">
                    <Icon name="warning_amber" className="text-amber-500 text-base flex-shrink-0 mt-px" />
                    <p className="text-xs font-sans text-amber-800 dark:text-amber-200 leading-relaxed">
                      <strong>Aviso:</strong> Esta análise é gerada por IA com base nos dados da Serasa Experian, que podem conter imprecisões.
                      Exemplo: empresas classificadas sem funcionários pelo Serasa que efetivamente possuem colaboradores. Use como ferramenta auxiliar — não substitui a análise humana.
                    </p>
                  </div>

                  {/* Risk + recommendation */}
                  {(risco || rec) && (
                    <div className="flex flex-wrap gap-3">
                      {risco && (
                        <div className={`inline-flex items-center gap-2 px-4 py-2 rounded-xl border font-sans font-bold text-sm ${risco.bg} ${risco.text} ${risco.border}`}>
                          <span className={`w-2 h-2 rounded-full flex-shrink-0 ${risco.dot}`} />
                          {risco.label}
                        </div>
                      )}
                      {rec && (
                        <div className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-sky-200 dark:border-sky-700 bg-white/60 dark:bg-sky-900/20 font-sans font-bold text-sm text-sky-900 dark:text-sky-100">
                          <Icon name={rec.icon} className={`text-base ${rec.color}`} />
                          {rec.label}
                        </div>
                      )}
                    </div>
                  )}

                  {data.parecer && (
                    <div>
                      <p className="text-[10px] font-sans font-bold text-sky-600/70 dark:text-sky-400/70 uppercase tracking-widest mb-2">Parecer Geral</p>
                      <p className="text-sm font-serif text-sky-950 dark:text-sky-100 leading-relaxed whitespace-pre-line">{data.parecer}</p>
                    </div>
                  )}

                  {data.visaoCedente && (
                    <div className="rounded-xl bg-white/70 dark:bg-sky-900/30 border border-sky-200 dark:border-sky-700/60 p-4">
                      <p className="text-[10px] font-sans font-bold text-sky-700 dark:text-sky-300 uppercase tracking-widest mb-2 flex items-center gap-1.5">
                        <Icon name="receipt_long" className="text-sm" />
                        Visão Cedente (Factoring)
                      </p>
                      <p className="text-sm font-serif text-sky-950 dark:text-sky-100 leading-relaxed whitespace-pre-line">{data.visaoCedente}</p>
                    </div>
                  )}

                  {((data.pontosFortes?.length ?? 0) > 0 || (data.pontosAtencao?.length ?? 0) > 0) && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      {(data.pontosFortes?.length ?? 0) > 0 && (
                        <div className="rounded-xl bg-emerald-50/80 dark:bg-emerald-900/15 border border-emerald-200 dark:border-emerald-800/50 p-4">
                          <p className="text-[10px] font-sans font-bold text-emerald-700 dark:text-emerald-400 uppercase tracking-widest mb-2 flex items-center gap-1.5">
                            <Icon name="thumb_up" className="text-sm" />Pontos Positivos
                          </p>
                          <ul className="space-y-1.5">
                            {data.pontosFortes!.map((p, i) => (
                              <li key={i} className="flex items-start gap-2 text-sm font-serif text-emerald-900 dark:text-emerald-200">
                                <Icon name="check_circle" className="text-emerald-500 text-sm flex-shrink-0 mt-px" />{p}
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}
                      {(data.pontosAtencao?.length ?? 0) > 0 && (
                        <div className="rounded-xl bg-amber-50/80 dark:bg-amber-900/15 border border-amber-200 dark:border-amber-800/50 p-4">
                          <p className="text-[10px] font-sans font-bold text-amber-700 dark:text-amber-400 uppercase tracking-widest mb-2 flex items-center gap-1.5">
                            <Icon name="warning" className="text-sm" />Pontos de Atenção
                          </p>
                          <ul className="space-y-1.5">
                            {data.pontosAtencao!.map((p, i) => (
                              <li key={i} className="flex items-start gap-2 text-sm font-serif text-amber-900 dark:text-amber-200">
                                <Icon name="error_outline" className="text-amber-500 text-sm flex-shrink-0 mt-px" />{p}
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}

              {/* No Serasa data */}
              {!isLoading && !hasSerasaData && (
                <div className="flex items-center gap-3 p-4 rounded-xl bg-sky-100/50 dark:bg-sky-900/15">
                  <Icon name="lock_clock" className="text-sky-400 text-xl" />
                  <p className="text-sm font-sans text-sky-700 dark:text-sky-300">Consulte o Serasa para liberar a análise de IA.</p>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </>
  );
}
