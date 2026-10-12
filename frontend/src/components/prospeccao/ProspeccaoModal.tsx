"use client";

import { useState } from "react";
import Link from "next/link";
import Icon from "@/components/ui/Icon";
import Dialogo from "@/components/liberacao/Dialogo";
import { ROTULO_ESTAGIO, ehTerminal, type EstagioProspeccao, type MotivoRecusa, type RespostaCliente } from "@/types/prospeccao";
import {
  useAssumirProspeccao,
  useExcluirProspeccoes,
  useProspeccaoDetalhe,
  useReabrirProspeccao,
  useTransicionarProspeccao,
} from "@/hooks/useProspeccao";
import AceiteDialog from "./AceiteDialog";
import DadosComerciaisAba from "./DadosComerciaisAba";
import DecisaoAba from "./DecisaoAba";
import DecisaoDialog from "./DecisaoDialog";
import DocumentChecklist from "./DocumentChecklist";
import EsteiraTrilha from "./EsteiraTrilha";
import MotivoDialog from "./MotivoDialog";
import PendenciaDialog from "./PendenciaDialog";
import PendenciasBloco from "./PendenciasBloco";
import ProspeccaoTimeline from "./ProspeccaoTimeline";
import ResumoAba from "./ResumoAba";
import { TOTAL_CAMPOS_COMERCIAIS, camposPreenchidos } from "./DadosComerciaisForm";
import { CLASSE_BADGE_SEMAFORO, acaoPropria, confirmarExclusao, ehAvanco, formatCnpj, rotuloAcao, semaforoDe, textoPrazo } from "./formatters";

type Aba = "resumo" | "dados" | "decisao" | "documentos" | "historico";

/** Diálogo de ação própria aberto por cima do card. */
export type AcaoAberta = { tipo: "decidir" } | { tipo: "aceite"; resposta: RespostaCliente };

/** Resposta do cliente que cada destino do aceite representa — para o arraste abrir já marcado. */
export const RESPOSTA_DO_DESTINO: Partial<Record<EstagioProspeccao, RespostaCliente>> = {
  DOCUMENTACAO: "ACEITO",
  RECUSADO_CLIENTE: "RECUSADO",
  EM_ANALISE: "CONTRAPROPOSTA",
};

/** Passagem que pede algo antes de mover: motivo, texto livre ou nada. */
type Pergunta =
  | { destino: EstagioProspeccao; modo: "motivo" }
  | { destino: EstagioProspeccao; modo: "recusa" }
  | { modo: "reabrir" };

const BOTAO_ACAO =
  "inline-flex min-h-9 cursor-pointer items-center gap-1.5 rounded-lg px-3 text-xs font-medium transition-colors " +
  "focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] focus-visible:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50 " +
  "dark:focus-visible:ring-offset-slate-900";
const PRIMARIO = `${BOTAO_ACAO} bg-[#612035] text-white shadow-sm hover:bg-[#4e1a2a]`;
const SECUNDARIO = `${BOTAO_ACAO} border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700`;

/**
 * Detalhe do card.
 *
 * <p>No topo, a barra de ações mostra só o que vale no estágio atual: os avanços em destaque, os
 * retrocessos e saídas ao lado, discretos. Abaixo, abas para o trabalho e, a partir de {@code lg},
 * o histórico fixo na lateral — é ali que a equipe registra cobrança e nota sem trocar de aba.</p>
 */
