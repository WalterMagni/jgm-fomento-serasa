"use client";

import { useState, type ReactNode } from "react";
import MentionTextarea from "@/components/ui/mencao/MentionTextarea";
import { useMoverCard } from "@/hooks/useLiberacao";
import {
  ROTULO_ETAPA_CURTO,
  type DecisaoSacado,
  type EtapaLiberacao,
  type LiberacaoCard,
  type NovaPendencia,
  type Resultado,
} from "@/types/liberacao";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, ROTULO } from "./Dialogo";
import FinalizarDialog from "./FinalizarDialog";
import PendenciaDialog from "./PendenciaDialog";

type Confirmacao = {
  card: LiberacaoCard;
  para: EtapaLiberacao;
  titulo: string;
  texto: ReactNode;
  rotulo: string;
  tom: "primario" | "perigo" | "sucesso";
  /** Confirmação que só libera o próximo passo (aviso de parecer faltando), sem mover ainda. */
  aoConfirmar?: () => void;
  /** Sem campo de observação: a observação vem no passo seguinte. */
  semObservacao?: boolean;
};

const CLASSE_TOM = {
  primario: BOTAO_PRIMARIO,
  perigo: BOTAO_PRIMARIO.replace("bg-[#612035]", "bg-rose-700").replace("hover:bg-[#4e1a2a]", "hover:bg-rose-800"),
  sucesso: BOTAO_PRIMARIO.replace("bg-[#612035]", "bg-emerald-700").replace("hover:bg-[#4e1a2a]", "hover:bg-emerald-800"),
};

/**
 * Pede o que cada movimento exige antes de chamar o servidor.
 *
 * <p>Mesma regra para arrastar e para o botão "Mover" do detalhe: ir para Pendência pergunta
 * para quem; finalizar pede a decisão de cada sacado; devolver e reabrir confirmam com espaço
 * para observação. Saindo do Comitê com parecer faltando, confirma antes. Mover da Origem para o
 * Comitê, ou da Pendência de volta, vai direto — é o caminho do dia a dia.</p>
 */
export function useMovimento(euId?: string) {
  const mover = useMoverCard();
  const [pendenciaPara, setPendenciaPara] = useState<LiberacaoCard | null>(null);
  const [confirmacao, setConfirmacao] = useState<Confirmacao | null>(null);
  const [observacao, setObservacao] = useState("");
  const [finalizar, setFinalizar] = useState<{ card: LiberacaoCard; aviso: string | null } | null>(null);

  function executar(
    card: LiberacaoCard,
    para: EtapaLiberacao,
    extra?: { pendencias?: NovaPendencia[]; observacao?: string; decisoes?: DecisaoSacado[]; resultado?: Resultado },
  ) {
    mover.mutate(
      { id: card.id, de: card.etapa, para, ...extra },
      {
        onSettled: () => {
          setPendenciaPara(null);
          setConfirmacao(null);
          setFinalizar(null);
          setObservacao("");
        },
      },
    );
  }

  function pedir(card: LiberacaoCard, para: EtapaLiberacao) {
    const destino = card.destinos.find(item => item.etapa === para);
    if (!destino?.permitido) return;

    // Finalizar é decidir os sacados; o diálogo mostra o aviso de parecer faltando, se houver.
    if (para === "FINALIZADO") {
      setFinalizar({ card, aviso: destino.aviso });
      return;
    }
    // Saindo do Comitê com parecer faltando: confirma antes de seguir para o passo normal.
    if (destino.aviso) {
      setConfirmacao({
        card,
        para,
        titulo: "Mover sem todos os pareceres?",
        tom: "primario",
        rotulo: "Mover mesmo assim",
        texto: <>{destino.aviso} O histórico vai registrar que o card saiu do Comitê sem esse parecer.</>,
        aoConfirmar: () => continuar(card, para),
        semObservacao: true,
      });
      return;
    }
    continuar(card, para);
  }

  function continuar(card: LiberacaoCard, para: EtapaLiberacao) {
    if (para === "PENDENCIA") {
      setConfirmacao(null);
      setPendenciaPara(card);
      return;
    }
    if (card.etapa === "COMITE" && para === "ORIGEM") {
      setConfirmacao({
        card,
        para,
        titulo: "Devolver para a Origem?",
        tom: "primario",
        rotulo: "Devolver",
        texto: <>Pareceres que ainda faltam deixam de ser esperados. Quando o card voltar ao Comitê, começa uma rodada nova.</>,
      });
      return;
    }
    if (card.etapa === "FINALIZADO") {
      setConfirmacao({
        card,
        para,
        titulo: `Reabrir #${card.numero}?`,
        tom: "primario",
        rotulo: "Reabrir no Comitê",
        texto: <>O card volta ao Comitê com uma rodada nova de pareceres. As decisões dos sacados continuam e podem ser revistas.</>,
      });
      return;
    }
    setConfirmacao(null);
    executar(card, para);
  }

  const dialogo = (
    <>
      {finalizar && (
        <FinalizarDialog
          card={finalizar.card}
          aviso={finalizar.aviso}
          enviando={mover.isPending}
          onFechar={() => setFinalizar(null)}
          onConfirmar={dados => executar(finalizar.card, "FINALIZADO", dados)}
        />
      )}
      {pendenciaPara && (
        <PendenciaDialog
          titulo={`Pendência em #${pendenciaPara.numero}`}
          subtitulo={pendenciaPara.cedenteNome}
          multiplas
          euId={euId}
          enviando={mover.isPending}
          onFechar={() => setPendenciaPara(null)}
          onConfirmar={pendencias => executar(pendenciaPara, "PENDENCIA", { pendencias })}
        />
      )}
      {confirmacao && (
        <Dialogo
          titulo={confirmacao.titulo}
          subtitulo={`${ROTULO_ETAPA_CURTO[confirmacao.card.etapa]} → ${ROTULO_ETAPA_CURTO[confirmacao.para]}`}
          onFechar={() => setConfirmacao(null)}
          largura="sm"
          rodape={
            <>
              <button type="button" onClick={() => setConfirmacao(null)} className={BOTAO_SECUNDARIO}>
                Cancelar
              </button>
              <button
                type="button"
                data-autofocus
                disabled={mover.isPending}
                onClick={() =>
                  confirmacao.aoConfirmar
                    ? confirmacao.aoConfirmar()
                    : executar(confirmacao.card, confirmacao.para, { observacao: observacao.trim() || undefined })
                }
                className={CLASSE_TOM[confirmacao.tom]}
              >
                {mover.isPending && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />}
                {confirmacao.rotulo}
              </button>
            </>
          }
        >
          <div className="space-y-4 px-5 py-5 text-sm text-slate-700 dark:text-slate-300">
            <div>{confirmacao.texto}</div>
            {!confirmacao.semObservacao && (
            <div>
              <label htmlFor="mov-obs" className={ROTULO}>
                Observação <span className="font-normal text-slate-400">(vai para o histórico)</span>
              </label>
              <MentionTextarea id="mov-obs" value={observacao} onChange={setObservacao} rows={3} />
            </div>
            )}
          </div>
        </Dialogo>
      )}
    </>
  );

  return { pedir, dialogo, movendo: mover.isPending };
}
