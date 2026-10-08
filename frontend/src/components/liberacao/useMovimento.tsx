"use client";

import { useState, type ReactNode } from "react";
import Icon from "@/components/ui/Icon";
import { useMoverCard } from "@/hooks/useLiberacao";
import { ROTULO_ETAPA_CURTO, type EtapaLiberacao, type LiberacaoCard, type NovaPendencia } from "@/types/liberacao";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "./Dialogo";
import PendenciaDialog from "./PendenciaDialog";

type Confirmacao = {
  card: LiberacaoCard;
  para: EtapaLiberacao;
  titulo: string;
  texto: ReactNode;
  rotulo: string;
  tom: "primario" | "perigo" | "sucesso";
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
 * para quem; decidir, devolver e reabrir confirmam com espaço para observação. Mover da Origem
 * para o Comitê, ou da Pendência de volta, vai direto — é o caminho do dia a dia.</p>
 */
export function useMovimento(euId?: string) {
  const mover = useMoverCard();
  const [pendenciaPara, setPendenciaPara] = useState<LiberacaoCard | null>(null);
  const [confirmacao, setConfirmacao] = useState<Confirmacao | null>(null);
  const [observacao, setObservacao] = useState("");

  function executar(card: LiberacaoCard, para: EtapaLiberacao, extra?: { pendencias?: NovaPendencia[]; observacao?: string }) {
    mover.mutate(
      { id: card.id, de: card.etapa, para, ...extra },
      {
        onSettled: () => {
          setPendenciaPara(null);
          setConfirmacao(null);
          setObservacao("");
        },
      },
    );
  }

  function pedir(card: LiberacaoCard, para: EtapaLiberacao) {
    const destino = card.destinos.find(item => item.etapa === para);
    if (!destino?.permitido) return;

    if (para === "PENDENCIA") {
      setPendenciaPara(card);
      return;
    }
    if (para === "APROVADO") {
      setConfirmacao({
        card,
        para,
        titulo: `Aprovar #${card.numero}?`,
        tom: "sucesso",
        rotulo: "Aprovar",
        texto:
          card.pendenciasAbertas > 0 ? (
            <span className="flex items-start gap-2 rounded-lg bg-amber-50 p-3 text-amber-900 dark:bg-amber-900/30 dark:text-amber-100">
              <Icon name="warning" size={16} className="mt-0.5 shrink-0" />
              Há {card.pendenciasAbertas} pendência(s) sem resposta. Aprovar mesmo assim?
            </span>
          ) : (
            <>Operação de <strong>{card.cedenteNome}</strong> segue para liberação.</>
          ),
      });
      return;
    }
    if (para === "REPROVADO") {
      setConfirmacao({
        card,
        para,
        titulo: `Reprovar #${card.numero}?`,
        tom: "perigo",
        rotulo: "Reprovar",
        texto: <>A observação abaixo fica no histórico do card e explica a decisão para quem abriu.</>,
      });
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
    if (card.etapa === "APROVADO" || card.etapa === "REPROVADO") {
      setConfirmacao({
        card,
        para,
        titulo: `Reabrir #${card.numero}?`,
        tom: "primario",
        rotulo: "Reabrir no Comitê",
        texto: <>O card volta ao Comitê com uma rodada nova de pareceres. A decisão anterior continua no histórico.</>,
      });
      return;
    }
    executar(card, para);
  }

  const dialogo = (
    <>
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
                onClick={() => executar(confirmacao.card, confirmacao.para, { observacao: observacao.trim() || undefined })}
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
            <div>
              <label htmlFor="mov-obs" className={ROTULO}>
                Observação <span className="font-normal text-slate-400">(vai para o histórico)</span>
              </label>
              <textarea
                id="mov-obs"
                value={observacao}
                onChange={event => setObservacao(event.target.value)}
                rows={3}
                className={`${CAMPO} resize-y`}
              />
            </div>
          </div>
        </Dialogo>
      )}
    </>
  );

  return { pedir, dialogo, movendo: mover.isPending };
}
