"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import MentionTextarea from "@/components/ui/mencao/MentionTextarea";
import TextoRico from "@/components/ui/mencao/TextoRico";
import Avatar from "@/components/liberacao/Avatar";
import { BOTAO_PRIMARIO, BOTAO_SECUNDARIO } from "@/components/liberacao/Dialogo";
import { confirmar, formatDataHora, tempoRelativo } from "@/components/liberacao/formatters";
import { useUsuarioAtual } from "@/hooks/useLiberacao";
import {
  useApagarComentarioProspeccao,
  useComentarProspeccao,
  useEditarComentarioProspeccao,
  useRegistrarEvento,
} from "@/hooks/useProspeccao";
import {
  ROTULO_CANAL,
  ROTULO_ESTAGIO,
  type CanalContato,
  type ComentarioProspeccao,
  type ProspeccaoEvento,
  type TipoEvento,
} from "@/types/prospeccao";

const ICONE_EVENTO: Record<TipoEvento, string> = {
  CRIACAO: "add_circle",
  TRANSICAO: "arrow_forward",
  COBRANCA: "campaign",
  NOTA: "sticky_note_2",
  DOC_RECEBIDO: "inbox",
  DOC_VALIDADO: "check_circle",
  DOC_REJEITADO: "cancel",
  DOC_DISPENSADO: "remove_circle",
  ARQUIVO_ENVIADO: "attach_file",
  ARQUIVO_REMOVIDO: "delete",
  ANALISTA_ALTERADO: "person",
  REABERTURA: "restart_alt",
  DADOS_COMERCIAIS: "storefront",
  DECISAO: "gavel",
  ACEITE: "handshake",
  PENDENCIA_ABERTA: "hourglass_top",
  PENDENCIA_RESPONDIDA: "reply",
  PENDENCIA_CANCELADA: "cancel",
};

const TITULO_EVENTO: Partial<Record<TipoEvento, string>> = {
  PENDENCIA_ABERTA: "Pendência aberta",
  PENDENCIA_RESPONDIDA: "Pendência respondida",
  PENDENCIA_CANCELADA: "Pendência cancelada",
};

/**
 * Canais que o time usa de fato.
 *
 * <p>Ligação e reunião existem no domínio e continuam legíveis no histórico antigo, mas não são
 * oferecidos: o departamento de cadastro respondeu que cobra por e-mail e WhatsApp.</p>
 */
const CANAIS: CanalContato[] = ["EMAIL", "WHATSAPP"];

function descricao(evento: ProspeccaoEvento) {
  if (evento.tipo === "TRANSICAO" && evento.estagioDe && evento.estagioPara) {
    return `${ROTULO_ESTAGIO[evento.estagioDe]} → ${ROTULO_ESTAGIO[evento.estagioPara]}`;
  }
  if (evento.canal && evento.canal !== "SISTEMA") {
    return `Cobrança por ${ROTULO_CANAL[evento.canal]}`;
  }
  return TITULO_EVENTO[evento.tipo] ?? null;
}

function iniciais(nome: string) {
  return nome.split(/\s+/).filter(Boolean).slice(0, 2).map(parte => parte[0]?.toUpperCase() ?? "").join("");
}

type Item =
  | { tipo: "comentario"; data: string; comentario: ComentarioProspeccao }
  | { tipo: "evento"; data: string; evento: ProspeccaoEvento };

/**
 * Atividade do card: conversa da equipe e histórico numa linha só, como na esteira de liberação.
 *
 * <p>São dois jeitos de escrever. <b>Comentário</b> é conversa interna, com {@code @} para marcar
 * quem precisa ver — avisa no sino. <b>Cobrança</b> registra contato com o cliente por e-mail ou
 * WhatsApp e zera o contador de silêncio. A antiga "nota interna" virou comentário.</p>
 */
