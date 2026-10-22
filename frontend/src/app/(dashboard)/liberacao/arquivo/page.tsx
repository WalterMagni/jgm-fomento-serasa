"use client";

import dynamic from "next/dynamic";

/** Como o quadro, só existe no navegador: depende do token no localStorage. */
const ArquivoLiberacao = dynamic(() => import("@/components/liberacao/ArquivoLiberacao"), {
  ssr: false,
  loading: () => <div className="h-96 animate-pulse rounded-2xl bg-slate-200/60 dark:bg-slate-800/60" />,
});

export default function ArquivoLiberacaoPage() {
  return <ArquivoLiberacao />;
}
