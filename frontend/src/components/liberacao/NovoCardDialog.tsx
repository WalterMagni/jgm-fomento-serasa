"use client";

import { useCriarCard } from "@/hooks/useLiberacao";
import type { LiberacaoCard } from "@/types/liberacao";
import CardForm from "./CardForm";
import Dialogo from "./Dialogo";

type Props = {
  onFechar: () => void;
  /** Card criado: o diálogo fecha e o card aparece na Origem, sem abrir sozinho (pedido do time). */
  onCriado: (card: LiberacaoCard) => void;
};

export default function NovoCardDialog({ onFechar, onCriado }: Props) {
  const criar = useCriarCard();
  return (
    <Dialogo titulo="Nova análise" subtitulo="O card entra na Origem. Daqui ele segue para o Comitê." onFechar={onFechar} largura="lg">
      <CardForm
        enviando={criar.isPending}
        rotuloEnviar="Criar card"
        onCancelar={onFechar}
        onEnviar={dados => criar.mutate(dados, { onSuccess: onCriado })}
      />
    </Dialogo>
  );
}
