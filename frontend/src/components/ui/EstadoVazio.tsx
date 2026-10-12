import type { ReactNode } from "react";
import Icon from "@/components/ui/Icon";

/** Quando não há o que mostrar: diz o porquê e oferece a saída, em vez de um "sem dados". */
export default function EstadoVazio({
  icone,
  titulo,
  descricao,
  acoes,
}: {
  icone: string;
  titulo: string;
  descricao?: ReactNode;
  acoes?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-2.5 px-5 py-16 text-center">
      <Icon name={icone} size={64} className="text-primary animate-flutuar motion-reduce:animate-none" />
      <p className="font-display text-[22px] font-semibold text-grafite">{titulo}</p>
      {descricao && <p className="max-w-md text-sm text-suave">{descricao}</p>}
      {acoes && <div className="mt-2 flex flex-wrap justify-center gap-2.5">{acoes}</div>}
    </div>
  );
}
