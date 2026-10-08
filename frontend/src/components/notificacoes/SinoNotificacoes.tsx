"use client";

import { useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import Icon from "@/components/ui/Icon";
import { useDefinirPreferencia, useMarcarLida, useMarcarTodasLidas, useNotificacoes } from "@/hooks/useNotificacoes";
import { useUsuarioAtual } from "@/hooks/useLiberacao";
import type { Notificacao, TipoNotificacao } from "@/types/notificacao";
import { tempoRelativo } from "@/components/liberacao/formatters";
import { useTempoReal } from "./NotificacoesProvider";
import { tocarSino } from "./som";

const VISUAL: Record<TipoNotificacao, { icone: string; cor: string }> = {
  CARD_CRIADO: { icone: "inbox", cor: "bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-200" },
  PARECER_ESPERADO: { icone: "gavel", cor: "bg-[#612035]/10 text-[#612035] dark:bg-[#612035]/40 dark:text-[#f2c4d1]" },
  PARECER_REGISTRADO: { icone: "gavel", cor: "bg-[#612035]/10 text-[#612035] dark:bg-[#612035]/40 dark:text-[#f2c4d1]" },
  COMITE_COMPLETO: { icone: "check_circle", cor: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300" },
  PENDENCIA_ABERTA: { icone: "hourglass_top", cor: "bg-[#D1732C]/15 text-[#a8551a] dark:text-[#f0a46b]" },
  PENDENCIA_RESPONDIDA: { icone: "reply", cor: "bg-[#D1732C]/15 text-[#a8551a] dark:text-[#f0a46b]" },
  MENCAO: { icone: "alternate_email", cor: "bg-[#2956E0]/10 text-[#2956E0] dark:bg-[#2956E0]/30 dark:text-[#b8caff]" },
  COMENTARIO: { icone: "chat", cor: "bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-200" },
  DECISAO: { icone: "fact_check", cor: "bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200" },
};

function Interruptor({
  rotulo,
  dica,
  ligado,
  desabilitado,
  onMudar,
  extra,
}: {
  rotulo: string;
  dica?: string;
  ligado: boolean;
  desabilitado: boolean;
  onMudar: (valor: boolean) => void;
  extra?: ReactNode;
}) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-3" title={dica}>
      <span className="text-slate-600 dark:text-slate-300">{rotulo}</span>
      <span className="flex items-center gap-2">
        {extra}
        <input
          type="checkbox"
          role="switch"
          checked={ligado}
          disabled={desabilitado}
          onChange={event => onMudar(event.target.checked)}
          className="peer sr-only"
        />
        <span
          aria-hidden
          className="relative h-5 w-9 rounded-full bg-slate-300 transition-colors after:absolute after:left-0.5 after:top-0.5 after:h-4 after:w-4
            after:rounded-full after:bg-white after:shadow after:transition-transform peer-checked:bg-[#612035] peer-checked:after:translate-x-4
            peer-focus-visible:ring-2 peer-focus-visible:ring-[#612035] peer-focus-visible:ring-offset-1 dark:bg-slate-600"
        />
      </span>
    </label>
  );
}

/** Falso no servidor e na hidratação, verdadeiro depois: o contador só existe no navegador. */
function useMontado() {
  return useSyncExternalStore(
    () => () => undefined,
    () => true,
    () => false,
  );
}

function Item({ notificacao, onAbrir }: { notificacao: Notificacao; onAbrir: () => void }) {
  const visual = VISUAL[notificacao.tipo] ?? VISUAL.DECISAO;
  const nova = !notificacao.lidaEm;
  return (
    <li>
      <button
        type="button"
        onClick={onAbrir}
        className={`flex w-full cursor-pointer gap-3 px-4 py-3 text-left transition-colors hover:bg-slate-50 focus:outline-none
          focus-visible:bg-slate-50 dark:hover:bg-slate-800/70 dark:focus-visible:bg-slate-800/70 ${nova ? "bg-[#612035]/[0.035] dark:bg-[#612035]/15" : ""}`}
      >
        <span className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${visual.cor}`}>
          <Icon name={visual.icone} size={15} />
        </span>
        <span className="min-w-0 flex-1">
          <span className={`block text-[13px] leading-snug text-slate-800 dark:text-slate-100 ${nova ? "font-semibold" : ""}`}>
            {notificacao.titulo}
          </span>
          {notificacao.resumo && (
            <span className="mt-0.5 line-clamp-2 block text-xs leading-snug text-slate-500 dark:text-slate-400">{notificacao.resumo}</span>
          )}
          <span className="mt-1 block text-[11px] text-slate-400">{tempoRelativo(notificacao.criadaEm)}</span>
        </span>
        {nova && <span className="mt-2 h-2 w-2 shrink-0 rounded-full bg-[#612035] dark:bg-[#e8a3b6]" aria-label="não lida" />}
      </button>
    </li>
  );
}

/**
 * Sino da barra do topo.
 *
 * <p>Contador vermelho com as não lidas; o painel lista as 20 últimas, leva ao card ao clicar e
 * guarda as preferências de som e de e-mail.</p>
 */
export default function SinoNotificacoes() {
  const montado = useMontado();
  const router = useRouter();
  const conectado = useTempoReal();
  const { data } = useNotificacoes(conectado);
  const { data: eu } = useUsuarioAtual();
  const marcarLida = useMarcarLida();
  const marcarTodas = useMarcarTodasLidas();
  const definir = useDefinirPreferencia();
  const [aberto, setAberto] = useState(false);
  const painel = useRef<HTMLDivElement>(null);

  const naoLidas = montado ? (data?.naoLidas ?? 0) : 0;
  const som = eu?.somNotificacao ?? true;

  useEffect(() => {
    if (!aberto) return;
    function fora(event: MouseEvent) {
      if (painel.current && !painel.current.contains(event.target as Node)) setAberto(false);
    }
    function esc(event: KeyboardEvent) {
      if (event.key === "Escape") setAberto(false);
    }
    document.addEventListener("mousedown", fora);
    document.addEventListener("keydown", esc);
    return () => {
      document.removeEventListener("mousedown", fora);
      document.removeEventListener("keydown", esc);
    };
  }, [aberto]);

  function abrir(notificacao: Notificacao) {
    setAberto(false);
    if (!notificacao.lidaEm) marcarLida.mutate(notificacao.id);
    router.push(notificacao.link);
  }

  return (
    <div className="relative" ref={painel}>
      <button
        type="button"
        onClick={() => setAberto(atual => !atual)}
        aria-label={naoLidas > 0 ? `Notificações: ${naoLidas} não lida(s)` : "Notificações"}
        aria-expanded={aberto}
        aria-haspopup="dialog"
        className="relative flex h-11 w-11 items-center justify-center rounded-full text-gray-500 transition-colors hover:bg-gray-100
          focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] dark:text-gray-300 dark:hover:bg-gray-700"
      >
        {/* key pela contagem: cada notificação nova remonta o ícone e o sino balança de novo. */}
        <svg key={naoLidas} width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"
          strokeLinejoin="round" aria-hidden className={naoLidas > 0 ? "origin-top motion-safe:animate-[sino_1.2s_ease-in-out_1]" : ""}>
          <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {naoLidas > 0 && (
          <span className="absolute right-1.5 top-1.5 flex min-w-[18px] items-center justify-center rounded-full bg-red-600 px-1 text-[10px]
            font-bold leading-[18px] text-white ring-2 ring-white dark:ring-[#262833]">
            {naoLidas > 99 ? "99+" : naoLidas}
          </span>
        )}
      </button>

      {aberto && (
        <div
          role="dialog"
          aria-label="Notificações"
          className="esteira-modal-in fixed inset-x-3 top-[4.25rem] z-50 flex max-h-[75vh] flex-col overflow-hidden rounded-2xl border border-slate-200
            bg-white shadow-2xl sm:absolute sm:inset-x-auto sm:right-0 sm:top-full sm:mt-2 sm:w-[23rem] dark:border-slate-700 dark:bg-slate-900"
        >
          <header className="flex items-center justify-between gap-2 border-b border-slate-100 px-4 py-3 dark:border-slate-800">
            <div>
              <h2 className="font-display text-base font-semibold text-slate-900 dark:text-white">Notificações</h2>
              <p className="flex items-center gap-1.5 text-[11px] text-slate-400">
                <span className={`h-1.5 w-1.5 rounded-full ${conectado ? "bg-emerald-500" : "bg-amber-500"}`} />
                {conectado ? "ao vivo" : "reconectando…"}
              </p>
            </div>
            {(data?.naoLidas ?? 0) > 0 && (
              <button
                type="button"
                onClick={() => marcarTodas.mutate()}
                className="cursor-pointer rounded-lg px-2 py-1 text-xs font-medium text-[#612035] hover:bg-[#612035]/5 dark:text-[#e8a3b6]"
              >
                Marcar todas como lidas
              </button>
            )}
          </header>

          <ul className="min-h-0 flex-1 divide-y divide-slate-100 overflow-y-auto dark:divide-slate-800">
            {(data?.itens ?? []).map(notificacao => (
              <Item key={notificacao.id} notificacao={notificacao} onAbrir={() => abrir(notificacao)} />
            ))}
            {data && data.itens.length === 0 && (
              <li className="flex flex-col items-center gap-2 px-6 py-10 text-center text-sm text-slate-400">
                <Icon name="check_circle" size={22} className="opacity-60" />
                Nada por aqui. Quando alguém te marcar ou um card chegar até você, aparece aqui.
              </li>
            )}
          </ul>

          <footer className="space-y-2.5 border-t border-slate-100 bg-slate-50/80 px-4 py-3 text-xs dark:border-slate-800 dark:bg-slate-900">
            <Interruptor
              rotulo="Som de notificação"
              ligado={som}
              desabilitado={definir.isPending || !eu}
              onMudar={valor => definir.mutate({ somNotificacao: valor })}
              extra={
                som && (
                  <button type="button" onClick={tocarSino} className="cursor-pointer text-[11px] text-slate-400 hover:text-slate-700 hover:underline">
                    testar
                  </button>
                )
              }
            />
            <Interruptor
              rotulo="Receber por e-mail"
              dica="Card no Comitê, menção, pendência e decisão chegam também no seu e-mail, com link para o card."
              ligado={eu?.emailLiberacao ?? true}
              desabilitado={definir.isPending || !eu}
              onMudar={valor => definir.mutate({ emailLiberacao: valor })}
            />
          </footer>
        </div>
      )}
    </div>
  );
}
