"use client";

import { useRef, useState } from "react";
import Icon from "@/components/ui/Icon";
import {
  ProspeccaoArquivo,
  ProspeccaoDocumento,
  ROTULO_STATUS_DOC,
  StatusDocumento,
} from "@/types/prospeccao";
import { baixarArquivo, useAtualizarDocumento, useDefinirSocio, useEnviarArquivo, useRemoverArquivo } from "@/hooks/useProspeccao";
import { formatBytes, formatDate } from "./formatters";

const CLASSE_STATUS: Record<StatusDocumento, string> = {
  PENDENTE: "bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300",
  RECEBIDO: "bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-300",
  VALIDADO: "bg-emerald-100 text-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300",
  REJEITADO: "bg-red-100 text-red-800 dark:bg-red-950/50 dark:text-red-300",
  NAO_APLICAVEL: "bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400",
  DISPENSADO: "bg-sky-100 text-sky-800 dark:bg-sky-950/50 dark:text-sky-300",
};

/**
 * Ações de conferência.
 *
 * <p>Para item que admite exceção — endividamento, curva ABC, autorização SCR — a ação se chama
 * "Liberar na exceção", porque é isso que o time faz: o cliente legitimamente não tem o documento,
 * ou não aceita assinar, e a casa libera registrando o porquê. Chamar de "dispensar" esconderia
 * que existe uma justificativa obrigatória por trás.</p>
 */
function acoesDe(documento: ProspeccaoDocumento): { status: StatusDocumento; rotulo: string; pedeMotivo?: boolean }[] {
  return [
    { status: "VALIDADO", rotulo: "Validar" },
    { status: "REJEITADO", rotulo: "Rejeitar", pedeMotivo: true },
    {
      status: "DISPENSADO",
      rotulo: documento.admiteExcecao ? "Liberar na exceção" : "Dispensar",
      pedeMotivo: true,
    },
    { status: "PENDENTE", rotulo: "Voltar a pendente" },
  ];
}

function Arquivo({ arquivo, cardId }: { arquivo: ProspeccaoArquivo; cardId: string }) {
  const remover = useRemoverArquivo();
  return (
    <li className="flex items-center gap-2 py-0.5 text-[11px]">
      <Icon name="description" className="text-[13px] text-slate-400" />
      <button
        type="button"
        onClick={() => baixarArquivo(arquivo)}
        className="truncate text-[#2956E0] hover:underline dark:text-sky-400"
      >
        v{arquivo.versao} · {arquivo.nomeOriginal}
      </button>
      <span className="shrink-0 text-slate-400">{formatBytes(arquivo.tamanhoBytes)}</span>
      <button
        type="button"
        onClick={() => remover.mutate({ cardId, arquivoId: arquivo.id })}
        disabled={remover.isPending}
        className="ml-auto shrink-0 text-slate-400 hover:text-red-600"
        title="Remover da esteira (o arquivo continua no compartilhamento)"
      >
        <Icon name="close" className="text-[14px]" />
      </button>
    </li>
  );
}

