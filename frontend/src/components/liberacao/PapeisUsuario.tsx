"use client";

import Icon from "@/components/ui/Icon";
import { useDefinirPapeis } from "@/hooks/useLiberacao";

type Papeis = { analista: boolean; comite: boolean; diretoria: boolean; judit: boolean };

type Props = Papeis & {
  usuarioId: string;
  onMudou: (papeis: Papeis) => void;
};

/**
 * Marcas de usuário na tabela do Settings: esteira de liberação e Judit.
 *
 * <p>Comitê, Diretoria e Judit exigem Analista, e a tela já faz a dependência: marcar qualquer um
 * liga Analista, desligar Analista desliga todos. Assim o admin nunca vê o erro que o banco daria.
 * Comitê e Diretoria são independentes: um diretor pode estar no Comitê.</p>
 */
export default function PapeisUsuario({ usuarioId, analista, comite, diretoria, judit, onMudou }: Props) {
  const definir = useDefinirPapeis();

  function gravar(novo: Papeis) {
    definir.mutate(
      { id: usuarioId, ...novo },
      {
        onSuccess: resposta =>
          onMudou({ analista: resposta.analista, comite: resposta.comite, diretoria: resposta.diretoria, judit: resposta.judit }),
      },
    );
  }

  const chip = (ativo: boolean) =>
    `inline-flex min-h-8 cursor-pointer items-center gap-1 rounded-full border px-2.5 text-[11px] font-semibold transition-colors
     focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] disabled:cursor-wait disabled:opacity-60 ${
       ativo
         ? "border-[#612035] bg-[#612035] text-white"
         : "border-gray-200 bg-white text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400"
     }`;

  return (
    <div className="flex flex-wrap gap-1.5">
      <button
        type="button"
        aria-pressed={analista}
        disabled={definir.isPending}
        onClick={() =>
          gravar(analista ? { analista: false, comite: false, diretoria: false, judit: false } : { analista: true, comite, diretoria, judit })
        }
        title="Edita e move cards da Esteira de Liberação a partir do Comitê"
        className={chip(analista)}
      >
        {analista && <Icon name="check" size={11} strokeWidth={3} />}
        Analista
      </button>
      <button
        type="button"
        aria-pressed={comite}
        disabled={definir.isPending}
        onClick={() => gravar({ analista: comite ? analista : true, comite: !comite, diretoria, judit })}
        title="Dá parecer no Comitê. Liga Analista junto."
        className={chip(comite)}
      >
        {comite && <Icon name="check" size={11} strokeWidth={3} />}
        Comitê
      </button>
      <button
        type="button"
        aria-pressed={diretoria}
        disabled={definir.isPending}
        onClick={() => gravar({ analista: diretoria ? analista : true, comite, diretoria: !diretoria, judit })}
        title="Decide os cards que o Comitê manda para a Diretoria. Liga Analista junto."
        className={chip(diretoria)}
      >
        {diretoria && <Icon name="check" size={11} strokeWidth={3} />}
        Diretoria
      </button>
      <button
        type="button"
        aria-pressed={judit}
        disabled={definir.isPending}
        onClick={() => gravar({ analista: judit ? analista : true, comite, diretoria, judit: !judit })}
        title="Liga a Judit nas empresas e consulta processos — cada consulta tem custo. Liga Analista junto."
        className={chip(judit)}
      >
        {judit && <Icon name="check" size={11} strokeWidth={3} />}
        Judit
      </button>
    </div>
  );
}
