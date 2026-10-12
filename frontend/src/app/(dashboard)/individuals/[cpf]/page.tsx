"use client";

import React from "react";
import Icon from "@/components/ui/Icon";
import { caixaTitulo, diasDesde, formatMoeda, iniciais, tempoRelativo, tomDoAvatar } from "@/lib/formatadores";
import { BOTAO } from "@/components/ui/botao";
import BlocoResumo from "@/components/ui/BlocoResumo";
import IndiceSecoes, { irParaSecao, type SecaoIndice } from "@/components/ui/IndiceSecoes";
import { DIAS_CONSULTA_VELHA, SITUACAO, classificarSituacao } from "@/types/pessoa-resumo";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";
import { reserveNewTab } from "@/lib/newTab";
import { toast } from "sonner";
import { usePersonProfile } from "../../../../hooks/usePersonProfile";
import { PersonNotesPanel } from "./PersonNotesPanel";
import {
  formatCpf,
  totalNegativesFromPF,
  totalDebtFromPF,
  type PFNegativeSection,
  type PFPartnerCompany,
  type PFMonthlyInquiryItem,
  type PersonAddress,
  type PFPefinRefinRecord,
  type PFCollectionRecord,
  type PFCheckRecord,
  type PFNotaryRecord,
  type PFJudgementFilingRecord,
  type PFBankruptRecord,
  type PFStolenDocumentRecord,
} from "../../../../types/person-analysis";

function buildContextQuery(fromPath: string, fromLabel?: string) {
  const params = new URLSearchParams({ from: fromPath });
  if (fromLabel) params.set("fromLabel", fromLabel);
  return params.toString();
}

function formatDate(raw: string | undefined) {
  if (!raw) return "—";
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const [y, m, d] = raw.split("-");
    return `${d}/${m}/${y}`;
  }
  const d = new Date(raw);
  return isNaN(d.getTime()) ? raw : d.toLocaleDateString("pt-BR");
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(value);
}

function formatCnpj(raw: string) {
  return raw.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}

function formatPhone(areaCode?: number, phoneNumber?: number) {
  if (!areaCode && !phoneNumber) return "—";
  if (areaCode && phoneNumber) return `(${areaCode}) ${phoneNumber}`;
  return String(phoneNumber ?? areaCode);
}

function translatePhoneType(type?: string) {
  const labels: Record<string, string> = {
    "commercial phone": "Telefone comercial",
    "resident phone": "Telefone residencial",
    "smart phone": "Celular",
    "mobile phone": "Celular",
    "cell phone": "Celular",
  };
  const normalized = (type ?? "").trim().toLowerCase();
  return labels[normalized] ?? type ?? "Telefone";
}

function formatAddress(address?: PersonAddress | null) {
  if (!address) return "—";
  return [
    address.addressLine,
    address.addressNumber,
    address.addressComplement,
    address.district,
    [address.city, address.state].filter(Boolean).join(" / "),
    address.zipCode ? `CEP ${address.zipCode}` : null,
  ]
    .filter(Boolean)
    .join(" · ");
}

function formatMonthLabel(raw?: string) {
  if (!raw || !/^\d{4}-\d{2}$/.test(raw)) return raw ?? "—";
  const [year, month] = raw.split("-");
  return new Date(Number(year), Number(month) - 1, 1).toLocaleDateString("pt-BR", {
    month: "short",
    year: "numeric",
  });
}

function normalizeCnpj(raw?: string) {
  return (raw ?? "").replace(/\D/g, "");
}

function isInactiveCompanyStatus(status?: string) {
  const normalized = (status ?? "").trim().toUpperCase();
  if (!normalized) return false;
  return ["BAIX", "CANCEL", "INAPTA", "SUSPEN", "NULA", "EXTINT"].some((term) =>
    normalized.includes(term),
  );
}

function InfoRow({ label, value }: { label: string; value: string | number | undefined | null }) {
  if (!value && value !== 0) return null;
  return (
    <div className="min-w-0">
      <dt className="text-[11px] font-bold uppercase tracking-[0.1em] text-[#7F7C73]">{label}</dt>
      <dd className="mt-0.5 break-words text-sm text-grafite">{String(value)}</dd>
    </div>
  );
}

function SectionCard({
  title,
  icon,
  children,
  actions,
}: {
  title: string;
  icon: string;
  children: React.ReactNode;
  actions?: React.ReactNode;
}) {
  return (
    <div className="rounded-cartao border border-linha bg-white p-6">
      <div className="flex items-center justify-between gap-4 mb-5">
        <div className="flex items-center gap-2">
          <Icon name={icon} className="text-lg text-primary" />
          <h2 className="font-sans text-base font-bold text-primary">{title}</h2>
        </div>
        {actions}
      </div>
      {children}
    </div>
  );
}

function NegativeCard({
  title,
  icon,
  section,
  hasDetail = false,
  isOpen = false,
  onToggle,
}: {
  title: string;
  icon: string;
  section: PFNegativeSection | undefined;
  hasDetail?: boolean;
  isOpen?: boolean;
  onToggle?: () => void;
}) {
  const count = section?.summary?.count ?? 0;
  const balance = section?.summary?.balance ?? 0;
  const hasData = count > 0;
  const isClickable = hasDetail && onToggle;

  return (
    <button
      type="button"
      onClick={isClickable ? onToggle : undefined}
      className={`rounded-xl p-4 border w-full text-left transition-all ${
        hasData
          ? "border-[#F0C9D2] bg-[#FBEEF1]"
          : "border-linha bg-white"
      } ${isClickable ? "cursor-pointer hover:-translate-y-[1px] hover:shadow-sm" : "cursor-default"} ${
        isOpen ? "ring-2 ring-red-300 dark:ring-red-700" : ""
      }`}
    >
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2">
          <Icon name={icon} className={`text-xl ${hasData ? "text-red-500" : "text-gray-400"}`} />
          <span className="text-xs font-sans font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
            {title}
          </span>
        </div>
        {hasDetail && (
          <Icon name={isOpen ? "expand_less" : "expand_more"} className="text-base text-gray-400 dark:text-gray-500" />
        )}
      </div>
      <p
        className={`font-display text-[28px] font-semibold leading-none ${
          hasData ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"
        }`}
      >
        {count}
      </p>
      {balance > 0 && <p className="text-xs text-red-500 font-sans mt-0.5">{formatCurrency(balance)}</p>}
      {!hasData && <p className="text-xs text-green-600 dark:text-green-400 font-sans mt-0.5">Nada consta</p>}
    </button>
  );
}

