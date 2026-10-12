"use client";

/** Páginas numeradas com reticências: primeira, última e as vizinhas da atual. `pagina` começa em 0. */
export default function Paginacao({
  pagina,
  totalPaginas,
  onIr,
}: {
  pagina: number;
  totalPaginas: number;
  onIr: (pagina: number) => void;
}) {
  if (totalPaginas <= 1) return null;

  const itens: (number | "…")[] = [];
  for (let n = 0; n < totalPaginas; n++) {
    if (n !== 0 && n !== totalPaginas - 1 && Math.abs(n - pagina) > 1) continue;
    const anterior = itens[itens.length - 1];
    if (typeof anterior === "number" && n - anterior > 1) itens.push("…");
    itens.push(n);
  }

  const base =
    "min-w-10 h-10 rounded-[10px] px-2 text-[13px] font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary";

  return (
    <nav aria-label="Paginação" className="flex flex-wrap gap-1">
      <button
        type="button"
        onClick={() => onIr(pagina - 1)}
        disabled={pagina === 0}
        aria-label="Página anterior"
        className={`${base} text-[#55534D] hover:bg-areia disabled:cursor-default disabled:text-[#B5B1A6] disabled:hover:bg-transparent`}
      >
        ‹
      </button>
      {itens.map((item, i) =>
        item === "…" ? (
          <span key={`r${i}`} className={`${base} inline-grid place-items-center text-[#B5B1A6]`}>…</span>
        ) : (
          <button
            key={item}
            type="button"
            onClick={() => onIr(item)}
            aria-current={item === pagina ? "page" : undefined}
            className={`${base} ${item === pagina ? "bg-grafite text-white" : "text-[#55534D] hover:bg-areia"}`}
          >
            {item + 1}
          </button>
        ),
      )}
      <button
        type="button"
        onClick={() => onIr(pagina + 1)}
        disabled={pagina >= totalPaginas - 1}
        aria-label="Próxima página"
        className={`${base} text-[#55534D] hover:bg-areia disabled:cursor-default disabled:text-[#B5B1A6] disabled:hover:bg-transparent`}
      >
        ›
      </button>
    </nav>
  );
}
