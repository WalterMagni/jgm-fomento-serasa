"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import Dialogo from "@/components/liberacao/Dialogo";
import NotaDaEquipe from "@/components/nota/NotaDaEquipe";
import {
  ROTULO_ESTAGIO,
  ehTerminal,
  type EstagioProspeccao,
  type MotivoRecusa,
  type RespostaCliente,
  type ResultadoComite,
} from "@/types/prospeccao";
import { useUsuarioAtual } from "@/hooks/useLiberacao";
import {
  useAssumirProspeccao,
  useCancelarProspeccao,
  useEnviarComite,
  useExcluirProspeccoes,
  useProspeccaoDetalhe,
  useReabrirProspeccao,
  useTransicionarProspeccao,
} from "@/hooks/useProspeccao";
import AceiteDialog from "./AceiteDialog";
import ComiteAba from "./ComiteAba";
import ContratoAba from "./ContratoAba";
import ResultadoComiteDialog from "./ResultadoComiteDialog";
import DadosComerciaisAba from "./DadosComerciaisAba";
import DecisaoAba from "./DecisaoAba";
import DecisaoDialog from "./DecisaoDialog";
import DocumentChecklist from "./DocumentChecklist";
import EsteiraTrilha from "./EsteiraTrilha";
import MotivoDialog from "./MotivoDialog";
import OrganizacaoProspeccao from "./OrganizacaoProspeccao";
import { COR_ESTAGIO, ICONE_ESTAGIO } from "./estilo";
import PendenciaDialog from "./PendenciaDialog";
import PendenciasBloco from "./PendenciasBloco";
import ProspeccaoTimeline from "./ProspeccaoTimeline";
import ResumoAba from "./ResumoAba";
import { TOTAL_CAMPOS_COMERCIAIS, camposPreenchidos } from "./DadosComerciaisForm";
import { CLASSE_BADGE_SEMAFORO, acaoPropria, cancelavel, confirmarExclusao, ehAvanco, formatCnpj, rotuloAcao, semaforoDe, textoPrazo } from "./formatters";

type Aba = "resumo" | "dados" | "decisao" | "documentos" | "comite" | "contrato" | "historico";

/** Diálogo de ação própria aberto por cima do card, ou a aba onde a ação acontece. */
export type AcaoAberta =
  | { tipo: "decidir" }
  | { tipo: "aceite"; resposta: RespostaCliente }
  | { tipo: "resultado"; resultado: ResultadoComite }
  | { tipo: "contrato" }
  | { tipo: "cancelar" };

/** Resultado do comitê que cada destino representa — para o arraste abrir já marcado. */
export const RESULTADO_DO_DESTINO: Partial<Record<EstagioProspeccao, ResultadoComite>> = {
  AGUARDANDO_ACEITE: "APROVADO",
  EM_ANALISE: "DEVOLVIDO",
  REPROVADO: "REPROVADO",
};

/** Resposta do cliente que cada destino do aceite representa — para o arraste abrir já marcado. */
export const RESPOSTA_DO_DESTINO: Partial<Record<EstagioProspeccao, RespostaCliente>> = {
  CONTRATO: "ACEITO",
  RECUSADO_CLIENTE: "RECUSADO",
  COMITE: "CONTRAPROPOSTA",
};