export default function ProspeccaoModal({
  cardId,
  onFechar,
  acaoInicial = null,
}: {
  cardId: string;
  onFechar: () => void;
  /** Vindo do arraste para um estágio que exige decisão ou resposta do cliente. */
  acaoInicial?: AcaoAberta | null;
}) {
  const { data, isLoading, error } = useProspeccaoDetalhe(cardId);
  const assumir = useAssumirProspeccao();
  const transicionar = useTransicionarProspeccao();
  const reabrir = useReabrirProspeccao();
  const excluir = useExcluirProspeccoes();
  const [aba, setAba] = useState<Aba>("resumo");
  const [pergunta, setPergunta] = useState<Pergunta | null>(null);
  const [acao, setAcao] = useState<AcaoAberta | null>(acaoInicial);
  const [abrindoPendencia, setAbrindoPendencia] = useState(false);

  const card = data?.card;
  const ocupado = transicionar.isPending || assumir.isPending || reabrir.isPending;

  function mover(destino: EstagioProspeccao) {
    if (destino === "REPROVADO" || destino === "INERTE") {
      setPergunta({ destino, modo: "motivo" });
      return;
    }
    if (destino === "RECUSADO_CLIENTE") {
      setPergunta({ destino, modo: "recusa" });
      return;
    }
    transicionar.mutate({ id: cardId, estagio: destino });
  }

  function confirmarPergunta(dados: { motivoRecusa?: MotivoRecusa; observacao?: string }) {
    if (!pergunta) return;
    const fechar = { onSuccess: () => setPergunta(null) };
    if (pergunta.modo === "reabrir") {
      reabrir.mutate({ id: cardId, texto: dados.observacao ?? "" }, fechar);
      return;
    }
    transicionar.mutate({ id: cardId, estagio: pergunta.destino, ...dados }, fechar);
  }

  // Assumir é o avanço de Nova: tem endpoint próprio, que impede duas analistas de pegarem juntas.
  // Decisão e resposta do cliente também: viram um botão só, que abre o diálogo.
  const destinos = card
    ? (data?.destinosPossiveis ?? []).filter(
        destino => !(card.estagio === "NOVA" && destino === "EM_ANALISE") && !acaoPropria(card.estagio, destino),
      )
    : [];
  const avancos = card ? destinos.filter(destino => ehAvanco(card.estagio, destino)) : [];
  const outros = destinos.filter(destino => !avancos.includes(destino));

  const vigente = data?.decisoes.find(decisao => decisao.vigente) ?? null;
  const ultimoAceite = data?.aceites[0];
  const pedidoCliente =
    ultimoAceite && ultimoAceite.resposta === "CONTRAPROPOSTA" && vigente && ultimoAceite.decisaoId === vigente.id
      ? { mensagem: ultimoAceite.mensagem, limiteSugerido: ultimoAceite.limiteSugerido, condicoes: ultimoAceite.condicoesSugeridas }
      : null;
  const propostaAberta = vigente && vigente.resultado === "APROVADO" ? vigente : null;

  const botaoDecidir = card?.estagio === "EM_ANALISE" && (
    <button
      type="button"
      onClick={() => setAcao({ tipo: "decidir" })}
      disabled={ocupado || !data?.podeDecidir}
      title={data?.podeDecidir ? undefined : "Só a analista dona do card decide"}
      className={PRIMARIO}
    >
      <Icon name="gavel" size={15} />
      {pedidoCliente ? "Decidir a contraproposta" : "Decidir"}
    </button>
  );
  const botaoAceite = card?.estagio === "AGUARDANDO_ACEITE" && (
    <button
      type="button"
      onClick={() => setAcao({ tipo: "aceite", resposta: "ACEITO" })}
      disabled={ocupado || !propostaAberta}
      title={propostaAberta ? undefined : "Não há proposta aprovada vigente"}
      className={PRIMARIO}
    >
      <Icon name="handshake" size={15} />
      Registrar resposta do cliente
    </button>
  );

  const titulo = card ? card.razaoSocial : isLoading ? "Carregando…" : "Card da esteira";
  const subtitulo = card && (
    <span className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
      <Link href={`/clients/${card.cnpj}`} className="font-mono text-[#2956E0] hover:underline dark:text-sky-400">
        {formatCnpj(card.cnpj)}
      </Link>
      <span aria-hidden>·</span>
      <span className="font-medium text-slate-700 dark:text-slate-200">{ROTULO_ESTAGIO[card.estagio]}</span>
      <span aria-hidden>·</span>
      <span className={CLASSE_BADGE_SEMAFORO[semaforoDe(card)]}>{textoPrazo(card)}</span>
      {card.pendenciasAbertas > 0 && (
        <span className="rounded bg-[#D1732C]/15 px-1.5 py-px text-[10px] font-semibold uppercase tracking-wide text-[#a8551a] dark:text-[#f0a46b]">
          aguardando comercial
        </span>
      )}
      {card.reaberturas > 0 && (
        <>
          <span aria-hidden>·</span>
          <span>{card.reaberturas}ª reanálise</span>
        </>
      )}
      {card.legado && (
        <span className="rounded bg-amber-100 px-1.5 py-px text-[10px] font-semibold uppercase tracking-wide text-amber-800 dark:bg-amber-900/40 dark:text-amber-200">
          legado
        </span>
      )}
    </span>
  );

  const preenchidos = data ? camposPreenchidos(data.dadosComerciais) : 0;
  const abas: [Aba, string, string?][] = [
    ["resumo", "Resumo", card && card.pendenciasAbertas > 0 ? `${card.pendenciasAbertas} pendente${card.pendenciasAbertas > 1 ? "s" : ""}` : undefined],
    ["dados", "Dados comerciais", preenchidos ? `${preenchidos}/${TOTAL_CAMPOS_COMERCIAIS}` : undefined],
    ["decisao", "Decisão e aceite", data && data.decisoes.length > 0 ? String(data.decisoes.length) : undefined],
    ["documentos", "Documentos", card && card.documentosTotal > 0 ? `${card.documentosResolvidos}/${card.documentosTotal}` : undefined],
    ["historico", "Atividade", data && data.comentarios.length > 0 ? String(data.comentarios.length) : undefined],
  ];

  return (
    <>
      <Dialogo titulo={titulo} subtitulo={subtitulo} onFechar={onFechar} largura="xl" rotulo={card ? `Card de ${card.razaoSocial}` : "Card da esteira"}>
        {error && <p className="p-8 text-center text-sm text-rose-600">{(error as Error).message}</p>}
        {isLoading && <div className="m-5 h-64 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-800" />}

        {card && data && (
          <div className="flex min-h-0 flex-col">
            {/* ------------------------------------------------ trilha e ações */}
            <div className="space-y-3 border-b border-slate-100 px-5 py-4 dark:border-slate-800">
              <EsteiraTrilha estagio={card.estagio} timeline={data.timeline} criadoEm={card.createdAt} />

              <div className="flex flex-wrap items-center gap-2">
                {card.estagio === "NOVA" && (
                  <button type="button" onClick={() => assumir.mutate(cardId)} disabled={ocupado} className={PRIMARIO}>
                    <Icon name="how_to_reg" size={15} />
                    {assumir.isPending ? "Assumindo…" : "Assumir análise"}
                  </button>
                )}
                {botaoDecidir}
                {botaoAceite}
                {card.estagio === "EM_ANALISE" && !data.podeDecidir && (
                  <span className="text-[11px] text-slate-400">
                    {card.analistaNome ? `Quem decide é ${card.analistaNome}` : "Assuma para decidir"}
                  </span>
                )}
                {data.podeDecidir && !ehTerminal(card.estagio) && (
                  <button type="button" onClick={() => setAbrindoPendencia(true)} disabled={ocupado} className={SECUNDARIO}>
                    <Icon name="hourglass_top" size={15} />
                    Abrir pendência
                  </button>
                )}
                {avancos.map(destino => (
                  <button key={destino} type="button" onClick={() => mover(destino)} disabled={ocupado} className={PRIMARIO}>
                    {rotuloAcao(card.estagio, destino)}
                  </button>
                ))}
                {outros.map(destino => (
                  <button key={destino} type="button" onClick={() => mover(destino)} disabled={ocupado} className={SECUNDARIO}>
                    {rotuloAcao(card.estagio, destino)}
                  </button>
                ))}
                {ehTerminal(card.estagio) && (
                  <button type="button" onClick={() => setPergunta({ modo: "reabrir" })} disabled={ocupado} className={SECUNDARIO}>
                    <Icon name="restart_alt" size={15} />
                    Reabrir em Nova
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => {
                    if (!confirmarExclusao([card])) return;
                    excluir.mutate([cardId], { onSuccess: onFechar });
                  }}
                  disabled={excluir.isPending}
                  title="Apagar card criado por engano"
                  className={`${BOTAO_ACAO} ml-auto text-rose-600 hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-950/40`}
                >
                  <Icon name="delete" size={15} />
                  {excluir.isPending ? "Apagando…" : "Apagar"}
                </button>
              </div>
            </div>

            {/* ------------------------------------------------ abas + histórico */}
            <div className="grid min-h-0 lg:grid-cols-[minmax(0,1fr)_22rem]">
              <div className="min-w-0">
                <nav className="flex gap-1 overflow-x-auto border-b border-slate-100 px-4 dark:border-slate-800" aria-label="Seções do card">
                  {abas.map(([chave, rotulo, contador]) => (
                    <button
                      key={chave}
                      type="button"
                      onClick={() => setAba(chave)}
                      aria-current={aba === chave ? "page" : undefined}
                      className={`-mb-px inline-flex min-h-11 shrink-0 cursor-pointer items-center gap-1.5 border-b-2 px-2.5 text-sm font-medium transition-colors
                        ${chave === "historico" ? "lg:hidden" : ""} ${
                          aba === chave
                            ? "border-[#612035] text-[#612035] dark:border-[#e8a3b6] dark:text-[#e8a3b6]"
                            : "border-transparent text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200"
                        }`}
                    >
                      {rotulo}
                      {contador && <span className="rounded-full bg-slate-100 px-1.5 text-[10px] tabular-nums text-slate-500 dark:bg-slate-800">{contador}</span>}
                    </button>
                  ))}
                </nav>
                <div className="p-5">
                  {aba === "resumo" && (
                    <div className="space-y-3">
                      <PendenciasBloco
                        cardId={cardId}
                        pendencias={data.pendencias}
                        documentos={data.documentos}
                        podeCancelar={data.podeDecidir}
                      />
                      <ResumoAba detalhe={data} onIrPara={setAba} />
                    </div>
                  )}
                  {aba === "decisao" && (
                    <DecisaoAba
                      decisoes={data.decisoes}
                      aceites={data.aceites}
                      legado={card.legado}
                      emRevisao={card.estagio === "EM_ANALISE"}
                      acao={botaoDecidir || botaoAceite || undefined}
                    />
                  )}
                  {aba === "dados" && (
                    <DadosComerciaisAba
                      cardId={cardId}
                      dados={data.dadosComerciais}
                      somenteLeitura={ehTerminal(card.estagio)}
                      exigidos={new Set(data.pendencias.filter(p => p.status === "ABERTA").flatMap(p => p.camposExigidos))}
                    />
                  )}
                  {aba === "documentos" && <DocumentChecklist cardId={cardId} documentos={data.documentos} />}
                  {aba === "historico" && <ProspeccaoTimeline cardId={cardId} eventos={data.timeline} comentarios={data.comentarios} />}
                </div>
              </div>
              <aside className="hidden border-l border-slate-100 p-5 lg:block dark:border-slate-800" aria-label="Histórico">
                <h3 className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">Atividade</h3>
                <ProspeccaoTimeline cardId={cardId} eventos={data.timeline} comentarios={data.comentarios} />
              </aside>
            </div>
          </div>
        )}
      </Dialogo>

      {card && acao?.tipo === "decidir" && (
        <DecisaoDialog
          cardId={cardId}
          razaoSocial={card.razaoSocial}
          anterior={vigente}
          pedidoCliente={pedidoCliente}
          onFechar={() => setAcao(null)}
        />
      )}
      {card && acao?.tipo === "aceite" && propostaAberta && (
        <AceiteDialog
          cardId={cardId}
          razaoSocial={card.razaoSocial}
          proposta={propostaAberta}
          inicial={acao.resposta}
          onFechar={() => setAcao(null)}
        />
      )}

      {card && data && abrindoPendencia && (
        <PendenciaDialog
          cardId={cardId}
          razaoSocial={card.razaoSocial}
          documentos={data.documentos}
          onFechar={() => setAbrindoPendencia(false)}
        />
      )}

      {pergunta && (
        <MotivoDialog
          destino={pergunta.modo === "reabrir" ? "NOVA" : pergunta.destino}
          modo={
            pergunta.modo === "motivo"
              ? { tipo: "motivo" }
              : pergunta.modo === "recusa"
                ? { tipo: "texto", titulo: "O cliente recusou", rotulo: "O que o cliente disse? (opcional)", obrigatorio: false, botao: "Registrar recusa" }
                : { tipo: "texto", titulo: "Reabrir o card", rotulo: "Por que reabrir?", obrigatorio: true, botao: "Reabrir em Nova" }
          }
          onConfirmar={confirmarPergunta}
          onFechar={() => setPergunta(null)}
          enviando={transicionar.isPending || reabrir.isPending}
        />
      )}
    </>
  );
}
