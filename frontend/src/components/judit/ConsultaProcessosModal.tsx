"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { BOTAO } from "@/components/ui/botao";
import Dialogo from "@/components/liberacao/Dialogo";
import { useConsultarOnDemand, useConsultarResumoJudit } from "@/hooks/useJudit";
import { Carimbo, Numero, PontosDeAtencao, faixaDoMes, moeda, motivoBloqueio } from "@/components/judit/JuditPecas";
import type { JuditEstimativa, JuditPoloConsulta, JuditSituacao } from "@/types/judit";

const COMO: Record<JuditPoloConsulta, string> = { PASSIVO: "como ré", ATIVO: "como autora" };

/**
 * Popup da consulta dos processos: On Demand (base histórica + tribunais ao vivo), cobrado a cada
 * 1.000 processos devolvidos. Ver docs/judit/03-on-demand.md.
 *
 * <p>Dois caminhos. "Consultar direto" estima um bloco. A pré-consulta (o resumo do Data Lake, preço
 * fixo) mostra antes quantos processos a empresa tem no polo, os pontos de atenção e o custo estimado —
 * recomendada para empresa grande, onde o On Demand pode custar vários blocos. Não é obrigatória; o
 * painel de consumo mostra quem pulou.</p>
 *
 * <p>Com pré-consulta dentro da validade, o popup já abre no resultado, sem cobrar de novo.</p>
 */
export function ConsultaProcessosModal({
  cnpj,
  nomeEmpresa,
  situacao,
  polo,
  onFechar,
}: {
  cnpj: string;
  nomeEmpresa?: string;
  situacao: JuditSituacao;
  polo: JuditPoloConsulta;
  onFechar: () => void;
}) {
  const consultarResumo = useConsultarResumoJudit(cnpj);
  const consultarOnDemand = useConsultarOnDemand(cnpj);
  const { resumo, precos, consumo } = situacao;
  const preConsultaValida = resumo !== null && !resumo.desatualizado;
  // Escolha de quem já viu o resultado: pré-consulta feita aqui ou ainda válida.
  const [verResultado, setVerResultado] = useState(preConsultaValida);
  const noResultado = verResultado && resumo !== null;

  const direto: JuditEstimativa = { processos: null, blocos: 1, custo: precos.onDemandPorMil, podeVirarBloco: false };
  const estimativa = noResultado ? (polo === "PASSIVO" ? resumo.estimativaReu : resumo.estimativaAutor) : direto;
  const bloqueioOnDemand = motivoBloqueio(situacao, estimativa.custo);
  const bloqueioResumo = motivoBloqueio(situacao, precos.resumo);
  const ocupado = consultarResumo.isPending || consultarOnDemand.isPending;

  function preConsultar() {
    consultarResumo.mutate({ nomeEmpresa: nomeEmpresa ?? null }, { onSuccess: () => setVerResultado(true) });
  }

  function pedirOnDemand() {
    consultarOnDemand.mutate({ polo, nomeEmpresa: nomeEmpresa ?? null }, { onSuccess: onFechar });
  }

  const botaoOnDemand = (
    <button
      type="button"
      className={BOTAO.judit}
      disabled={ocupado || bloqueioOnDemand !== null}
      title={bloqueioOnDemand ?? undefined}
      onClick={pedirOnDemand}
      data-autofocus={noResultado ? true : undefined}
    >
      <Icon
        name={consultarOnDemand.isPending ? "progress_activity" : "gavel"}
        size={16}
        className={consultarOnDemand.isPending ? "animate-spin" : ""}
      />
      {noResultado ? "Consultar On Demand" : "Consultar direto"} · {noResultado ? "" : "~"}
      {moeda(estimativa.custo)}
    </button>
  );

  return (
    <Dialogo
      largura="lg"
      onFechar={onFechar}
      titulo={polo === "PASSIVO" ? "Consultar processos na Judit" : "Buscar também como autora"}
      subtitulo={`${nomeEmpresa ? `${nomeEmpresa} · ` : ""}On Demand · todos os tribunais · só ${COMO[polo]}`}
      rodape={
        <>
          <button type="button" className={BOTAO.fantasma} onClick={onFechar}>
            Cancelar
          </button>
          {!noResultado && (
            <button
              type="button"
              className={BOTAO.fantasma}
              disabled={ocupado || bloqueioResumo !== null}
              title={bloqueioResumo ?? undefined}
              onClick={preConsultar}
              data-autofocus
            >
              <Icon
                name={consultarResumo.isPending ? "progress_activity" : "query_stats"}
                size={16}
                className={consultarResumo.isPending ? "animate-spin" : ""}
              />
              Pré-consulta · {moeda(precos.resumo)}
            </button>
          )}
          {botaoOnDemand}
        </>
      }
    >
      <div className="space-y-4 px-5 py-4 text-sm text-grafite dark:text-gray-200">
        {noResultado ? (
          <Resultado situacao={situacao} polo={polo} estimativa={estimativa} porMil={precos.onDemandPorMil} />
        ) : (
          <Escolha polo={polo} porMil={precos.onDemandPorMil} precoResumo={precos.resumo} />
        )}
        <Faixa consumo={consumo} custo={estimativa.custo} />
      </div>
    </Dialogo>
  );
}

