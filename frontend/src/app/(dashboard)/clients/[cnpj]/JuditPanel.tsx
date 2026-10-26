"use client";

import Link from "next/link";
import Icon from "@/components/ui/Icon";
import { BOTAO } from "@/components/ui/botao";
import { useConsultarProcessosJudit, useConsultarResumoJudit, useJudit } from "@/hooks/useJudit";
import {
  BotaoCobrado,
  Carimbo,
  ListaContagem,
  Numero,
  PontosDeAtencao,
  SelosJudit,
  TotaisDaLista,
  VERDE,
  moeda,
  motivoBloqueio,
} from "@/components/judit/JuditPecas";
import type { JuditResumo, JuditSituacao } from "@/types/judit";

function nomeDoMes() {
  return new Date().toLocaleDateString("pt-BR", { month: "long" });
}

/**
 * Processos judiciais da empresa pela Judit, na Visão 360º. Só os totais: a lista inteira abre na
 * página de processos, para a Visão 360º não crescer com empresa de centenas de processos.
 *
 * <p>Como o Serasa e o CNPJ Já: um botão, sem ligar nada antes na empresa. Nada consulta sozinho —
 * abrir a página só lê o que já foi pago, e cada botão que cobra mostra o preço e pede confirmação.
 * Duas travas no servidor: marca Judit do usuário e limite do mês.</p>
 *
 * <p>As duas consultas são da base histórica (Data Lake). O On Demand, em tempo real nos tribunais,
 * foi testado e ficou de fora (docs/judit/03-on-demand.md).</p>
 */
export function JuditPanel({ cnpj, nomeEmpresa }: { cnpj: string; nomeEmpresa?: string }) {
  const { data: situacao, isLoading, isError, error } = useJudit(cnpj);

  return (
    <div className="mb-6 overflow-hidden rounded-xl border border-[#15BE62]/50 bg-surface-light shadow-[0_10px_28px_-18px_rgba(21,190,98,0.55)] dark:border-[#15BE62]/40 dark:bg-surface-dark print:hidden">
      <div className="h-1" style={{ backgroundColor: VERDE }} aria-hidden />
      <div className="flex flex-wrap items-center gap-2 border-b border-[#C6F1D6] p-5 dark:border-[#15BE62]/25">
        <Icon name="gavel" className="text-[#047857] dark:text-[#15BE62]" />
        <h2 className="font-sans text-base font-bold text-[#047857] dark:text-[#15BE62]">Processos judiciais</h2>
        <SelosJudit />
        <span className="text-[11px] text-gray-400">· base histórica (Data Lake), sob demanda — cada consulta tem custo</span>
        {situacao && <ConsumoDoMes consumo={situacao.consumo} />}
      </div>

      <div className="space-y-4 p-5">
        {isLoading ? (
          <p className="text-sm text-gray-500">Carregando…</p>
        ) : isError || !situacao ? (
          <p className="text-sm text-gray-500">{(error as Error)?.message ?? "Não foi possível carregar os processos."}</p>
        ) : (
          <Conteudo cnpj={cnpj} nomeEmpresa={nomeEmpresa} situacao={situacao} />
        )}
      </div>
    </div>
  );
}

