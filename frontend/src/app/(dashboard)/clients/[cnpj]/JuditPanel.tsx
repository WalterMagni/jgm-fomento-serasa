"use client";

import { useState } from "react";
import Link from "next/link";
import Icon from "@/components/ui/Icon";
import { BOTAO } from "@/components/ui/botao";
import { useJudit, useVerificarOnDemand } from "@/hooks/useJudit";
import {
  Carimbo,
  ListaContagem,
  Numero,
  PontosDeAtencao,
  SelosJudit,
  TotaisDaLista,
  VERDE,
  data,
  hora,
  moeda,
  motivoBloqueio,
} from "@/components/judit/JuditPecas";
import { ConsultaProcessosModal } from "@/components/judit/ConsultaProcessosModal";
import type { JuditOnDemand, JuditPoloConsulta, JuditResumo, JuditSituacao } from "@/types/judit";

function nomeDoMes() {
  return new Date().toLocaleDateString("pt-BR", { month: "long" });
}

/**
 * Processos judiciais da empresa pela Judit, na Visão 360º. Só os totais: a lista inteira abre na
 * página de processos, para a Visão 360º não crescer com empresa de centenas de processos.
 *
 * <p>Como o Serasa e o CNPJ Já: um botão, sem ligar nada antes na empresa. Nada consulta sozinho —
 * abrir a página só lê o que já foi pago, e o botão que cobra abre um popup com o preço. Duas travas
 * no servidor: marca Judit do usuário e limite do mês.</p>
 *
 * <p>A consulta é o On Demand (base histórica + tribunais ao vivo), só como ré; como autora é um
 * pedido à parte. A pré-consulta, opcional, estima o custo antes. Ver docs/judit/03-on-demand.md.</p>
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
        <span className="text-[11px] text-gray-400">· On Demand: base histórica + tribunais, sob demanda — cada consulta tem custo</span>
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
  const [popup, setPopup] = useState<JuditPoloConsulta | null>(null);
  const verificar = useVerificarOnDemand(cnpj);
  const { precos, resumo, processos, onDemandReu, onDemandAutor } = situacao;

  const reuPendente = onDemandReu?.situacao === "PENDENTE";
  const autorPendente = onDemandAutor?.situacao === "PENDENTE";
  // O botão abre o popup, que não cobra; a trava geral (marca, chave, limite) já desliga aqui.
  const bloqueio = motivoBloqueio(situacao, Math.min(precos.resumo, precos.onDemandPorMil));
  const podeBuscarAutora =
    processos?.onDemand === true && !autorPendente && (processos.somenteReu || onDemandAutor?.desatualizado === true);

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

      {[onDemandReu, onDemandAutor].map(
        pedido =>
          pedido &&
          pedido.situacao !== "CONCLUIDA" && (
            <PedidoAberto
              key={pedido.polo}
              pedido={pedido}
              verificando={verificar.isPending}
              onVerificar={() => verificar.mutate({ polo: pedido.polo })}
              onTentarDeNovo={bloqueio === null ? () => setPopup(pedido.polo) : undefined}
            />
          ),
      )}

      {processos ? (
        // Com a lista, os números são dela: processos únicos, não registros por instância.
        <TotaisDaLista dados={processos.dados} totalNoResumo={processos.totalNoResumo} />
      ) : resumo ? (
        <>
          <ResumoNumeros resumo={resumo.dados} />
          {resumo.dados.total > 0 && <PontosDeAtencao pontos={resumo.pontosDeAtencao} />}
        </>
      ) : (
        !reuPendente && (
          <p className="text-sm text-gray-500">
            Nunca consultado. A consulta traz os processos em que a empresa é ré, da base histórica da Judit e dos tribunais
            ao vivo. Para empresa grande, faça antes a pré-consulta: ela mostra quantos processos há e quanto vai custar.
          </p>
        )
      )}

      {resumo && resumo.dados.total > 0 && (
        <details className="group rounded-lg border border-border-light dark:border-border-dark">
          <summary className="flex cursor-pointer list-none items-center gap-1.5 px-3 py-2 text-xs font-semibold text-gray-500 [&::-webkit-details-marker]:hidden">
            <Icon name="expand_more" size={14} className="transition-transform group-open:rotate-180" />
            Distribuição por assunto, classe, tribunal e área
            <span className="font-normal text-gray-400">· pré-consulta da Judit, conta cada instância</span>
          </summary>
          <div className="grid gap-4 border-t border-border-light p-3 sm:grid-cols-2 lg:grid-cols-4 dark:border-border-dark">
            <ListaContagem titulo="Assuntos" itens={resumo.dados.assuntos} />
            <ListaContagem titulo="Classes" itens={resumo.dados.classes} />
            <ListaContagem titulo="Tribunais" itens={resumo.dados.tribunais} />
            <ListaContagem titulo="Áreas" itens={resumo.dados.areas} />
          </div>
        </details>
      )}

      {/* Só a faixa do pedido pendente, sem nada consultado antes: o rodapé ficaria vazio. */}
      {(processos || resumo || !reuPendente) && (
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
          {!processos && !reuPendente && (
            <button
              type="button"
              className={BOTAO.judit}
              disabled={bloqueio !== null}
              title={bloqueio ?? undefined}
              onClick={() => setPopup("PASSIVO")}
            >
              <Icon name="gavel" size={16} /> Consultar processos
            </button>
          )}
          <div className="ml-auto flex flex-wrap items-center gap-x-4 gap-y-2">
            <div className="flex flex-col items-end gap-0.5">
              {processos && (
                <Carimbo
                  rotulo={processos.onDemand ? (processos.somenteReu ? "On Demand (ré)" : "On Demand (ré e autora)") : "Lista consultada"}
                  {...processos}
                  validadeDias={situacao.validadeDias}
                />
              )}
              {resumo && <Carimbo rotulo="Pré-consulta" {...resumo} validadeDias={situacao.validadeDias} />}
            </div>
            {podeBuscarAutora && (
              <button
                type="button"
                className={BOTAO.fantasma}
                disabled={bloqueio !== null}
                title={bloqueio ?? "Outro pedido On Demand, só com os processos em que a empresa é autora"}
                onClick={() => setPopup("ATIVO")}
              >
                <Icon name="add" size={16} />
                {processos?.somenteReu ? "Buscar também como autora" : "Atualizar como autora"}
              </button>
            )}
            {processos && !reuPendente && (
              <button
                type="button"
                className={BOTAO.fantasma}
                disabled={bloqueio !== null}
                title={bloqueio ?? undefined}
                onClick={() => setPopup("PASSIVO")}
              >
                <Icon name="refresh" size={16} /> Atualizar
              </button>
            )}
          </div>
        </div>
      )}

      {popup && (
        <ConsultaProcessosModal
          cnpj={cnpj}
          nomeEmpresa={nomeEmpresa}
          situacao={situacao}
          polo={popup}
          onFechar={() => setPopup(null)}
        />
      )}
    </>
  );
}

