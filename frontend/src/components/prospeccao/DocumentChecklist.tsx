"use client";

import { useRef, useState, type DragEvent } from "react";
import Icon from "@/components/ui/Icon";
import { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO } from "@/components/liberacao/Dialogo";
import { confirmar } from "@/components/liberacao/formatters";
import {
  ProspeccaoArquivo,
  ProspeccaoDocumento,
  ROTULO_STATUS_DOC,
  StatusDocumento,
} from "@/types/prospeccao";
import {
  abrirArquivo,
  baixarArquivo,
  copiarCaminho,
  useAdicionarPessoa,
  useAtualizarDocumento,
  useDefinirSocio,
  useEnviarArquivo,
  useRemoverArquivo,
} from "@/hooks/useProspeccao";
import { formatBytes, formatDate } from "./formatters";

/** Status com cor própria: trilho à esquerda do item e selo no canto. */
const ESTILO_STATUS: Record<StatusDocumento, { trilho: string; selo: string; icone: string }> = {
  PENDENTE: { trilho: "bg-slate-300 dark:bg-slate-600", selo: "bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300", icone: "schedule" },
  RECEBIDO: { trilho: "bg-amber-400", selo: "bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-300", icone: "inbox" },
  VALIDADO: { trilho: "bg-emerald-500", selo: "bg-emerald-100 text-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300", icone: "check_circle" },
  REJEITADO: { trilho: "bg-rose-500", selo: "bg-rose-100 text-rose-800 dark:bg-rose-950/50 dark:text-rose-300", icone: "cancel" },
  NAO_APLICAVEL: { trilho: "bg-slate-200 dark:bg-slate-700", selo: "bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400", icone: "remove_circle" },
  DISPENSADO: { trilho: "bg-sky-400", selo: "bg-sky-100 text-sky-800 dark:bg-sky-950/50 dark:text-sky-300", icone: "remove_circle" },
};

const RESOLVIDOS: StatusDocumento[] = ["VALIDADO", "NAO_APLICAVEL", "DISPENSADO"];

/** Tipo do arquivo pelo MIME ou pela extensão — vira a etiqueta colorida do arquivo. */
function tipoDoArquivo(arquivo: ProspeccaoArquivo): { sigla: string; cor: string } {
  const nome = arquivo.nomeOriginal.toLowerCase();
  const mime = (arquivo.mimeType ?? "").toLowerCase();
  if (mime.includes("pdf") || nome.endsWith(".pdf")) return { sigla: "PDF", cor: "bg-rose-600" };
  if (mime.startsWith("image/")) return { sigla: "IMG", cor: "bg-violet-600" };
  if (mime.includes("sheet") || mime.includes("excel") || /\.(xlsx?|csv)$/.test(nome)) return { sigla: "XLS", cor: "bg-emerald-600" };
  if (mime.includes("word") || /\.docx?$/.test(nome)) return { sigla: "DOC", cor: "bg-[#2956E0]" };
  return { sigla: "ARQ", cor: "bg-slate-500" };
}

const ACAO_ICONE =
  "inline-flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-lg text-slate-500 transition-colors " +
  "hover:bg-slate-100 hover:text-slate-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] " +
  "disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-slate-700 dark:hover:text-slate-100";

