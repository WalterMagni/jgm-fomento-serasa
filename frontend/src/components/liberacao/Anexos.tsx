"use client";

import { useRef, useState } from "react";
import { toast } from "sonner";
import Icon from "@/components/ui/Icon";
import { baixarAnexo, useAnexar, useRemoverAnexo } from "@/hooks/useLiberacao";
import type { Anexo } from "@/types/liberacao";
import { confirmar, formatDataHora, tempoRelativo } from "./formatters";

const ACEITOS = ".pdf,.jpg,.jpeg,.png,.heic,.xlsx,.xls,.docx,.doc,.txt,.csv";
const LIMITE = 10 * 1024 * 1024;

function tamanho(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function icone(mime: string | null) {
  if (!mime) return "description";
  if (mime.startsWith("image/")) return "image";
  if (mime.includes("sheet") || mime.includes("excel") || mime === "text/csv") return "table_chart";
  return "description";
}

/**
 * Anexos do card. Vão para a pasta do cedente no compartilhamento de rede, como os arquivos da
 * prospecção; aqui só aparece a lista e o download.
 *
 * <p>Qualquer pessoa anexa em qualquer etapa. Dá para arrastar o arquivo para a área ou clicar.</p>
 */
export default function Anexos({ cardId, anexos }: { cardId: string; anexos: Anexo[] }) {
  const anexar = useAnexar();
  const remover = useRemoverAnexo();
  const entrada = useRef<HTMLInputElement>(null);
  const [sobre, setSobre] = useState(false);

  function enviar(arquivos: FileList | null) {
    if (!arquivos) return;
    Array.from(arquivos).forEach(arquivo => {
      if (arquivo.size > LIMITE) {
        toast.error(`${arquivo.name} passa de 10 MB.`);
        return;
      }
      anexar.mutate({ id: cardId, arquivo });
    });
  }

  return (
    <div className="space-y-2">
      {anexos.length > 0 && (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 dark:divide-slate-800 dark:border-slate-700">
          {anexos.map(anexo => (
            <li key={anexo.id} className="flex items-center gap-3 px-3 py-2">
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500 dark:bg-slate-800">
                <Icon name={icone(anexo.mimeType)} size={15} />
              </span>
              <div className="min-w-0 flex-1">
                <button
                  type="button"
                  onClick={() => baixarAnexo(cardId, anexo.id, anexo.nome).catch(erro => toast.error((erro as Error).message))}
                  className="block max-w-full cursor-pointer truncate text-left text-sm font-medium text-slate-800 hover:text-[#612035] hover:underline dark:text-slate-100"
                  title="Baixar"
                >
                  {anexo.nome}
                </button>
                <p className="text-[11px] text-slate-500" title={formatDataHora(anexo.enviadoEm)}>
                  {tamanho(anexo.tamanhoBytes)} · {anexo.enviadoPorNome} · {tempoRelativo(anexo.enviadoEm)}
                </p>
              </div>
              <button
                type="button"
                aria-label={`Baixar ${anexo.nome}`}
                onClick={() => baixarAnexo(cardId, anexo.id, anexo.nome).catch(erro => toast.error((erro as Error).message))}
                className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800"
              >
                <Icon name="download" size={14} />
              </button>
              {anexo.podeRemover && (
                <button
                  type="button"
                  aria-label={`Remover ${anexo.nome}`}
                  disabled={remover.isPending}
                  onClick={() => confirmar(`Remover ${anexo.nome} do card? O arquivo continua na pasta do cedente.`) && remover.mutate({ id: cardId, anexoId: anexo.id })}
                  className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-600 dark:hover:bg-rose-900/30"
                >
                  <Icon name="delete" size={14} />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      <button
        type="button"
        onClick={() => entrada.current?.click()}
        onDragOver={event => {
          event.preventDefault();
          setSobre(true);
        }}
        onDragLeave={() => setSobre(false)}
        onDrop={event => {
          event.preventDefault();
          setSobre(false);
          enviar(event.dataTransfer.files);
        }}
        disabled={anexar.isPending}
        className={`flex min-h-14 w-full cursor-pointer flex-col items-center justify-center gap-0.5 rounded-xl border-2 border-dashed px-4 py-3 text-xs transition-colors ${
          sobre
            ? "border-[#612035] bg-[#612035]/5 text-[#612035] dark:text-[#e8a3b6]"
            : "border-slate-200 text-slate-500 hover:border-slate-300 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800/50"
        }`}
      >
        {anexar.isPending ? (
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-[#612035]" />
        ) : (
          <>
            <span className="inline-flex items-center gap-1 font-medium">
              <Icon name="attach_file" size={14} /> Anexar arquivo
            </span>
            <span className="text-[10.5px] text-slate-400">arraste aqui ou clique · PDF, imagem, Word, Excel · até 10 MB</span>
          </>
        )}
      </button>
      <input
        ref={entrada}
        type="file"
        multiple
        accept={ACEITOS}
        className="hidden"
        onChange={event => {
          enviar(event.target.files);
          event.target.value = "";
        }}
      />
    </div>
  );
}
