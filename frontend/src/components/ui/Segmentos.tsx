"use client";

export type OpcaoSegmento<T extends string> = {
  valor: T;
  rotulo: string;
  /** Quantos itens a opção mostra, para a escolha não ser às cegas. */
  contagem?: number;
  /** Bolinha de cor antes do rótulo (situação, etapa). */
  cor?: string;
};

/** Grupo de botões exclusivos, com contagem por opção. */
export default function Segmentos<T extends string>({
  opcoes,
  valor,
  onChange,
  rotulo,
}: {
  opcoes: OpcaoSegmento<T>[];
  valor: T;
  onChange: (valor: T) => void;
  /** Lido pelo leitor de tela: o que o grupo filtra. */
  rotulo: string;
}) {
  return (
    <div role="group" aria-label={rotulo} className="inline-flex flex-wrap gap-0.5 rounded-[13px] bg-[#ECE9E1] p-[3px]">
      {opcoes.map(opcao => {
        const ativo = opcao.valor === valor;
        return (
          <button
            key={opcao.valor}
            type="button"
            aria-pressed={ativo}
            onClick={() => onChange(opcao.valor)}
            className={`inline-flex h-10 items-center gap-2 rounded-[10px] px-3.5 text-[13px] font-semibold transition-[background-color,color,box-shadow] duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
              ativo
                ? "bg-white text-grafite shadow-[0_1px_2px_rgba(38,40,51,0.08),0_6px_14px_-8px_rgba(38,40,51,0.3)]"
                : "text-[#5E5C55] hover:text-grafite"
            }`}
          >
            {opcao.cor && <span className="h-2 w-2 rounded-full" style={{ background: opcao.cor }} />}
            {opcao.rotulo}
            {opcao.contagem != null && (
              <span className="text-[11px] font-bold text-[#7F7C73]">{opcao.contagem.toLocaleString("pt-BR")}</span>
            )}
          </button>
        );
      })}
    </div>
  );
}
