"use client";

import dynamic from "next/dynamic";
import { Suspense } from "react";

/**
 * O quadro só existe no navegador: depende do token guardado no localStorage e mostra prazos
 * relativos ao relógio de quem olha. Mesmo arranjo da esteira de liberação.
 */
const QuadroProspeccao = dynamic(() => import("@/components/prospeccao/QuadroProspeccao"), {
  ssr: false,
  loading: () => (
    <div className="flex gap-2.5 overflow-hidden pt-24">
      {Array.from({ length: 6 }, (_, i) => (
        <div key={i} className="h-96 min-w-[12.5rem] flex-1 animate-pulse rounded-2xl bg-slate-200/60 dark:bg-slate-800/60" />
      ))}
    </div>
  ),
});

export default function ProspeccaoPage() {
  // useSearchParams (o card aberto vive na URL) pede Suspense no App Router.
  return (
    <Suspense fallback={null}>
      <QuadroProspeccao />
    </Suspense>
  );
}
