"use client";

import type { ReactNode } from "react";
import Icon from "@/components/ui/Icon";
import { diasDesde, formatData, formatMoeda, tempoRelativo } from "@/lib/formatadores";
import { DIAS_CONSULTA_VELHA } from "@/types/pessoa-resumo";

function Bloco({
  icone,
  cor,
  rotulo,
  valor,
  apoio,
  corValor,
  atraso,
  onClick,
}: {
  icone: string;
  cor: string;
  rotulo: string;
  valor: ReactNode;
  apoio: ReactNode;
  corValor?: string;
  atraso: number;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="group flex flex-col gap-1.5 rounded-cartao border border-linha bg-white px-5 py-4 text-left transition-[transform,box-shadow] duration-300 ease-saida hover:-translate-y-0.5 hover:shadow-elevado focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary animate-subir motion-reduce:animate-none motion-reduce:hover:translate-y-0"
      style={{ animationDelay: `${atraso}ms` }}
    >
      <span className="flex items-center gap-2 text-[12.5px] font-semibold text-suave">
        <Icon name={icone} size={18} style={{ color: cor }} />
        {rotulo}
        <Icon name="chevron_right" size={14} className="ml-auto text-[#B5B1A6] transition-transform group-hover:translate-x-0.5" />
      </span>
      <span className="font-display text-[26px] font-semibold leading-tight tracking-[-0.015em]" style={{ color: corValor ?? "#262833" }}>
        {valor}
      </span>
      <span className="text-[12.5px] text-suave">{apoio}</span>
    </button>
  );
}

/**
 * O que decide crédito, logo abaixo do nome: pendências, Visão Cedente, quem anda consultando o
 * CNPJ e a idade da consulta Serasa. Cada bloco leva à seção com o detalhe.
 */
export default function ResumoCliente({
  pendencias,
  visaoCedente,
  consultas30Dias,
  consultaEm,
  consultaEmTela,
  onIr,
}: {
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
    <section aria-label="Resumo do cliente" className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4 print:hidden">
      <Bloco
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
      <Bloco
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
      <Bloco
        icone="manage_search"
        cor="#612035"
        rotulo="Consultas ao CNPJ"
        atraso={180}
        onClick={() => onIr("serasa")}
        valor={consultas30Dias != null ? consultas30Dias.toLocaleString("pt-BR") : "—"}
        apoio={consultas30Dias != null ? "empresas consultaram nos últimos 30 dias" : "Disponível após consulta Serasa"}
      />
      <Bloco
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
    </section>
  );
}
