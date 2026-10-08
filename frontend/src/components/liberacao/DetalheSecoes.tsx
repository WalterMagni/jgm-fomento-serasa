"use client";

import { useState, type ReactNode } from "react";
import Icon from "@/components/ui/Icon";
import MentionTextarea from "@/components/ui/mencao/MentionTextarea";
import TextoRico from "@/components/ui/mencao/TextoRico";
import { useRegistrarParecer, useResponderPendencia } from "@/hooks/useLiberacao";
import {
  ROTULO_POSICAO,
  type LiberacaoCard,
  type Parecer,
  type Pendencia,
  type PosicaoParecer,
  type Sacado,
} from "@/types/liberacao";
import Avatar from "./Avatar";
import { BOTAO_PRIMARIO, BOTAO_SECUNDARIO } from "./Dialogo";
import { COR_POSICAO, ICONE_POSICAO, formatDataHora, formatDocumento, formatMoeda, tempoRelativo } from "./formatters";

export function Secao({ titulo, icone, acao, children }: { titulo: string; icone: string; acao?: ReactNode; children: ReactNode }) {
  return (
    <section className="border-t border-slate-100 pt-5 first:border-t-0 first:pt-0 dark:border-slate-800">
      <header className="mb-3 flex items-center justify-between gap-2">
        <h3 className="inline-flex items-center gap-1.5 font-sans text-[11px] font-bold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400">
          <Icon name={icone} size={14} />
          {titulo}
        </h3>
        {acao}
      </header>
      {children}
    </section>
  );
}

// ------------------------------------------------------------------ sacados

