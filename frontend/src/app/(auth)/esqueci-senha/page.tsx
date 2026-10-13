"use client";

import { Suspense } from "react";
import TelaEntrada from "@/components/login/TelaEntrada";
import FormularioEsqueci from "@/components/login/FormularioEsqueci";

export default function EsqueciSenhaPage() {
  return (
    <TelaEntrada>
      {/* Lê ?email= vindo do login; useSearchParams exige Suspense no App Router. */}
      <Suspense fallback={null}>
        <FormularioEsqueci />
      </Suspense>
    </TelaEntrada>
  );
}
