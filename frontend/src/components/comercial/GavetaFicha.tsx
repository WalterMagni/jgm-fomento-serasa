"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import Gaveta from "@/components/ui/Gaveta";
import Icon from "@/components/ui/Icon";
import { BOTAO } from "@/components/ui/botao";
import { caixaTitulo, formatCnpj, iniciais, tempoRelativo, tomDoAvatar } from "@/lib/formatadores";
import { LIQUIDACAO, formatMoedaFicha, formatPct, numeroBr } from "@/types/informacao-comercial";
import { BarraLiquidacao, usoDoLimite, type FichaNaLista } from "./LinhaFicha";

function Secao({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section>
      <h3 className="mb-2.5 font-sans text-[11px] font-bold uppercase tracking-[0.12em] text-[#7F7C73]">{titulo}</h3>
      {children}
    </section>
  );
}

function Linha({ rotulo, valor, destaque }: { rotulo: string; valor: ReactNode; destaque?: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-[#F0EEE8] py-3 text-sm last:border-b-0">
      <span className="text-suave">{rotulo}</span>
      <span className="text-right font-semibold" style={{ color: destaque }}>
        {valor}
      </span>
    </div>
  );
}

const dinheiro = (texto?: string | null) => (numeroBr(texto) > 0 ? formatMoedaFicha(numeroBr(texto)) : "—");
const texto = (valor?: string | null) => (valor && valor.trim() ? valor : "—");

/**
 * A ficha inteira sem sair da lista: exposição frente ao limite, vencidos, liquidação L1–L4 e o
 * relacionamento com o parceiro. Editar continua na Visão 360º da empresa.
 */
export default function GavetaFicha({
  ficha,
  outras,
  onAbrirOutra,
  onFechar,
}: {
  ficha: FichaNaLista;
  /** Fichas de outros parceiros sobre a mesma empresa. */
  outras: FichaNaLista[];
  onAbrirOutra: (id: string) => void;
  onFechar: () => void;
}) {
  const tom = tomDoAvatar(ficha.cnpj ?? ficha.id);
  const nome = caixaTitulo(ficha.empresa ?? null) || "Empresa sem nome";
  const uso = usoDoLimite(ficha);
  const riscos = [
    { rotulo: "Duplicata", valor: numeroBr(ficha.riscoDuplicata) },
    { rotulo: "Cheque", valor: numeroBr(ficha.riscoCheque) },
    { rotulo: "Comissária", valor: numeroBr(ficha.riscoComissaria) },
  ];
  const maiorRisco = Math.max(1, ...riscos.map(r => r.valor));
  const temLiquidacao = ficha.liquidacao.some(l => l.valor > 0);

  const rodape = ficha.cnpj ? (
    <Link href={`/clients/${ficha.cnpj}`} className={BOTAO.primario}>
      Abrir Visão 360º
    </Link>
  ) : undefined;

  return (
    <Gaveta rotulo={`Ficha comercial de ${nome}`} onFechar={onFechar} rodape={rodape}>
      <div className="flex items-start gap-4">
        <span
          className="grid h-[60px] w-[60px] shrink-0 place-items-center rounded-[18px] text-[19px] font-bold"
          style={{ background: tom.fundo, color: tom.texto }}
        >
          {iniciais(ficha.empresa ?? null)}
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-[26px] font-semibold leading-[1.1] tracking-[-0.015em] text-grafite">{nome}</h2>
          <p className="mt-1.5 font-mono text-[13px] text-suave">{ficha.cnpj ? formatCnpj(ficha.cnpj) : "sem CNPJ"}</p>
          <div className="mt-2.5 flex flex-wrap gap-1.5">
            {ficha.parceiro && (
              <span className="inline-flex h-[26px] items-center gap-1.5 rounded-full bg-primary/10 px-2.5 text-xs font-bold text-primary">
                <Icon name="handshake" size={14} />
                {ficha.parceiro}
              </span>
            )}
            {ficha.data && (
              <span className="inline-flex h-[26px] items-center rounded-full bg-[#EFEDE6] px-2.5 text-xs font-bold text-[#5E5C55]">
                Ficha de {ficha.data}
                {ficha.dias != null && ` · ${tempoRelativo(ficha.dias)}`}
              </span>
            )}
          </div>
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

      {ficha.vencidoN > 0 && (
        <p className="flex items-start gap-2.5 rounded-[14px] bg-[#F8E1E6] px-3.5 py-3 text-[13px] text-[#8E1B37]">
          <Icon name="warning" size={20} className="shrink-0" />
          <span>
            <b>{formatMoedaFicha(ficha.vencidoN)}</b> vencidos neste parceiro
            {ficha.vencidosData && <> desde {ficha.vencidosData}</>}.
          </span>
        </p>
      )}

      <Secao titulo="Risco no parceiro">
        <div className="rounded-2xl border border-linha bg-white px-4 pt-4">
          <div className="flex items-end justify-between gap-3">
            <span className="font-display text-[30px] font-semibold leading-none tracking-[-0.015em] text-grafite">
              {ficha.riscoN > 0 ? formatMoedaFicha(ficha.riscoN) : "—"}
            </span>
            <span className="text-right text-xs text-suave">
              {uso ? (
                <>
                  <b className="text-grafite">{formatPct(Math.round(uso.fracao * 1000) / 10)}</b> do limite de {formatMoedaFicha(ficha.limiteN)}
                </>
              ) : (
                "sem limite informado"
              )}
            </span>
          </div>
          {uso && (
            <span className="mt-3 block h-2 overflow-hidden rounded-full bg-[#F0EEE8]">
              <i
                className="block h-full origin-left rounded-full animate-crescer-x motion-reduce:animate-none"
                style={{ width: `${Math.min(100, uso.fracao * 100)}%`, background: uso.cor, animationDelay: "200ms" }}
              />
            </span>
          )}
          <div className="mt-2">
            {riscos.map(r => (
              <div key={r.rotulo} className="grid grid-cols-[1fr_auto] items-center gap-x-3 gap-y-1.5 border-t border-[#F0EEE8] py-3">
                <span className="text-sm font-semibold text-grafite">{r.rotulo}</span>
                <span className={`text-sm font-bold ${r.valor > 0 ? "text-grafite" : "text-[#B0ACA1]"}`}>
                  {r.valor > 0 ? formatMoedaFicha(r.valor) : "—"}
                </span>
                <span className="col-span-2 h-[5px] overflow-hidden rounded-full bg-[#F0EEE8]">
                  <i
                    className="block h-full origin-left rounded-full bg-primary animate-crescer-x motion-reduce:animate-none"
                    style={{ width: `${(r.valor / maiorRisco) * 100}%`, animationDelay: "250ms" }}
                  />
                </span>
              </div>
            ))}
          </div>
        </div>
      </Secao>

      <Secao titulo="Vencidos">
        <div className="rounded-2xl border border-linha bg-white px-4">
          <Linha rotulo="Valor vencido" valor={dinheiro(ficha.vencidosValorMonetario)} destaque={ficha.vencidoN > 0 ? "#B42345" : undefined} />
          <Linha rotulo="Data do vencido" valor={texto(ficha.vencidosData)} />
          <Linha rotulo="Vencimento da última duplicata" valor={texto(ficha.vencidosValor)} />
        </div>
      </Secao>

      <Secao titulo="Liquidação dos títulos">
        <div className="rounded-2xl border border-linha bg-white px-4 py-4">
          {temLiquidacao ? (
            <>
              <BarraLiquidacao ficha={ficha} alta />
              <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2.5">
                {ficha.liquidacao.map(l => (
                  <span key={l.chave} className="flex items-center gap-2 text-sm">
                    <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: LIQUIDACAO[l.chave].cor }} />
                    <span className="text-suave">
                      {LIQUIDACAO[l.chave].nivel} {LIQUIDACAO[l.chave].rotulo.toLowerCase()}
                    </span>
                    <b className="ml-auto text-grafite">{formatPct(l.valor)}</b>
                  </span>
                ))}
              </div>
            </>
          ) : (
            <p className="text-sm text-suave">O parceiro não informou L1 a L4.</p>
          )}
          <div className="mt-3 flex items-baseline justify-between border-t border-[#F0EEE8] pt-3 text-sm">
            <span className="text-suave">VOP</span>
            <span className="font-semibold text-grafite">{dinheiro(ficha.vop)}</span>
          </div>
        </div>
      </Secao>

      <Secao titulo="Relacionamento">
        <div className="rounded-2xl border border-linha bg-white px-4">
          <Linha rotulo="Cliente desde" valor={texto(ficha.clienteDesde)} />
          <Linha rotulo="Última operação" valor={texto(ficha.ultimaOperacaoData)} />
          <Linha rotulo="Valor da última operação" valor={dinheiro(ficha.ultimaOperacaoValor)} />
          <Linha rotulo="Limite" valor={dinheiro(ficha.limite)} />
          <Linha rotulo="Recebida por" valor={ficha.tipo ? caixaTitulo(ficha.tipo) : "—"} />
        </div>
      </Secao>

      {ficha.observacao?.trim() && (
        <Secao titulo="Observação">
          <p className="whitespace-pre-wrap rounded-2xl border border-linha bg-white px-4 py-3.5 text-sm text-grafite">{ficha.observacao}</p>
        </Secao>
      )}

      {outras.length > 0 && (
        <Secao titulo={outras.length === 1 ? "Outra ficha desta empresa" : `Outras ${outras.length} fichas desta empresa`}>
          <div className="rounded-2xl border border-linha bg-white px-4">
            {outras.map(o => (
              <button
                key={o.id}
                type="button"
                onClick={() => onAbrirOutra(o.id)}
                className="group flex w-full items-center gap-3 border-b border-[#F0EEE8] py-3 text-left text-grafite last:border-b-0 hover:text-primary"
              >
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-[10px] bg-[#F4F2EC] text-primary">
                  <Icon name="handshake" size={20} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold">{o.parceiro || "Parceiro não informado"}</span>
                  <span className="block text-xs text-suave">
                    {o.data ? `Ficha de ${o.data}` : "Sem data"}
                    {o.vencidoN > 0 ? ` · ${formatMoedaFicha(o.vencidoN)} vencidos` : ""}
                  </span>
                </span>
                <Icon name="chevron_right" className="text-[#B5B1A6] transition-transform group-hover:translate-x-1" />
              </button>
            ))}
          </div>
        </Secao>
      )}

      {(ficha.authorName || ficha.createdAt) && (
        <p className="text-xs text-suave">
          Registrada
          {ficha.authorName && <> por <b className="text-grafite">{ficha.authorName}</b></>}
          {ficha.createdAt && <> em {new Date(ficha.createdAt).toLocaleDateString("pt-BR")}</>}. Para editar, abra a Visão 360º.
        </p>
      )}
    </Gaveta>
  );
}
