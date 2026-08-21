"use client";

import { useEffect, useMemo, useState } from "react";
import Icon from "@/components/ui/Icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
const PREVIEW_LIMIT = 5;

type CompanyPartner = {
  id: string;
  cnpj: string;
  companyName: string | null;
  alias: string | null;
  address: string | null;
  city: string | null;
  state: string | null;
  note: string | null;
  authorName: string | null;
  createdAt: string | null;
};

type CompanySearchItem = {
  cnpj: string;
  companyName: string | null;
  alias: string | null;
  address: string | null;
  city: string | null;
  state: string | null;
  alreadyPartner: boolean;
};

function getAuthHeaders() {
  const token = typeof window !== "undefined" ? localStorage.getItem("serasa_token") : null;
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

function formatCnpj(cnpj: string) {
  const d = (cnpj || "").replace(/\D/g, "").padStart(14, "0");
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12, 14)}`;
}

async function readError(res: Response, fallback: string) {
  const text = await res.text().catch(() => "");
  if (!text) return fallback;
  try {
    const json = JSON.parse(text);
    return json.message || json.error || fallback;
  } catch {
    return fallback;
  }
}

export function CompanyPartnersPanel({ cnpj }: { cnpj: string }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [term, setTerm] = useState("");
  const [debouncedTerm, setDebouncedTerm] = useState("");
  const [noteDraft, setNoteDraft] = useState<Record<string, string>>({});
  const [editingNote, setEditingNote] = useState<string | null>(null);

  useEffect(() => {
    const id = setTimeout(() => setDebouncedTerm(term.trim()), 300);
    return () => clearTimeout(id);
  }, [term]);

  const partnersQuery = useQuery<CompanyPartner[]>({
    queryKey: ["companyPartners", cnpj],
    enabled: Boolean(cnpj),
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/${cnpj}/parceiras`, { headers: getAuthHeaders() });
      if (!res.ok) throw new Error(await readError(res, "Erro ao carregar empresas parceiras"));
      return res.json();
    },
  });

  const searchQuery = useQuery<CompanySearchItem[]>({
    queryKey: ["companyPartnerSearch", cnpj, debouncedTerm],
    enabled: searchOpen && debouncedTerm.length >= 2,
    queryFn: async () => {
      const res = await fetch(
        `${API_BASE_URL}/company/${cnpj}/parceiras/busca?q=${encodeURIComponent(debouncedTerm)}`,
        { headers: getAuthHeaders() },
      );
      if (!res.ok) throw new Error(await readError(res, "Erro na busca de empresas"));
      return res.json();
    },
  });

  const invalidate = (partnerCnpj?: string) => {
    queryClient.invalidateQueries({ queryKey: ["companyPartners", cnpj] });
    queryClient.invalidateQueries({ queryKey: ["companyPartnerSearch", cnpj] });
    // O vínculo é bidirecional: o perfil da outra empresa também muda.
    if (partnerCnpj) queryClient.invalidateQueries({ queryKey: ["companyPartners", partnerCnpj] });
  };

  const addPartner = useMutation({
    mutationFn: async (partnerCnpj: string) => {
      const res = await fetch(`${API_BASE_URL}/company/${cnpj}/parceiras`, {
        method: "POST",
        headers: getAuthHeaders(),
        body: JSON.stringify({ partnerCnpj }),
      });
      if (!res.ok) throw new Error(await readError(res, "Falha ao vincular empresa parceira"));
      return partnerCnpj;
    },
    onSuccess: (partnerCnpj) => {
      toast.success("Empresa vinculada como parceira");
      invalidate(partnerCnpj);
    },
    onError: (e) => toast.error((e as Error).message),
  });

  const removePartner = useMutation({
    mutationFn: async (partnerCnpj: string) => {
      const res = await fetch(`${API_BASE_URL}/company/${cnpj}/parceiras/${partnerCnpj}`, {
        method: "DELETE",
        headers: getAuthHeaders(),
      });
      if (!res.ok) throw new Error(await readError(res, "Falha ao remover vínculo"));
      return partnerCnpj;
    },
    onSuccess: (partnerCnpj) => {
      toast.success("Vínculo removido");
      invalidate(partnerCnpj);
    },
    onError: (e) => toast.error((e as Error).message),
  });

  const saveNote = useMutation({
    mutationFn: async ({ partnerCnpj, note }: { partnerCnpj: string; note: string }) => {
      const res = await fetch(`${API_BASE_URL}/company/${cnpj}/parceiras/${partnerCnpj}`, {
        method: "PATCH",
        headers: getAuthHeaders(),
        body: JSON.stringify({ note }),
      });
      if (!res.ok) throw new Error(await readError(res, "Falha ao salvar observação"));
      return partnerCnpj;
    },
    onSuccess: (partnerCnpj) => {
      setEditingNote(null);
      invalidate(partnerCnpj);
    },
    onError: (e) => toast.error((e as Error).message),
  });

  const partners = useMemo(() => partnersQuery.data ?? [], [partnersQuery.data]);
  const shown = expanded ? partners : partners.slice(0, PREVIEW_LIMIT);
  const results = searchQuery.data ?? [];

  return (
    <div className="mb-6 rounded-xl border border-border-light bg-surface-light shadow-sm dark:border-border-dark dark:bg-surface-dark print:hidden">
      <div className="flex flex-wrap items-center gap-2 border-b border-border-light p-5 dark:border-border-dark">
        <Icon name="handshake" className="text-primary" />
        <h2 className="font-sans text-base font-bold text-primary">Empresas parceiras</h2>
        <span className="text-[11px] text-gray-400">· vínculo manual entre CNPJs do mesmo grupo</span>
        <div className="ml-auto flex items-center gap-3">
          {partners.length > 0 ? (
            <span className="text-xs text-gray-400">{partners.length} vinculadas</span>
          ) : null}
          <button
            type="button"
            onClick={() => setSearchOpen((v) => !v)}
            className="rounded-md bg-secondary px-2.5 py-1 text-xs font-semibold text-white transition hover:opacity-90"
          >
            {searchOpen ? "Fechar busca" : "Vincular empresa"}
          </button>
        </div>
      </div>

      {searchOpen ? (
        <div className="border-b border-border-light bg-gray-50/60 p-5 dark:border-border-dark dark:bg-white/5">
          <div className="relative">
            <Icon
              name="search"
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-base text-gray-400"
            />
            <input
              autoFocus
              value={term}
              onChange={(e) => setTerm(e.target.value)}
              placeholder="Buscar empresa cadastrada por nome ou CNPJ…"
              className="w-full rounded-lg border border-border-light bg-white py-2 pl-9 pr-3 text-sm text-grafite outline-none focus:border-primary dark:border-border-dark dark:bg-surface-dark dark:text-gray-200"
            />
          </div>

          <div className="mt-3 space-y-2">
            {debouncedTerm.length < 2 ? (
              <p className="text-xs text-gray-500">Digite ao menos 2 caracteres.</p>
            ) : searchQuery.isFetching ? (
              <p className="text-xs text-gray-500">Buscando…</p>
            ) : searchQuery.isError ? (
              <p className="text-xs text-gray-500">{(searchQuery.error as Error)?.message}</p>
            ) : results.length === 0 ? (
              <p className="text-xs text-gray-500">
                Nenhuma empresa cadastrada encontrada. Crie o perfil da empresa antes de vinculá-la.
              </p>
            ) : (
              results.map((item) => (
                <div
                  key={item.cnpj}
                  className="flex flex-wrap items-center gap-3 rounded-lg border border-border-light bg-white p-3 dark:border-border-dark dark:bg-surface-dark"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-sm font-semibold text-grafite dark:text-gray-200">
                        {formatCnpj(item.cnpj)}
                      </span>
                      <span className="truncate text-xs text-gray-500">
                        · {item.companyName ?? item.alias ?? "—"}
                      </span>
                    </div>
                    <p className="mt-0.5 truncate text-xs text-gray-500">
                      {item.address ?? `${item.city ?? "—"}/${item.state ?? ""}`}
                    </p>
                  </div>
                  {item.alreadyPartner ? (
                    <span className="rounded bg-primary/10 px-2 py-1 text-[10px] font-bold uppercase text-primary">
                      Já parceira
                    </span>
                  ) : (
                    <button
                      type="button"
                      onClick={() => addPartner.mutate(item.cnpj)}
                      disabled={addPartner.isPending}
                      className="rounded-md bg-secondary px-2.5 py-1 text-xs font-semibold text-white transition hover:opacity-90 disabled:opacity-50"
                    >
                      {addPartner.isPending && addPartner.variables === item.cnpj ? "Vinculando…" : "Marcar parceira"}
                    </button>
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      ) : null}

      <div className="p-5">
        {partnersQuery.isLoading ? (
          <p className="text-sm text-gray-500">Carregando parceiras…</p>
        ) : partnersQuery.isError ? (
          <p className="text-sm text-gray-500">{(partnersQuery.error as Error)?.message}</p>
        ) : partners.length === 0 ? (
          <p className="text-sm text-gray-500">
            Nenhuma empresa parceira vinculada. Use “Vincular empresa” para marcar empresas do mesmo grupo.
          </p>
        ) : (
          <div className="space-y-2">
            {shown.map((p) => (
              <div key={p.cnpj} className="rounded-lg border border-border-light p-3 dark:border-border-dark">
                <div className="flex flex-wrap items-center gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-sm font-semibold text-grafite dark:text-gray-200">
                        {formatCnpj(p.cnpj)}
                      </span>
                      <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] font-bold uppercase text-primary">
                        Parceira
                      </span>
                      {p.companyName || p.alias ? (
                        <span className="truncate text-xs text-gray-500">· {p.companyName ?? p.alias}</span>
                      ) : null}
                    </div>
                    <p className="mt-0.5 truncate text-xs text-gray-500">
                      {p.address ?? `${p.city ?? "—"}/${p.state ?? ""}`}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => router.push(`/clients/${p.cnpj.replace(/\D/g, "")}`)}
                    className="rounded-md border border-border-light px-2.5 py-1 text-xs font-semibold text-grafite transition hover:bg-gray-50 dark:border-border-dark dark:text-gray-200 dark:hover:bg-white/5"
                  >
                    Ver perfil
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setEditingNote(editingNote === p.cnpj ? null : p.cnpj);
                      setNoteDraft((d) => ({ ...d, [p.cnpj]: p.note ?? "" }));
                    }}
                    title="Observação do vínculo"
                    className="rounded-md border border-border-light p-1 text-gray-500 transition hover:bg-gray-50 dark:border-border-dark dark:hover:bg-white/5"
                  >
                    <Icon name="edit_note" className="text-base" />
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      if (confirm(`Remover o vínculo de parceria com ${formatCnpj(p.cnpj)}?`)) {
                        removePartner.mutate(p.cnpj);
                      }
                    }}
                    disabled={removePartner.isPending}
                    title="Remover vínculo"
                    className="rounded-md border border-border-light p-1 text-gray-500 transition hover:bg-red-50 hover:text-red-600 disabled:opacity-50 dark:border-border-dark dark:hover:bg-red-500/10"
                  >
                    <Icon name="link_off" className="text-base" />
                  </button>
                </div>

                {editingNote === p.cnpj ? (
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <input
                      value={noteDraft[p.cnpj] ?? ""}
                      onChange={(e) => setNoteDraft((d) => ({ ...d, [p.cnpj]: e.target.value }))}
                      placeholder="Observação (ex.: mesma controladora, opera como filial de fato)"
                      className="min-w-0 flex-1 rounded-md border border-border-light bg-white px-3 py-1.5 text-xs text-grafite outline-none focus:border-primary dark:border-border-dark dark:bg-surface-dark dark:text-gray-200"
                    />
                    <button
                      type="button"
                      onClick={() => saveNote.mutate({ partnerCnpj: p.cnpj, note: noteDraft[p.cnpj] ?? "" })}
                      disabled={saveNote.isPending}
                      className="rounded-md bg-primary px-2.5 py-1 text-xs font-semibold text-white transition hover:opacity-90 disabled:opacity-50"
                    >
                      Salvar
                    </button>
                    <button
                      type="button"
                      onClick={() => setEditingNote(null)}
                      className="rounded-md border border-border-light px-2.5 py-1 text-xs font-semibold text-grafite dark:border-border-dark dark:text-gray-200"
                    >
                      Cancelar
                    </button>
                  </div>
                ) : p.note ? (
                  <p className="mt-2 text-xs italic text-gray-500">“{p.note}”</p>
                ) : null}
              </div>
            ))}

            {partners.length > PREVIEW_LIMIT ? (
              <button
                type="button"
                onClick={() => setExpanded((v) => !v)}
                className="mt-1 text-xs font-semibold text-primary hover:underline"
              >
                {expanded ? "Ver menos" : `Ver todas (${partners.length})`}
              </button>
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
}