function Escolha({ polo, porMil, precoResumo }: { polo: JuditPoloConsulta; porMil: number; precoResumo: number }) {
  return (
    <>
      <p>
        A Judit busca os processos em que a empresa está {COMO[polo]} na base histórica dela e nos tribunais, ao vivo.
        Custa <strong>{moeda(porMil)} a cada 1.000 processos</strong> devolvidos. Pode levar de minutos a horas: você
        recebe um aviso no sino quando ficar pronto.
      </p>
      <div className="flex gap-2.5 rounded-lg border border-[#D1732C]/40 bg-[#D1732C]/[0.08] px-3 py-3 text-[13px] text-[#7a3d10] dark:text-[#f0a46b]">
        <Icon name="warning" size={18} className="mt-0.5 shrink-0" />
        <div className="space-y-1">
          <p className="font-semibold">Empresa grande? Faça antes a pré-consulta ({moeda(precoResumo)}).</p>
          <p>
            Banco, varejo, telefonia e indústria grande podem ter milhares de processos — e cada 1.000 custa{" "}
            {moeda(porMil)}. A pré-consulta mostra quantos processos a empresa tem {COMO[polo]}, os pontos de atenção e o
            custo estimado, antes de pagar o On Demand. Consultando direto, a estimativa é de um bloco: se vier mais, o
            custo real é maior.
          </p>
        </div>
      </div>
    </>
  );
}

function Resultado({
  situacao,
  polo,
  estimativa,
  porMil,
}: {
  situacao: JuditSituacao;
  polo: JuditPoloConsulta;
  estimativa: JuditEstimativa;
  porMil: number;
}) {
  const resumo = situacao.resumo!;
  const dados = resumo.dados;
  const grande = estimativa.blocos > 1;
  return (
    <>
      <div className="grid grid-cols-3 gap-2">
        <Numero rotulo="Como ré" valor={dados.comoReu} destaque={polo === "PASSIVO"} />
        <Numero rotulo="Como autora" valor={dados.comoAutor} destaque={polo === "ATIVO"} />
        <Numero rotulo="Registros" valor={dados.total} dica="Um por instância: o mesmo processo em 1ª e 2ª instância conta dois." />
      </div>

      {dados.total === 0 ? (
        <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-[13px] text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">
          Nada na base histórica da Judit. O On Demand ainda pode achar processos recentes nos tribunais.
        </p>
      ) : (
        <div
          className={`rounded-lg border px-3 py-3 text-[13px] ${
            grande
              ? "border-[#D1732C]/50 bg-[#D1732C]/[0.08] text-[#7a3d10] dark:text-[#f0a46b]"
              : "border-[#15BE62]/40 bg-[#15BE62]/[0.06] text-[#065f46] dark:text-[#15BE62]"
          }`}
        >
          <p className="font-semibold">
            {grande && <Icon name="warning" size={15} className="mr-1 inline align-[-2px]" />}
            Custo estimado do On Demand: {moeda(estimativa.custo)}
            {estimativa.blocos > 1 && ` (${estimativa.blocos} blocos de 1.000)`}
          </p>
          <p className="mt-0.5">
            {estimativa.processos} processos {COMO[polo]} na base histórica.
            {estimativa.podeVirarBloco &&
              ` Está perto da virada do bloco: o tribunal pode trazer mais alguns e o custo chegar a ${moeda(estimativa.custo + porMil)}.`}
          </p>
        </div>
      )}

      {dados.total > 0 && <PontosDeAtencao pontos={resumo.pontosDeAtencao} />}

      <Carimbo rotulo="Pré-consulta" {...resumo} validadeDias={situacao.validadeDias} />
    </>
  );
}

function Faixa({ consumo, custo }: { consumo: JuditSituacao["consumo"]; custo: number }) {
  const faixa = faixaDoMes(consumo, custo);
  const cor =
    faixa.tom === "limite"
      ? "text-red-700 dark:text-red-300"
      : faixa.tom === "excedente"
        ? "text-[#a8551a] dark:text-[#f0a46b]"
        : "text-[#047857] dark:text-[#15BE62]";
  return (
    <p className={`flex gap-1.5 border-t border-slate-100 pt-3 text-xs dark:border-slate-800 ${cor}`}>
      <Icon name={faixa.tom === "franquia" ? "account_balance_wallet" : "warning"} size={14} className="mt-px shrink-0" />
      {faixa.texto}
    </p>
  );
}
