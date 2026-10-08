"use client";

import { useEffect, useState } from "react";
import { useMarcarLidasDoLink } from "@/hooks/useNotificacoes";
import Icon from "@/components/ui/Icon";
import TextoRico, { MencoesProvider } from "@/components/ui/mencao/TextoRico";
import { useCadastrarEmpresa, useEditarCard, useExcluirCard, useLiberacaoDetalhe, useNovaPendencia } from "@/hooks/useLiberacao";
import { ROTULO_ETAPA, ROTULO_ETAPA_CURTO, ROTULO_TIPO, type LiberacaoCard } from "@/types/liberacao";
import CardForm from "./CardForm";
import Atividade from "./Atividade";
import OrganizacaoCard from "./OrganizacaoCard";
import { PareceresComite, PendenciasLista, SacadosLista, Secao } from "./DetalheSecoes";
import Dialogo from "./Dialogo";
import PendenciaDialog from "./PendenciaDialog";
import {
  COR_ETAPA,
  ICONE_ETAPA,
  confirmar,
  formatDataHora,
  formatDocumento,
  formatMoeda,
  situacaoPrazo,
  tempoRelativo,
} from "./formatters";

type Props = {
  cardId: string;
  euId?: string;
  ehAnalista: boolean;
  onFechar: () => void;
  onMover: (card: LiberacaoCard, para: LiberacaoCard["etapa"]) => void;
};

function Dado({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-[11px] font-medium text-slate-500 dark:text-slate-400">{rotulo}</dt>
      <dd className="mt-0.5 text-sm text-slate-800 dark:text-slate-100">{children}</dd>
    </div>
  );
}

