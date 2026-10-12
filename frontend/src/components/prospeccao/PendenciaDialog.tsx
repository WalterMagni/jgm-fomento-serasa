"use client";

import { useState } from "react";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, ROTULO } from "@/components/liberacao/Dialogo";
import MentionTextarea from "@/components/ui/mencao/MentionTextarea";
import { useAbrirPendencia } from "@/hooks/useProspeccao";
import { ROTULO_CAMPO_COMERCIAL, ROTULO_STATUS_DOC, type DadosComerciais, type ProspeccaoDocumento } from "@/types/prospeccao";

const CAMPOS = Object.keys(ROTULO_CAMPO_COMERCIAL) as (keyof DadosComerciais)[];

/**
 * Pedido ao comercial: o que falta, em texto, e opcionalmente quais campos comerciais e quais
 * documentos do checklist. Enquanto a pendência estiver aberta, o prazo da análise para.
 */
export default function PendenciaDialog({
  cardId,
  razaoSocial,
  documentos,
  onFechar,
}: {
  cardId: string;
  razaoSocial: string;
  documentos: ProspeccaoDocumento[];
  onFechar: () => void;
}) {
  const abrir = useAbrirPendencia();
  const [pergunta, setPergunta] = useState("");
  const [campos, setCampos] = useState<Set<string>>(new Set());
  const [docs, setDocs] = useState<Set<string>>(new Set());

  // Só faz sentido pedir o que ainda não está resolvido.
  const pediveis = documentos.filter(
    doc => doc.socioAtivo && (doc.status === "PENDENTE" || doc.status === "REJEITADO" || doc.status === "RECEBIDO"),
  );

  function alternar(conjunto: Set<string>, valor: string, mudar: (s: Set<string>) => void) {
    const proximo = new Set(conjunto);
    if (proximo.has(valor)) proximo.delete(valor);
    else proximo.add(valor);
    mudar(proximo);
  }

  function enviar() {
    if (!pergunta.trim() || abrir.isPending) return;
    abrir.mutate(
      { id: cardId, pendencia: { pergunta, camposExigidos: [...campos], documentosPedidos: [...docs] } },
      { onSuccess: onFechar },
    );
  }

  return (
    <Dialogo
      titulo="Abrir pendência"
      subtitulo={`${razaoSocial} · o prazo da análise para até a resposta`}
      onFechar={onFechar}
      largura="lg"
      rodape={
        <>
          <button type="button" onClick={onFechar} className={BOTAO_SECUNDARIO}>Cancelar</button>
          <button type="button" onClick={enviar} disabled={!pergunta.trim() || abrir.isPending} className={BOTAO_PRIMARIO}>
            {abrir.isPending ? "Abrindo…" : "Abrir pendência"}
          </button>
        </>
      }
    >
      <div className="space-y-5 px-5 py-4">
        <div>
          <span className={ROTULO}>O que precisa vir do comercial</span>
          <MentionTextarea
            value={pergunta}
            onChange={setPergunta}
            rows={3}
            autoFocus
            placeholder="Ex.: faturamento dos últimos 12 meses assinado pelo contador. Use @ para marcar alguém."
            aria-label="Pergunta da pendência"
            onEnviar={enviar}
          />
        </div>

        <fieldset>
          <legend className={ROTULO}>Dados comerciais exigidos (opcional)</legend>
          <div className="grid gap-1 sm:grid-cols-2">
            {CAMPOS.map(campo => (
              <label key={campo} className="flex min-h-8 cursor-pointer items-center gap-2 rounded-lg px-2 text-sm text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800">
                <input type="checkbox" checked={campos.has(campo)} onChange={() => alternar(campos, campo, setCampos)} className="accent-[#612035]" />
                {ROTULO_CAMPO_COMERCIAL[campo]}
              </label>
            ))}
          </div>
        </fieldset>

        {pediveis.length > 0 && (
          <fieldset>
            <legend className={ROTULO}>Documentos pedidos (opcional)</legend>
            <div className="max-h-56 space-y-0.5 overflow-y-auto rounded-lg border border-slate-200 p-1 dark:border-slate-700">
              {pediveis.map(doc => (
                <label key={doc.id} className="flex min-h-8 cursor-pointer items-center gap-2 rounded-md px-2 text-sm text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800">
                  <input type="checkbox" checked={docs.has(doc.id)} onChange={() => alternar(docs, doc.id, setDocs)} className="accent-[#612035]" />
                  <span className="min-w-0 flex-1 truncate">
                    {doc.nome}
                    {doc.socioNome && <span className="text-slate-400"> · {doc.socioNome}</span>}
                  </span>
                  <span className="shrink-0 text-[11px] text-slate-400">{ROTULO_STATUS_DOC[doc.status]}</span>
                </label>
              ))}
            </div>
          </fieldset>
        )}
      </div>
    </Dialogo>
  );
}
