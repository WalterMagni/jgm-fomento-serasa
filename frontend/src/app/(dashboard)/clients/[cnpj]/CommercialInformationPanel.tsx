"use client";

import React, { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

type CommercialRecord = {
  id: string;
  data: string;
  tipo: string;
  parceiro: string;
  clienteDesde: string;
  ultimaOperacaoData: string;
  ultimaOperacaoValor: string;
  limite: string;
  riscoDuplicata: string;
  riscoCheque: string;
  riscoComissaria: string;
  vencidosData: string;
  vencidosValorMonetario: string;
  vencidosValor: string;
  vop: string;
  pontual: string;
  atraso: string;
  recompra: string;
  cartorio: string;
  observacao: string;
  authorName?: string | null;
  authorEmail?: string | null;
  canManage?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

function getAuthHeaders() {
  const token = localStorage.getItem("serasa_token");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function extractErrorMessage(res: Response, fallback: string) {
  const contentType = res.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    const data = await res.json().catch(() => ({}));
    return data.message || data.error || fallback;
  }
  const text = await res.text().catch(() => "");
  return text || fallback;
}

const moneyFields: Array<keyof CommercialRecord> = [
  "ultimaOperacaoValor",
  "limite",
  "riscoDuplicata",
  "riscoCheque",
  "riscoComissaria",
  "vencidosValorMonetario",
  "vop",
];

const dateFields: Array<keyof CommercialRecord> = [
  "data",
  "clienteDesde",
  "ultimaOperacaoData",
  "vencidosData",
  "vencidosValor",
];

const percentFields: Array<keyof CommercialRecord> = [
  "pontual",
  "atraso",
  "cartorio",
  "recompra",
];

const emptyRecord: CommercialRecord = {
  id: "",
  data: "",
  tipo: "EMAIL",
  parceiro: "",
  clienteDesde: "",
  ultimaOperacaoData: "",
  ultimaOperacaoValor: "",
  limite: "",
  riscoDuplicata: "",
  riscoCheque: "",
  riscoComissaria: "",
  vencidosData: "",
  vencidosValorMonetario: "",
  vencidosValor: "",
  vop: "",
  pontual: "",
  atraso: "",
  recompra: "",
  cartorio: "",
  observacao: "",
};

function normalizeRecord(record: Partial<CommercialRecord>): CommercialRecord {
  return {
    ...emptyRecord,
    ...record,
    id: record.id ?? "",
    data: record.data ?? "",
    tipo: record.tipo ?? "EMAIL",
    parceiro: record.parceiro ?? "",
    clienteDesde: record.clienteDesde ?? "",
    ultimaOperacaoData: record.ultimaOperacaoData ?? "",
    ultimaOperacaoValor: record.ultimaOperacaoValor ?? "",
    limite: record.limite ?? "",
    riscoDuplicata: record.riscoDuplicata ?? "",
    riscoCheque: record.riscoCheque ?? "",
    riscoComissaria: record.riscoComissaria ?? "",
    vencidosData: record.vencidosData ?? "",
    vencidosValorMonetario: record.vencidosValorMonetario ?? "",
    vencidosValor: record.vencidosValor ?? "",
    vop: record.vop ?? "",
    pontual: record.pontual ?? "",
    atraso: record.atraso ?? "",
    recompra: record.recompra ?? "",
    cartorio: record.cartorio ?? "",
    observacao: record.observacao ?? "",
  };
}

function parseNumber(value: string) {
  const normalized = value.replace(/\./g, "").replace(",", ".").replace(/[^\d.-]/g, "");
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatMoneyInput(value: string) {
  const digits = value.replace(/\D/g, "");
  if (!digits) return "";
  const integer = Number(digits) / 100;
  return new Intl.NumberFormat("pt-BR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(integer);
}

function formatDateInput(value: string) {
  const digits = value.replace(/\D/g, "").slice(0, 8);
  if (digits.length <= 2) return digits;
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`;
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`;
}

function formatPercentInput(value: string) {
  const digits = value.replace(/\D/g, "");
  if (!digits) return "";
  const integer = Number(digits) / 100;
  return new Intl.NumberFormat("pt-BR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(integer);
}

function formatMoney(value: string) {
  const parsed = parseNumber(value);
  if (!value || parsed === 0) return "—";
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(parsed);
}

function formatPercent(value: string) {
  const parsed = parseNumber(value);
  if (!value) return "—";
  return `${new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(parsed)}%`;
}

function formatDate(value: string) {
  if (!value) return "—";
  if (/^\d{2}\/\d{2}\/\d{4}$/.test(value)) return value;
  const [year, month, day] = value.split("-");
  return year && month && day ? `${day}/${month}/${year}` : value;
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const normalized = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(value) ? `${value}-03:00` : value;
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("pt-BR", {
    timeZone: "America/Sao_Paulo",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function summarizeRisk(record: CommercialRecord) {
  const risks = [record.riscoDuplicata, record.riscoCheque, record.riscoComissaria]
    .filter(Boolean)
    .map(formatMoney);
  return risks.length > 0 ? risks.join(" / ") : "—";
}

function summarizeOverdue(record: CommercialRecord) {
  const date = formatDate(record.vencidosValor);
  const value = formatMoney(record.vencidosValorMonetario);
  if (date === "—" && value === "—") return "—";
  if (date === "—") return value;
  if (value === "—") return date;
  return `${date} · ${value}`;
}

function RiskHeader() {
  return (
    <span className="inline-flex items-center gap-1">
      Risco
      <span className="group relative inline-flex h-4 w-4 cursor-help items-center justify-center rounded-full border border-gray-300 text-[10px] font-bold text-gray-400 dark:border-gray-600">
        ?
        <span className="pointer-events-none absolute left-1/2 top-full z-20 mt-2 hidden w-52 -translate-x-1/2 rounded-lg bg-grafite px-3 py-2 text-left text-[11px] font-medium normal-case tracking-normal text-white shadow-xl group-hover:block dark:bg-gray-900">
          Valores exibidos na ordem: risco duplicata / risco cheque / risco comissária.
        </span>
      </span>
    </span>
  );
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function downloadCommercialXls(records: CommercialRecord[]) {
  const headers = [
    "Data",
    "Tipo",
    "Parceiro",
    "Cliente desde",
    "Ultima operacao",
    "Valor da operacao",
    "Limite",
    "Risco duplicata",
    "Risco cheque",
    "Risco comissaria",
    "Vencidos data",
    "Vencidos valor",
    "Último Vencimento da Duplicata",
    "VOP",
    "L1 pontual",
    "L2 atraso",
    "L3 cartorio",
    "L4 recompra",
    "Observacao",
  ];

  const rows = records.map((record) => [
    formatDate(record.data),
    record.tipo || "—",
    record.parceiro || "—",
    formatDate(record.clienteDesde),
    formatDate(record.ultimaOperacaoData),
    formatMoney(record.ultimaOperacaoValor),
    formatMoney(record.limite),
    formatMoney(record.riscoDuplicata),
    formatMoney(record.riscoCheque),
    formatMoney(record.riscoComissaria),
    formatDate(record.vencidosData),
    formatMoney(record.vencidosValorMonetario),
    formatDate(record.vencidosValor),
    formatMoney(record.vop),
    formatPercent(record.pontual),
    formatPercent(record.atraso),
    formatPercent(record.cartorio),
    formatPercent(record.recompra),
    record.observacao || "—",
  ]);

  const table = `
    <html>
      <head><meta charset="UTF-8" /></head>
      <body>
        <table border="1">
          <thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join("")}</tr></thead>
          <tbody>
            ${rows.map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(String(cell))}</td>`).join("")}</tr>`).join("")}
          </tbody>
        </table>
      </body>
    </html>
  `;

  const blob = new Blob([table], { type: "application/vnd.ms-excel;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `informacoes-comerciais-${new Date().toISOString().slice(0, 10)}.xls`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function Field({
  label,
  name,
  type = "text",
  value,
  onChange,
  placeholder,
  inputMode,
}: {
  label: string;
  name: keyof CommercialRecord;
  type?: string;
  value: string;
  onChange: (name: keyof CommercialRecord, value: string) => void;
  placeholder?: string;
  inputMode?: React.HTMLAttributes<HTMLInputElement>["inputMode"];
}) {
  return (
    <label className="block">
      <span className="text-xs font-sans font-bold uppercase tracking-wide text-gray-400">{label}</span>
      <input
        name={name}
        type={type}
        value={value}
        inputMode={inputMode}
        placeholder={placeholder}
        onChange={(event) => onChange(name, event.target.value)}
        className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm font-sans text-grafite outline-none transition-colors placeholder:text-gray-300 focus:border-primary dark:border-border-dark dark:bg-gray-900 dark:text-white"
      />
    </label>
  );
}

function FormSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="mb-3 flex items-center gap-2 text-sm font-sans font-bold text-grafite dark:text-white">
        <span className="h-2 w-2 rounded-full bg-primary" />
        {title}
      </h3>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-3">{children}</div>
    </div>
  );
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border-light bg-white px-3 py-2 dark:border-border-dark dark:bg-gray-950">
      <p className="text-[10px] font-sans font-bold uppercase tracking-wide text-gray-400">{label}</p>
      <p className="mt-1 text-sm font-serif text-grafite dark:text-gray-200">{value}</p>
    </div>
  );
}

function ExpandedCommercialRecord({
  record,
  onEdit,
  onDelete,
}: {
  record: CommercialRecord;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="rounded-xl border border-border-light bg-background-light p-4 dark:border-border-dark dark:bg-background-dark">
      <div className="mb-3 flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
          <span>
            Criado em <strong className="font-bold text-grafite dark:text-white">{formatDateTime(record.createdAt)}</strong>
          </span>
          <span className="hidden text-gray-300 dark:text-gray-600 md:inline">|</span>
          <span>
            Por <strong className="font-bold text-grafite dark:text-white">{record.authorName || "Usuário não identificado"}</strong>
          </span>
        </div>
        {record.canManage ? (
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onEdit}
              className="text-xs text-gray-400 underline underline-offset-2 transition-colors hover:text-primary dark:hover:text-primary-light"
            >
              editar
            </button>
            <span className="px-1 text-xs text-gray-300 dark:text-gray-600">|</span>
            <button
              type="button"
              onClick={onDelete}
              className="text-xs text-gray-400 underline underline-offset-2 transition-colors hover:text-red-600 dark:hover:text-red-400"
            >
              excluir
            </button>
          </div>
        ) : (
          <span className="text-xs font-bold uppercase tracking-wide text-gray-400">Somente leitura</span>
        )}
      </div>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-3 xl:grid-cols-4">
        <DetailItem label="Cliente desde" value={formatDate(record.clienteDesde)} />
        <DetailItem label="Risco duplicata" value={formatMoney(record.riscoDuplicata)} />
        <DetailItem label="Risco cheque" value={formatMoney(record.riscoCheque)} />
        <DetailItem label="Risco comissaria" value={formatMoney(record.riscoComissaria)} />
        <DetailItem label="Vencidos data" value={formatDate(record.vencidosData)} />
        <DetailItem label="Vencidos valor" value={formatMoney(record.vencidosValorMonetario)} />
        <DetailItem label="L2 atraso" value={formatPercent(record.atraso)} />
        <DetailItem label="L3 cartorio" value={formatPercent(record.cartorio)} />
        <DetailItem label="L4 recompra" value={formatPercent(record.recompra)} />
      </div>
      <div className="mt-3 rounded-lg border border-border-light bg-white px-3 py-2 dark:border-border-dark dark:bg-gray-950">
        <p className="text-[10px] font-sans font-bold uppercase tracking-wide text-gray-400">Observacao</p>
        <p className="mt-1 whitespace-pre-wrap text-sm font-serif text-grafite dark:text-gray-200">
          {record.observacao || "—"}
        </p>
      </div>
    </div>
  );
}

function CommercialInformationModal({
  open,
  draft,
  isSaving,
  onChange,
  onClose,
  onSave,
}: {
  open: boolean;
  draft: CommercialRecord;
  isSaving: boolean;
  onChange: (name: keyof CommercialRecord, value: string) => void;
  onClose: () => void;
  onSave: () => void;
}) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <button className="absolute inset-0 bg-black/45 backdrop-blur-sm" onClick={onClose} aria-label="Fechar formulário" />
      <div className="relative flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl border border-border-light bg-surface-light shadow-2xl dark:border-border-dark dark:bg-surface-dark">
        <div className="flex items-start justify-between gap-4 border-b border-border-light px-6 py-5 dark:border-border-dark">
          <div>
            <p className="text-xs font-sans font-bold uppercase tracking-[0.18em] text-primary">Informações Comerciais</p>
            <h2 className="mt-1 text-xl font-sans font-bold text-grafite dark:text-white">
              {draft.id ? "Editar ficha comercial" : "Nova ficha comercial"}
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-9 w-9 items-center justify-center rounded-lg text-gray-400 transition-colors hover:bg-gray-100 hover:text-grafite dark:hover:bg-gray-800 dark:hover:text-white"
          >
            <span className="material-icons-outlined text-lg">close</span>
          </button>
        </div>

        <div className="overflow-y-auto px-6 py-5">
          <div className="space-y-6">
            <FormSection title="Operação">
              <Field label="Data" name="data" value={draft.data} onChange={onChange} placeholder="dd/MM/aaaa" inputMode="numeric" />
              <label className="block">
                <span className="text-xs font-sans font-bold uppercase tracking-wide text-gray-400">Tipo</span>
                <select
                  value={draft.tipo}
                  onChange={(event) => onChange("tipo", event.target.value)}
                  aria-label="Tipo"
                  className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm font-sans text-grafite outline-none transition-colors focus:border-primary dark:border-border-dark dark:bg-gray-900 dark:text-white"
                >
                  <option>EMAIL</option>
                  <option>WHATSAPP</option>
                  <option>TELEFONE</option>
                  <option>REUNIÃO</option>
                </select>
              </label>
              <Field label="Parceiro" name="parceiro" value={draft.parceiro} onChange={onChange} placeholder="Ex.: PROA FIDC" />
              <Field label="Cliente desde" name="clienteDesde" value={draft.clienteDesde} onChange={onChange} placeholder="dd/MM/aaaa" inputMode="numeric" />
              <Field label="Última operação" name="ultimaOperacaoData" value={draft.ultimaOperacaoData} onChange={onChange} placeholder="dd/MM/aaaa" inputMode="numeric" />
              <Field label="Valor da operação" name="ultimaOperacaoValor" value={draft.ultimaOperacaoValor} onChange={onChange} placeholder="250.000,00" inputMode="numeric" />
              <Field label="Limite" name="limite" value={draft.limite} onChange={onChange} placeholder="250.000,00" inputMode="numeric" />
            </FormSection>

            <FormSection title="Riscos e Vencidos">
              <Field label="Risco duplicata" name="riscoDuplicata" value={draft.riscoDuplicata} onChange={onChange} placeholder="25.000,00" inputMode="numeric" />
              <Field label="Risco cheque" name="riscoCheque" value={draft.riscoCheque} onChange={onChange} placeholder="25.000,00" inputMode="numeric" />
              <Field label="Risco comissária" name="riscoComissaria" value={draft.riscoComissaria} onChange={onChange} placeholder="25.000,00" inputMode="numeric" />
              <Field label="Vencidos data" name="vencidosData" value={draft.vencidosData} onChange={onChange} placeholder="dd/MM/aaaa" inputMode="numeric" />
              <Field label="Vencidos valor" name="vencidosValorMonetario" value={draft.vencidosValorMonetario} onChange={onChange} placeholder="687.053,00" inputMode="numeric" />
              <Field label="Último Vencimento da Duplicata" name="vencidosValor" value={draft.vencidosValor} onChange={onChange} placeholder="dd/MM/aaaa" inputMode="numeric" />
            </FormSection>

            <FormSection title="Performance">
              <Field label="VOP" name="vop" value={draft.vop} onChange={onChange} placeholder="128.869,53" inputMode="numeric" />
              <Field label="L1 pontual (%)" name="pontual" value={draft.pontual} onChange={onChange} placeholder="99,34" inputMode="decimal" />
              <Field label="L2 atraso (%)" name="atraso" value={draft.atraso} onChange={onChange} placeholder="0,66" inputMode="decimal" />
              <Field label="L3 cartório (%)" name="cartorio" value={draft.cartorio} onChange={onChange} placeholder="0,00" inputMode="decimal" />
              <Field label="L4 recompra (%)" name="recompra" value={draft.recompra} onChange={onChange} placeholder="0,00" inputMode="decimal" />
            </FormSection>

            <label className="block">
              <span className="text-xs font-sans font-bold uppercase tracking-wide text-gray-400">Observação</span>
              <textarea
                value={draft.observacao}
                onChange={(event) => onChange("observacao", event.target.value)}
                rows={4}
                className="mt-1 w-full resize-none rounded-lg border border-border-light bg-white px-3 py-2 text-sm font-sans text-grafite outline-none transition-colors placeholder:text-gray-300 focus:border-primary dark:border-border-dark dark:bg-gray-900 dark:text-white"
                placeholder="Registre contexto, retorno do parceiro, motivo de pausa ou qualquer ponto importante para o comercial."
              />
            </label>
          </div>
        </div>

        <div className="flex flex-col-reverse gap-2 border-t border-border-light px-6 py-4 dark:border-border-dark sm:flex-row sm:justify-end">
          <button
            type="button"
            onClick={onClose}
            className="inline-flex h-10 items-center justify-center rounded-lg border border-border-light px-4 text-sm font-sans font-bold text-gray-600 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-gray-800"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={onSave}
            disabled={isSaving}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-primary px-4 text-sm font-sans font-bold text-white transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <span className="material-icons-outlined text-base">save</span>
            {isSaving ? "Salvando..." : "Salvar ficha"}
          </button>
        </div>
      </div>
    </div>
  );
}

export function CommercialInformationPanel({ cnpj }: { cnpj: string }) {
  const cleanCnpj = cnpj ? cnpj.replace(/\D/g, "") : "";
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [draft, setDraft] = useState<CommercialRecord>(emptyRecord);
  const [expandedRecordIds, setExpandedRecordIds] = useState<Set<string>>(new Set());

  const recordsQuery = useQuery<CommercialRecord[]>({
    queryKey: ["companyCommercialInformation", cleanCnpj],
    queryFn: async () => {
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/commercial-information`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao carregar informações comerciais"));
      }

      const data = await response.json();
      return Array.isArray(data) ? data.map((record) => normalizeRecord(record)) : [];
    },
    enabled: !!cleanCnpj,
  });

  const records = useMemo(() => recordsQuery.data ?? [], [recordsQuery.data]);

  const saveRecord = useMutation({
    mutationFn: async (record: CommercialRecord) => {
      const isEditing = Boolean(record.id);
      const response = await fetch(
        isEditing
          ? `${API_BASE_URL}/company/${cleanCnpj}/commercial-information/${record.id}`
          : `${API_BASE_URL}/company/${cleanCnpj}/commercial-information`,
        {
          method: isEditing ? "PUT" : "POST",
          headers: getAuthHeaders(),
          body: JSON.stringify(record),
        },
      );

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao salvar informação comercial"));
      }

      return normalizeRecord(await response.json());
    },
    onSuccess: (savedRecord) => {
      toast.success("Informação comercial salva");
      queryClient.invalidateQueries({ queryKey: ["companyCommercialInformation", cleanCnpj] });
      setExpandedRecordIds((current) => new Set(current).add(savedRecord.id));
      setModalOpen(false);
      setDraft(emptyRecord);
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao salvar informação comercial");
    },
  });

  const deleteRecord = useMutation({
    mutationFn: async (recordId: string) => {
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/commercial-information/${recordId}`, {
        method: "DELETE",
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao excluir informação comercial"));
      }
    },
    onSuccess: (_, recordId) => {
      toast.success("Informação comercial excluída");
      queryClient.invalidateQueries({ queryKey: ["companyCommercialInformation", cleanCnpj] });
      setExpandedRecordIds((current) => {
        const next = new Set(current);
        next.delete(recordId);
        return next;
      });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao excluir informação comercial");
    },
  });

  const summary = useMemo(() => {
    const partners = new Set(records.map((record) => record.parceiro).filter(Boolean));
    const totalVop = records.reduce((sum, record) => sum + parseNumber(record.vop), 0);
    return { partners: partners.size, totalVop };
  }, [records]);

  const updateDraft = (name: keyof CommercialRecord, value: string) => {
    let nextValue = value;
    if (moneyFields.includes(name)) {
      nextValue = formatMoneyInput(value);
    } else if (percentFields.includes(name)) {
      nextValue = formatPercentInput(value);
    } else if (dateFields.includes(name)) {
      nextValue = formatDateInput(value);
    }
    setDraft((current) => ({ ...current, [name]: nextValue }));
  };

  const allExpanded = records.length > 0 && records.every((record) => expandedRecordIds.has(record.id));

  const toggleRecord = (recordId: string) => {
    setExpandedRecordIds((current) => {
      const next = new Set(current);
      if (next.has(recordId)) {
        next.delete(recordId);
      } else {
        next.add(recordId);
      }
      return next;
    });
  };

  const toggleAllRecords = () => {
    setExpandedRecordIds(allExpanded ? new Set() : new Set(records.map((record) => record.id)));
  };

  const openNewRecord = () => {
    const today = new Intl.DateTimeFormat("pt-BR").format(new Date());
    setDraft({ ...emptyRecord, data: today });
    setModalOpen(true);
  };

  const openEditRecord = (record: CommercialRecord) => {
    setDraft(record);
    setModalOpen(true);
  };

  const saveDraft = () => {
    saveRecord.mutate(draft);
  };

  const handleDeleteRecord = (record: CommercialRecord) => {
    if (!window.confirm("Excluir esta informação comercial?")) {
      return;
    }
    deleteRecord.mutate(record.id);
  };

  return (
    <section className="mb-6 rounded-xl border border-border-light dark:border-border-dark bg-surface-light dark:bg-surface-dark p-6 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h2 className="flex items-center gap-2 font-sans text-base font-bold text-primary">
            <span className="material-icons-outlined text-lg">business_center</span>
            Informações Comerciais
          </h2>
          <p className="mt-1 text-xs font-sans text-gray-500">
            Resumo operacional por parceiro, risco, limite, VOP e performance.
          </p>
        </div>
        <button
          type="button"
          onClick={openNewRecord}
          className="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-primary px-4 text-sm font-sans font-bold text-white shadow-sm transition-colors hover:bg-primary/90"
        >
          <span className="material-icons-outlined text-base">add_business</span>
          Nova informação comercial
        </button>
      </div>

      <div className="mt-5 grid grid-cols-1 gap-3 md:grid-cols-3">
        {[
          ["Registros", String(records.length)],
          ["Parceiros", String(summary.partners)],
          ["VOP", summary.totalVop > 0 ? new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(summary.totalVop) : "R$ 0,00"],
        ].map(([label, value]) => (
          <div key={label} className="rounded-xl border border-border-light dark:border-border-dark bg-background-light dark:bg-background-dark p-4">
            <p className="text-xs font-sans font-bold uppercase tracking-wide text-gray-400">{label}</p>
            <p className="mt-2 text-xl font-sans font-bold text-grafite dark:text-white">{value}</p>
          </div>
        ))}
      </div>

      <div className="mt-4 overflow-hidden rounded-xl border border-border-light dark:border-border-dark">
        {records.length > 0 && (
          <div className="flex flex-wrap items-center justify-end gap-2 border-b border-border-light bg-background-light px-4 py-3 dark:border-border-dark dark:bg-background-dark">
            <button
              type="button"
              onClick={toggleAllRecords}
              className="text-xs text-gray-400 underline underline-offset-2 transition-colors hover:text-primary dark:hover:text-primary-light"
            >
              {allExpanded ? "recolher tudo" : "expandir tudo"}
            </button>
            <span className="px-1 text-xs text-gray-300 dark:text-gray-600">|</span>
            <button
              type="button"
              onClick={() => downloadCommercialXls(records)}
              className="text-xs text-gray-400 underline underline-offset-2 transition-colors hover:text-primary dark:hover:text-primary-light"
            >
              exportar XLS
            </button>
          </div>
        )}
        <div className="overflow-x-auto">
          <table className="w-full min-w-[820px] text-left text-sm">
            <thead className="bg-background-light dark:bg-background-dark">
              <tr className="border-b border-border-light dark:border-border-dark">
                {["Data", "Parceiro", "Última op.", "Limite"].map((header) => (
                  <th key={header} className="px-4 py-3 text-xs font-sans font-bold uppercase tracking-wide text-gray-400">
                    {header}
                  </th>
                ))}
                <th className="px-4 py-3 text-xs font-sans font-bold uppercase tracking-wide text-gray-400">
                  <RiskHeader />
                </th>
                {["Vencidos", "VOP", "Pontual"].map((header) => (
                  <th key={header} className="px-4 py-3 text-xs font-sans font-bold uppercase tracking-wide text-gray-400">
                    {header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-border-light dark:divide-border-dark">
              {records.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-4 py-10 text-center">
                    <p className="font-sans text-sm font-bold text-grafite dark:text-white">
                      {recordsQuery.isLoading ? "Carregando informações comerciais..." : "Nenhuma informação comercial cadastrada."}
                    </p>
                    <p className="mx-auto mt-1 max-w-xl text-sm font-sans text-gray-500">
                      {recordsQuery.isLoading
                        ? "Buscando o histórico salvo para esta empresa."
                        : "Use o botão acima para abrir o formulário e validar os campos com a equipe."}
                    </p>
                    {recordsQuery.isError && (
                      <p className="mx-auto mt-2 max-w-xl text-sm font-sans text-red-600">
                        {(recordsQuery.error as Error)?.message || "Erro ao carregar informações comerciais."}
                      </p>
                    )}
                  </td>
                </tr>
              ) : (
                records.map((record) => {
                  const isExpanded = expandedRecordIds.has(record.id);
                  return (
                    <React.Fragment key={record.id}>
                      <tr
                        className={`cursor-pointer transition-colors ${isExpanded ? "bg-primary/5 dark:bg-primary/10" : "hover:bg-background-light dark:hover:bg-background-dark"}`}
                        onClick={() => toggleRecord(record.id)}
                        tabIndex={0}
                        onKeyDown={(event) => {
                          if (event.key === "Enter" || event.key === " ") {
                            event.preventDefault();
                            toggleRecord(record.id);
                          }
                        }}
                      >
                        <td className="px-4 py-3 font-serif text-grafite dark:text-gray-200 whitespace-nowrap">
                          <span className="inline-flex items-center gap-1.5">
                            <span className="material-icons-outlined text-sm text-gray-400">
                              {isExpanded ? "expand_less" : "expand_more"}
                            </span>
                            {formatDate(record.data)}
                          </span>
                        </td>
                        <td className="px-4 py-3 font-sans font-bold text-grafite dark:text-white">{record.parceiro || "—"}</td>
                        <td className="px-4 py-3 font-serif text-grafite dark:text-gray-200">{formatMoney(record.ultimaOperacaoValor)}</td>
                        <td className="px-4 py-3 font-serif text-grafite dark:text-gray-200">{formatMoney(record.limite)}</td>
                        <td className="px-4 py-3 font-serif text-grafite dark:text-gray-200">{summarizeRisk(record)}</td>
                        <td className="px-4 py-3 font-serif text-red-600">{summarizeOverdue(record)}</td>
                        <td className="px-4 py-3 font-serif text-grafite dark:text-gray-200">{formatMoney(record.vop)}</td>
                        <td className="px-4 py-3 font-serif text-grafite dark:text-gray-200">{formatPercent(record.pontual)}</td>
                      </tr>
                      {isExpanded && (
                        <tr className="bg-primary/5 dark:bg-primary/10">
                          <td colSpan={8} className="px-4 pb-4 pt-0">
                            <ExpandedCommercialRecord
                              record={record}
                              onEdit={() => openEditRecord(record)}
                              onDelete={() => handleDeleteRecord(record)}
                            />
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      <CommercialInformationModal
        open={modalOpen}
        draft={draft}
        isSaving={saveRecord.isPending}
        onChange={updateDraft}
        onClose={() => setModalOpen(false)}
        onSave={saveDraft}
      />
    </section>
  );
}