type ItemOcorrencia = {
  id: string;
  title: string;
  icon: string;
  section: PFNegativeSection | undefined;
  hasDetail: boolean;
};

/** Só o que tem ocorrência vira card; o que está limpo vira uma linha de "nada consta". */
function GradeOcorrencias({
  itens,
  abertos,
  onAlternar,
}: {
  itens: ItemOcorrencia[];
  abertos: Record<string, boolean>;
  onAlternar: (id: string) => void;
}) {
  const comOcorrencia = itens.filter((item) => (item.section?.summary?.count ?? 0) > 0);
  const limpos = itens.filter((item) => (item.section?.summary?.count ?? 0) === 0);
  return (
    <>
      {limpos.length > 0 && (
        <div className="mb-3 flex flex-wrap items-center gap-2 rounded-xl border border-[#CFE6DA] bg-[#EEF7F2] px-4 py-3 text-sm text-[#17583F]">
          <Icon name="check_circle" size={18} />
          <span className="font-semibold">Nada consta:</span>
          {limpos.map((item) => (
            <span key={item.id} className="rounded-full bg-white/80 px-2.5 py-0.5 text-xs font-semibold">
              {item.title}
            </span>
          ))}
        </div>
      )}
      {comOcorrencia.length > 0 && (
        <div className="grid gap-3 [grid-template-columns:repeat(auto-fit,minmax(220px,1fr))]">
          {comOcorrencia.map((item) => (
            <NegativeCard
              key={item.id}
              title={item.title}
              icon={item.icon}
              section={item.section}
              hasDetail={item.hasDetail}
              isOpen={!!abertos[item.id]}
              onToggle={() => onAlternar(item.id)}
            />
          ))}
        </div>
      )}
    </>
  );
}

function NegDetailTable({
  title,
  children,
  shownCount,
  totalCount,
}: {
  title: string;
  children: React.ReactNode;
  shownCount?: number;
  totalCount?: number;
}) {
  const hasMissing = totalCount != null && shownCount != null && shownCount < totalCount;
  return (
    <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-5 shadow-sm border border-red-200 dark:border-red-800 mt-3">
      <p className="text-xs font-sans font-bold text-red-600 dark:text-red-400 uppercase tracking-wide mb-3 flex items-center gap-2">
        <Icon name="receipt_long" className="text-sm" />
        {title}
      </p>
      <div className="overflow-x-auto">{children}</div>
      {hasMissing && (
        <div className="mt-3 flex items-start gap-2 p-2.5 rounded-lg bg-amber-50 dark:bg-amber-900/10 border border-amber-200 dark:border-amber-700">
          <Icon name="info" className="text-sm text-amber-600 dark:text-amber-400 mt-0.5 shrink-0" />
          <p className="text-xs font-sans text-amber-700 dark:text-amber-300">
            Exibindo <strong>{shownCount}</strong> de <strong>{totalCount}</strong> ocorrência(s).
          </p>
        </div>
      )}
    </div>
  );
}