export function SacadosLista({ sacados, valorOperacao }: { sacados: Sacado[]; valorOperacao: number | null }) {
  if (sacados.length === 0) {
    return <p className="text-sm text-slate-400">Nenhum sacado informado.</p>;
  }
  const comValor = sacados.filter(sacado => sacado.valor != null);
  const soma = comValor.reduce((total, sacado) => total + (sacado.valor ?? 0), 0);
  const divergente = valorOperacao != null && comValor.length > 0 && Math.abs(soma - valorOperacao) >= 0.01;

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 dark:border-slate-700">
      <ul className="divide-y divide-slate-100 dark:divide-slate-800">
        {sacados.map(sacado => (
          <li key={sacado.documento} className="flex items-center gap-3 px-3 py-2.5">
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-slate-800 dark:text-slate-100">
                {sacado.nome ?? <span className="font-normal italic text-slate-400">sem nome na base</span>}
              </p>
              <p className="font-mono text-[11px] text-slate-500">{formatDocumento(sacado.documento)}</p>
            </div>
            {sacado.valor != null && (
              <span className="shrink-0 text-sm tabular-nums text-slate-700 dark:text-slate-200">{formatMoeda(sacado.valor)}</span>
            )}
            {sacado.documento.length === 14 && (
              <a
                href={`/clients/${sacado.documento}`}
                target="_blank"
                rel="noopener noreferrer"
                title={sacado.cadastrado ? "Abrir a página da empresa em nova aba" : "Empresa não cadastrada: abre a página para cadastrar"}
                aria-label={`Abrir ${sacado.nome ?? formatDocumento(sacado.documento)} em nova aba`}
                className={`inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg transition-colors focus:outline-none
                  focus-visible:ring-2 focus-visible:ring-[#612035] ${
                    sacado.cadastrado
                      ? "text-slate-500 hover:bg-slate-100 hover:text-[#612035] dark:hover:bg-slate-800"
                      : "border border-dashed border-amber-400 text-amber-600 hover:bg-amber-50 dark:hover:bg-amber-900/30"
                  }`}
              >
                <Icon name="open_in_new" size={14} />
              </a>
            )}
          </li>
        ))}
      </ul>
      {comValor.length > 0 && (
        <p className={`flex items-center gap-1 border-t border-slate-100 bg-slate-50 px-3 py-2 text-[11px] dark:border-slate-800 dark:bg-slate-800/50
          ${divergente ? "text-amber-700 dark:text-amber-300" : "text-slate-500"}`}>
          {divergente && <Icon name="warning" size={12} />}
          Soma dos sacados <strong className="ml-auto tabular-nums">{formatMoeda(soma)}</strong>
          {divergente && <span className="ml-1">≠ {formatMoeda(valorOperacao)}</span>}
        </p>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ Comitê

const POSICOES: PosicaoParecer[] = ["FAVORAVEL", "COM_RESSALVAS", "DESFAVORAVEL"];

function FormParecer({ card, atual, onCancelar }: { card: LiberacaoCard; atual?: Parecer; onCancelar?: () => void }) {
  const registrar = useRegistrarParecer();
  const [posicao, setPosicao] = useState<PosicaoParecer | null>(atual?.posicao ?? null);
  const [texto, setTexto] = useState(atual?.texto ?? "");

  return (
    <div className="mt-3 rounded-xl border border-[#612035]/25 bg-[#612035]/[0.03] p-3 dark:border-[#e8a3b6]/25 dark:bg-[#612035]/10">
      <p className="mb-2 text-xs font-semibold text-[#612035] dark:text-[#e8a3b6]">
        {atual?.posicao ? "Rever meu parecer" : "Seu parecer é esperado"}
      </p>
      <div role="radiogroup" aria-label="Posição" className="grid grid-cols-3 gap-1.5">
        {POSICOES.map(opcao => (
          <button
            key={opcao}
            type="button"
            role="radio"
            aria-checked={posicao === opcao}
            onClick={() => setPosicao(opcao)}
            className={`flex min-h-10 cursor-pointer items-center justify-center gap-1 rounded-lg border px-2 text-xs font-medium transition-colors
              focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] ${
                posicao === opcao
                  ? `${COR_POSICAO[opcao]} border-transparent`
                  : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
              }`}
          >
            <Icon name={ICONE_POSICAO[opcao]} size={13} />
            {ROTULO_POSICAO[opcao]}
          </button>
        ))}
      </div>
      <div className="mt-2">
        <MentionTextarea
          value={texto}
          onChange={setTexto}
          rows={3}
          placeholder="Fundamente: o que pesou a favor, o que preocupa. Use @ para marcar."
          aria-label="Texto do parecer"
        />
      </div>
      <div className="mt-2 flex justify-end gap-2">
        {onCancelar && (
          <button type="button" onClick={onCancelar} className={BOTAO_SECUNDARIO}>
            Cancelar
          </button>
        )}
        <button
          type="button"
          disabled={!posicao || registrar.isPending}
          onClick={() =>
            posicao && registrar.mutate({ id: card.id, posicao, texto }, { onSuccess: () => onCancelar?.() })
          }
          className={BOTAO_PRIMARIO}
        >
          {registrar.isPending && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />}
          Registrar parecer
        </button>
      </div>
    </div>
  );
}

function LinhaParecer({ parecer }: { parecer: Parecer }) {
  return (
    <li className="flex gap-3 py-3 first:pt-0">
      <Avatar nome={parecer.usuarioNome} iniciais={parecer.iniciais} tamanho="md" aguardando={!parecer.posicao} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="text-sm font-semibold text-slate-800 dark:text-slate-100">{parecer.usuarioNome}</span>
          {parecer.posicao ? (
            <span className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-medium ${COR_POSICAO[parecer.posicao]}`}>
              <Icon name={ICONE_POSICAO[parecer.posicao]} size={11} />
              {ROTULO_POSICAO[parecer.posicao]}
            </span>
          ) : (
            <span className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-500 dark:bg-slate-800">aguardando</span>
          )}
          {parecer.registradoEm && (
            <span className="text-[11px] text-slate-400" title={formatDataHora(parecer.registradoEm)}>
              {tempoRelativo(parecer.registradoEm)}
            </span>
          )}
        </div>
        {parecer.texto && (
          <p className="mt-1 text-sm leading-relaxed text-slate-700 dark:text-slate-300">
            <TextoRico texto={parecer.texto} />
          </p>
        )}
      </div>
    </li>
  );
}

export function PareceresComite({ card, pareceres, euId }: { card: LiberacaoCard; pareceres: Parecer[]; euId?: string }) {
  const [revendo, setRevendo] = useState(false);
  const [verAnteriores, setVerAnteriores] = useState(false);
  const vigentes = pareceres.filter(parecer => parecer.rodada === card.rodada);
  const anteriores = pareceres.filter(parecer => parecer.rodada !== card.rodada && parecer.posicao);
  const meu = vigentes.find(parecer => parecer.usuarioId === euId);
  const noComite = card.etapa === "COMITE";

  if (vigentes.length === 0 && anteriores.length === 0) {
    return <p className="text-sm text-slate-400">O Comitê é convocado quando o card entra na coluna Comitê.</p>;
  }

  return (
    <div>
      {vigentes.length > 0 && (
        <>
          {card.rodada > 1 && <p className="mb-2 text-[11px] font-medium text-slate-400">Rodada {card.rodada}</p>}
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {vigentes.map(parecer => (
              <LinhaParecer key={parecer.id} parecer={parecer} />
            ))}
          </ul>
        </>
      )}

      {noComite && meu && !meu.posicao && <FormParecer card={card} />}
      {noComite && meu?.posicao && !revendo && (
        <button
          type="button"
          onClick={() => setRevendo(true)}
          className="mt-1 inline-flex cursor-pointer items-center gap-1 text-xs font-medium text-[#612035] hover:underline dark:text-[#e8a3b6]"
        >
          <Icon name="edit" size={12} /> rever meu parecer
        </button>
      )}
      {noComite && meu?.posicao && revendo && <FormParecer card={card} atual={meu} onCancelar={() => setRevendo(false)} />}

      {anteriores.length > 0 && (
        <div className="mt-3">
          <button
            type="button"
            onClick={() => setVerAnteriores(atual => !atual)}
            aria-expanded={verAnteriores}
            className="inline-flex cursor-pointer items-center gap-1 text-xs text-slate-500 hover:text-slate-800 dark:hover:text-slate-200"
          >
            <Icon name={verAnteriores ? "expand_less" : "expand_more"} size={14} />
            {anteriores.length} parecer(es) de rodadas anteriores
          </button>
          {verAnteriores && (
            <div className="mt-2 divide-y divide-slate-100 rounded-xl bg-slate-50 px-3 pt-3 dark:divide-slate-800 dark:bg-slate-800/40">
              {anteriores.map(parecer => (
                <div key={parecer.id} className="pt-2 first:pt-0">
                  <p className="text-[10px] font-medium uppercase tracking-wide text-slate-400">Rodada {parecer.rodada}</p>
                  <ul>
                    <LinhaParecer parecer={parecer} />
                  </ul>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ pendências

function ItemPendencia({ cardId, pendencia }: { cardId: string; pendencia: Pendencia }) {
  const responder = useResponderPendencia();
  const [resposta, setResposta] = useState("");
  const aberta = !pendencia.respondidaEm;

  return (
    <li
      className={`rounded-xl border p-3 ${
        aberta
          ? "border-[#D1732C]/40 bg-[#D1732C]/[0.05] dark:border-[#D1732C]/40 dark:bg-[#D1732C]/10"
          : "border-slate-200 dark:border-slate-700"
      }`}
    >
      <div className="flex flex-wrap items-center gap-x-1.5 text-xs text-slate-500 dark:text-slate-400">
        <strong className="font-semibold text-slate-700 dark:text-slate-200">{pendencia.abertaPorNome}</strong>
        <Icon name="arrow_forward" size={12} />
        <strong className="font-semibold text-slate-700 dark:text-slate-200">{pendencia.destinatarioNome}</strong>
        <span title={formatDataHora(pendencia.abertaEm)}>· {tempoRelativo(pendencia.abertaEm)}</span>
        <span
          className={`ml-auto rounded-md px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${
            aberta ? "bg-[#D1732C] text-white" : "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200"
          }`}
        >
          {aberta ? "aberta" : "respondida"}
        </span>
      </div>
      <p className="mt-1.5 text-sm text-slate-800 dark:text-slate-100">
        <TextoRico texto={pendencia.texto} />
      </p>

      {pendencia.resposta && (
        <div className="mt-2 rounded-lg border-l-2 border-emerald-500 bg-white px-3 py-2 dark:bg-slate-900">
          <p className="text-[11px] text-slate-500">
            {pendencia.respondidaPorNome} respondeu {tempoRelativo(pendencia.respondidaEm)}
          </p>
          <p className="mt-0.5 text-sm text-slate-700 dark:text-slate-200">
            <TextoRico texto={pendencia.resposta} />
          </p>
        </div>
      )}

      {pendencia.podeResponder && (
        <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-end">
          <div className="min-w-0 flex-1">
            <MentionTextarea value={resposta} onChange={setResposta} rows={2} placeholder="Responder a pendência" aria-label="Resposta da pendência" />
          </div>
          <button
            type="button"
            disabled={!resposta.trim() || responder.isPending}
            onClick={() =>
              responder.mutate({ id: cardId, pendenciaId: pendencia.id, resposta }, { onSuccess: () => setResposta("") })
            }
            className={`${BOTAO_PRIMARIO} shrink-0`}
          >
            <Icon name="reply" size={14} /> Responder
          </button>
        </div>
      )}
    </li>
  );
}

export function PendenciasLista({ cardId, pendencias }: { cardId: string; pendencias: Pendencia[] }) {
  if (pendencias.length === 0) return <p className="text-sm text-slate-400">Nenhuma pendência.</p>;
  const ordenadas = [...pendencias].sort((a, b) => Number(Boolean(a.respondidaEm)) - Number(Boolean(b.respondidaEm)));
  return (
    <ul className="space-y-2">
      {ordenadas.map(pendencia => (
        <ItemPendencia key={pendencia.id} cardId={cardId} pendencia={pendencia} />
      ))}
    </ul>
  );
}
