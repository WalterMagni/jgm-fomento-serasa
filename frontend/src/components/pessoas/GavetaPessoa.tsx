"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import Gaveta from "@/components/ui/Gaveta";
import Icon from "@/components/ui/Icon";
import { BOTAO } from "@/components/ui/botao";
import { useEnrichPersonSerasa } from "@/hooks/usePersonProfile";
import { formatCpf } from "@/types/person-analysis";
import { CUSTO_CONSULTA_PF, DIAS_CONSULTA_VELHA, SITUACAO, TIPO_RESTRICAO } from "@/types/pessoa-resumo";
import type { PessoaNaLista } from "./LinhaPessoa";
import { caixaTitulo, formatCnpj, formatData, formatMoeda, iniciais, tempoRelativo, tomDoAvatar } from "@/lib/formatadores";

function Secao({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <section>
      <h3 className="mb-2.5 font-sans text-[11px] font-bold uppercase tracking-[0.12em] text-[#7F7C73]">{titulo}</h3>
      {children}
    </section>
  );
}

/**
 * Resumo da pessoa sem sair da lista: restrições por tipo, vínculos na carteira e idade da
 * consulta. Reconsultar custa dinheiro, então pede um segundo clique para confirmar.
 */
export default function GavetaPessoa({ pessoa, onFechar }: { pessoa: PessoaNaLista; onFechar: () => void }) {
  const tom = tomDoAvatar(pessoa.cpf);
  const situacao = SITUACAO[pessoa.situacao];
  const nome = caixaTitulo(pessoa.nome) || "Nome não informado";
  const local = [caixaTitulo(pessoa.cidade), pessoa.uf].filter(Boolean).join(" · ");
  const maiorValor = Math.max(1, ...pessoa.restricoes.map(r => r.valor));

  const [confirmando, setConfirmando] = useState(false);
  const { mutate: reconsultar, isPending } = useEnrichPersonSerasa(pessoa.cpf, () => setConfirmando(false));

  // O pedido de confirmação expira: um clique esquecido não pode virar consulta paga depois.
  useEffect(() => {
    if (!confirmando) return;
    const prazo = setTimeout(() => setConfirmando(false), 4000);
    return () => clearTimeout(prazo);
  }, [confirmando]);

  const rodape = (
    <>
      <Link href={`/individuals/${pessoa.cpf}`} className={BOTAO.primario}>
        Abrir análise completa
      </Link>
      <button
        type="button"
        disabled={isPending}
        onClick={() => (confirmando ? reconsultar() : setConfirmando(true))}
        className={confirmando ? BOTAO.laranja : BOTAO.fantasma}
      >
        <Icon name={isPending ? "progress_activity" : "history"} className={isPending ? "animate-spin" : ""} />
        {isPending ? "Consultando…" : confirmando ? `Confirmar · ${CUSTO_CONSULTA_PF}` : `Reconsultar · ${CUSTO_CONSULTA_PF}`}
      </button>
    </>
  );

  return (
    <Gaveta rotulo={`Resumo de ${nome}`} onFechar={onFechar} rodape={rodape}>
      <div className="flex items-start gap-4">
        <span
          className="grid h-[60px] w-[60px] shrink-0 place-items-center rounded-[18px] text-[19px] font-bold"
          style={{ background: tom.fundo, color: tom.texto }}
        >
          {iniciais(pessoa.nome)}
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-[26px] font-semibold leading-[1.1] tracking-[-0.015em] text-grafite">{nome}</h2>
          <p className="mt-1.5 font-mono text-[13px] text-suave">
            {formatCpf(pessoa.cpf)}
            {local && ` · ${local}`}
          </p>
          <span
            className="mt-2.5 inline-flex h-[26px] items-center rounded-full px-2.5 text-xs font-bold"
            style={{ background: situacao.fundo, color: situacao.texto }}
          >
            {situacao.rotulo}
          </span>
        </div>
        <button
          type="button"
          onClick={onFechar}
          aria-label="Fechar"
          className="grid h-11 w-11 shrink-0 place-items-center rounded-xl text-[#55534D] transition-colors hover:bg-white"
        >
          <Icon name="close" />
        </button>
      </div>

      {pessoa.dias > DIAS_CONSULTA_VELHA && (
        <p className="flex items-start gap-2.5 rounded-[14px] bg-secondary/10 px-3.5 py-3 text-[13px] text-[#7A3E12]">
          <Icon name="schedule" size={20} className="shrink-0" />
          <span>
            Consulta de <b>{tempoRelativo(pessoa.dias)}</b>. Para uma decisão de crédito vale reconsultar.
          </span>
        </p>
      )}

      <Secao titulo="Restrições">
        {pessoa.totalOcorrencias === 0 ? (
          <p className="flex items-center gap-3 rounded-2xl bg-[#E7F2EC] p-4 text-sm font-medium text-[#17583F]">
            <Icon name="check_circle" size={26} className="shrink-0" />
            Nada consta em Pefin, Refin, cheques, protestos, dívidas vencidas, ações e falências.
          </p>
        ) : (
          <div className="rounded-2xl border border-linha bg-white px-4 py-1.5">
            {pessoa.restricoes.map(r => (
              <div key={r.tipo} className="grid grid-cols-[1fr_auto] items-center gap-x-3 gap-y-1.5 border-b border-[#F0EEE8] py-3">
                <span className="text-sm font-semibold text-grafite">
                  {TIPO_RESTRICAO[r.tipo].rotulo}{" "}
                  <span className="font-medium text-suave">· {r.quantidade === 1 ? "1 registro" : `${r.quantidade} registros`}</span>
                </span>
                <span className="text-sm font-bold text-grafite">{formatMoeda(r.valor)}</span>
                <span className="col-span-2 h-[5px] overflow-hidden rounded-full bg-[#F0EEE8]">
                  <i
                    className="block h-full origin-left rounded-full animate-crescer-x motion-reduce:animate-none"
                    style={{ width: `${(r.valor / maiorValor) * 100}%`, background: TIPO_RESTRICAO[r.tipo].cor, animationDelay: "200ms" }}
                  />
                </span>
              </div>
            ))}
            <div className="flex items-center justify-between py-3">
              <span className="text-sm font-bold text-grafite">Total</span>
              <span className="font-display text-xl font-semibold text-grafite">{formatMoeda(pessoa.dividaTotal)}</span>
            </div>
          </div>
        )}
      </Secao>

      <Secao titulo="Vínculos na carteira">
        <div className="rounded-2xl border border-linha bg-white px-4">
          {pessoa.vinculos.length === 0 ? (
            <p className="py-3.5 text-[13.5px] text-suave">Consulta avulsa, sem empresa da carteira ligada.</p>
          ) : (
            pessoa.vinculos.map(v => (
              <Link
                key={v.cnpj}
                href={`/clients/${v.cnpj}`}
                className="group flex items-center gap-3 border-b border-[#F0EEE8] py-3 text-grafite last:border-b-0 hover:text-primary"
              >
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-[10px] bg-[#F4F2EC] text-primary">
                  <Icon name="business" size={20} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold">{caixaTitulo(v.razaoSocial) || formatCnpj(v.cnpj)}</span>
                  <span className="block text-xs text-suave">Sócio · abrir Visão 360º</span>
                </span>
                <Icon name="chevron_right" className="text-[#B5B1A6] transition-transform group-hover:translate-x-1" />
              </Link>
            ))
          )}
        </div>
      </Secao>

      <Secao titulo="Consulta">
        <div className="rounded-2xl border border-linha bg-white px-4">
          <div className="flex justify-between border-b border-[#F0EEE8] py-3 text-sm">
            <span>Consultado em</span>
            <span className="font-semibold">
              {formatData(pessoa.consultaEm)} · {tempoRelativo(pessoa.dias)}
            </span>
          </div>
          <div className="flex justify-between py-3 text-sm">
            <span>Base</span>
            <span className="font-semibold">Serasa PF</span>
          </div>
        </div>
      </Secao>
    </Gaveta>
  );
}
