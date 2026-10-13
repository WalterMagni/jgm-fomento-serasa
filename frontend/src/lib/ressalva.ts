/**
 * Aprovação com ressalva, igual nas duas esteiras: o card continua em Aprovado, com etiqueta e
 * faixa amarelas — amarelo puro (yellow), mais claro que o âmbar do "Parcialmente aprovado" para
 * os dois não se confundirem. O texto da ressalva é de quem aprovou.
 */
export const RESSALVA = {
  rotulo: "Aprovado c/ ressalva",
  hex: "#EAB308",
  icone: "info",
  chip: "bg-yellow-100 text-yellow-800 dark:bg-yellow-400/20 dark:text-yellow-100",
  caixa: "border-yellow-300 bg-yellow-50 text-yellow-900 dark:border-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-100",
} as const;
