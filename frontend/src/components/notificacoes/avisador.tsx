"use client";

import { toast } from "sonner";
import type { Notificacao } from "@/types/notificacao";
import ToastJudit, { ehDaJudit } from "./ToastJudit";
import ToastResumo from "./ToastResumo";

/** O que chega dentro desta janela vira um aviso só. */
const JANELA_MS = 600;

/**
 * Um aviso de notificação por vez no canto da tela.
 *
 * <p>Rajada (vários processos da Judit prontos, o comitê inteiro dando parecer) espera a janela
 * fechar e aparece num toast só. Se já há aviso na tela quando chega mais, ele dá lugar a um resumo
 * com tudo: "5 novas notificações", as três mais novas e "Ver todas", que abre o sino.</p>
 */
export function criarAvisador({ navegar, abrirSino }: { navegar: (link: string) => void; abrirSino: () => void }) {
  let fila: Notificacao[] = [];
  let espera: ReturnType<typeof setTimeout> | undefined;
  let naTela: { id: string; itens: Notificacao[] } | null = null;
  let sequencia = 0;

  function mostrar() {
    espera = undefined;
    const novas = fila.reverse();
    fila = [];
    const itens = [...novas, ...(naTela?.itens ?? []).filter(item => !novas.some(nova => nova.id === item.id))];

    // Troca o toast em vez de atualizar: atualizado, o sonner mantém o prazo antigo e mistura o
    // layout padrão com o personalizado.
    if (naTela) toast.dismiss(naTela.id);
    const id = `notificacao-${++sequencia}`;
    naTela = { id, itens };
    const fechar = () => toast.dismiss(id);
    const saiu = () => {
      if (naTela?.id === id) naTela = null;
    };

    const [unica] = itens;
    if (itens.length === 1 && ehDaJudit(unica)) {
      toast.custom(
        () => (
          <ToastJudit
            notificacao={unica}
            onAbrir={() => {
              fechar();
              navegar(unica.link);
            }}
            onFechar={fechar}
          />
        ),
        { id, duration: 15000, onDismiss: saiu, onAutoClose: saiu },
      );
    } else if (itens.length === 1) {
      toast(unica.titulo, {
        id,
        description: unica.resumo ?? undefined,
        action: { label: "Abrir", onClick: () => navegar(unica.link) },
        duration: 8000,
        onDismiss: saiu,
        onAutoClose: saiu,
      });
    } else {
      toast.custom(
        () => (
          <ToastResumo
            itens={itens}
            onAbrir={notificacao => {
              fechar();
              navegar(notificacao.link);
            }}
            onVerTodas={() => {
              fechar();
              abrirSino();
            }}
            onFechar={fechar}
          />
        ),
        { id, duration: 12000, onDismiss: saiu, onAutoClose: saiu },
      );
    }
  }

  return {
    avisar(notificacao: Notificacao) {
      fila.push(notificacao);
      espera ??= setTimeout(mostrar, JANELA_MS);
    },
    parar() {
      clearTimeout(espera);
    },
  };
}