/** On Demand pendente (a Judit está nos tribunais) ou que falhou. A lista anterior, se houver, segue abaixo. */
function PedidoAberto({
  pedido,
  verificando,
  onVerificar,
  onTentarDeNovo,
}: {
  pedido: JuditOnDemand;
  verificando: boolean;
  onVerificar: () => void;
  /** Sem ele, a pessoa não pode consultar (sem marca, sem chave ou no limite). */
  onTentarDeNovo?: () => void;
}) {
  const como = pedido.polo === "PASSIVO" ? "como ré" : "como autora";
  const quando = `${data(pedido.pedidoEm)} às ${hora(pedido.pedidoEm)}${pedido.pedidoPor ? ` por ${pedido.pedidoPor}` : ""}`;
  if (pedido.situacao === "FALHOU") {
    return (
      <div className="flex flex-wrap items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100">
        <Icon name="warning" size={18} className="shrink-0" />
        <div className="min-w-0 flex-1">
          <p className="font-semibold">A Judit não conseguiu consultar os processos {como}.</p>
          <p className="text-xs">
            Pedido em {quando} · {moeda(pedido.custo)}. {pedido.erro}
          </p>
        </div>
        {onTentarDeNovo && (
          <button type="button" className={BOTAO.fantasma} onClick={onTentarDeNovo}>
            <Icon name="refresh" size={16} /> Tentar de novo
          </button>
        )}
      </div>
    );
  }
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-xl border border-[#15BE62]/50 bg-[#15BE62]/[0.06] px-4 py-3">
      <Icon name="progress_activity" size={20} className="shrink-0 animate-spin text-[#047857] dark:text-[#15BE62]" />
      <div className="min-w-0 flex-1 text-sm text-grafite dark:text-gray-200">
        <p className="font-semibold">A Judit está buscando os processos {como} nos tribunais…</p>
        <p className="mt-0.5 text-xs text-gray-500">
          Pedido em {quando} · estimativa {moeda(pedido.custoEstimado ?? pedido.custo)}
          {!pedido.comPreConsulta && " (sem pré-consulta)"}. Pode levar de minutos a horas — o sino avisa quando ficar pronto.
        </p>
      </div>
      <button type="button" className={BOTAO.fantasma} disabled={verificando} onClick={onVerificar}>
        <Icon name="refresh" size={16} /> Verificar agora (sem custo)
      </button>
    </div>
  );
}

/** Só a pré-consulta, sem a lista: os números são da Judit, que conta cada instância como um registro. */
function ResumoNumeros({ resumo }: { resumo: JuditResumo }) {
  if (resumo.total === 0) {
    return (
      <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">
        {resumo.naoEncontrado
          ? "A Judit não tem registro de processos para este CNPJ na base histórica."
          : "Nenhum processo na base histórica para este CNPJ."}{" "}
        O On Demand ainda pode achar processos recentes nos tribunais.
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
        Pré-consulta: a Judit conta um registro por instância, então o mesmo processo pode aparecer duas vezes. O On Demand
        junta as instâncias e mostra os processos únicos — o número dele costuma ser menor.
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