function PefinRefinTable({ records }: { records: PFPefinRefinRecord[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-linha dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-left pb-2 pr-3">Credor</th>
          <th className="text-left pb-2 pr-3">Contrato</th>
          <th className="text-left pb-2 pr-3">Natureza</th>
          <th className="text-left pb-2 pr-3">Banco</th>
          <th className="text-left pb-2 pr-3">CADUS</th>
          <th className="text-right pb-2">Valor</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((record, index) => (
          <tr key={`${record.contractId ?? "debt"}-${index}`}>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200 whitespace-nowrap">
              {formatDate(record.occurrenceDate)}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">
              {formatDate(record.inclusionDate)}
            </td>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200">
              {record.creditorName ?? "—"}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">{record.contractId ?? "—"}</td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">
              {record.legalNature ?? record.legalNatureId ?? "—"}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">
              {record.bank?.bankName ?? "—"}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">{record.cadus ?? "—"}</td>
            <td className="py-1.5 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">
              {record.amount != null ? formatCurrency(record.amount) : "—"}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function CollectionDetailTable({ records }: { records: PFCollectionRecord[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-linha dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-left pb-2 pr-3">Credor</th>
          <th className="text-left pb-2 pr-3">Contrato</th>
          <th className="text-left pb-2 pr-3">Natureza</th>
          <th className="text-left pb-2 pr-3">CADUS</th>
          <th className="text-right pb-2">Valor</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((record, index) => (
          <tr key={`${record.contractId ?? "collection"}-${index}`}>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200 whitespace-nowrap">
              {formatDate(record.occurrenceDate)}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">
              {formatDate(record.inclusionDate)}
            </td>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200">
              {record.creditorName ?? "—"}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">{record.contractId ?? "—"}</td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">{record.legalNature ?? "—"}</td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">{record.cadus ?? "—"}</td>
            <td className="py-1.5 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">
              {record.amount != null ? formatCurrency(record.amount) : "—"}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function CheckDetailTable({ records }: { records: PFCheckRecord[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-linha dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-left pb-2 pr-3">Banco</th>
          <th className="text-left pb-2 pr-3">Agência</th>
          <th className="text-left pb-2 pr-3">CADUS</th>
          <th className="text-right pb-2">Valor</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((record, index) => (
          <tr key={`${record.cadus ?? "check"}-${index}`}>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200 whitespace-nowrap">
              {formatDate(record.occurrenceDate)}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">
              {formatDate(record.inclusionDate)}
            </td>
            <td className="py-1.5 pr-3 text-gray-500">{record.bankName ?? record.bankCode ?? "—"}</td>
            <td className="py-1.5 pr-3 text-gray-500">{record.agency ?? "—"}</td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">{record.cadus ?? "—"}</td>
            <td className="py-1.5 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">
              {record.amount != null ? formatCurrency(record.amount) : "—"}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function NotaryDetailTable({ records }: { records: PFNotaryRecord[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-linha dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-left pb-2 pr-3">Cartório</th>
          <th className="text-left pb-2 pr-3">Cidade / UF</th>
          <th className="text-left pb-2 pr-3">CADUS</th>
          <th className="text-right pb-2">Valor</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((record, index) => (
          <tr key={`${record.cadus ?? "notary"}-${index}`}>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200 whitespace-nowrap">
              {formatDate(record.occurrenceDate)}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">
              {formatDate(record.inclusionDate)}
            </td>
            <td className="py-1.5 pr-3 text-gray-500">
              {record.officeNumber ? `Nº ${record.officeNumber}` : "—"}
            </td>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200">
              {[record.city, record.federalUnit].filter(Boolean).join(" / ") || "—"}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">{record.cadus ?? "—"}</td>
            <td className="py-1.5 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">
              {record.amount != null ? formatCurrency(record.amount) : "—"}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function JudgementDetailTable({ records }: { records: PFJudgementFilingRecord[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-linha dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-left pb-2 pr-3">Natureza</th>
          <th className="text-left pb-2 pr-3">Dist / Vara</th>
          <th className="text-left pb-2 pr-3">Cidade / UF</th>
          <th className="text-right pb-2">Valor</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((record, index) => (
          <tr key={`${record.distributor ?? "judgement"}-${index}`}>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200 whitespace-nowrap">
              {formatDate(record.occurrenceDate)}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">
              {formatDate(record.inclusionDate)}
            </td>
            <td className="py-1.5 pr-3 text-gray-500">
              {record.legalNature ?? record.legalNatureId ?? "—"}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">
              {[record.distributor, record.civilCourt].filter(Boolean).join(" / ") || "—"}
            </td>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200">
              {[record.city, record.state].filter(Boolean).join(" / ") || "—"}
            </td>
            <td className="py-1.5 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">
              {record.amount != null ? formatCurrency(record.amount) : "—"}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function BankruptDetailTable({ records }: { records: PFBankruptRecord[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-linha dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-left pb-2 pr-3">Natureza</th>
          <th className="text-left pb-2 pr-3">Cidade / UF</th>
          <th className="text-right pb-2">Valor</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((record, index) => (
          <tr key={`${record.city ?? "bankrupt"}-${index}`}>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200 whitespace-nowrap">
              {formatDate(record.occurrenceDate)}
            </td>
            <td className="py-1.5 pr-3 text-gray-500 whitespace-nowrap">
              {formatDate(record.inclusionDate)}
            </td>
            <td className="py-1.5 pr-3 text-gray-500">
              {record.legalNature ?? record.legalNatureId ?? "—"}
            </td>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200">
              {[record.city, record.state].filter(Boolean).join(" / ") || "—"}
            </td>
            <td className="py-1.5 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">
              {record.amount != null ? formatCurrency(record.amount) : "—"}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function StolenDocumentsTable({ records }: { records: PFStolenDocumentRecord[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-linha dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Tipo</th>
          <th className="text-left pb-2 pr-3">Documento</th>
          <th className="text-left pb-2">Origem</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((record, index) => (
          <tr key={`${record.documentNumber ?? "stolen"}-${index}`}>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200 whitespace-nowrap">
              {formatDate(record.occurrenceDate)}
            </td>
            <td className="py-1.5 pr-3 text-gray-500">{record.documentType ?? "—"}</td>
            <td className="py-1.5 pr-3 text-grafite dark:text-gray-200">
              {record.documentNumber ?? "—"}
            </td>
            <td className="py-1.5 text-gray-500">{record.sourceDescription ?? "—"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export default function PersonDetailPage() {
  const { cpf } = useParams<{ cpf: string }>();
  const pathname = usePathname();
  const router = useRouter();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const { profile, isLoading, isError, consultSerasa, isConsulting } = usePersonProfile(cpf);
  const [openSections, setOpenSections] = React.useState<Record<string, boolean>>({});
  const [isAddressHistoryOpen, setIsAddressHistoryOpen] = React.useState(false);
  const [showInactiveCompanies, setShowInactiveCompanies] = React.useState(false);
  const [creatingCompanyCnpj, setCreatingCompanyCnpj] = React.useState<string | null>(null);
  const backTarget = searchParams.get("from");
  const backTargetLabel = searchParams.get("fromLabel");
  const handleGoIndividuals = () => {
    router.push("/individuals");
  };
  const handleGoBack = () => {
    if (backTarget?.startsWith("/")) {
      router.push(backTarget);
      return;
    }
    if (typeof window !== "undefined" && window.history.length > 1) {
      router.back();
      return;
    }
    router.push("/individuals");
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-32">
        <Icon name="refresh" className="text-5xl text-gray-300 dark:text-gray-600 animate-spin" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-32 gap-4">
        <Icon name="error_outline" className="text-5xl text-red-400" />
        <p className="text-gray-500 font-sans">Erro ao carregar dados. Tente novamente.</p>
        <button onClick={() => router.back()} className="text-sm text-primary font-sans underline">
          Voltar
        </button>
      </div>
    );
  }

  if (!profile) {
    return (
      <div className="flex flex-col items-center justify-center py-32 gap-6">
        <Icon name="person_search" className="text-6xl text-gray-300 dark:text-gray-600" />
        <div className="text-center">
          <p className="font-heading font-bold text-grafite dark:text-white text-lg">Nenhuma análise encontrada</p>
          <p className="text-sm text-gray-500 font-sans mt-1">
            CPF: {formatCpf(cpf)} ainda não foi consultado na Serasa.
          </p>
        </div>
        <button
          onClick={() => consultSerasa()}
          disabled={isConsulting}
          className="flex items-center gap-2 px-5 py-2.5 rounded-xl font-sans font-bold text-sm text-white transition-all"
          style={{ background: "#E4006F" }}
        >
          <Icon name={isConsulting ? "hourglass_empty" : "analytics"} className="text-base" />
          {isConsulting ? "Consultando..." : "Consultar Serasa"}
        </button>
      </div>
    );
  }

  const reg = profile.registration ?? {};
  const normalizedPersonLabel = (profile.personName ?? formatCpf(profile.cpf)).trim().toLocaleLowerCase("pt-BR");
  const normalizedBackTargetLabel = (backTargetLabel ?? "").trim().toLocaleLowerCase("pt-BR");
  const effectiveBackTargetLabel =
    backTarget?.startsWith("/clients/") && normalizedBackTargetLabel === normalizedPersonLabel
      ? null
      : backTargetLabel;
  const contextualBackLabel = backTarget?.startsWith("/clients/")
    ? `Voltar para ${effectiveBackTargetLabel || "Empresa"}`
    : backTarget?.startsWith("/")
      ? `Voltar para ${effectiveBackTargetLabel || "origem"}`
      : null;
  const neg = profile.negativeSummary ?? {};
  const facts = profile.facts ?? {};
  const partnerCompanies = profile.partnerCompanies?.partnershipResponse ?? [];
  const registeredCompanyCnpjs = new Set((profile.registeredCompanyCnpjs ?? []).map((cnpj) => normalizeCnpj(cnpj)));
  const inquiries = facts.inquiry?.inquiryResponse ?? [];
  const monthlyCreditInquiries = facts.inquirySummary?.inquiryQuantity?.creditInquiriesQuantity ?? [];
  const addressHistory = reg.addresses ?? [];
  const pefinRecords = neg.pefin?.pefinResponse ?? [];
  const refinRecords = neg.refin?.refinResponse ?? [];
  const checkRecords = neg.check?.checkResponse ?? [];
  const notaryRecords = neg.notary?.notaryResponse ?? [];
  const collectionRecords = neg.collectionRecords?.collectionRecordsResponse ?? [];
  const stolenDocuments = facts.stolenDocuments?.stolenDocumentsResponse ?? [];
  const judgementRecords = facts.judgementFilings?.judgementFilingsResponse ?? [];
  const bankruptRecords = facts.bankrupts?.bankruptsResponse ?? [];
  const totalNeg = totalNegativesFromPF(profile);
  const totalDebt = totalDebtFromPF(profile);
  const activePartnerCompanies = partnerCompanies.filter((company) => !isInactiveCompanyStatus(company.companyStatus));
  const inactivePartnerCompanies = partnerCompanies.filter((company) => isInactiveCompanyStatus(company.companyStatus));
  const visiblePartnerCompanies = showInactiveCompanies
    ? [...activePartnerCompanies, ...inactivePartnerCompanies]
    : activePartnerCompanies;

  const situacaoInfo = SITUACAO[classificarSituacao(totalNeg, totalDebt)];
  const tomAvatar = tomDoAvatar(profile.cpf);
  const idade = reg.birthDate && !Number.isNaN(new Date(reg.birthDate).getTime())
    ? Math.floor(diasDesde(reg.birthDate) / 365.25)
    : null;
  const localPessoa = [caixaTitulo(reg.address?.city ?? null), reg.address?.state].filter(Boolean).join(" · ");
  const cadastroRegular = (reg.statusRegistration ?? "").trim().toUpperCase() === "REGULAR";
  const empresasNaCarteira = activePartnerCompanies.filter((c) => registeredCompanyCnpjs.has(normalizeCnpj(c.businessDocument))).length;
  const diasConsulta = diasDesde(profile.consultaEm);
  const consultaVelha = diasConsulta > DIAS_CONSULTA_VELHA;
  const temConsultas = inquiries.length > 0 || monthlyCreditInquiries.length > 0;
  const secoesIndice: SecaoIndice[] = [
    { id: "cadastro", rotulo: "Cadastro" },
    { id: "contato", rotulo: "Endereço e contato" },
    { id: "restricoes", rotulo: "Restrições" },
    ...(temConsultas ? [{ id: "consultas", rotulo: "Consultas" }] : []),
    ...(partnerCompanies.length > 0 ? [{ id: "empresas", rotulo: "Empresas" }] : []),
    { id: "equipe", rotulo: "Equipe" },
  ];

  const expandableIds = [
    { id: "pefin", hasRecords: pefinRecords.length > 0 },
    { id: "refin", hasRecords: refinRecords.length > 0 },
    { id: "check", hasRecords: checkRecords.length > 0 },
    { id: "notary", hasRecords: notaryRecords.length > 0 },
    { id: "collection", hasRecords: collectionRecords.length > 0 },
    { id: "stolen", hasRecords: stolenDocuments.length > 0 },
    { id: "judgement", hasRecords: judgementRecords.length > 0 },
    { id: "bankrupt", hasRecords: bankruptRecords.length > 0 },
  ]
    .filter((item) => item.hasRecords)
    .map((item) => item.id);
  const allExpanded = expandableIds.length > 0 && expandableIds.every((id) => openSections[id]);

  const toggleSection = (id: string) => {
    setOpenSections((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const handleDownloadPdf = async () => {
    try {
      const token = localStorage.getItem("serasa_token");
      const response = await fetch(`/api/person/${profile.cpf}/report-pdf`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });

      if (!response.ok) {
        throw new Error("Erro ao gerar PDF da análise PF.");
      }

      const pdfBlob = await response.blob();
      const url = URL.createObjectURL(pdfBlob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `analise_pf_${profile.cpf}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error(error);
      alert(error instanceof Error ? error.message : "Erro ao gerar PDF da análise PF.");
    }
  };

  const handleCreateCompany = async (cnpj: string) => {
    const cnpjClean = normalizeCnpj(cnpj);
    if (cnpjClean.length !== 14) {
      toast.error("CNPJ inválido para criação da empresa.");
      return;
    }

    setCreatingCompanyCnpj(cnpjClean);
    const tab = reserveNewTab();
    try {
      const token = localStorage.getItem("serasa_token");
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1"}/company/enrich/cnpja/${cnpjClean}`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
        },
      );

      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.error || error.message || "Não foi possível criar a empresa.");
      }

      toast.success("Empresa criada com sucesso na carteira.");
      queryClient.invalidateQueries({ queryKey: ["personProfile", cpf.replace(/\D/g, "")] });
      tab.go(`/clients/${cnpjClean}?${buildContextQuery(pathname, profile.personName ?? formatCpf(profile.cpf))}`);
    } catch (error) {
      tab.cancel();
      toast.error(error instanceof Error ? error.message : "Erro ao criar empresa.");
    } finally {
      setCreatingCompanyCnpj(null);
    }
  };

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px]">
          <button
            type="button"
            onClick={handleGoIndividuals}
            className="inline-flex items-center gap-1 font-medium text-suave transition-colors hover:text-primary"
          >
            <Icon name="arrow_back" size={16} />
            Voltar para Pessoas Físicas
          </button>
          {contextualBackLabel && (
            <button
              type="button"
              onClick={handleGoBack}
              className="inline-flex items-center gap-1 font-medium text-suave transition-colors hover:text-primary"
            >
              <Icon name="arrow_back" size={16} />
              {contextualBackLabel}
            </button>
          )}
        </div>

        <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
          <div className="flex min-w-0 items-start gap-4 animate-subir motion-reduce:animate-none">
            <span
              className="grid h-16 w-16 shrink-0 place-items-center rounded-[20px] text-xl font-bold"
              style={{ background: tomAvatar.fundo, color: tomAvatar.texto }}
            >
              {iniciais(profile.personName)}
            </span>
            <div className="min-w-0">
              <p className="text-xs font-bold uppercase tracking-[0.14em] text-laranja-texto">Pessoa física · análise Serasa</p>
              <h1 className="mt-1.5 text-3xl font-semibold leading-[1.05] tracking-[-0.02em] text-grafite sm:text-[40px]">
                {caixaTitulo(profile.personName) || "Nome não disponível"}
              </h1>
              <p className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-suave">
                <span className="font-mono text-[13px]">{formatCpf(profile.cpf)}</span>
                {idade != null && (
                  <>
                    <span aria-hidden>·</span>
                    <span>{idade} anos</span>
                  </>
                )}
                {localPessoa && (
                  <>
                    <span aria-hidden>·</span>
                    <span>{localPessoa}</span>
                  </>
                )}
              </p>
              <div className="mt-3 flex flex-wrap items-center gap-1.5">
                <span
                  className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-bold"
                  style={{ background: situacaoInfo.fundo, color: situacaoInfo.texto }}
                >
                  <span className="h-1.5 w-1.5 rounded-full" style={{ background: situacaoInfo.cor }} />
                  {situacaoInfo.rotulo}
                </span>
                {reg.statusRegistration &&
                  (cadastroRegular ? (
                    <span className="rounded-md bg-[#E2F0E9] px-2 py-0.5 text-[11px] font-bold text-[#17583F]">CPF regular</span>
                  ) : (
                    <span className="rounded-md bg-[#F8E1E6] px-2 py-0.5 text-[11px] font-bold text-[#8E1B37]">
                      CPF: {caixaTitulo(reg.statusRegistration)}
                    </span>
                  ))}
                {activePartnerCompanies.length > 0 && (
                  <span className="rounded-md bg-[#EFEDE6] px-2 py-0.5 text-[11px] font-bold text-[#5E5C55]">
                    {activePartnerCompanies.length === 1 ? "1 empresa no QSA" : `${activePartnerCompanies.length} empresas no QSA`}
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <button onClick={() => consultSerasa()} disabled={isConsulting} className={BOTAO.serasa}>
              <Icon name={isConsulting ? "progress_activity" : "refresh"} className={isConsulting ? "animate-spin" : ""} />
              {isConsulting ? "Consultando…" : "Atualizar Serasa"}
            </button>
            <button onClick={handleDownloadPdf} className={BOTAO.fantasma}>
              <Icon name="picture_as_pdf" />
              Gerar PDF
            </button>
          </div>
        </div>
      </header>

      <section aria-label="Resumo da pessoa" className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <BlocoResumo
          icone="warning"
          cor="#D1732C"
          rotulo="Restrições"
          atraso={60}
          onClick={() => irParaSecao("restricoes")}
          corValor={situacaoInfo.cor}
          valor={totalNeg === 0 ? "Nada consta" : formatMoeda(totalDebt)}
          apoio={totalNeg === 0 ? "Nenhuma ocorrência no relatório" : totalNeg === 1 ? "1 ocorrência" : `${totalNeg} ocorrências`}
        />
        <BlocoResumo
          icone="badge"
          cor="#612035"
          rotulo="Situação do CPF"
          atraso={120}
          onClick={() => irParaSecao("cadastro")}
          corValor={!reg.statusRegistration ? "#8A877E" : cadastroRegular ? "#1F7A5C" : "#B42345"}
          valor={reg.statusRegistration ? (cadastroRegular ? "Regular" : caixaTitulo(reg.statusRegistration)) : "—"}
          apoio={reg.statusDate ? `Situação desde ${formatDate(reg.statusDate)}` : "Situação cadastral do CPF"}
        />
        <BlocoResumo
          icone="business"
          cor="#612035"
          rotulo="Empresas no QSA"
          atraso={180}
          onClick={() => irParaSecao(partnerCompanies.length > 0 ? "empresas" : "cadastro")}
          valor={activePartnerCompanies.length.toLocaleString("pt-BR")}
          apoio={
            activePartnerCompanies.length === 0
              ? "Não aparece em quadro societário"
              : `${empresasNaCarteira} na carteira${inactivePartnerCompanies.length > 0 ? ` · ${inactivePartnerCompanies.length} baixada(s)` : ""}`
          }
        />
        <BlocoResumo
          icone="history"
          cor={consultaVelha ? "#D1732C" : "#612035"}
          rotulo="Consulta Serasa"
          atraso={240}
          onClick={() => irParaSecao(temConsultas ? "consultas" : "cadastro")}
          corValor={consultaVelha ? "#944C17" : undefined}
          valor={formatDate(profile.consultaEm)}
          apoio={consultaVelha ? `${tempoRelativo(diasConsulta)} · vale reconsultar` : tempoRelativo(diasConsulta)}
        />
      </section>

      <IndiceSecoes secoes={secoesIndice} />

      <section id="cadastro" className="space-y-6 scroll-mt-20">
      <SectionCard title="Dados Pessoais" icon="person">
        <dl className="grid gap-x-8 gap-y-3.5 sm:grid-cols-2 xl:grid-cols-3">
          <InfoRow label="CPF" value={formatCpf(reg.documentNumber ?? profile.cpf)} />
          <InfoRow label="Nome da Mãe" value={reg.motherName} />
          <InfoRow label="Data de Nascimento" value={formatDate(reg.birthDate)} />
          <InfoRow
            label="Gênero"
            value={
              reg.consumerGender === "M"
                ? "Masculino"
                : reg.consumerGender === "F"
                  ? "Feminino"
                  : reg.consumerGender
            }
          />
          <InfoRow label="Estado Civil" value={reg.maritalStatusDescription} />
          <InfoRow
            label="Naturalidade"
            value={
              reg.birthSquareCity && reg.birthSquareState
                ? `${reg.birthSquareCity} / ${reg.birthSquareState}`
                : undefined
            }
          />
          <InfoRow label="Profissão" value={reg.jobDescription ?? reg.profession} />
          <InfoRow label="Ocupação" value={reg.occupation} />
          <InfoRow label="Escolaridade" value={reg.educationLevelDescription} />
          <InfoRow label="Empresa" value={reg.corporateCompanyName} />
          <InfoRow
            label="CNPJ da Empresa"
            value={
              reg.corporateCompanyDocumentNumber
                ? formatCnpj(reg.corporateCompanyDocumentNumber.replace(/\D/g, ""))
                : undefined
            }
          />
          <InfoRow label="Dependentes" value={reg.dependentsNumber} />
          <InfoRow label="Situação Cadastral" value={reg.statusRegistration} />
          <InfoRow label="Data da Situação" value={formatDate(reg.statusDate)} />
        </dl>
      </SectionCard>

      {(reg.alternativeDocumentNumber || reg.issuingAgency) && (
        <SectionCard title="Documentos" icon="badge">
          <dl className="grid gap-x-6 gap-y-3.5 sm:grid-cols-2">
            <InfoRow
              label={reg.alternativeDocumentDescription ?? "Documento Alternativo"}
              value={reg.alternativeDocumentNumber}
            />
            <InfoRow
              label="Órgão Emissor"
              value={
                reg.issuingAgency && reg.issuingAgencyState
                  ? `${reg.issuingAgency} — ${reg.issuingAgencyState}`
                  : reg.issuingAgency
              }
            />
            <InfoRow label="Data Emissão" value={formatDate(reg.alternativeDocumentDate)} />
          </dl>
        </SectionCard>
      )}

      </section>

      <section id="contato" className="space-y-6 scroll-mt-20">
      <div className="grid grid-cols-1 items-start md:grid-cols-2 gap-6">
        {reg.address && (
          <SectionCard title="Endereço" icon="location_on">
            <dl className="grid gap-x-6 gap-y-3.5 sm:grid-cols-2">
              <InfoRow label="Logradouro" value={reg.address.addressLine} />
              <InfoRow label="Número" value={reg.address.addressNumber} />
              <InfoRow label="Complemento" value={reg.address.addressComplement} />
              <InfoRow label="Bairro" value={reg.address.district} />
              <InfoRow
                label="Cidade/UF"
                value={reg.address.city && reg.address.state ? `${reg.address.city} / ${reg.address.state}` : undefined}
              />
              <InfoRow label="CEP" value={reg.address.zipCode} />
              <InfoRow label="País" value={reg.address.country} />
            </dl>
          </SectionCard>
        )}

        {(reg.phones ?? []).length > 0 && (
          <SectionCard title="Telefones" icon="phone">
            <ul className="space-y-2">
              {(reg.phones ?? []).map((phone, index) => (
                <li
                  key={`${phone.phoneNumber ?? "phone"}-${index}`}
                  className="flex items-center justify-between gap-4 py-2 border-b border-linha/50 dark:border-border-dark/50 last:border-0"
                >
                  <span className="text-sm font-sans text-grafite dark:text-gray-200">
                    {formatPhone(phone.areaCode, phone.phoneNumber)}
                  </span>
                  <div className="text-right">
                    <p className="text-xs text-gray-400 font-sans">{translatePhoneType(phone.phoneType)}</p>
                    {phone.updateDate && (
                      <p className="text-[11px] text-gray-400 font-sans mt-0.5">
                        Atualizado em {formatDate(phone.updateDate)}
                      </p>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </SectionCard>
        )}
      </div>

      {addressHistory.length > 1 && (
        <SectionCard
          title="Histórico de Endereços"
          icon="home_work"
          actions={
            <button
              type="button"
              onClick={() => setIsAddressHistoryOpen((prev) => !prev)}
              className="text-xs text-gray-400 hover:text-primary transition-colors underline underline-offset-2"
            >
              {isAddressHistoryOpen ? "recolher histórico" : "expandir histórico"}
            </button>
          }
        >
          <p className="text-sm font-sans text-gray-500 mb-4">
            {addressHistory.length} endereço{addressHistory.length !== 1 ? "s" : ""} encontrado
            {addressHistory.length !== 1 ? "s" : ""}
          </p>
          {isAddressHistoryOpen && (
            <ul className="space-y-3">
              {addressHistory.map((address, index) => (
                <li
                  key={`${address.addressLine ?? "address"}-${index}`}
                  className="rounded-xl border border-linha dark:border-border-dark p-4"
                >
                  <p className="text-sm font-sans text-grafite dark:text-gray-200 leading-6">
                    {formatAddress(address)}
                  </p>
                  {address.updateDate && (
                    <p className="text-xs text-gray-400 font-sans mt-2">Atualizado em {formatDate(address.updateDate)}</p>
                  )}
                </li>
              ))}
            </ul>
          )}
        </SectionCard>
      )}

      </section>

      <section id="restricoes" className="space-y-6 scroll-mt-20">
      <div>
        <div className="flex items-center justify-between gap-4 mb-4">
          <h2 className="flex items-center gap-2 font-sans text-base font-bold text-primary"><Icon name="warning" className="text-lg" /> Dados Negativos</h2>
          {expandableIds.length > 0 && (
            <button
              type="button"
              onClick={() =>
                setOpenSections(allExpanded ? {} : Object.fromEntries(expandableIds.map((id) => [id, true])))
              }
              className="text-xs text-gray-400 hover:text-primary transition-colors underline underline-offset-2"
            >
              {allExpanded ? "recolher tudo" : "expandir tudo"}
            </button>
          )}
        </div>
        <GradeOcorrencias
          abertos={openSections}
          onAlternar={toggleSection}
          itens={[
            { id: "pefin", title: "PEFIN", icon: "receipt_long", section: neg.pefin, hasDetail: pefinRecords.length > 0 },
            { id: "refin", title: "REFIN", icon: "account_balance", section: neg.refin, hasDetail: refinRecords.length > 0 },
            { id: "check", title: "Cheques", icon: "payment", section: neg.check, hasDetail: checkRecords.length > 0 },
            { id: "notary", title: "Protestos", icon: "gavel", section: neg.notary, hasDetail: notaryRecords.length > 0 },
            { id: "collection", title: "Dívidas Vencidas", icon: "request_quote", section: neg.collectionRecords, hasDetail: collectionRecords.length > 0 },
          ]}
        />

        {openSections.pefin && pefinRecords.length > 0 && (
          <NegDetailTable title="PEFIN — Pendências Financeiras" shownCount={pefinRecords.length} totalCount={neg.pefin?.summary?.count}>
            <PefinRefinTable records={pefinRecords} />
          </NegDetailTable>
        )}
        {openSections.refin && refinRecords.length > 0 && (
          <NegDetailTable title="REFIN — Refinanciamentos" shownCount={refinRecords.length} totalCount={neg.refin?.summary?.count}>
            <PefinRefinTable records={refinRecords} />
          </NegDetailTable>
        )}
        {openSections.check && checkRecords.length > 0 && (
          <NegDetailTable title="Cheques" shownCount={checkRecords.length} totalCount={neg.check?.summary?.count}>
            <CheckDetailTable records={checkRecords} />
          </NegDetailTable>
        )}
        {openSections.notary && notaryRecords.length > 0 && (
          <NegDetailTable title="Protestos em Cartório" shownCount={notaryRecords.length} totalCount={neg.notary?.summary?.count}>
            <NotaryDetailTable records={notaryRecords} />
          </NegDetailTable>
        )}
        {openSections.collection && collectionRecords.length > 0 && (
          <NegDetailTable title="Dívidas Vencidas" shownCount={collectionRecords.length} totalCount={neg.collectionRecords?.summary?.count}>
            <CollectionDetailTable records={collectionRecords} />
          </NegDetailTable>
        )}
      </div>

      <SectionCard title="Fatos Relevantes" icon="balance">
        <GradeOcorrencias
          abertos={openSections}
          onAlternar={toggleSection}
          itens={[
            { id: "stolen", title: "Documentos Roubados", icon: "report", section: { summary: facts.stolenDocuments?.summary }, hasDetail: stolenDocuments.length > 0 },
            { id: "judgement", title: "Ações Judiciais", icon: "gavel", section: { summary: facts.judgementFilings?.summary }, hasDetail: judgementRecords.length > 0 },
            { id: "bankrupt", title: "Falências", icon: "do_not_disturb_on", section: { summary: facts.bankrupts?.summary }, hasDetail: bankruptRecords.length > 0 },
          ]}
        />

        {openSections.stolen && stolenDocuments.length > 0 && (
          <NegDetailTable
            title="Documentos Roubados / Perdidos"
            shownCount={stolenDocuments.length}
            totalCount={facts.stolenDocuments?.summary?.count}
          >
            <StolenDocumentsTable records={stolenDocuments} />
          </NegDetailTable>
        )}
        {openSections.judgement && judgementRecords.length > 0 && (
          <NegDetailTable
            title="Ações Judiciais"
            shownCount={judgementRecords.length}
            totalCount={facts.judgementFilings?.summary?.count}
          >
            <JudgementDetailTable records={judgementRecords} />
          </NegDetailTable>
        )}
        {openSections.bankrupt && bankruptRecords.length > 0 && (
          <NegDetailTable
            title="Falências / Recuperação Judicial"
            shownCount={bankruptRecords.length}
            totalCount={facts.bankrupts?.summary?.count}
          >
            <BankruptDetailTable records={bankruptRecords} />
          </NegDetailTable>
        )}
      </SectionCard>

      </section>

      <section id="consultas" className="space-y-6 scroll-mt-20">
      {inquiries.length > 0 && (
        <SectionCard title="Consultas Recentes" icon="manage_search">
          <ul className="space-y-0">
            {inquiries.map((inquiry, index) => (
              <li
                key={`${inquiry.occurrenceDate ?? "inq"}-${index}`}
                className="flex items-center justify-between py-3 border-b border-linha/50 dark:border-border-dark/50 last:border-0"
              >
                <div className="flex items-center gap-3">
                  <Icon name="search" className="text-base text-gray-400" />
                  <span className="text-sm font-sans text-grafite dark:text-gray-200">
                    {inquiry.segmentDescription ?? "Consulta"}
                  </span>
                </div>
                <span className="text-xs text-gray-400 font-sans">{formatDate(inquiry.occurrenceDate)}</span>
              </li>
            ))}
          </ul>
        </SectionCard>
      )}

      {monthlyCreditInquiries.length > 0 && (
        <SectionCard title="Histórico Mensal de Consultas" icon="timeline">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[520px]">
              <thead>
                <tr className="border-b border-linha dark:border-border-dark">
                  <th className="text-left py-3 pr-4 text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">
                    Mês
                  </th>
                  <th className="text-center py-3 px-4 text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">
                    Consultas
                  </th>
                  <th className="text-center py-3 px-4 text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">
                    Bancos
                  </th>
                  <th className="text-center py-3 pl-4 text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">
                    Empresas
                  </th>
                </tr>
              </thead>
              <tbody>
                {monthlyCreditInquiries.map((item: PFMonthlyInquiryItem, index) => (
                  <tr
                    key={`${item.inquiryDate ?? "month"}-${index}`}
                    className="border-b border-linha/50 dark:border-border-dark/50 last:border-0"
                  >
                    <td className="py-3 pr-4 text-sm font-sans text-grafite dark:text-gray-200">
                      {formatMonthLabel(item.inquiryDate)}
                    </td>
                    <td className="py-3 px-4 text-center text-sm font-sans text-grafite dark:text-gray-200">
                      {item.occurrences ?? 0}
                    </td>
                    <td className="py-3 px-4 text-center text-sm font-sans text-grafite dark:text-gray-200">
                      {item.bankOccurrences ?? 0}
                    </td>
                    <td className="py-3 pl-4 text-center text-sm font-sans text-grafite dark:text-gray-200">
                      {item.companyOccurrences ?? 0}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </SectionCard>
      )}

      </section>

      <section id="empresas" className="scroll-mt-20">
      {partnerCompanies.length > 0 && (
        <SectionCard
          title={`Empresas — Sociedades (${partnerCompanies.length})`}
          icon="business"
          actions={
            inactivePartnerCompanies.length > 0 ? (
              <button
                type="button"
                onClick={() => setShowInactiveCompanies((current) => !current)}
                className="inline-flex items-center gap-1 rounded-full border border-linha dark:border-border-dark px-3 py-1 text-[11px] font-sans font-bold uppercase tracking-wide text-gray-500 transition-colors hover:border-primary/30 hover:text-primary"
              >
                <Icon name={showInactiveCompanies ? "unfold_less" : "unfold_more"} className="text-sm" />
                {showInactiveCompanies ? "Ocultar baixadas" : `Ver baixadas (${inactivePartnerCompanies.length})`}
              </button>
            ) : undefined
          }
        >
          <ul className="space-y-0">
            {visiblePartnerCompanies.map((company: PFPartnerCompany, index) => {
              const cnpjClean = normalizeCnpj(company.businessDocument);
              const isRegistered = registeredCompanyCnpjs.has(cnpjClean);
              const isInactive = isInactiveCompanyStatus(company.companyStatus);
              return (
                <li
                  key={`${cnpjClean || "company"}-${index}`}
                  className="flex items-center justify-between gap-4 py-3.5 border-b border-linha/50 dark:border-border-dark/50 last:border-0"
                >
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-sans font-medium text-grafite dark:text-white truncate">
                      {company.companyName ?? "—"}
                    </p>
                    <p className="text-xs text-gray-400 font-sans mt-0.5">
                      CNPJ: {cnpjClean.length === 14 ? formatCnpj(cnpjClean) : cnpjClean}
                      {company.participationPercentage != null && company.participationPercentage > 0
                        ? ` · ${company.participationPercentage}% participação`
                        : ""}
                      {company.companyState ? ` · ${company.companyState}` : ""}
                    </p>
                    {(company.companyStatus || company.companyStatusDate || company.participationInitialDate || company.updateDate) && (
                      <p className="text-xs text-gray-400 font-sans mt-1">
                        {[
                          company.companyStatus,
                          company.companyStatusDate ? `situação em ${formatDate(company.companyStatusDate)}` : null,
                          company.participationInitialDate ? `desde ${formatDate(company.participationInitialDate)}` : null,
                          company.updateDate ? `atualizado em ${formatDate(company.updateDate)}` : null,
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      </p>
                    )}
                    {isInactive && (
                      <p className="text-[11px] font-sans font-medium text-amber-700 dark:text-amber-400 mt-1">
                        Empresa baixada/cancelada
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    {company.hasNegative && <span className="w-2 h-2 rounded-full bg-red-500 flex-shrink-0" title="Com restrição" />}
                    {cnpjClean.length === 14 && isRegistered && (
                      <Link
                        href={`/clients/${cnpjClean}?${buildContextQuery(pathname, profile.personName ?? formatCpf(profile.cpf))}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-xs font-sans font-medium text-primary hover:text-primary/80 transition-colors flex items-center gap-0.5"
                      >
                        Ver empresa
                        <Icon name="open_in_new" className="text-sm" />
                      </Link>
                    )}
                    {cnpjClean.length === 14 && !isRegistered && (
                      <button
                        type="button"
                        onClick={() => handleCreateCompany(cnpjClean)}
                        disabled={creatingCompanyCnpj === cnpjClean}
                        className="inline-flex items-center gap-1 rounded-full border border-primary/20 bg-primary/5 px-3 py-1 text-[11px] font-sans font-bold text-primary transition-colors hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        <Icon name={creatingCompanyCnpj === cnpjClean ? "hourglass_empty" : "add_business"} className="text-sm" />
                        {creatingCompanyCnpj === cnpjClean ? "Criando..." : "Criar empresa"}
                      </button>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
          {!showInactiveCompanies && inactivePartnerCompanies.length > 0 && (
            <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs font-sans text-amber-800 dark:border-amber-800 dark:bg-amber-900/10 dark:text-amber-300">
              Exibindo {activePartnerCompanies.length} empresa(s) ativa(s). {inactivePartnerCompanies.length} empresa(s) baixada(s) ou cancelada(s) ficaram ocultas para evitar ruído na análise.
            </div>
          )}
        </SectionCard>
      )}

      </section>

      <section id="equipe" className="scroll-mt-20">
        <PersonNotesPanel cpf={cpf} />
      </section>
    </div>
  );
}
