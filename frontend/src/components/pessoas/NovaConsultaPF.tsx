"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { BOTAO } from "@/components/ui/botao";
import { useEnrichPersonSerasa } from "@/hooks/usePersonProfile";
import { CUSTO_CONSULTA_PF } from "@/types/pessoa-resumo";

function mascaraCpf(valor: string) {
  const d = valor.replace(/\D/g, "").slice(0, 11);
  if (d.length <= 3) return d;
  if (d.length <= 6) return d.replace(/^(\d{3})(\d+)/, "$1.$2");
  if (d.length <= 9) return d.replace(/^(\d{3})(\d{3})(\d+)/, "$1.$2.$3");
  return d.replace(/^(\d{3})(\d{3})(\d{3})(\d{0,2})/, "$1.$2.$3-$4");
}

/** Consulta avulsa de CPF, sem passar pela página de uma empresa. Abre a análise ao terminar. */
export default function NovaConsultaPF({ onFechar }: { onFechar: () => void }) {
  const router = useRouter();
  const [cpf, setCpf] = useState("");
  const digitos = cpf.replace(/\D/g, "");
  const { mutate: consultar, isPending } = useEnrichPersonSerasa(digitos, () => router.push(`/individuals/${digitos}`));

  return (
    <section
      aria-label="Nova consulta de pessoa física"
      className="relative flex flex-wrap items-end gap-x-5 gap-y-3.5 overflow-hidden rounded-cartao bg-grafite px-6 py-5 text-areia animate-cair motion-reduce:animate-none"
    >
      <span
        aria-hidden
        className="pointer-events-none absolute -right-16 -top-20 h-60 w-60 rounded-full bg-[radial-gradient(circle,rgba(209,115,44,0.35),transparent_65%)]"
      />
      <form
        className="relative flex flex-1 basis-[300px] flex-col gap-1"
        onSubmit={e => {
          e.preventDefault();
          if (digitos.length === 11 && !isPending) consultar();
        }}
      >
        <label htmlFor="nova-consulta-cpf" className="text-[15px] font-bold text-white">
          Consultar um CPF no Serasa
        </label>
        <span className="text-[13px] text-[#C9C8C1]">Sem passar pela página de uma empresa. O resultado entra nesta lista.</span>
        <input
          id="nova-consulta-cpf"
          autoFocus
          inputMode="numeric"
          autoComplete="off"
          placeholder="000.000.000-00"
          value={cpf}
          onChange={e => setCpf(mascaraCpf(e.target.value))}
          className="mt-1.5 h-12 rounded-xl border border-white/20 bg-white/[0.06] px-4 font-mono text-[17px] tracking-[0.04em] text-white outline-none placeholder:text-white/35 focus:border-secondary focus:ring-4 focus:ring-secondary/25"
        />
      </form>
      <div className="relative flex flex-wrap items-center gap-3">
        <span className="rounded-full bg-secondary/[0.18] px-3 py-1.5 text-xs font-bold text-[#F3B98A]">
          Custo estimado {CUSTO_CONSULTA_PF}
        </span>
        <button
          type="button"
          onClick={() => consultar()}
          disabled={digitos.length !== 11 || isPending}
          className={BOTAO.laranja}
        >
          {isPending && <Icon name="progress_activity" className="animate-spin" />}
          {isPending ? "Consultando…" : "Consultar"}
        </button>
        <button
          type="button"
          onClick={onFechar}
          aria-label="Fechar consulta"
          className="grid h-11 w-11 place-items-center rounded-xl text-[#C9C8C1] transition-colors hover:bg-white/10 hover:text-white"
        >
          <Icon name="close" />
        </button>
      </div>
    </section>
  );
}