export default function CardDetalheModal({ cardId, euId, ehAnalista, onFechar, onMover }: Props) {
  const { data, isLoading, error } = useLiberacaoDetalhe(cardId);
  const editar = useEditarCard();
  const excluir = useExcluirCard();
  const novaPendencia = useNovaPendencia();
  const cadastrar = useCadastrarEmpresa();
  const [editando, setEditando] = useState(false);
  const marcarLidas = useMarcarLidasDoLink();
  const { mutate: marcarLidasDoCard } = marcarLidas;

  // Abrir o card resolve as notificações que apontavam para ele.
  useEffect(() => {
    marcarLidasDoCard(`/liberacao?card=${cardId}`);
  }, [cardId, marcarLidasDoCard]);
  const [abrindoPendencia, setAbrindoPendencia] = useState(false);

  const card = data?.card;

  if (!card) {
    return (
      <Dialogo titulo="Card" onFechar={onFechar} largura="xl">
        <div className="flex min-h-64 items-center justify-center p-10 text-sm text-slate-500">
          {isLoading ? (
            <span className="h-6 w-6 animate-spin rounded-full border-2 border-slate-300 border-t-[#612035]" />
          ) : (
            (error as Error | null)?.message ?? "Card não encontrado"
          )}
        </div>
      </Dialogo>
    );
  }

  const cor = COR_ETAPA[card.etapa];
  const prazo = situacaoPrazo(card);
  // Mesmo motivo em vários destinos (os três de decisão esperando o Comitê): mostra uma vez só.
  const motivos = card.destinos.filter(destino => !destino.permitido && destino.motivo).map(destino => destino.motivo!);
  const motivoComum = motivos.length > 1 && motivos.every(motivo => motivo === motivos[0]) ? motivos[0] : null;

  const titulo = (
    <span className="flex flex-col gap-1">
      <span className="flex flex-wrap items-center gap-2 font-sans text-[11px] font-medium">
        <span className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 uppercase tracking-wide ${cor.fundo} ${cor.texto}`}>
          <Icon name={ICONE_ETAPA[card.etapa]} size={12} />
          {ROTULO_ETAPA[card.etapa]}
        </span>
        <span className="font-mono text-slate-400">#{card.numero}</span>
        {card.rodada > 1 && <span className="text-slate-400">· rodada {card.rodada}</span>}
      </span>
      <span className="leading-tight">{card.cedenteNome}</span>
    </span>
  );

  const subtitulo = (
    <span className="flex flex-wrap items-center gap-2">
      <span className="font-mono">{formatDocumento(card.cedenteCnpj)}</span>
      {card.cedenteCadastrado ? (
        <a
          href={`/clients/${card.cedenteCnpj}`}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1 font-medium text-[#612035] hover:underline dark:text-[#e8a3b6]"
        >
          página da empresa <Icon name="open_in_new" size={11} />
        </a>
      ) : (
        <span className="inline-flex items-center gap-1.5">
          <span className="rounded-md bg-amber-100 px-1.5 py-0.5 font-medium text-amber-800 dark:bg-amber-900/40 dark:text-amber-200">
            empresa não cadastrada
          </span>
          <button
            type="button"
            disabled={cadastrar.isPending}
            onClick={() => cadastrar.mutate({ cnpj: card.cedenteCnpj, cardId: card.id })}
            className="inline-flex cursor-pointer items-center gap-1 font-medium text-[#2956E0] hover:underline disabled:opacity-50"
          >
            {cadastrar.isPending ? "cadastrando…" : "cadastrar via CNPJ Já"}
          </button>
        </span>
      )}
    </span>
  );

  return (
    // O contexto atravessa o portal do Dialogo: os textos lá dentro sabem quais empresas existem.
    <MencoesProvider empresas={data.empresas} cardId={card.id}>
    <Dialogo titulo={titulo} subtitulo={subtitulo} rotulo={`Card ${card.numero}: ${card.cedenteNome}`} onFechar={onFechar} largura="xl">
      <div className="grid min-h-full lg:grid-cols-[minmax(0,1fr)_17rem]">
        {/* ---------------------------------------------------------------- conteúdo */}
        <div className="min-w-0">
          {editando ? (
            <CardForm
              inicial={{
                cedente: { cnpj: card.cedenteCnpj, nome: card.cedenteNome, cadastrado: card.cedenteCadastrado },
                tipoOperacao: card.tipoOperacao,
                valor: card.valor,
                prazo: card.prazo,
                parecerOrigem: card.parecerOrigem,
                sacados: data.sacados,
              }}
              enviando={editar.isPending}
              rotuloEnviar="Salvar alterações"
              onCancelar={() => setEditando(false)}
              onEnviar={dados =>
                editar.mutate({ id: card.id, dados: { ...dados, version: card.version } }, { onSuccess: () => setEditando(false) })
              }
            />
          ) : (
            <div className="space-y-5 px-5 py-5">
              <Secao
                titulo="Operação"
                icone="receipt_long"
                acao={
                  card.podeEditar && (
                    <button
                      type="button"
                      onClick={() => setEditando(true)}
                      className="inline-flex min-h-8 cursor-pointer items-center gap-1 rounded-lg px-2 text-xs font-medium text-slate-600
                        hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] dark:text-slate-300 dark:hover:bg-slate-800"
                    >
                      <Icon name="edit" size={13} /> Editar
                    </button>
                  )
                }
              >
                <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
                  <Dado rotulo="Valor">
                    <span className="font-display text-xl font-semibold tracking-tight">{formatMoeda(card.valor)}</span>
                  </Dado>
                  <Dado rotulo="Tipo">{card.tipoOperacao ? ROTULO_TIPO[card.tipoOperacao] : "—"}</Dado>
                  <Dado rotulo="Prazo">
                    {card.prazo ? (
                      <span
                        className={
                          prazo === "vencido"
                            ? "font-semibold text-rose-700 dark:text-rose-300"
                            : prazo === "perto"
                              ? "font-semibold text-amber-700 dark:text-amber-300"
                              : ""
                        }
                      >
                        {formatDataHora(card.prazo)}
                        {prazo === "vencido" && " · vencido"}
                      </span>
                    ) : (
                      "—"
                    )}
                  </Dado>
                </dl>
              </Secao>

              <Secao titulo={`Sacados (${data.sacados.length})`} icone="people">
                <SacadosLista sacados={data.sacados} valorOperacao={card.valor} />
              </Secao>

              <Secao titulo="Parecer da origem" icone="description">
                {card.parecerOrigem ? (
                  <p className="text-sm leading-relaxed text-slate-700 dark:text-slate-300">
                    <TextoRico texto={card.parecerOrigem} />
                  </p>
                ) : (
                  <p className="text-sm text-slate-400">Sem parecer.</p>
                )}
              </Secao>

              {(card.etapa !== "ORIGEM" || data.pareceres.length > 0) && (
                <Secao titulo="Comitê" icone="gavel">
                  <PareceresComite card={card} pareceres={data.pareceres} euId={euId} />
                </Secao>
              )}

              {(card.etapa === "PENDENCIA" || data.pendencias.length > 0) && (
                <Secao
                  titulo="Pendências"
                  icone="hourglass_top"
                  acao={
                    card.etapa === "PENDENCIA" &&
                    ehAnalista && (
                      <button
                        type="button"
                        onClick={() => setAbrindoPendencia(true)}
                        className="inline-flex min-h-8 cursor-pointer items-center gap-1 rounded-lg px-2 text-xs font-medium text-[#a8551a]
                          hover:bg-[#D1732C]/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#D1732C] dark:text-[#f0a46b]"
                      >
                        <Icon name="add" size={13} /> Nova pendência
                      </button>
                    )
                  }
                >
                  <PendenciasLista cardId={card.id} pendencias={data.pendencias} />
                </Secao>
              )}

              <Secao titulo="Atividade" icone="chat">
                <Atividade cardId={card.id} eventos={data.eventos} comentarios={data.comentarios} euId={euId} />
              </Secao>
            </div>
          )}
        </div>

        {/* ---------------------------------------------------------------- lateral */}
        <aside className="space-y-6 border-t border-slate-100 bg-slate-50/70 px-5 py-5 lg:border-l lg:border-t-0 dark:border-slate-800 dark:bg-slate-900/60">
          <div>
            <h3 className="mb-2 font-sans text-[11px] font-bold uppercase tracking-[0.08em] text-slate-500">Mover para</h3>
            {motivoComum && (
              <p className="mb-2 flex items-start gap-1.5 rounded-lg bg-slate-100 px-2.5 py-2 text-[11px] leading-snug text-slate-600
                dark:bg-slate-800 dark:text-slate-300">
                <Icon name="lock" size={12} className="mt-px shrink-0" />
                {motivoComum}
              </p>
            )}
            <div className="space-y-1.5">
              {card.destinos.map(destino => {
                const corDestino = COR_ETAPA[destino.etapa];
                return (
                  <div key={destino.etapa}>
                    <button
                      type="button"
                      disabled={!destino.permitido}
                      onClick={() => onMover(card, destino.etapa)}
                      title={destino.motivo ?? undefined}
                      className={`group flex min-h-10 w-full cursor-pointer items-center gap-2 rounded-lg border bg-white px-3 text-left text-sm font-medium
                        transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] disabled:cursor-not-allowed
                        dark:bg-slate-800 ${
                          destino.permitido
                            ? `border-slate-200 text-slate-700 hover:border-transparent hover:ring-2 ${corDestino.anel} dark:border-slate-700 dark:text-slate-200`
                            : "border-slate-200 text-slate-400 dark:border-slate-700 dark:text-slate-500"
                        }`}
                    >
                      <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${destino.permitido ? corDestino.faixa : "bg-slate-300 dark:bg-slate-600"}`} />
                      {card.etapa === "COMITE" && destino.etapa === "ORIGEM"
                        ? "Devolver à Origem"
                        : (card.etapa === "APROVADO" || card.etapa === "REPROVADO") && destino.etapa === "COMITE"
                          ? "Reabrir no Comitê"
                          : ROTULO_ETAPA_CURTO[destino.etapa]}
                      {destino.permitido ? (
                        <Icon name="arrow_forward" size={14} className="ml-auto text-slate-300 transition-colors group-hover:text-slate-600" />
                      ) : (
                        <Icon name="lock" size={13} className="ml-auto" />
                      )}
                    </button>
                    {!destino.permitido && destino.motivo && destino.motivo !== motivoComum && (
                      <p className="mt-1 px-1 text-[11px] leading-snug text-slate-500 dark:text-slate-400">{destino.motivo}</p>
                    )}
                  </div>
                );
              })}
              {card.destinos.length === 0 && <p className="text-xs text-slate-400">Sem movimentos a partir daqui.</p>}
            </div>
          </div>

          <OrganizacaoCard card={card} euId={euId} ehAnalista={ehAnalista} />

          <div className="space-y-2 text-[11px] text-slate-500 dark:text-slate-400">
            <p>
              Criado por <strong className="font-semibold text-slate-700 dark:text-slate-200">{card.criadoPorNome}</strong>
              <br />
              {formatDataHora(card.criadoEm)}
            </p>
            <p>
              Última alteração por <strong className="font-semibold text-slate-700 dark:text-slate-200">{card.atualizadoPorNome}</strong>
              <br />
              <span title={formatDataHora(card.atualizadoEm)}>
                {formatDataHora(card.atualizadoEm)} · {tempoRelativo(card.atualizadoEm)}
              </span>
            </p>
          </div>

          {card.podeEditar && (
            <button
              type="button"
              disabled={excluir.isPending}
              onClick={() => {
                if (confirmar(`Apagar o card #${card.numero} (${card.cedenteNome})?\n\nEle some do quadro, mas fica registrado quem apagou.`)) {
                  excluir.mutate({ id: card.id, numero: card.numero }, { onSuccess: onFechar });
                }
              }}
              className="inline-flex min-h-9 w-full cursor-pointer items-center justify-center gap-1.5 rounded-lg border border-rose-200 bg-white text-xs
                font-medium text-rose-700 transition-colors hover:bg-rose-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500
                disabled:opacity-50 dark:border-rose-900/60 dark:bg-transparent dark:text-rose-300 dark:hover:bg-rose-950/40"
            >
              <Icon name="delete" size={14} /> Apagar card
            </button>
          )}
        </aside>
      </div>

      {abrindoPendencia && (
        <PendenciaDialog
          titulo={`Nova pendência em #${card.numero}`}
          subtitulo={card.cedenteNome}
          euId={euId}
          enviando={novaPendencia.isPending}
          onFechar={() => setAbrindoPendencia(false)}
          onConfirmar={([pendencia]) =>
            novaPendencia.mutate({ id: card.id, ...pendencia }, { onSuccess: () => setAbrindoPendencia(false) })
          }
        />
      )}
    </Dialogo>
    </MencoesProvider>
  );
}
