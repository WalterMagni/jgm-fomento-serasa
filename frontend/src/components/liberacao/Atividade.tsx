"use client";

import { useState, type ReactNode } from "react";
import Icon from "@/components/ui/Icon";
import MentionTextarea from "@/components/ui/mencao/MentionTextarea";
import TextoRico from "@/components/ui/mencao/TextoRico";
import { useApagarComentario, useComentar, useEditarComentario } from "@/hooks/useLiberacao";
import { ROTULO_ETAPA_CURTO, type Comentario, type EventoLiberacao } from "@/types/liberacao";
import Avatar from "./Avatar";
import { BOTAO_PRIMARIO, BOTAO_SECUNDARIO } from "./Dialogo";
import { confirmar, formatDataHora, tempoRelativo } from "./formatters";

const ROTULO_CAMPO: Record<string, string> = {
  cedente: "o cedente",
  tipoOperacao: "o tipo",
  valor: "o valor",
  prazo: "o prazo",
  parecerOrigem: "o parecer da origem",
  sacados: "os sacados",
};

const ICONE_EVENTO: Record<EventoLiberacao["tipo"], string> = {
  CRIACAO: "add_circle",
  EDICAO: "edit",
  TRANSICAO: "arrow_forward",
  REABERTURA: "restart_alt",
  PARECER: "gavel",
  PENDENCIA_ABERTA: "hourglass_top",
  PENDENCIA_RESPONDIDA: "reply",
  EXCLUSAO: "delete",
};

function descrever(evento: EventoLiberacao): ReactNode {
  switch (evento.tipo) {
    case "CRIACAO":
      return "criou o card";
    case "EDICAO":
      if (evento.campo === "parecerOrigem") return "editou o parecer da origem";
      if (evento.campo === "sacados") return <>alterou os sacados: <span className="text-slate-500">{evento.texto}</span></>;
      return (
        <>
          alterou {ROTULO_CAMPO[evento.campo ?? ""] ?? evento.campo}:{" "}
          <span className="text-slate-400 line-through">{evento.valorAntes}</span> →{" "}
          <strong className="font-semibold">{evento.valorDepois}</strong>
        </>
      );
    case "TRANSICAO":
      return (
        <>
          moveu de <strong className="font-semibold">{evento.etapaDe ? ROTULO_ETAPA_CURTO[evento.etapaDe] : "?"}</strong> para{" "}
          <strong className="font-semibold">{evento.etapaPara ? ROTULO_ETAPA_CURTO[evento.etapaPara] : "?"}</strong>
        </>
      );
    case "REABERTURA":
      return <>reabriu o card no <strong className="font-semibold">Comitê</strong></>;
    case "PARECER":
      return (
        <>
          {evento.texto === "Parecer revisto" ? "reviu o parecer" : "deu parecer"}:{" "}
          <strong className="font-semibold">{evento.valorDepois}</strong>
        </>
      );
    case "PENDENCIA_ABERTA":
    case "PENDENCIA_RESPONDIDA":
      return <TextoRico texto={evento.texto} />;
    case "EXCLUSAO":
      return "apagou o card";
  }
}

function ItemEvento({ evento }: { evento: EventoLiberacao }) {
  return (
    <li className="relative flex gap-3">
      <span className="relative z-[1] flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-white text-slate-500 ring-1 ring-slate-200
        dark:bg-slate-900 dark:ring-slate-700">
        <Icon name={ICONE_EVENTO[evento.tipo]} size={12} />
      </span>
      <div className="min-w-0 flex-1 pt-0.5 text-[13px] leading-snug text-slate-700 dark:text-slate-300">
        <strong className="font-semibold text-slate-900 dark:text-white">{evento.usuarioNome}</strong> {descrever(evento)}
        {(evento.tipo === "TRANSICAO" || evento.tipo === "REABERTURA") && evento.texto && (
          <p className="mt-1 rounded-lg bg-slate-50 px-2.5 py-1.5 text-xs text-slate-600 dark:bg-slate-800 dark:text-slate-300">
            <TextoRico texto={evento.texto} />
          </p>
        )}
        <p className="mt-0.5 text-[11px] text-slate-400">{formatDataHora(evento.criadoEm)}</p>
      </div>
    </li>
  );
}

