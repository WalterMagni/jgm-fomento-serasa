"use client";

import dynamic from "next/dynamic";
import { Suspense } from "react";

/**
 * O quadro só existe no navegador: depende do token guardado no localStorage e mostra prazos
 * relativos ao relógio de quem olha. Renderizar no servidor não teria dado nenhum e ainda
 * criaria divergência de hidratação quando o menu lateral, que hidrata antes, já trouxe o resumo.
 */
const QuadroLiberacao = dynamic(() => import("@/components/liberacao/QuadroLiberacao"), {
  ssr: false,
  loading: () => (
    <div className="flex gap-3 overflow-hidden pt-20">
      {Array.from({ length: 5 }, (_, i) => (
        <div key={i} className="h-96 w-[17.5rem] shrink-0 animate-pulse rounded-2xl bg-slate-200/60 dark:bg-slate-800/60" />
      ))}
    </div>
  ),
});

export default function LiberacaoPage() {
  // useSearchParams (o card aberto vive na URL) pede Suspense no App Router.
  return (
    <Suspense fallback={null}>
      <QuadroLiberacao />
    </Suspense>
  );
}