/** Passagem que pede algo antes de mover: motivo, texto livre ou nada. */
type Pergunta =
  | { destino: EstagioProspeccao; modo: "motivo" }
  | { destino: EstagioProspeccao; modo: "recusa" }
  | { modo: "reabrir" }
  | { modo: "cancelar" };

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
  const enviarComite = useEnviarComite();
  const cancelar = useCancelarProspeccao();
  const { data: eu } = useUsuarioAtual();
  const [aba, setAba] = useState<Aba>(acaoInicial?.tipo === "contrato" ? "contrato" : "resumo");
  // Arrastar para Reprovado depois da aprovação já abre a pergunta do cancelamento.
  const [pergunta, setPergunta] = useState<Pergunta | null>(acaoInicial?.tipo === "cancelar" ? { modo: "cancelar" } : null);
  const [acao, setAcao] = useState<AcaoAberta | null>(
    acaoInicial?.tipo === "contrato" || acaoInicial?.tipo === "cancelar" ? null : acaoInicial,
  );
  const [abrindoPendencia, setAbrindoPendencia] = useState(false);

  const card = data?.card;
  const ocupado = transicionar.isPending || assumir.isPending || reabrir.isPending || enviarComite.isPending || cancelar.isPending;

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
    if (pergunta.modo === "cancelar") {
      if (dados.motivoRecusa) cancelar.mutate({ id: cardId, motivo: dados.motivoRecusa, observacao: dados.observacao }, fechar);
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
  const rodadaVigente = data ? data.pareceres.filter(parecer => parecer.rodada === data.rodadaComite) : [];
  const faltandoParecer = rodadaVigente.filter(parecer => !parecer.posicao).map(parecer => parecer.usuarioNome);
  const algumParecer = rodadaVigente.some(parecer => parecer.posicao);

  const botaoEnviarComite = card && (card.estagio === "DOCUMENTACAO" || card.estagio === "CONTRATO") && (
    <button
      type="button"
      onClick={() => enviarComite.mutate({ id: cardId })}
      disabled={ocupado || !data?.podeDecidir}
      title={data?.podeDecidir ? "Convoca os membros do comitê numa rodada nova" : "Só a analista dona do card envia ao comitê"}
      className={card.estagio === "DOCUMENTACAO" ? PRIMARIO : SECUNDARIO}
    >
      <Icon name="groups" size={15} />
      {card.estagio === "DOCUMENTACAO" ? "Enviar ao comitê" : "Voltar para o comitê"}
    </button>
  );
  const botaoResultado = card?.estagio === "COMITE" && (
    <button
      type="button"
      onClick={() => setAcao({ tipo: "resultado", resultado: "APROVADO" })}
      disabled={ocupado || !data?.podeRegistrarResultado || !algumParecer}
      title={
        !data?.podeRegistrarResultado
          ? "Só a dona do card ou um membro do comitê registra o resultado"
          : !algumParecer
            ? "O comitê ainda não deu nenhum parecer"
            : undefined
      }
      className={PRIMARIO}
    >
      <Icon name="gavel" size={15} />
      Registrar resultado
    </button>
  );
  const botaoConcluir = card?.estagio === "CONTRATO" && (
    <button type="button" onClick={() => setAba("contrato")} disabled={ocupado} className={PRIMARIO}>
      <Icon name="verified" size={15} />
      Concluir contrato
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

  // Mesmo cabeçalho da liberação: estágio e situação em cima, a empresa, e embaixo CNPJ, score,
  // praça e o atalho para a página da empresa.
  const titulo = card ? (
    <span className="flex flex-col gap-1">
      <span className="flex flex-wrap items-center gap-2 font-sans text-[11px] font-medium">
        <span className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 uppercase tracking-wide ${COR_ESTAGIO[card.estagio].fundo} ${COR_ESTAGIO[card.estagio].texto}`}>
          <Icon name={ICONE_ESTAGIO[card.estagio]} size={12} />
          {ROTULO_ESTAGIO[card.estagio]}
        </span>
        <span className={CLASSE_BADGE_SEMAFORO[semaforoDe(card)]}>{textoPrazo(card)}</span>
        {card.pendenciasAbertas > 0 && (
          <span className="rounded-md bg-[#D1732C]/15 px-1.5 py-0.5 uppercase tracking-wide text-[#a8551a] dark:text-[#f0a46b]">
            aguardando comercial
          </span>
        )}
        {card.reaberturas > 0 && <span className="text-slate-400">· {card.reaberturas}ª reanálise</span>}
        {card.legado && (
          <span className="rounded-md bg-amber-100 px-1.5 py-0.5 uppercase tracking-wide text-amber-800 dark:bg-amber-900/40 dark:text-amber-200">
            legado
          </span>
        )}
      </span>
      <span className="leading-tight">{card.razaoSocial}</span>
    </span>
  ) : isLoading ? "Carregando…" : "Card da esteira";
  const subtitulo = card && (
    <span className="flex flex-wrap items-center gap-2">
      <span className="font-mono">{formatCnpj(card.cnpj)}</span>
      <NotaDaEquipe cnpj={card.cnpj} compacto />
      {data?.empresaPraca && (
        <span className="inline-flex items-center gap-1">
          <Icon name="map" size={11} /> {data.empresaPraca}
        </span>
      )}
      <a
        href={`/clients/${card.cnpj}`}
        target="_blank"
        rel="noopener noreferrer"
        className="inline-flex items-center gap-1 font-medium text-[#612035] hover:underline dark:text-[#e8a3b6]"
      >
        página da empresa <Icon name="open_in_new" size={11} />
      </a>
    </span>
  );

  const preenchidos = data ? camposPreenchidos(data.dadosComerciais) : 0;
  const abas: [Aba, string, string?][] = [
    ["resumo", "Resumo", card && card.pendenciasAbertas > 0 ? `${card.pendenciasAbertas} pendente${card.pendenciasAbertas > 1 ? "s" : ""}` : undefined],
    ["dados", "Dados comerciais", preenchidos ? `${preenchidos}/${TOTAL_CAMPOS_COMERCIAIS}` : undefined],
    ["decisao", "Decisão e aceite", data && data.decisoes.length > 0 ? String(data.decisoes.length) : undefined],
    ["documentos", "Documentos", card && card.documentosTotal > 0 ? `${card.documentosResolvidos}/${card.documentosTotal}` : undefined],
    ...((data && (data.pareceres.length > 0 || card?.estagio === "COMITE")
      ? [["comite", "Comitê", rodadaVigente.length ? `${rodadaVigente.filter(p => p.posicao).length}/${rodadaVigente.length}` : undefined]]
      : []) as [Aba, string, string?][]),
    ...((card && (card.estagio === "CONTRATO" || card.estagio === "HABILITADO" || data?.contrato.nome)
      ? [["contrato", "Contrato"]]
      : []) as [Aba, string, string?][]),
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
            <div className="space-y-4 border-b border-slate-100 px-5 py-4 dark:border-slate-800">
              <OrganizacaoProspeccao card={card} admin={eu?.admin ?? false} />
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
                {card.estagio === "DOCUMENTACAO" && botaoEnviarComite}
                {botaoResultado}
                {botaoConcluir}
                {card.estagio === "CONTRATO" && botaoEnviarComite}
                {card.estagio === "APROVADO" && (
                  <span className="inline-flex items-center gap-1.5 rounded-lg bg-lime-50 px-2.5 py-1.5 text-[11.5px] text-lime-800 dark:bg-lime-900/30 dark:text-lime-200">
                    <Icon name="folder_open" size={14} />
                    {card.documentosFaltamAnexar === 0
                      ? "Todos os obrigatórios anexados"
                      : `Com o comercial: faltam ${card.documentosFaltamAnexar} obrigatório${card.documentosFaltamAnexar > 1 ? "s" : ""}. Anexados, o card segue sozinho para a documentação.`}
                  </span>
                )}
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
                {cancelavel(card.estagio) && (
                  <button
                    type="button"
                    onClick={() => setPergunta({ modo: "cancelar" })}
                    disabled={ocupado || !data.podeDecidir}
                    title="O cliente teve um problema que impede de seguir: o card vai para Reprovado, com o motivo"
                    className={`${SECUNDARIO} text-rose-700 dark:text-rose-300`}
                  >
                    <Icon name="block" size={15} />
                    Reprovar
                  </button>
                )}
                {/* Botão travado sem explicação parece defeito: diz quem pode. */}
                {cancelavel(card.estagio) && !data.podeDecidir && (
                  <span className="text-[11px] text-slate-400">
                    Reprova {card.analistaNome ? `quem assumiu (${card.analistaNome})` : "quem assumiu o card"} ou um admin
                  </span>
                )}
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
                  {aba === "comite" && (
                    <ComiteAba
                      cardId={cardId}
                      pareceres={data.pareceres}
                      rodadaVigente={data.rodadaComite}
                      noComite={card.estagio === "COMITE"}
                      convocado={data.convocadoNoComite}
                      euId={eu?.id}
                      acao={botaoResultado || (card.estagio === "DOCUMENTACAO" ? botaoEnviarComite : undefined) || undefined}
                    />
                  )}
                  {aba === "contrato" && (
                    <ContratoAba cardId={cardId} contrato={data.contrato} estagio={card.estagio} podeConcluir={data.podeDecidir} />
                  )}
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
      {card && acao?.tipo === "resultado" && (
        <ResultadoComiteDialog
          cardId={cardId}
          razaoSocial={card.razaoSocial}
          faltando={faltandoParecer}
          inicial={acao.resultado}
          proposta={propostaAberta}
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
          destino={pergunta.modo === "reabrir" ? "NOVA" : pergunta.modo === "cancelar" ? "REPROVADO" : pergunta.destino}
          modo={
            pergunta.modo === "motivo"
              ? { tipo: "motivo" }
              : pergunta.modo === "cancelar"
                ? {
                    tipo: "motivo",
                    titulo: `Reprovar ${card?.razaoSocial ?? "o card"}?`,
                    botao: "Reprovar",
                    aviso: `O card sai de ${card ? ROTULO_ESTAGIO[card.estagio] : "onde está"} e vai para Reprovado. O histórico guarda de onde saiu, e dá para reabrir depois.`,
                  }
              : pergunta.modo === "recusa"
                ? { tipo: "texto", titulo: "O cliente recusou", rotulo: "O que o cliente disse? (opcional)", obrigatorio: false, botao: "Registrar recusa" }
                : { tipo: "texto", titulo: "Reabrir o card", rotulo: "Por que reabrir?", obrigatorio: true, botao: "Reabrir em Nova" }
          }
          onConfirmar={confirmarPergunta}
          onFechar={() => setPergunta(null)}
          enviando={transicionar.isPending || reabrir.isPending || cancelar.isPending}
        />
      )}
    </>
  );
}
