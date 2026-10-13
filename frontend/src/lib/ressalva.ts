/**
 * Aprovação com ressalva, igual nas duas esteiras: o card continua em Aprovado, com etiqueta e
 * faixa verde-oliva — o verde da aprovação puxado para a atenção, sem se confundir com o âmbar do
 * "Parcialmente aprovado". O texto da ressalva é de quem aprovou.
 */
export const RESSALVA = {
  rotulo: "Aprovado c/ ressalva",
  hex: "#7A8B26",
  icone: "info",
  chip: "bg-[#EEF1D9] text-[#4D5A12] dark:bg-[#3a4113]/60 dark:text-[#dfe6a8]",
  caixa: "border-[#C9D28F] bg-[#F5F7E6] text-[#4D5A12] dark:border-[#6b7a1f] dark:bg-[#3a4113]/40 dark:text-[#dfe6a8]",
} as const;