function Arquivo({ arquivo, cardId, ultimo }: { arquivo: ProspeccaoArquivo; cardId: string; ultimo: boolean }) {
  const remover = useRemoverArquivo();
  const tipo = tipoDoArquivo(arquivo);

  return (
    <li className={`group flex items-center gap-3 rounded-lg px-2 py-2 transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/60 ${ultimo ? "" : "opacity-70"}`}>
      <span className={`flex h-9 w-8 shrink-0 items-end justify-center rounded-md pb-1 text-[9px] font-bold tracking-wide text-white shadow-sm ${tipo.cor}`}>
        {tipo.sigla}
      </span>
      <div className="min-w-0 flex-1">
        <button
          type="button"
          onClick={() => abrirArquivo(arquivo)}
          className="block max-w-full cursor-pointer truncate text-left text-[13px] font-medium text-slate-800 hover:text-[#2956E0] hover:underline dark:text-slate-100 dark:hover:text-[#9db4ff]"
          title="Abrir numa aba nova"
        >
          {arquivo.nomeOriginal}
        </button>
        <p className="flex flex-wrap items-center gap-x-1.5 text-[10.5px] text-slate-400">
          <span className="rounded bg-slate-100 px-1 font-semibold text-slate-500 dark:bg-slate-700 dark:text-slate-300">v{arquivo.versao}</span>
          {!ultimo && <span>versão anterior</span>}
          <span>{formatBytes(arquivo.tamanhoBytes)}</span>
          <span aria-hidden>·</span>
          <span>{formatDate(arquivo.enviadoEm)}</span>
        </p>
        {arquivo.caminho && (
          <p className="mt-0.5 truncate font-mono text-[10px] text-slate-400" title={arquivo.caminho}>
            {arquivo.caminho}
          </p>
        )}
      </div>
      <div className="flex shrink-0 items-center">
        <button type="button" onClick={() => abrirArquivo(arquivo)} className={ACAO_ICONE} aria-label={`Abrir ${arquivo.nomeOriginal}`} title="Abrir">
          <Icon name="open_in_new" size={15} />
        </button>
        <button type="button" onClick={() => baixarArquivo(arquivo)} className={ACAO_ICONE} aria-label={`Baixar ${arquivo.nomeOriginal}`} title="Baixar">
          <Icon name="download" size={15} />
        </button>
        <button
          type="button"
          onClick={() => copiarCaminho(arquivo.caminho)}
          disabled={!arquivo.caminho}
          className={ACAO_ICONE}
          aria-label="Copiar caminho na rede"
          title="Copiar caminho na rede"
        >
          <Icon name="content_copy" size={15} />
        </button>
        <button
          type="button"
          onClick={() => confirmar(`Tirar "${arquivo.nomeOriginal}" da esteira? O arquivo continua na pasta CLIENTES.`) &&
            remover.mutate({ cardId, arquivoId: arquivo.id })}
          disabled={remover.isPending}
          className={`${ACAO_ICONE} hover:bg-rose-50 hover:text-rose-600 dark:hover:bg-rose-950/40`}
          aria-label={`Remover ${arquivo.nomeOriginal} da esteira`}
          title="Remover da esteira (o arquivo continua no compartilhamento)"
        >
          <Icon name="delete" size={15} />
        </button>
      </div>
    </li>
  );
}

