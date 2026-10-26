"use client";

import BlocoResumo from "@/components/ui/BlocoResumo";
import { diasDesde, formatData, formatMoeda, tempoRelativo } from "@/lib/formatadores";
import { DIAS_CONSULTA_VELHA } from "@/types/pessoa-resumo";
import LimiteSacadoBloco from "./LimiteSacadoBloco";

/**
 * O que decide crédito, logo abaixo do nome: pendências, Visão Cedente, quem anda consultando o
 * CNPJ, a idade da consulta Serasa e o limite de sacado. Cada bloco leva à seção com o detalhe;
 * o limite se edita ali mesmo.
 */
export default function ResumoCliente({
  cnpj,
  pendencias,
  visaoCedente,
  consultas30Dias,
  consultaEm,
  consultaEmTela,
  onIr,
}: {
  cnpj: string;
  /** null: ainda não há consulta Serasa. */
  pendencias: { ocorrencias: number; divida: number } | null;
  visaoCedente?: string | null;
  consultas30Dias?: number | null;
  consultaEm?: string | null;
  /** "Consulta 2 · 01/03/2026" quando uma consulta antiga está carregada na tela. */
  consultaEmTela?: string | null;
  onIr: (secao: string) => void;
}) {
  const dias = consultaEm ? diasDesde(consultaEm) : null;
  const velha = dias != null && dias > DIAS_CONSULTA_VELHA;

  return (
    <section aria-label="Resumo do cliente" className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 print:hidden">
      <BlocoResumo
        icone="warning"
        cor="#D1732C"
        rotulo="Pendências Serasa"
        atraso={60}
        onClick={() => onIr("serasa")}
        corValor={!pendencias ? "#8A877E" : pendencias.ocorrencias === 0 ? "#1F7A5C" : "#B42345"}
        valor={!pendencias ? "Sem consulta" : pendencias.ocorrencias === 0 ? "Nada consta" : formatMoeda(pendencias.divida)}
        apoio={
          !pendencias
            ? "Consulte o Serasa para ver restrições"
            : pendencias.ocorrencias === 0
              ? "Nenhuma ocorrência no relatório"
              : pendencias.ocorrencias === 1
                ? "1 ocorrência"
                : `${pendencias.ocorrencias} ocorrências`
        }
      />
      <BlocoResumo
        icone="swap_horiz"
        cor="#1F7A5C"
        rotulo="Visão Cedente"
        atraso={120}
        onClick={() => onIr("serasa")}
        corValor={visaoCedente === "SIM" ? "#1F7A5C" : visaoCedente === "NAO" ? "#8E1B37" : "#8A877E"}
        valor={visaoCedente === "SIM" ? "Cedente" : visaoCedente === "NAO" ? "Não" : "Pendente"}
        apoio={
          visaoCedente === "SIM"
            ? "Opera como cedente em factoring"
            : visaoCedente === "NAO"
              ? "Sem operação como cedente"
              : "Sem resposta do Serasa ainda"
        }
      />
      <BlocoResumo
        icone="manage_search"
        cor="#612035"
        rotulo="Consultas ao CNPJ"
        atraso={180}
        onClick={() => onIr("serasa")}
        valor={consultas30Dias != null ? consultas30Dias.toLocaleString("pt-BR") : "—"}
        apoio={consultas30Dias != null ? "empresas consultaram nos últimos 30 dias" : "Disponível após consulta Serasa"}
      />
      <BlocoResumo
        icone="history"
        cor={velha ? "#D1732C" : "#612035"}
        rotulo="Consulta Serasa"
        atraso={240}
        onClick={() => onIr("serasa")}
        corValor={!consultaEm ? "#8A877E" : velha ? "#944C17" : undefined}
        valor={consultaEm ? formatData(consultaEm) : "Nunca"}
        apoio={
          consultaEmTela
            ? `Em tela: ${consultaEmTela}`
            : dias != null
              ? velha
                ? `${tempoRelativo(dias)} · vale reconsultar`
                : tempoRelativo(dias)
              : "Nenhuma consulta feita"
        }
      />
      <LimiteSacadoBloco cnpj={cnpj} atraso={300} />
    </section>
  );
}
