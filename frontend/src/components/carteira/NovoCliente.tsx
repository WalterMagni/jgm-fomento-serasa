"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import Icon from "@/components/ui/Icon";
import { BOTAO } from "@/components/ui/botao";
import { CUSTO_CONSULTA_PJ, NOME_PROVEDOR, consultarEmpresa, type Provedor } from "./consultaEmpresa";

function mascaraCnpj(valor: string) {
  const d = valor.replace(/\D/g, "").slice(0, 14);
  return d
    .replace(/^(\d{2})(\d)/, "$1.$2")
    .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d)/, ".$1/$2")
    .replace(/(\d{4})(\d)/, "$1-$2");
}

/**
 * Inclui uma empresa na carteira consultando o CNPJ no CNPJ Já ou no Serasa.
 * Ao terminar, avisa a página com o CNPJ para ela mostrar o resultado na lista.
 */
export default function NovoCliente({ onFechar, onIncluido }: { onFechar: () => void; onIncluido: (cnpj: string) => void }) {
  const queryClient = useQueryClient();
  const [cnpj, setCnpj] = useState("");
  const [consultando, setConsultando] = useState<Provedor | null>(null);
  const [erro, setErro] = useState("");
  const digitos = cnpj.replace(/\D/g, "");
  const valido = digitos.length === 14;

  const consultar = async (provedor: Provedor) => {
    setErro("");
    setConsultando(provedor);
    try {
      await consultarEmpresa(provedor, digitos);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["companyList"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboardMetrics"] }),
      ]);
      toast.success(`Dados do ${NOME_PROVEDOR[provedor]} adicionados.`);
      onIncluido(digitos);
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Não foi possível concluir a consulta.");
    } finally {
      setConsultando(null);
    }
  };

  return (
    <section
      aria-label="Novo cliente"
      className="relative flex flex-wrap items-end gap-x-5 gap-y-3.5 overflow-hidden rounded-cartao bg-grafite px-6 py-5 text-areia animate-cair motion-reduce:animate-none"
    >
      <span
        aria-hidden
        className="pointer-events-none absolute -right-16 -top-20 h-60 w-60 rounded-full bg-[radial-gradient(circle,rgba(209,115,44,0.35),transparent_65%)]"
      />
      <div className="relative flex flex-1 basis-[300px] flex-col gap-1">
        <label htmlFor="novo-cliente-cnpj" className="text-[15px] font-bold text-white">
          Incluir empresa na carteira
        </label>
        <span className="text-[13px] text-[#C9C8C1]">
          CNPJ Já traz cadastro e endereço. Serasa traz pendências e Visão Cedente ({CUSTO_CONSULTA_PJ}).
        </span>
        <input
          id="novo-cliente-cnpj"
          autoFocus
          inputMode="numeric"
          autoComplete="off"
          placeholder="00.000.000/0000-00"
          value={cnpj}
          onChange={e => setCnpj(mascaraCnpj(e.target.value))}
          className="mt-1.5 h-12 rounded-xl border border-white/20 bg-white/[0.06] px-4 font-mono text-[17px] tracking-[0.04em] text-white outline-none placeholder:text-white/35 focus:border-secondary focus:ring-4 focus:ring-secondary/25"
        />
        {erro && (
          <span role="alert" className="mt-1 flex items-start gap-1.5 text-[13px] font-medium text-[#F7B5C4]">
            <Icon name="error_outline" size={16} className="mt-px shrink-0" />
            {erro}
          </span>
        )}
      </div>
      <div className="relative flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={() => consultar("cnpja")}
          disabled={!valido || consultando !== null}
          className={BOTAO.cnpja}
        >
          <Icon name={consultando === "cnpja" ? "progress_activity" : "domain"} className={consultando === "cnpja" ? "animate-spin" : ""} />
          CNPJ Já
        </button>
        <button
          type="button"
          onClick={() => consultar("serasa")}
          disabled={!valido || consultando !== null}
          className={BOTAO.serasa}
        >
          <Icon name={consultando === "serasa" ? "progress_activity" : "verified_user"} className={consultando === "serasa" ? "animate-spin" : ""} />
          Serasa
        </button>
        <button
          type="button"
          onClick={onFechar}
          aria-label="Fechar"
          className="grid h-11 w-11 place-items-center rounded-xl text-[#C9C8C1] transition-colors hover:bg-white/10 hover:text-white"
        >
          <Icon name="close" />
        </button>
      </div>
    </section>
  );
}
