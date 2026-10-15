"use client";

import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import Icon from "@/components/ui/Icon";
import { useDarNota, useNotaEmpresa } from "@/hooks/useNotaEmpresa";
import { diasDesde, formatData, tempoRelativo } from "@/lib/formatadores";
import { NOTA, NOTAS, type NotaEmpresa } from "@/types/nota-empresa";
import SeloNota from "./SeloNota";

const quando = (iso: string) => {
  const dias = diasDesde(iso);
  return dias < 30 ? tempoRelativo(dias) : formatData(iso);
};

/**
 * Nota da equipe ao lado do nome da empresa, na Visão 360º. Qualquer usuário dá ou troca: o
 * clique no selo abre as quatro letras com o que cada uma quer dizer, e embaixo o histórico de
 * quem deu cada nota.
 *
 * <p>`compacto`: selo pequeno e só o rótulo, para o cabeçalho dos cards das esteiras. Ali o
 * Esc fecha só a lista de letras, não o card inteiro.</p>
 */
export default function NotaDaEquipe({ cnpj, compacto = false }: { cnpj: string; compacto?: boolean }) {
  const { data, isLoading } = useNotaEmpresa(cnpj);
  const darNota = useDarNota(cnpj);
  const [aberto, setAberto] = useState(false);
  const caixa = useRef<HTMLDivElement>(null);
  const nota = data?.nota ?? null;

  useEffect(() => {
    if (!aberto) return;
    const fora = (e: MouseEvent) => {
      if (caixa.current && !caixa.current.contains(e.target as Node)) setAberto(false);
    };
    // Na captura, antes do Esc do diálogo da esteira: fecha a lista e para aí.
    const tecla = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      e.stopPropagation();
      setAberto(false);
    };
    document.addEventListener("mousedown", fora);
    window.addEventListener("keydown", tecla, true);
    return () => {
      document.removeEventListener("mousedown", fora);
      window.removeEventListener("keydown", tecla, true);
    };
  }, [aberto]);

  const escolher = (nova: NotaEmpresa | null) => {
    darNota.mutate(nova, {
      onSuccess: () => {
        setAberto(false);
        toast.success(nova ? `Score ${nova} · ${NOTA[nova].rotulo}` : "Score retirado.");
      },
      onError: erro => toast.error(erro instanceof Error ? erro.message : "Não foi possível salvar o score."),
    });
  };

  return (
    <div ref={caixa} className="relative print:hidden">
      <button
        type="button"
        data-sem-autofoco={compacto || undefined}
        onClick={() => setAberto(a => !a)}
        aria-haspopup="dialog"
        aria-expanded={aberto}
        disabled={isLoading}
        title={nota ? `Score: ${NOTA[nota].descricao}. Clique para trocar.` : "Dar score para esta empresa"}
        className={`group text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-wait ${
          compacto
            ? "-mx-1.5 -my-1 inline-flex cursor-pointer items-center gap-1.5 rounded-lg px-1.5 py-1 hover:bg-slate-100"
            : "-m-2 flex items-center gap-3 rounded-2xl p-2 hover:bg-white"
        }`}
      >
        <SeloNota nota={nota} tamanho={compacto ? "compacto" : "grande"} />
        {compacto ? (
          nota ? (
            <span className="text-[11.5px] font-bold" style={{ color: NOTA[nota].texto }}>
              {NOTA[nota].rotulo}
            </span>
          ) : (
            <span className="text-[11.5px] font-semibold text-primary group-hover:underline">Dar score</span>
          )
        ) : (
          <span className="flex flex-col leading-tight">
            <span className="text-[10.5px] font-bold uppercase tracking-[0.14em] text-[#7F7C73]">Score</span>
            {nota ? (
              <>
                <span className="mt-0.5 text-[14px] font-bold" style={{ color: NOTA[nota].texto }}>
                  {NOTA[nota].rotulo}
                </span>
                {data?.por && data.em && (
                  <span className="mt-0.5 text-[11.5px] text-suave">
                    por {data.por.split(" ")[0]} · {quando(data.em)}
                  </span>
                )}
              </>
            ) : (
              <span className="mt-0.5 inline-flex items-center gap-1 text-[13px] font-bold text-primary group-hover:underline">
                <Icon name="emoji_events" size={15} />
                Dar score
              </span>
            )}
          </span>
        )}
      </button>

      {aberto && (
        <div
          role="dialog"
          aria-label="Score da empresa"
          className={`absolute left-0 top-full z-30 ${compacto ? "mt-2" : "mt-3"} w-[min(340px,calc(100vw-2.5rem))] overflow-hidden rounded-painel border border-linha bg-white shadow-elevado animate-cair motion-reduce:animate-none`}
        >
          <div className="flex items-start gap-3 border-b border-[#F0EEE8] px-5 py-4">
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-[11px] bg-primary/10 text-primary">
              <Icon name="emoji_events" size={20} />
            </span>
            <span>
              <span className="block font-display text-[17px] font-semibold text-grafite">Score</span>
              <span className="block text-[12.5px] text-suave">Como a equipe avalia esta empresa.</span>
            </span>
          </div>

          <ul className="flex flex-col gap-0.5 p-2">
            {NOTAS.map(letra => {
              const atual = letra === nota;
              return (
                <li key={letra}>
                  <button
                    type="button"
                    onClick={() => escolher(letra)}
                    disabled={darNota.isPending}
                    aria-pressed={atual}
                    className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-60 ${
                      atual ? "bg-[#F6EFEA]" : "hover:bg-papel"
                    }`}
                  >
                    <span
                      className="grid h-10 w-10 shrink-0 place-items-center rounded-[11px] font-display text-[22px] font-semibold text-white"
                      style={{ background: NOTA[letra].cor, textShadow: "0 1px 2px rgba(0,0,0,0.3)" }}
                    >
                      {letra}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-bold text-grafite">{NOTA[letra].rotulo}</span>
                      <span className="block text-xs text-suave">{NOTA[letra].descricao}</span>
                    </span>
                    {atual && <Icon name="check" size={18} className="shrink-0 text-primary" />}
                  </button>
                </li>
              );
            })}
          </ul>

          {nota && (
            <div className="px-2 pb-2">
              <button
                type="button"
                onClick={() => escolher(null)}
                disabled={darNota.isPending}
                className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-[13px] font-semibold text-suave transition-colors hover:bg-papel hover:text-grafite disabled:opacity-60"
              >
                <Icon name="close" size={14} />
                Retirar score
              </button>
            </div>
          )}

          {!!data?.historico.length && (
            <div className="border-t border-[#F0EEE8] bg-[#FBFAF7] px-5 py-3">
              <p className="mb-2 text-[11px] font-bold uppercase tracking-[0.1em] text-[#7F7C73]">Histórico</p>
              <ol className="flex flex-col gap-1.5">
                {data.historico.slice(0, 5).map((registro, i) => (
                  <li key={`${registro.em}-${i}`} className="flex items-center gap-2 text-[12.5px] text-suave">
                    {registro.nota ? (
                      <span
                        className="grid h-5 w-5 shrink-0 place-items-center rounded-[5px] font-display text-[11px] font-semibold text-white"
                        style={{ background: NOTA[registro.nota].cor, textShadow: "0 1px 1px rgba(0,0,0,0.3)" }}
                      >
                        {registro.nota}
                      </span>
                    ) : (
                      <span className="grid h-5 w-5 shrink-0 place-items-center rounded-[5px] bg-[#ECE9E1] text-[#8A877E]">
                        <Icon name="close" size={11} />
                      </span>
                    )}
                    <span className="min-w-0 flex-1 truncate">
                      <b className="font-semibold text-grafite">{registro.nota ? NOTA[registro.nota].rotulo : "Score retirado"}</b>
                      {registro.por && ` · ${registro.por}`}
                    </span>
                    <span className="shrink-0 text-[11.5px]">{quando(registro.em)}</span>
                  </li>
                ))}
              </ol>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