function Conteudo({ cnpj, nomeEmpresa, situacao }: { cnpj: string; nomeEmpresa?: string; situacao: JuditSituacao }) {
  const consultarResumo = useConsultarResumoJudit(cnpj);
  const consultarProcessos = useConsultarProcessosJudit(cnpj);
  const { precos, resumo, processos } = situacao;

  const bloqueio = (preco: number) => motivoBloqueio(situacao, preco);

  const botaoResumo = (
    <BotaoCobrado
      rotulo={resumo ? "Atualizar resumo" : "Consultar resumo"}
      preco={precos.resumo}
      bloqueio={bloqueio(precos.resumo)}
      pendente={consultarResumo.isPending}
      onConfirmar={() => consultarResumo.mutate({ nomeEmpresa: nomeEmpresa ?? null })}
      discreto={Boolean(resumo)}
    />
  );
  const temProcessos = resumo !== null && resumo.dados.total > 0;
  const botaoLista = temProcessos && (
    <BotaoCobrado
      rotulo={processos ? "Atualizar lista" : "Buscar lista"}
      preco={precos.processosPorMil}
      bloqueio={bloqueio(precos.processosPorMil)}
      pendente={consultarProcessos.isPending}
      onConfirmar={() => consultarProcessos.mutate({ nomeEmpresa: nomeEmpresa ?? null })}
      discreto={Boolean(processos)}
    />
  );

  return (
    <>
      {!situacao.configurada && (
        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
          A chave da Judit não está configurada no servidor (JUDIT_API_KEY). Dá para ver o que já foi consultado, mas não
          consultar de novo.
        </p>
      )}
      {!situacao.podeUsar && (
        <p className="text-xs text-gray-500">
          Você vê o que já foi consultado. Para consultar, peça ao administrador a marca Judit em Configurações › Usuários.
        </p>
      )}

      {processos ? (
        // Com a lista, os números são dela: processos únicos, não registros por instância.
        <TotaisDaLista dados={processos.dados} totalNoResumo={processos.totalNoResumo} />
      ) : resumo ? (
        <ResumoNumeros resumo={resumo.dados} />
      ) : (
        <p className="text-sm text-gray-500">
          Nunca consultado. O resumo traz quantos processos a empresa tem, como ré e como autora, a distribuição por
          assunto, classe e tribunal e os pontos de atenção. Depois, a lista traz cada processo.
        </p>
      )}

      {temProcessos && <PontosDeAtencao pontos={resumo.pontosDeAtencao} />}

      {temProcessos && (
        <details className="group rounded-lg border border-border-light dark:border-border-dark">
          <summary className="flex cursor-pointer list-none items-center gap-1.5 px-3 py-2 text-xs font-semibold text-gray-500 [&::-webkit-details-marker]:hidden">
            <Icon name="expand_more" size={14} className="transition-transform group-open:rotate-180" />
            Distribuição por assunto, classe, tribunal e área
            <span className="font-normal text-gray-400">· resumo da Judit, conta cada instância</span>
          </summary>
          <div className="grid gap-4 border-t border-border-light p-3 sm:grid-cols-2 lg:grid-cols-4 dark:border-border-dark">
            <ListaContagem titulo="Assuntos" itens={resumo.dados.assuntos} />
            <ListaContagem titulo="Classes" itens={resumo.dados.classes} />
            <ListaContagem titulo="Tribunais" itens={resumo.dados.tribunais} />
            <ListaContagem titulo="Áreas" itens={resumo.dados.areas} />
          </div>
        </details>
      )}

      <div className="flex flex-wrap items-center gap-2 border-t border-[#C6F1D6] pt-4 dark:border-[#15BE62]/25">
        {processos && (
          <Link
            href={`/clients/${cnpj}/processos${nomeEmpresa ? `?nome=${encodeURIComponent(nomeEmpresa)}` : ""}`}
            className={BOTAO.judit}
          >
            <Icon name="format_list_bulleted" size={16} />
            Ver os {processos.dados.quantidade} processos
          </Link>
        )}
        {!processos && botaoLista}
        {!resumo && botaoResumo}
        <div className="ml-auto flex flex-wrap items-center gap-x-4 gap-y-2">
          <div className="flex flex-col items-end gap-0.5">
            {processos && <Carimbo rotulo="Lista consultada" {...processos} validadeDias={situacao.validadeDias} />}
            {resumo && <Carimbo rotulo="Resumo consultado" {...resumo} validadeDias={situacao.validadeDias} />}
          </div>
          {processos && botaoLista}
          {resumo && botaoResumo}
        </div>
      </div>
    </>
  );
}

/** Só o resumo, sem a lista: os números são da Judit, que conta cada instância como um registro. */
function ResumoNumeros({ resumo }: { resumo: JuditResumo }) {
  if (resumo.total === 0) {
    return (
      <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">
        {resumo.naoEncontrado
          ? "A Judit não tem registro de processos para este CNPJ."
          : "Nenhum processo encontrado para este CNPJ."}
      </p>
    );
  }
  return (
    <div className="space-y-2">
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Numero rotulo="Registros na Judit" valor={resumo.total} dica="Um por instância: o mesmo processo em 1ª e 2ª instância conta dois." />
        <Numero rotulo="Como ré" valor={resumo.comoReu} destaque />
        <Numero rotulo="Como autora" valor={resumo.comoAutor} />
        <Numero rotulo="Em segredo" valor={resumo.emSegredo} />
      </div>
      <p className="text-[11px] text-gray-500">
        A Judit conta um registro por instância, então o mesmo processo pode aparecer duas vezes. A lista junta as
        instâncias e mostra os processos únicos — o número dela costuma ser menor.
      </p>
    </div>
  );
}

/** Verde dentro da franquia (já paga), laranja no excedente, vermelho no limite do mês. */
function ConsumoDoMes({ consumo }: { consumo: JuditSituacao["consumo"] }) {
  const { gastoMes, franquia, limite } = consumo;
  const excedente = gastoMes > franquia;
  const cor =
    gastoMes >= limite
      ? "border-red-200 bg-red-50 text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300"
      : excedente
        ? "border-[#D1732C]/40 bg-[#D1732C]/10 text-[#a8551a] dark:text-[#f0a46b]"
        : "border-[#15BE62]/40 bg-[#15BE62]/10 text-[#047857] dark:text-[#15BE62]";
  return (
    <span
      className={`ml-auto inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-[11px] font-semibold ${cor}`}
      title={`Franquia de ${moeda(franquia)} já paga; acima dela é excedente. Em ${moeda(limite)} o portal para de consultar até o admin liberar. Valores pela tabela do contrato — a fatura da Judit é a palavra final.`}
    >
      {excedente && <Icon name="warning" size={12} />}
      Judit em {nomeDoMes()}: {moeda(gastoMes)} {excedente ? `· excedente ${moeda(gastoMes - franquia)}` : `de ${moeda(franquia)}`}
    </span>
  );
}