function ItemDocumento({ documento, cardId }: { documento: ProspeccaoDocumento; cardId: string }) {
  const atualizar = useAtualizarDocumento();
  const enviar = useEnviarArquivo();
  const inputRef = useRef<HTMLInputElement>(null);
  const [observacao, setObservacao] = useState(documento.observacao ?? "");
  const [editandoObs, setEditandoObs] = useState(false);

  function aplicar(status: StatusDocumento, pedeMotivo?: boolean) {
    let motivo: string | undefined;
    if (pedeMotivo) {
      const pergunta =
        status === "REJEITADO"
          ? `Por que rejeitar "${documento.nome}"?`
          : documento.admiteExcecao
            ? `Justifique a liberação de "${documento.nome}" (ex.: cliente não possui, cliente não autoriza):`
            : `Por que dispensar "${documento.nome}"?`;
      const informado = window.prompt(pergunta);
      if (!informado || !informado.trim()) return;
      motivo = informado.trim();
    }
    atualizar.mutate({ cardId, documentoId: documento.id, status, motivo, observacao: observacao || undefined });
  }

  return (
    <li className="rounded-md border border-slate-200 p-2 transition-colors duration-200 ease-out
      hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-700/40">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs font-medium text-slate-800 dark:text-slate-100">
            {documento.nome}
            {documento.obrigatorio && !documento.informativo && (
              <span className="ml-1 text-red-500" title="Obrigatório para fechar a documentação">*</span>
            )}
          </p>
          {documento.informativo && (
            <p className="text-[10px] uppercase tracking-wide text-slate-400">informativo, não trava</p>
          )}
          {documento.admiteExcecao && !documento.informativo && (
            <p className="text-[10px] uppercase tracking-wide text-slate-400">
              obrigatório · liberável com justificativa
            </p>
          )}
          {documento.motivo && (
            <p className="mt-0.5 text-[11px] italic text-slate-500 dark:text-slate-400">{documento.motivo}</p>
          )}
        </div>
        <span className={`shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium ${CLASSE_STATUS[documento.status]}`}>
          {ROTULO_STATUS_DOC[documento.status]}
        </span>
      </div>

      {documento.arquivos.length > 0 && (
        <ul className="mt-1.5 border-t border-slate-100 pt-1 dark:border-slate-700">
          {documento.arquivos.map(arquivo => (
            <Arquivo key={arquivo.id} arquivo={arquivo} cardId={cardId} />
          ))}
        </ul>
      )}

      {editandoObs ? (
        <div className="mt-1.5 flex gap-1">
          <input
            autoFocus
            value={observacao}
            onChange={event => setObservacao(event.target.value)}
            placeholder="ex.: CRC válido, 7 clientes, válida até 2032"
            className="min-w-0 flex-1 rounded border border-slate-300 px-1.5 py-1 text-[11px] dark:border-slate-600 dark:bg-slate-900"
          />
          <button
            type="button"
            onClick={() => {
              atualizar.mutate({ cardId, documentoId: documento.id, status: documento.status, observacao });
              setEditandoObs(false);
            }}
            className="rounded bg-[#612035] px-2 py-1 text-[11px] text-white"
          >
            Salvar
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setEditandoObs(true)}
          className="mt-1 text-left text-[11px] text-slate-500 hover:underline dark:text-slate-400"
        >
          {documento.observacao || "+ observação"}
        </button>
      )}

      <div className="mt-1.5 flex flex-wrap items-center gap-1">
        <input
          ref={inputRef}
          type="file"
          hidden
          onChange={event => {
            const file = event.target.files?.[0];
            if (file) enviar.mutate({ cardId, documentoId: documento.id, file });
            event.target.value = "";
          }}
        />
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={enviar.isPending}
          className="inline-flex items-center gap-1 rounded border border-slate-300 px-1.5 py-0.5 text-[11px]
            text-slate-600 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
        >
          <Icon name={enviar.isPending ? "hourglass_top" : "upload_file"} className="text-[13px]" />
          {enviar.isPending ? "enviando…" : "anexar"}
        </button>
        {acoesDe(documento).filter(acao => acao.status !== documento.status).map(acao => (
          <button
            key={acao.status}
            type="button"
            onClick={() => aplicar(acao.status, acao.pedeMotivo)}
            disabled={atualizar.isPending}
            className="rounded px-1.5 py-0.5 text-[11px] text-slate-600 hover:bg-slate-100 disabled:opacity-50
              dark:text-slate-300 dark:hover:bg-slate-700"
          >
            {acao.rotulo}
          </button>
        ))}
      </div>

      {documento.validadoEm && (
        <p className="mt-1 text-[10px] text-slate-400">validado em {formatDate(documento.validadoEm)}</p>
      )}
    </li>
  );
}

/**
 * Checklist agrupado por empresa e por sócio.
 *
 * <p>O bloco de sócio pode ser desligado sem apagar nada — a planilha registra sócio que saiu da
 * sociedade e sócio que faleceu, e os dois casos precisam continuar visíveis.</p>
 */
export default function DocumentChecklist({
  cardId,
  documentos,
}: {
  cardId: string;
  documentos: ProspeccaoDocumento[];
}) {
  const definirSocio = useDefinirSocio();

  if (documentos.length === 0) {
    return (
      <p className="rounded-md bg-slate-50 p-3 text-xs text-slate-500 dark:bg-slate-900 dark:text-slate-400">
        O checklist é criado quando a análise é aprovada.
      </p>
    );
  }

  const daEmpresa = documentos.filter(documento => documento.escopo === "EMPRESA");
  const socios = Array.from(
    new Map(
      documentos
        .filter(documento => documento.escopo === "SOCIO" && documento.socioNome)
        .map(documento => [documento.socioNome!, documento]),
    ).entries(),
  );

  return (
    <div className="space-y-4">
      <section>
        <h4 className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
          Documentos da empresa
        </h4>
        <ul className="space-y-1.5">
          {daEmpresa.map(documento => (
            <ItemDocumento key={documento.id} documento={documento} cardId={cardId} />
          ))}
        </ul>
      </section>

      {socios.map(([nome, referencia]) => {
        const itens = documentos.filter(documento => documento.socioNome === nome);
        return (
          <section key={nome} className={referencia.socioAtivo ? "" : "opacity-60"}>
            <div className="mb-1.5 flex items-center justify-between gap-2">
              <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                Sócio · {nome}
                {!referencia.socioAtivo && (
                  <span className="ml-1 normal-case text-[11px] font-normal italic">
                    fora do checklist{referencia.socioInativoMotivo ? ` — ${referencia.socioInativoMotivo}` : ""}
                  </span>
                )}
              </h4>
              <button
                type="button"
                onClick={() => {
                  if (referencia.socioAtivo) {
                    const motivo = window.prompt(`Por que ${nome} sai do checklist? (ex.: saiu da sociedade, faleceu)`);
                    if (!motivo || !motivo.trim()) return;
                    definirSocio.mutate({ cardId, socioNome: nome, ativo: false, motivo: motivo.trim() });
                  } else {
                    definirSocio.mutate({ cardId, socioNome: nome, ativo: true });
                  }
                }}
                className="shrink-0 text-[11px] text-slate-500 hover:underline dark:text-slate-400"
              >
                {referencia.socioAtivo ? "tirar do checklist" : "reativar"}
              </button>
            </div>
            <ul className="space-y-1.5">
              {itens.map(documento => (
                <ItemDocumento key={documento.id} documento={documento} cardId={cardId} />
              ))}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