function ItemComentario({ cardId, comentario, euId }: { cardId: string; comentario: Comentario; euId?: string }) {
  const editar = useEditarComentario();
  const apagar = useApagarComentario();
  const [editando, setEditando] = useState(false);
  const [texto, setTexto] = useState(comentario.texto);
  const meu = comentario.autorId === euId;

  return (
    <li className="relative flex gap-3">
      <Avatar nome={comentario.autorNome} iniciais={comentario.iniciais} tamanho="xs" className="relative z-[1]" />
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
            <MentionTextarea
              value={texto}
              onChange={setTexto}
              autoFocus
              aria-label="Editar comentário"
              onEnviar={() => texto.trim() && editar.mutate({ id: cardId, comentarioId: comentario.id, texto }, { onSuccess: () => setEditando(false) })}
            />
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
              <button
                type="button"
                disabled={!texto.trim() || editar.isPending}
                onClick={() => editar.mutate({ id: cardId, comentarioId: comentario.id, texto }, { onSuccess: () => setEditando(false) })}
                className={BOTAO_PRIMARIO}
              >
                Salvar
              </button>
            </div>
          </div>
        ) : (
          <div className="mt-1 rounded-xl rounded-tl-sm border border-slate-200 bg-white px-3 py-2 text-sm leading-relaxed text-slate-800
            shadow-[0_1px_2px_rgba(15,23,42,0.04)] dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100">
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

type Item = { tipo: "comentario"; data: string; comentario: Comentario } | { tipo: "evento"; data: string; evento: EventoLiberacao };

/**
 * Comentários e histórico numa linha do tempo, como no Trello.
 *
 * <p>Ficam em tabelas separadas no banco — comentário é editável, evento não — e se juntam só
 * aqui. O filtro "Comentários" esconde os eventos automáticos para quem só quer a conversa.</p>
 */
export default function Atividade({
  cardId,
  eventos,
  comentarios,
  euId,
}: {
  cardId: string;
  eventos: EventoLiberacao[];
  comentarios: Comentario[];
  euId?: string;
}) {
  const comentar = useComentar();
  const [texto, setTexto] = useState("");
  const [filtro, setFiltro] = useState<"tudo" | "comentarios">("tudo");
  const [todos, setTodos] = useState(false);

  const itens: Item[] = [
    ...comentarios.map(comentario => ({ tipo: "comentario" as const, data: comentario.criadoEm, comentario })),
    ...(filtro === "tudo" ? eventos.map(evento => ({ tipo: "evento" as const, data: evento.criadoEm, evento })) : []),
  ].sort((a, b) => b.data.localeCompare(a.data));
  const visiveis = todos ? itens : itens.slice(0, 12);

  function enviar() {
    if (!texto.trim()) return;
    comentar.mutate({ id: cardId, texto }, { onSuccess: () => setTexto("") });
  }

  return (
    <div>
      <div className="space-y-2">
        <MentionTextarea
          value={texto}
          onChange={setTexto}
          rows={2}
          placeholder="Escreva um comentário. Use @ para marcar pessoas ou empresas."
          aria-label="Novo comentário"
          onEnviar={enviar}
        />
        <div className="flex items-center justify-between gap-2">
          <span className="hidden text-[11px] text-slate-400 sm:inline">Ctrl + Enter envia</span>
          <button type="button" disabled={!texto.trim() || comentar.isPending} onClick={enviar} className={`${BOTAO_PRIMARIO} ml-auto`}>
            {comentar.isPending && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />}
            Comentar
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
              filtro === valor
                ? "bg-slate-900 text-white dark:bg-white dark:text-slate-900"
                : "text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
            }`}
          >
            {rotulo}
          </button>
        ))}
      </div>

      {itens.length === 0 ? (
        <p className="text-sm text-slate-400">Nenhum comentário ainda.</p>
      ) : (
        <ol className="relative space-y-4 before:absolute before:bottom-2 before:left-[11px] before:top-2 before:w-px before:bg-slate-200 dark:before:bg-slate-700">
          {visiveis.map(item =>
            item.tipo === "comentario" ? (
              <ItemComentario key={`c-${item.comentario.id}`} cardId={cardId} comentario={item.comentario} euId={euId} />
            ) : (
              <ItemEvento key={`e-${item.evento.id}`} evento={item.evento} />
            ),
          )}
        </ol>
      )}
      {itens.length > 12 && (
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