/** Área de envio: arrastar e soltar ou clicar. Encolhe para uma linha quando o item já tem arquivo. */
function Envio({ cardId, documentoId, compacto }: { cardId: string; documentoId: string; compacto: boolean }) {
  const enviar = useEnviarArquivo();
  const entrada = useRef<HTMLInputElement>(null);
  const [sobre, setSobre] = useState(false);

  function receber(arquivos: FileList | null | undefined) {
    const file = arquivos?.[0];
    if (file) enviar.mutate({ cardId, documentoId, file });
  }

  const eventosArraste = {
    onDragOver: (event: DragEvent) => {
      event.preventDefault();
      setSobre(true);
    },
    onDragLeave: () => setSobre(false),
    onDrop: (event: DragEvent) => {
      event.preventDefault();
      setSobre(false);
      receber(event.dataTransfer.files);
    },
  };

  return (
    <>
      <input
        ref={entrada}
        type="file"
        hidden
        accept=".pdf,.jpg,.jpeg,.png,.heic,.doc,.docx,.xls,.xlsx,.csv,.txt"
        onChange={event => {
          receber(event.target.files);
          event.target.value = "";
        }}
      />
      <button
        type="button"
        {...eventosArraste}
        onClick={() => entrada.current?.click()}
        disabled={enviar.isPending}
        className={`flex w-full cursor-pointer items-center gap-3 rounded-lg border border-dashed text-left transition-colors
          focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] disabled:cursor-wait
          ${compacto ? "px-3 py-1.5" : "px-3 py-3"}
          ${sobre
            ? "border-[#612035] bg-[#612035]/5 dark:border-[#e8a3b6] dark:bg-[#e8a3b6]/10"
            : "border-slate-300 hover:border-slate-400 hover:bg-slate-50 dark:border-slate-600 dark:hover:bg-slate-800/60"}`}
      >
        <span className={`flex shrink-0 items-center justify-center rounded-lg ${compacto ? "h-6 w-6" : "h-9 w-9"} ${
          enviar.isPending ? "bg-[#612035]/10 text-[#612035]" : "bg-slate-100 text-slate-500 dark:bg-slate-700 dark:text-slate-300"
        }`}>
          {enviar.isPending ? (
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-[#612035]/30 border-t-[#612035]" />
          ) : (
            <Icon name="cloud_upload" size={compacto ? 13 : 17} />
          )}
        </span>
        <span className="min-w-0">
          <span className="block text-xs font-medium text-slate-700 dark:text-slate-200">
            {enviar.isPending ? "Enviando…" : sobre ? "Solte para anexar" : compacto ? "Enviar nova versão" : "Arraste o arquivo aqui ou clique para escolher"}
          </span>
          {!compacto && <span className="block text-[10.5px] text-slate-400">PDF, imagem, Word ou Excel · até 10 MB</span>}
        </span>
      </button>
    </>
  );
}

function ItemDocumento({ documento, cardId }: { documento: ProspeccaoDocumento; cardId: string }) {
  const atualizar = useAtualizarDocumento();
  const [observacao, setObservacao] = useState(documento.observacao ?? "");
  const [editandoObs, setEditandoObs] = useState(false);
  const [pedindoMotivo, setPedindoMotivo] = useState<StatusDocumento | null>(null);
  const [motivo, setMotivo] = useState("");

  const estilo = ESTILO_STATUS[documento.status];
  const resolvido = RESOLVIDOS.includes(documento.status);
  const temArquivo = documento.arquivos.length > 0;
  const rotuloDispensa = documento.admiteExcecao ? "Liberar na exceção" : "Dispensar";

  function aplicar(status: StatusDocumento, motivoInformado?: string) {
    atualizar.mutate(
      { cardId, documentoId: documento.id, status, motivo: motivoInformado, observacao: observacao || undefined },
      {
        onSuccess: () => {
          setPedindoMotivo(null);
          setMotivo("");
        },
      },
    );
  }

  return (
    <li className="relative overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900/40">
      <span className={`absolute inset-y-0 left-0 w-1 ${estilo.trilho}`} aria-hidden />
      <div className="space-y-2.5 py-3 pl-4 pr-3">
        <header className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-[13px] font-semibold text-slate-900 dark:text-white">
              {documento.nome}
              {documento.obrigatorio && !documento.informativo && (
                <span className="ml-1 text-rose-500" title="Obrigatório para enviar ao comitê">*</span>
              )}
            </p>
            {(documento.informativo || documento.admiteExcecao) && (
              <p className="mt-0.5 text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                {documento.informativo ? "Informativo · não trava" : "Obrigatório · liberável com justificativa"}
              </p>
            )}
            {documento.motivo && <p className="mt-1 text-[11px] italic text-slate-500 dark:text-slate-400">“{documento.motivo}”</p>}
          </div>
          <span className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[10.5px] font-semibold ${estilo.selo}`}>
            <Icon name={estilo.icone} size={11} />
            {ROTULO_STATUS_DOC[documento.status]}
          </span>
        </header>

        {temArquivo && (
          <ul className="-mx-2 space-y-0.5">
            {documento.arquivos.map((arquivo, i) => (
              <Arquivo key={arquivo.id} arquivo={arquivo} cardId={cardId} ultimo={i === 0} />
            ))}
          </ul>
        )}

        {!temArquivo && resolvido && documento.pasta && (
          <div className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-[11px] text-slate-500 dark:bg-slate-800/60 dark:text-slate-400">
            <Icon name="folder_open" size={14} className="shrink-0" />
            <span className="min-w-0 flex-1">
              <span className="block">{documento.status === "VALIDADO" ? "Validado sem arquivo anexado na esteira." : "Sem arquivo."} Pasta do item:</span>
              <span className="block truncate font-mono text-[10px]" title={documento.pasta}>{documento.pasta}</span>
            </span>
            <button type="button" onClick={() => copiarCaminho(documento.pasta)} className={ACAO_ICONE} aria-label="Copiar caminho da pasta" title="Copiar caminho da pasta">
              <Icon name="content_copy" size={14} />
            </button>
          </div>
        )}

        {(!resolvido || temArquivo) && <Envio cardId={cardId} documentoId={documento.id} compacto={temArquivo} />}

        {editandoObs ? (
          <div className="flex gap-2">
            <input
              autoFocus
              value={observacao}
              onChange={event => setObservacao(event.target.value)}
              onKeyDown={event => {
                if (event.key === "Enter") {
                  atualizar.mutate({ cardId, documentoId: documento.id, status: documento.status, observacao });
                  setEditandoObs(false);
                }
              }}
              placeholder="ex.: CRC válido, 7 clientes, válida até 2032"
              className={`${CAMPO} py-1.5 text-xs`}
            />
            <button
              type="button"
              onClick={() => {
                atualizar.mutate({ cardId, documentoId: documento.id, status: documento.status, observacao });
                setEditandoObs(false);
              }}
              className={BOTAO_PRIMARIO}
            >
              Salvar
            </button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setEditandoObs(true)}
            className="inline-flex max-w-full cursor-pointer items-center gap-1.5 text-left text-[11px] text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200"
          >
            <Icon name="edit_note" size={13} className="shrink-0" />
            <span className="truncate">{documento.observacao || "Adicionar observação"}</span>
          </button>
        )}

        {pedindoMotivo ? (
          <div className="space-y-2 rounded-lg bg-slate-50 p-2.5 dark:bg-slate-800/60">
            <label className="block text-[11px] font-medium text-slate-600 dark:text-slate-300">
              {pedindoMotivo === "REJEITADO"
                ? "Por que rejeitar? (vencido, ilegível, documento errado…)"
                : documento.admiteExcecao
                  ? "Justifique a liberação (cliente não possui, não autoriza…)"
                  : "Por que dispensar?"}
              <input autoFocus value={motivo} onChange={event => setMotivo(event.target.value)} className={`${CAMPO} mt-1 py-1.5 text-xs`} />
            </label>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setPedindoMotivo(null)} className={BOTAO_SECUNDARIO}>Cancelar</button>
              <button
                type="button"
                disabled={!motivo.trim() || atualizar.isPending}
                onClick={() => aplicar(pedindoMotivo, motivo.trim())}
                className={BOTAO_PRIMARIO}
              >
                {pedindoMotivo === "REJEITADO" ? "Rejeitar" : rotuloDispensa}
              </button>
            </div>
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-1.5">
            {documento.status !== "VALIDADO" && (
              <button
                type="button"
                onClick={() => aplicar("VALIDADO")}
                disabled={atualizar.isPending}
                className="inline-flex min-h-8 cursor-pointer items-center gap-1 rounded-lg bg-emerald-600 px-3 text-xs font-semibold text-white shadow-sm
                  transition-colors hover:bg-emerald-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-1 disabled:opacity-50"
              >
                <Icon name="check" size={13} strokeWidth={2.5} /> Validar
              </button>
            )}
            {documento.status !== "REJEITADO" && (
              <button
                type="button"
                onClick={() => setPedindoMotivo("REJEITADO")}
                className="inline-flex min-h-8 cursor-pointer items-center rounded-lg border border-rose-200 px-3 text-xs font-medium text-rose-700
                  transition-colors hover:bg-rose-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 dark:border-rose-900/60 dark:text-rose-300 dark:hover:bg-rose-950/40"
              >
                Rejeitar
              </button>
            )}
            {documento.status !== "DISPENSADO" && (
              <button
                type="button"
                onClick={() => setPedindoMotivo("DISPENSADO")}
                className="inline-flex min-h-8 cursor-pointer items-center rounded-lg px-3 text-xs font-medium text-slate-600 transition-colors
                  hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] dark:text-slate-300 dark:hover:bg-slate-800"
              >
                {rotuloDispensa}
              </button>
            )}
            {documento.status !== "PENDENTE" && (
              <button
                type="button"
                onClick={() => aplicar("PENDENTE")}
                disabled={atualizar.isPending}
                className="ml-auto cursor-pointer text-[11px] text-slate-400 hover:text-slate-700 hover:underline dark:hover:text-slate-200"
              >
                voltar a pendente
              </button>
            )}
          </div>
        )}

        {documento.validadoEm && documento.status === "VALIDADO" && (
          <p className="text-[10.5px] text-emerald-700/80 dark:text-emerald-300/80">Validado em {formatDate(documento.validadoEm)}</p>
        )}
      </div>
    </li>
  );
}

function iniciais(nome: string) {
  return nome.split(/\s+/).filter(Boolean).slice(0, 2).map(parte => parte[0]?.toUpperCase() ?? "").join("");
}

/** Cabeçalho de um bloco (empresa ou pessoa) com o progresso daquele bloco. */
function CabecalhoBloco({ titulo, subtitulo, itens, avatar, acao }: {
  titulo: string;
  subtitulo?: React.ReactNode;
  itens: ProspeccaoDocumento[];
  avatar?: string;
  acao?: React.ReactNode;
}) {
  const contaveis = itens.filter(item => !item.informativo && item.socioAtivo);
  const resolvidos = contaveis.filter(item => RESOLVIDOS.includes(item.status)).length;
  return (
    <div className="mb-2 flex items-center gap-3">
      {avatar ? (
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#612035]/10 text-[11px] font-bold text-[#612035] dark:bg-[#612035]/40 dark:text-[#f2c4d1]">
          {avatar}
        </span>
      ) : (
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-teal-600/10 text-teal-700 dark:text-teal-300">
          <Icon name="business" size={16} />
        </span>
      )}
      <div className="min-w-0 flex-1">
        <h4 className="truncate text-sm font-semibold text-slate-900 dark:text-white">{titulo}</h4>
        {subtitulo && <p className="truncate text-[11px] text-slate-500 dark:text-slate-400">{subtitulo}</p>}
      </div>
      <span className="shrink-0 text-[11px] tabular-nums text-slate-500">{resolvidos}/{contaveis.length}</span>
      {acao}
    </div>
  );
}

type Filtro = "todos" | "faltando" | "resolvidos";

/**
 * Checklist agrupado por empresa e por pessoa (sócio ou avalista).
 *
 * <p>O bloco de sócio pode ser desligado sem apagar nada — a planilha registra sócio que saiu da
 * sociedade e sócio que faleceu, e os dois casos precisam continuar visíveis.</p>
 */
export default function DocumentChecklist({ cardId, documentos }: { cardId: string; documentos: ProspeccaoDocumento[] }) {
  const definirSocio = useDefinirSocio();
  const adicionarPessoa = useAdicionarPessoa();
  const [filtro, setFiltro] = useState<Filtro>("todos");
  const [tirando, setTirando] = useState<string | null>(null);
  const [motivoTirar, setMotivoTirar] = useState("");
  const [novoAvalista, setNovoAvalista] = useState<{ nome: string; documento: string } | null>(null);

  if (documentos.length === 0) {
    return (
      <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500 dark:bg-slate-900 dark:text-slate-400">
        Nenhum item no checklist ainda.
      </p>
    );
  }

  const contaveis = documentos.filter(item => !item.informativo && item.socioAtivo);
  const resolvidos = contaveis.filter(item => RESOLVIDOS.includes(item.status)).length;
  const obrigatoriosFaltando = contaveis.filter(item => item.obrigatorio && !RESOLVIDOS.includes(item.status)).length;
  const progresso = contaveis.length ? Math.round((resolvidos / contaveis.length) * 100) : 0;

  const visivel = (item: ProspeccaoDocumento) =>
    filtro === "todos" || (filtro === "resolvidos" ? RESOLVIDOS.includes(item.status) : !RESOLVIDOS.includes(item.status));

  const daEmpresa = documentos.filter(documento => documento.escopo === "EMPRESA");
  const pessoas = Array.from(
    new Map(
      documentos
        .filter(documento => documento.escopo === "SOCIO" && documento.socioNome)
        .map(documento => [documento.socioNome!, documento]),
    ).entries(),
  );

  return (
    <div className="space-y-6">
      {/* ------------------------------------------------------------ resumo */}
      <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-700">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="font-display text-2xl font-semibold leading-none text-slate-900 dark:text-white">
              {resolvidos}<span className="text-base text-slate-400"> / {contaveis.length}</span>
            </p>
            <p className="mt-1 text-xs text-slate-500">
              resolvidos
              {obrigatoriosFaltando > 0 ? (
                <span className="text-rose-600 dark:text-rose-400"> · {obrigatoriosFaltando} obrigatório{obrigatoriosFaltando > 1 ? "s" : ""} faltando</span>
              ) : (
                <span className="text-emerald-600 dark:text-emerald-400"> · obrigatórios completos</span>
              )}
            </p>
          </div>
          <div className="flex gap-1 rounded-lg bg-slate-100 p-1 dark:bg-slate-800" role="tablist" aria-label="Filtrar documentos">
            {([["todos", "Todos"], ["faltando", "Faltando"], ["resolvidos", "Resolvidos"]] as [Filtro, string][]).map(([valor, rotulo]) => (
              <button
                key={valor}
                type="button"
                role="tab"
                aria-selected={filtro === valor}
                onClick={() => setFiltro(valor)}
                className={`min-h-7 cursor-pointer rounded-md px-2.5 text-xs font-medium transition-colors ${
                  filtro === valor ? "bg-white text-slate-900 shadow-sm dark:bg-slate-900 dark:text-white" : "text-slate-500 hover:text-slate-800"
                }`}
              >
                {rotulo}
              </button>
            ))}
          </div>
        </div>
        <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
          <div className="h-full rounded-full bg-teal-600 transition-[width] duration-500" style={{ width: `${progresso}%` }} />
        </div>
      </div>

      {/* ----------------------------------------------------------- empresa */}
      <section>
        <CabecalhoBloco titulo="Documentos da empresa" itens={daEmpresa} />
        <ul className="space-y-2">
          {daEmpresa.filter(visivel).map(documento => (
            <ItemDocumento key={documento.id} documento={documento} cardId={cardId} />
          ))}
        </ul>
      </section>

      {/* ----------------------------------------------------------- pessoas */}
      {pessoas.map(([nome, referencia]) => {
        const itens = documentos.filter(documento => documento.socioNome === nome);
        const papel = referencia.pessoaPapel === "AVALISTA" ? "Avalista" : "Sócio";
        const subtitulo = (
          <>
            {papel}
            {referencia.socioParticipacao != null && ` · ${Number(referencia.socioParticipacao)}% do capital`}
            {!referencia.socioAtivo && ` · fora do checklist${referencia.socioInativoMotivo ? ` — ${referencia.socioInativoMotivo}` : ""}`}
          </>
        );
        return (
          <section key={nome} className={referencia.socioAtivo ? "" : "opacity-60"}>
            <CabecalhoBloco
              titulo={nome}
              subtitulo={subtitulo}
              itens={itens}
              avatar={iniciais(nome)}
              acao={
                <button
                  type="button"
                  onClick={() => (referencia.socioAtivo ? setTirando(nome) : definirSocio.mutate({ cardId, socioNome: nome, ativo: true }))}
                  className="shrink-0 cursor-pointer rounded-md px-2 py-1 text-[11px] text-slate-500 hover:bg-slate-100 hover:text-slate-800 dark:hover:bg-slate-800"
                >
                  {referencia.socioAtivo ? "tirar do checklist" : "reativar"}
                </button>
              }
            />
            {tirando === nome && (
              <div className="mb-2 flex gap-2 rounded-lg bg-slate-50 p-2.5 dark:bg-slate-800/60">
                <input
                  autoFocus
                  value={motivoTirar}
                  onChange={event => setMotivoTirar(event.target.value)}
                  placeholder="Por quê? (faleceu, saiu da sociedade, abaixo de 15%)"
                  className={`${CAMPO} py-1.5 text-xs`}
                />
                <button type="button" onClick={() => setTirando(null)} className={BOTAO_SECUNDARIO}>Cancelar</button>
                <button
                  type="button"
                  disabled={!motivoTirar.trim()}
                  onClick={() =>
                    definirSocio.mutate(
                      { cardId, socioNome: nome, ativo: false, motivo: motivoTirar.trim() },
                      { onSuccess: () => { setTirando(null); setMotivoTirar(""); } },
                    )
                  }
                  className={BOTAO_PRIMARIO}
                >
                  Tirar
                </button>
              </div>
            )}
            <ul className="space-y-2">
              {itens.filter(visivel).map(documento => (
                <ItemDocumento key={documento.id} documento={documento} cardId={cardId} />
              ))}
            </ul>
          </section>
        );
      })}

      {/* ---------------------------------------------------------- avalista */}
      {novoAvalista ? (
        <div className="space-y-2 rounded-xl border border-dashed border-slate-300 p-3 dark:border-slate-600">
          <p className="text-xs font-semibold text-slate-700 dark:text-slate-200">Novo avalista</p>
          <div className="grid gap-2 sm:grid-cols-[1fr_12rem]">
            <input
              autoFocus
              value={novoAvalista.nome}
              onChange={event => setNovoAvalista({ ...novoAvalista, nome: event.target.value })}
              placeholder="Nome"
              aria-label="Nome do avalista"
              className={`${CAMPO} py-1.5 text-xs`}
            />
            <input
              value={novoAvalista.documento}
              onChange={event => setNovoAvalista({ ...novoAvalista, documento: event.target.value })}
              placeholder="CPF (opcional)"
              aria-label="CPF do avalista"
              inputMode="numeric"
              className={`${CAMPO} py-1.5 text-xs`}
            />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setNovoAvalista(null)} className={BOTAO_SECUNDARIO}>Cancelar</button>
            <button
              type="button"
              disabled={!novoAvalista.nome.trim() || adicionarPessoa.isPending}
              onClick={() =>
                adicionarPessoa.mutate(
                  { cardId, nome: novoAvalista.nome.trim(), documento: novoAvalista.documento, papel: "AVALISTA" },
                  { onSuccess: () => setNovoAvalista(null) },
                )
              }
              className={BOTAO_PRIMARIO}
            >
              {adicionarPessoa.isPending ? "Adicionando…" : "Adicionar avalista"}
            </button>
          </div>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setNovoAvalista({ nome: "", documento: "" })}
          className="inline-flex min-h-9 cursor-pointer items-center gap-1.5 rounded-lg border border-dashed border-slate-300 px-3 text-xs font-medium text-slate-600
            transition-colors hover:border-slate-400 hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
            dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-800"
        >
          <Icon name="add" size={14} /> Adicionar avalista
        </button>
      )}
    </div>
  );
}
