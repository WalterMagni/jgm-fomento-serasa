"use client";

import { createContext, useContext, useEffect, useRef, useState, type Dispatch, type ReactNode, type SetStateAction } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { NOTIFICACOES_KEY, abrirUrlDoCanal, useNotificacoes } from "@/hooks/useNotificacoes";
import { useUsuarioAtual } from "@/hooks/useLiberacao";
import type { EntregaNotificacao, ListagemNotificacoes } from "@/types/notificacao";
import { criarAvisador } from "./avisador";
import { prepararSom, reivindicarAviso, tocarSino } from "./som";

const TempoRealContexto = createContext<{ conectado: boolean }>({ conectado: false });
const SinoContexto = createContext<[boolean, Dispatch<SetStateAction<boolean>>]>([false, () => undefined]);

/** Se o canal em tempo real está de pé. Telas que fazem polling param quando está. */
export function useTempoReal() {
  return useContext(TempoRealContexto).conectado;
}

/** Painel do sino aberto ou fechado. Fica no provider para o aviso de rajada conseguir abrir. */
export function usePainelDoSino() {
  return useContext(SinoContexto);
}

/**
 * Mantém o canal de notificações aberto enquanto o portal está aberto.
 *
 * <p>Recebe dois tipos de evento: {@code notificacao}, só para esta pessoa — atualiza o sino,
 * mostra o aviso (um por vez: rajada vira resumo) e toca o som — e
 * {@code quadro}, para todos — um card mudou, as telas da esteira se atualizam sozinhas.</p>
 *
 * <p>Se a conexão cai, tenta de novo com espera crescente (1 s, 2 s, 4 s… até 30 s), sempre com
 * ticket novo. Enquanto isso o sino volta a perguntar a cada 30 s.</p>
 */
export default function NotificacoesProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();
  const [conectado, setConectado] = useState(false);
  const painelDoSino = useState(false);
  const setSinoAberto = painelDoSino[1];
  const { data: eu } = useUsuarioAtual();
  const { data: listagem } = useNotificacoes(conectado);

  // O handler do canal vive mais que um render: lê preferência e roteador por ref.
  const somLigado = useRef(true);
  const navegar = useRef(router.push);
  useEffect(() => {
    somLigado.current = eu?.somNotificacao ?? true;
    navegar.current = router.push;
  }, [eu?.somNotificacao, router.push]);

  useEffect(() => prepararSom(), []);

  useEffect(() => {
    let ativo = true;
    let fonte: EventSource | null = null;
    let espera: ReturnType<typeof setTimeout> | undefined;
    let tentativas = 0;
    const avisador = criarAvisador({ navegar: link => navegar.current(link), abrirSino: () => setSinoAberto(true) });

    function reconectarDepois() {
      if (!ativo) return;
      const atraso = Math.min(30000, 1000 * 2 ** tentativas);
      tentativas += 1;
      espera = setTimeout(conectar, atraso);
    }

    async function aoNotificar(entrega: EntregaNotificacao) {
      const { notificacao, naoLidas } = entrega;
      queryClient.setQueryData<ListagemNotificacoes>(NOTIFICACOES_KEY, anterior => ({
        naoLidas,
        itens: [notificacao, ...(anterior?.itens ?? []).filter(item => item.id !== notificacao.id)].slice(0, 20),
      }));
      avisador.avisar(notificacao);
      if (somLigado.current && (await reivindicarAviso(notificacao.id))) tocarSino();
    }

    function aoMudarQuadro(cardId: string | undefined) {
      queryClient.invalidateQueries({ queryKey: ["liberacao"] });
      queryClient.invalidateQueries({ queryKey: ["liberacaoResumo"] });
      if (cardId) queryClient.invalidateQueries({ queryKey: ["liberacaoDetalhe", cardId] });
    }

    /** Mesma ideia para a esteira de prospecção: alguém mexeu num card, quem olha vê. */
    function aoMudarProspeccao(cardId: string | undefined) {
      queryClient.invalidateQueries({ queryKey: ["prospeccao"] });
      queryClient.invalidateQueries({ queryKey: ["prospeccaoResumo"] });
      if (cardId) queryClient.invalidateQueries({ queryKey: ["prospeccaoDetalhe", cardId] });
    }

    async function conectar() {
      if (!ativo) return;
      if (!localStorage.getItem("serasa_token")) {
        reconectarDepois();
        return;
      }
      try {
        const url = await abrirUrlDoCanal();
        if (!ativo) return;
        fonte = new EventSource(url);
        fonte.addEventListener("pronto", () => {
          tentativas = 0;
          setConectado(true);
          // O que chegou enquanto estava desconectado aparece agora.
          queryClient.invalidateQueries({ queryKey: NOTIFICACOES_KEY });
          aoMudarQuadro(undefined);
          aoMudarProspeccao(undefined);
        });
        fonte.addEventListener("notificacao", evento => {
          aoNotificar(JSON.parse((evento as MessageEvent<string>).data) as EntregaNotificacao);
        });
        fonte.addEventListener("quadro", evento => {
          const dados = JSON.parse((evento as MessageEvent<string>).data) as { cardId?: string };
          aoMudarQuadro(dados.cardId);
        });
        fonte.addEventListener("prospeccao", evento => {
          const dados = JSON.parse((evento as MessageEvent<string>).data) as { cardId?: string };
          aoMudarProspeccao(dados.cardId);
        });
        fonte.onerror = () => {
          // O EventSource tentaria sozinho com a mesma URL, mas o ticket já foi usado.
          fonte?.close();
          fonte = null;
          setConectado(false);
          reconectarDepois();
        };
      } catch {
        setConectado(false);
        reconectarDepois();
      }
    }

    conectar();
    return () => {
      ativo = false;
      clearTimeout(espera);
      avisador.parar();
      fonte?.close();
    };
  }, [queryClient, setSinoAberto]);

  // Contagem no título da aba: aparece mesmo com o portal em segundo plano. Reaplicada ao trocar
  // de página, porque o Next reescreve o título na navegação.
  const naoLidas = listagem?.naoLidas ?? 0;
  useEffect(() => {
    const base = document.title.replace(/^\(\d+\+?\)\s*/, "");
    document.title = naoLidas > 0 ? `(${naoLidas > 99 ? "99+" : naoLidas}) ${base}` : base;
  }, [naoLidas, pathname]);

  return (
    <TempoRealContexto.Provider value={{ conectado }}>
      <SinoContexto.Provider value={painelDoSino}>{children}</SinoContexto.Provider>
    </TempoRealContexto.Provider>
  );
}
