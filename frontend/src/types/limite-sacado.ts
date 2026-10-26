/** Uma troca do limite; `valor` nulo é uma retirada. */
export type RegistroLimite = { valor: number | null; por: string | null; em: string };

/** Limite da empresa como sacado e as últimas trocas, da mais recente para a mais antiga. */
export type LimiteSacado = {
  valor: number | null;
  por: string | null;
  em: string | null;
  historico: RegistroLimite[];
};