export default function ProspeccaoTimeline({
  cardId,
  eventos,
  comentarios,
}: {
  cardId: string;
  eventos: ProspeccaoEvento[];
  comentarios: ComentarioProspeccao[];
}) {
  const { data: eu } = useUsuarioAtual();
  const comentar = useComentarProspeccao();
  const registrar = useRegistrarEvento();
  const [modo, setModo] = useState<"comentario" | CanalContato>("comentario");
  const [texto, setTexto] = useState("");
  const [filtro, setFiltro] = useState<"tudo" | "comentarios">("tudo");
  const [todos, setTodos] = useState(false);

  const itens: Item[] = [
    ...comentarios.map(comentario => ({ tipo: "comentario" as const, data: comentario.criadoEm, comentario })),
    ...(filtro === "tudo" ? eventos.map(evento => ({ tipo: "evento" as const, data: evento.criadoEm, evento })) : []),
  ].sort((a, b) => b.data.localeCompare(a.data));
  const visiveis = todos ? itens : itens.slice(0, 15);

  const enviando = comentar.isPending || registrar.isPending;

  function enviar() {
    if (!texto.trim() || enviando) return;
    const limpar = { onSuccess: () => setTexto("") };
    if (modo === "comentario") comentar.mutate({ id: cardId, texto }, limpar);
    else registrar.mutate({ id: cardId, canal: modo, texto: texto.trim() }, limpar);
  }

  return (
    <div>
      <div className="space-y-2">
        <div className="flex gap-1" role="tablist" aria-label="Tipo de registro">
          {(["comentario", ...CANAIS] as const).map(opcao => (
            <button
              key={opcao}
              type="button"
              role="tab"
              aria-selected={modo === opcao}
              onClick={() => setModo(opcao)}
              title={opcao === "comentario" ? "Conversa da equipe, avisa no sino" : `Registrar cobrança ao cliente por ${ROTULO_CANAL[opcao]}`}
              className={`inline-flex min-h-8 cursor-pointer items-center gap-1 rounded-lg px-2.5 text-xs font-medium transition-colors ${
                modo === opcao
                  ? "bg-slate-900 text-white dark:bg-white dark:text-slate-900"
                  : "text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
              }`}
            >
              <Icon name={opcao === "comentario" ? "chat" : "campaign"} size={13} />
              {opcao === "comentario" ? "Comentário" : ROTULO_CANAL[opcao]}
            </button>
          ))}
        </div>
        {modo === "comentario" ? (
          <MentionTextarea
            value={texto}
            onChange={setTexto}
            rows={2}
            placeholder="Escreva um comentário. Use @ para marcar pessoas ou empresas."
            aria-label="Novo comentário"
            onEnviar={enviar}
          />
        ) : (
          <textarea
            value={texto}
            onChange={event => setTexto(event.target.value)}
            rows={2}
            placeholder={`O que foi cobrado do cliente por ${ROTULO_CANAL[modo]}…`}
            aria-label="Registro de cobrança"
            className="w-full resize-y rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-[#612035]
              focus:outline-none focus:ring-2 focus:ring-[#612035]/20 dark:border-slate-700 dark:bg-slate-800 dark:text-white"
          />
        )}
        <div className="flex items-center justify-between gap-2">
          <span className="text-[11px] text-slate-400">
            {modo === "comentario" ? "Ctrl + Enter envia" : "Zera o contador de silêncio do card"}
          </span>
          <button type="button" disabled={!texto.trim() || enviando} onClick={enviar} className={BOTAO_PRIMARIO}>
            {enviando && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />}
            {modo === "comentario" ? "Comentar" : "Registrar cobrança"}
          </button>
        </div>
      </div>

      <div className="mb-3 mt-5 flex gap-1" role="tablist" aria-label="Filtrar atividade">
        {(
          [
            ["tudo", "Tudo"],
            ["comentarios", `Comentários${comentarios.length ? ` (${comentarios.length})` : ""}`],
          ] as const
        ).map(([valor, rotulo]) => (
          <button
            key={valor}
            type="button"
            role="tab"
            aria-selected={filtro === valor}
            onClick={() => setFiltro(valor)}
            className={`min-h-8 cursor-pointer rounded-lg px-2.5 text-xs font-medium transition-colors ${
              filtro === valor ? "bg-slate-200 text-slate-900 dark:bg-slate-700 dark:text-white" : "text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
            }`}
          >
            {rotulo}
          </button>
        ))}
      </div>

      {itens.length === 0 ? (
        <p className="text-sm text-slate-400">{filtro === "comentarios" ? "Nenhum comentário ainda." : "Sem histórico ainda."}</p>
      ) : (
        <ol className="space-y-3">
          {visiveis.map(item =>
            item.tipo === "comentario" ? (
              <ItemComentario key={`c-${item.comentario.id}`} cardId={cardId} comentario={item.comentario} euId={eu?.id} />
            ) : (
              <ItemEvento key={`e-${item.evento.id}`} evento={item.evento} />
            ),
          )}
        </ol>
      )}
      {itens.length > 15 && (
        <button
          type="button"
          onClick={() => setTodos(atual => !atual)}
          className="mt-3 cursor-pointer text-xs font-medium text-[#612035] hover:underline dark:text-[#e8a3b6]"
        >
          {todos ? "mostrar menos" : `ver toda a atividade (${itens.length})`}
        </button>
      )}
    </div>
  );
}

function ItemEvento({ evento }: { evento: ProspeccaoEvento }) {
  const titulo = descricao(evento);
  return (
    <li className="flex gap-2.5">
      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-500 dark:bg-slate-800">
        <Icon name={ICONE_EVENTO[evento.tipo]} size={13} />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-xs text-slate-700 dark:text-slate-200">
          {titulo && <span className="font-medium">{titulo}</span>}
          {titulo && evento.texto && " · "}
          {evento.texto}
        </p>
        <p className="text-[10px] text-slate-400">
          {evento.usuarioNome ?? "Sistema"} · {formatDataHora(evento.criadoEm)}
        </p>
      </div>
    </li>
  );
}

function ItemComentario({ cardId, comentario, euId }: { cardId: string; comentario: ComentarioProspeccao; euId?: string }) {
  const editar = useEditarComentarioProspeccao();
  const apagar = useApagarComentarioProspeccao();
  const [editando, setEditando] = useState(false);
  const [texto, setTexto] = useState(comentario.texto);
  const meu = comentario.autorId != null && comentario.autorId === euId;

  function salvar() {
    if (!texto.trim()) return;
    editar.mutate({ id: cardId, comentarioId: comentario.id, texto }, { onSuccess: () => setEditando(false) });
  }

  return (
    <li className="flex gap-2.5">
      <Avatar nome={comentario.autorNome} iniciais={iniciais(comentario.autorNome)} tamanho="xs" />
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-baseline gap-x-2 text-[13px]">
          <strong className="font-semibold text-slate-900 dark:text-white">{comentario.autorNome}</strong>
          <span className="text-[11px] text-slate-400" title={formatDataHora(comentario.criadoEm)}>
            {tempoRelativo(comentario.criadoEm)}
            {comentario.editadoEm && <span title={`Editado em ${formatDataHora(comentario.editadoEm)}`}> · editado</span>}
          </span>
        </p>
        {editando ? (
          <div className="mt-1 space-y-2">
            <MentionTextarea value={texto} onChange={setTexto} autoFocus aria-label="Editar comentário" onEnviar={salvar} />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setTexto(comentario.texto);
                  setEditando(false);
                }}
                className={BOTAO_SECUNDARIO}
              >
                Cancelar
              </button>
              <button type="button" disabled={!texto.trim() || editar.isPending} onClick={salvar} className={BOTAO_PRIMARIO}>
                Salvar
              </button>
            </div>
          </div>
        ) : (
          <div className="mt-1 rounded-xl rounded-tl-sm border border-slate-200 bg-white px-3 py-2 text-sm leading-relaxed text-slate-800
            dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100">
            <TextoRico texto={comentario.texto} />
          </div>
        )}
        {meu && !editando && (
          <div className="mt-1 flex gap-3 text-[11px]">
            <button type="button" onClick={() => setEditando(true)} className="cursor-pointer text-slate-500 hover:text-slate-800 hover:underline dark:hover:text-slate-200">
              editar
            </button>
            <button
              type="button"
              disabled={apagar.isPending}
              onClick={() => confirmar("Apagar este comentário?") && apagar.mutate({ id: cardId, comentarioId: comentario.id })}
              className="cursor-pointer text-slate-500 hover:text-rose-700 hover:underline"
            >
              apagar
            </button>
          </div>
        )}
      </div>
    </li>
  );
}
