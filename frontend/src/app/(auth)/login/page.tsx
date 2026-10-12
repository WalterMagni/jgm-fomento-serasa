"use client";

import { Suspense } from "react";
import TelaEntrada from "@/components/login/TelaEntrada";
import FormularioLogin from "@/components/login/FormularioLogin";

export default function LoginPage() {
  return (
    <TelaEntrada>
      {/* O formulário lê ?expired= da URL; useSearchParams exige Suspense no App Router. */}
      <Suspense fallback={null}>
        <FormularioLogin />
      </Suspense>
    </TelaEntrada>
  );
}
