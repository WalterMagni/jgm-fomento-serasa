"use client";

import { Suspense } from "react";
import TelaEntrada from "@/components/login/TelaEntrada";
import FormularioRedefinir from "@/components/login/FormularioRedefinir";

export default function RedefinirSenhaPage() {
  return (
    <TelaEntrada>
      {/* O código do link vem em ?token=; useSearchParams exige Suspense no App Router. */}
      <Suspense fallback={null}>
        <FormularioRedefinir />
      </Suspense>
    </TelaEntrada>
  );
}
