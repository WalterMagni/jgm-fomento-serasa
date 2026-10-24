"use client";

import { useEffect, useState } from "react";
import { useMarcarLidasDoLink } from "@/hooks/useNotificacoes";
import Icon from "@/components/ui/Icon";
import NotaDaEquipe from "@/components/nota/NotaDaEquipe";
import { RESSALVA } from "@/lib/ressalva";
import TextoRico, { MencoesProvider } from "@/components/ui/mencao/TextoRico";
import { useArquivarCard, useCadastrarEmpresa, useEditarCard, useExcluirCard, useLiberacaoDetalhe, useNovaPendencia } from "@/hooks/useLiberacao";
import { ETAPAS_DECIDIDAS, ROTULO_ETAPA, ROTULO_POSICAO, ROTULO_RESULTADO, type LiberacaoCard } from "@/types/liberacao";
import CardForm from "./CardForm";
import Anexos from "./Anexos";
import Atividade from "./Atividade";
import OrganizacaoCard from "./OrganizacaoCard";
import { ResumoProposta, rotuloProposta } from "./PropostaAr";
import { PareceresComite, PendenciasLista, SacadosLista, Secao } from "./DetalheSecoes";
import Dialogo from "./Dialogo";
import PendenciaDialog from "./PendenciaDialog";
import DeclinarDialog from "./DeclinarDialog";
import {
  COR_ETAPA,
  COR_POSICAO,
  COR_RESULTADO,
  DECLINIO,
  ICONE_POSICAO,
  ICONE_RESULTADO,
  ICONE_ETAPA,
  confirmar,
  formatDataHora,
  formatDocumento,
  formatMoeda,
  formatTaxa,
  juntarNomes,
  pareceresIncompletos,
  rotuloMover,
  situacaoPrazo,
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
  const arquivar = useArquivarCard();
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
  const [declinando, setDeclinando] = useState(false);

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
  const incompleto = pareceresIncompletos(card);

  const titulo = (
    <span className="flex flex-col gap-1">
      <span className="flex flex-wrap items-center gap-2 font-sans text-[11px] font-medium">
        <span className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 uppercase tracking-wide ${cor.fundo} ${cor.texto}`}>
          <Icon name={ICONE_ETAPA[card.etapa]} size={12} />
          {ROTULO_ETAPA[card.etapa]}
        </span>
        <span className="font-mono text-slate-400">#{card.numero}</span>
        {card.declinadoEm && (
          <span className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 ${DECLINIO.chip}`}>
            <Icon name={DECLINIO.icone} size={11} /> declinado
          </span>
        )}
        {card.arquivadoEm && (
          <span className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-1.5 py-0.5 text-slate-500 dark:bg-slate-800">
            <Icon name="inventory_2" size={11} /> arquivado
          </span>
        )}
        {card.rodada > 1 && <span className="text-slate-400">· rodada {card.rodada}</span>}
        {incompleto && (
          <span
            title={`Saiu do Comitê só com o parecer de ${juntarNomes(incompleto.deram)}; faltou ${juntarNomes(incompleto.faltaram)}.`}
            className="inline-flex items-center gap-1 rounded-md bg-amber-100 px-1.5 py-0.5 text-amber-800 dark:bg-amber-900/40 dark:text-amber-200"
          >
            <Icon name="person" size={11} />
            {incompleto.deram.length} de {incompleto.total} pareceres · {juntarNomes(incompleto.deram.map(nome => nome.split(" ")[0]))}
          </span>
        )}
      </span>
      <span className="leading-tight">{card.cedenteNome}</span>
    </span>
  );

  const subtitulo = (
    <span className="flex flex-wrap items-center gap-2">
      <span className="font-mono">{formatDocumento(card.cedenteCnpj)}</span>
      {card.cedenteCnpj.length === 14 && <NotaDaEquipe cnpj={card.cedenteCnpj} compacto />}
      {card.cedentePraca && (
        <span className="inline-flex items-center gap-1">
          <Icon name="map" size={11} /> {card.cedentePraca}
        </span>
      )}
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
              sacadosTravados={ETAPAS_DECIDIDAS.includes(card.etapa)}
              inicial={{
                cedente: { cnpj: card.cedenteCnpj, nome: card.cedenteNome, cadastrado: card.cedenteCadastrado },
                tipoOperacao: card.tipoOperacao,
                empresa: card.empresa,
                valor: card.valor,
                taxa: card.taxa,
                tac: card.tac,
                tranche: card.tranche,
                prazo: card.prazo,
                parecerOrigem: card.parecerOrigem,
                posicaoOrigem: card.posicaoOrigem,
                sacados: data.sacados,
                proposta: card.proposta,
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
                <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                  <Dado rotulo="Valor">
                    <span className="font-display text-xl font-semibold tracking-tight">{formatMoeda(card.valor)}</span>
                  </Dado>
                  <Dado rotulo="Tipo">{card.tipoOperacao ?? "—"}</Dado>
                  <Dado rotulo="Empresa">{card.empresa ?? "—"}</Dado>
                  <Dado rotulo="Taxa">
                    <span className="font-display text-xl font-semibold tabular-nums tracking-tight">{formatTaxa(card.taxa)}</span>
                  </Dado>
                  <Dado rotulo="TAC">
                    <span className="font-display text-xl font-semibold tabular-nums tracking-tight">{formatMoeda(card.tac)}</span>
                  </Dado>
                  <Dado rotulo="Tranche">
                    <span className="font-display text-xl font-semibold tabular-nums tracking-tight">{formatMoeda(card.tranche)}</span>
                  </Dado>
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
                  {card.resultado && (
                    <Dado rotulo="Resultado">
                      <span className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-semibold ${COR_RESULTADO[card.resultado]}`}>
                        <Icon name={ICONE_RESULTADO[card.resultado]} size={12} />
                        {ROTULO_RESULTADO[card.resultado]}
                      </span>
                      {card.ressalva && (
                        <span className={`mt-1.5 block rounded-md border px-2 py-1.5 text-xs ${RESSALVA.caixa}`}>
                          <span className="font-semibold">Com ressalva:</span> {card.ressalva}
                        </span>
                      )}
                      {card.resultado === "PARCIAL" && card.valorAprovado != null && (
                        <span className="mt-1 block text-xs tabular-nums text-slate-500">
                          {formatMoeda(card.valorAprovado)} aprovados{card.valor != null && ` de ${formatMoeda(card.valor)}`}
                        </span>
                      )}
                    </Dado>
                  )}
                </dl>
                {card.motivosReprovacao.length > 0 && (
                  <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 px-3.5 py-2.5 text-sm text-rose-950 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-50">
                    <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide text-rose-800 dark:text-rose-300">
                      <Icon name="block" size={13} />
                      Motivos da reprovação
                    </p>
                    <ul className="mt-1.5 flex flex-wrap gap-1.5">
                      {card.motivosReprovacao.map(motivo => (
                        <li
                          key={motivo}
                          className="rounded-full border border-rose-200 bg-white px-2 py-0.5 text-xs font-medium text-rose-800 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-200"
                        >
                          {motivo}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {card.declinadoEm && (
                  <div className={`mt-4 rounded-xl border px-3.5 py-2.5 text-sm ${DECLINIO.caixa}`}>
                    <p className="flex flex-wrap items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide">
                      <Icon name={DECLINIO.icone} size={13} />
                      Declinado pelo cliente
                      {card.declinadoPorNome && <span className="font-medium normal-case tracking-normal">· registrado por {card.declinadoPorNome}</span>}
                      <span className="font-medium normal-case tracking-normal">· {formatDataHora(card.declinadoEm)}</span>
                    </p>
                    <p className="mt-1 leading-relaxed">
                      {card.motivoDeclinio ? <TextoRico texto={card.motivoDeclinio} /> : <span className="text-slate-500">Sem motivo informado.</span>}
                    </p>
                  </div>
                )}
                {card.motivoDiretoria && (
                  <div className="mt-4 rounded-xl border border-teal-200 bg-teal-50 px-3.5 py-2.5 text-sm text-teal-950 dark:border-teal-900/60 dark:bg-teal-950/30 dark:text-teal-50">
                    <p className="flex flex-wrap items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide text-teal-800 dark:text-teal-300">
                      <Icon name="account_balance" size={13} />
                      Enviado à Diretoria
                      {card.enviadoDiretoriaPorNome && <span className="font-medium normal-case tracking-normal">por {card.enviadoDiretoriaPorNome}</span>}
                      {card.enviadoDiretoriaEm && <span className="font-medium normal-case tracking-normal">· {formatDataHora(card.enviadoDiretoriaEm)}</span>}
                    </p>
                    <p className="mt-1 leading-relaxed">
                      <TextoRico texto={card.motivoDiretoria} />
                    </p>
                  </div>
                )}
                {card.pracaPagamento && (
                  <a
                    href={`/clients/${card.cedenteCnpj}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-4 flex items-start gap-2.5 rounded-xl border border-rose-200 bg-rose-50 px-3.5 py-2.5 text-sm text-rose-900
                      transition-colors hover:bg-rose-100 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-100 dark:hover:bg-rose-950/50"
                  >
                    <Icon name="alt_route" size={17} className="mt-0.5 shrink-0" />
                    <span className="min-w-0 flex-1">
                      <strong className="font-semibold">Praça de Pagamento:</strong> o cedente tem{" "}
                      <strong className="tabular-nums">{card.pracaPagamento.titulos}</strong> título{card.pracaPagamento.titulos === 1 ? "" : "s"} apontado
                      {card.pracaPagamento.titulos === 1 ? "" : "s"}, somando{" "}
                      <strong className="tabular-nums">{formatMoeda(card.pracaPagamento.valor)}</strong>.
                      <span className="block text-xs text-rose-700/80 dark:text-rose-200/80">Detalhes na página da empresa ↗</span>
                    </span>
                  </a>
                )}
              </Secao>

              {card.proposta && (
                <Secao
                  titulo="Análise de risco"
                  icone="analytics"
                  acao={<span className="text-[11px] text-slate-400">{rotuloProposta(card.proposta)}</span>}
                >
                  <ResumoProposta proposta={card.proposta} />
                </Secao>
              )}

              <Secao titulo={`Sacados (${data.sacados.length})`} icone="people">
                <SacadosLista
                  cardId={card.id}
                  sacados={data.sacados}
                  valorOperacao={card.valor}
                  podeOperador={ehAnalista && (card.etapa === "ORIGEM" || card.etapa === "COMITE" || card.etapa === "PENDENCIA")}
                  podeComite={ehAnalista && (card.etapa === "COMITE" || card.etapa === "PENDENCIA")}
                  // O servidor diz quem decide na Diretoria; o D leva ao mesmo aprovar/reprovar do "Mover para".
                  onDecidirCard={
                    card.etapa === "DIRETORIA" && card.destinos.some(destino => destino.etapa === "APROVADO" && destino.permitido)
                      ? para => onMover(card, para)
                      : undefined
                  }
                  quadro={ETAPAS_DECIDIDAS.includes(card.etapa) ? null : "COMITE"}
                />
              </Secao>

              <Secao
                titulo="Parecer da origem"
                icone="description"
                acao={
                  card.posicaoOrigem && (
                    <span className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-medium ${COR_POSICAO[card.posicaoOrigem]}`}>
                      <Icon name={ICONE_POSICAO[card.posicaoOrigem]} size={11} />
                      {ROTULO_POSICAO[card.posicaoOrigem]}
                    </span>
                  )
                }
              >
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

              <Secao titulo={`Anexos${data.anexos.length ? ` (${data.anexos.length})` : ""}`} icone="attach_file">
                <Anexos cardId={card.id} anexos={data.anexos} />
              </Secao>

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
                      {rotuloMover(card.etapa, destino.etapa)}
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
              {card.etapa !== "FINALIZADO" && card.podeEditar && (
                <button
                  type="button"
                  onClick={() => setDeclinando(true)}
                  className="flex min-h-10 w-full cursor-pointer items-center gap-2 rounded-lg border border-dashed border-slate-300 px-3 text-left
                    text-sm font-medium text-slate-600 transition-colors hover:border-slate-400 hover:bg-white focus:outline-none
                    focus-visible:ring-2 focus-visible:ring-[#612035] dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-800"
                >
                  <Icon name={DECLINIO.icone} size={15} />
                  Cliente declinou
                  <span className="ml-auto text-[11px] font-normal text-slate-400">vai p/ Finalizados</span>
                </button>
              )}
            </div>
            {card.etapa === "FINALIZADO" && ehAnalista && (
              <button
                type="button"
                disabled={arquivar.isPending}
                onClick={() => arquivar.mutate({ id: card.id, arquivado: !card.arquivadoEm }, { onSuccess: () => !card.arquivadoEm && onFechar() })}
                className="mt-1.5 flex min-h-10 w-full cursor-pointer items-center gap-2 rounded-lg border border-dashed border-slate-300 px-3 text-left
                  text-sm font-medium text-slate-600 transition-colors hover:border-slate-400 hover:bg-white focus:outline-none focus-visible:ring-2
                  focus-visible:ring-[#612035] disabled:opacity-60 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-800"
              >
                <Icon name={card.arquivadoEm ? "unarchive" : "inventory_2"} size={15} />
                {card.arquivadoEm ? "Desarquivar" : "Arquivar"}
                <span className="ml-auto text-[11px] font-normal text-slate-400">{card.arquivadoEm ? "volta ao quadro" : "sai do quadro"}</span>
              </button>
            )}
            {card.arquivadoEm && (
              <p className="mt-1.5 px-1 text-[11px] text-slate-500">
                Arquivado{card.arquivadoPorNome && <> por {card.arquivadoPorNome}</>} em {formatDataHora(card.arquivadoEm)}
              </p>
            )}
          </div>

          <OrganizacaoCard card={card} euId={euId} ehAnalista={ehAnalista} />

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

      {declinando && <DeclinarDialog card={card} onFechar={() => setDeclinando(false)} />}
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
