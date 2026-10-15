/** Nota que a equipe dá para a empresa, de A a D, no estilo do Nutri-Score. */
export type NotaEmpresa = "A" | "B" | "C" | "D";

export const NOTAS: NotaEmpresa[] = ["A", "B", "C", "D"];

/**
 * Cores do Nutri-Score: verde-escuro, verde, amarelo e vermelho. `texto` é a mesma cor escurecida
 * para ser lida sobre branco — o verde-claro e o amarelo puros não passam contraste como texto.
 */
export const NOTA: Record<NotaEmpresa, { rotulo: string; descricao: string; cor: string; texto: string }> = {
  A: { rotulo: "Excelente", descricao: "Cliente excelente", cor: "#038141", texto: "#03703A" },
  B: { rotulo: "Bom", descricao: "Bom cliente", cor: "#85BB2F", texto: "#4E7A12" },
  C: { rotulo: "Atenção", descricao: "Operar com atenção", cor: "#FECB02", texto: "#8A6A00" },
  D: { rotulo: "Diretoria", descricao: "Diretoria avaliar", cor: "#E63E11", texto: "#B8300B" },
};

/** Uma troca de nota; `nota` nula é uma retirada. */
export type RegistroNota = { nota: NotaEmpresa | null; por: string | null; em: string };

export type NotaAtual = {
  nota: NotaEmpresa | null;
  por: string | null;
  em: string | null;
  /** Últimas trocas, da mais recente para a mais antiga. */
  historico: RegistroNota[];
};
