import { NOTA, NOTAS, type NotaEmpresa } from "@/types/nota-empresa";

const TAMANHO = {
  grande: {
    faixa: "h-[38px] w-[34px] text-[19px]",
    ponta: "rounded-l-[11px]",
    fim: "rounded-r-[11px]",
    ativo: "h-[54px] w-[48px] rounded-[13px] text-[30px] ring-[3px]",
  },
  compacto: {
    faixa: "h-[18px] w-[14px] text-[9px]",
    ponta: "rounded-l-[6px]",
    fim: "rounded-r-[6px]",
    ativo: "h-[26px] w-[23px] rounded-[7px] text-[14px] ring-2",
  },
} as const;

/**
 * Selo da nota no estilo Nutri-Score: as quatro letras em faixa, a da empresa maior, com borda
 * branca e sombra na própria cor, como um troféu. As outras ficam apagadas. Sem nota, a faixa
 * fica neutra.
 *
 * <p>A letra ativa "carimba" ao aparecer e um brilho passa por ela uma vez; trocar a nota repete o
 * efeito. Quem pediu menos movimento vê o selo parado.</p>
 */
export default function SeloNota({ nota, tamanho = "grande" }: { nota: NotaEmpresa | null; tamanho?: "grande" | "compacto" }) {
  const t = TAMANHO[tamanho];
  return (
    <span
      role="img"
      aria-label={nota ? `Score ${nota}: ${NOTA[nota].rotulo}` : "Sem score"}
      className="inline-flex items-center"
    >
      {NOTAS.map((letra, i) => {
        const ativa = letra === nota;
        const cor = NOTA[letra].cor;
        if (ativa) {
          return (
            <span
              key={`${letra}-ativa`}
              aria-hidden
              className={`nota-carimbo relative z-10 -mx-[3px] grid shrink-0 place-items-center overflow-hidden font-display font-semibold leading-none text-white ring-white ${t.ativo}`}
              style={{
                background: cor,
                boxShadow: `0 10px 22px -8px ${cor}B3, 0 2px 4px rgba(38,40,51,0.18)`,
                textShadow: "0 1px 2px rgba(0,0,0,0.3)",
              }}
            >
              {letra}
              <span
                className="nota-brilho pointer-events-none absolute inset-y-0 left-0 w-1/2 bg-gradient-to-r from-transparent via-white/55 to-transparent"
              />
            </span>
          );
        }
        return (
          <span
            key={letra}
            aria-hidden
            className={`grid shrink-0 place-items-center font-display font-semibold leading-none ${t.faixa} ${i === 0 ? t.ponta : ""} ${
              i === NOTAS.length - 1 ? t.fim : ""
            } ${nota ? "text-white/90 opacity-45" : "bg-[#ECE9E1] text-[#B5B1A6]"}`}
            style={nota ? { background: cor } : undefined}
          >
            {letra}
          </span>
        );
      })}
    </span>
  );
}
