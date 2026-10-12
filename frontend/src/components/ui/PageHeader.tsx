import type { ReactNode } from "react";

/** Cabeçalho de página da repaginação: rótulo da seção, título grande, resumo e ações. */
export default function PageHeader({
  rotulo,
  titulo,
  resumo,
  acoes,
}: {
  rotulo?: string;
  titulo: string;
  resumo?: ReactNode;
  acoes?: ReactNode;
}) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-5 animate-subir motion-reduce:animate-none">
      <div className="min-w-0">
        {rotulo && (
          <p className="text-xs font-bold uppercase tracking-[0.14em] text-laranja-texto">{rotulo}</p>
        )}
        <h1 className="mt-2 text-4xl font-semibold leading-none tracking-[-0.02em] text-grafite sm:text-[46px]">
          {titulo}
        </h1>
        {resumo && <p className="mt-3 text-[15px] text-suave">{resumo}</p>}
      </div>
      {acoes && <div className="flex flex-wrap gap-2.5">{acoes}</div>}
    </header>
  );
}
