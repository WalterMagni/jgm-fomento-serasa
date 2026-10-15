"use client";

import { useState, useEffect, useMemo } from "react";
import Icon from "@/components/ui/Icon";
import Segmentos from "@/components/ui/Segmentos";
import { BOTAO } from "@/components/ui/botao";
import { useApiUsage, useBillingSettings } from "@/hooks/useApiUsage";
import { useImportCsv } from "@/hooks/useImportCsv";
import { useImportClientCodes } from "@/hooks/useImportClientCodes";
import PapeisUsuario from "@/components/liberacao/PapeisUsuario";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

export default function SettingsPage() {
  const [activeTab, setActiveTab] = useState<"profile" | "consumo">("profile");
  const { importCsv, isImporting: isImportingCsv } = useImportCsv();
  const { importCodes, isImporting: isImportingCodes } = useImportClientCodes();
  const [entityFilter, setEntityFilter] = useState<"ALL" | "PJ" | "PF">("ALL");

  // Profile State
  const [userName, setUserName] = useState<string>(() => {
    if (typeof window !== "undefined") {
      return localStorage.getItem("serasa_user_name") || "";
    }
    return "";
  });
  const [userEmail, setUserEmail] = useState<string>(() => {
    if (typeof window !== "undefined") {
      return localStorage.getItem("serasa_user_email") || "";
    }
    return "";
  });
  const [emailNotificacaoCedente, setEmailNotificacaoCedente] = useState<boolean>(() => {
    if (typeof window !== "undefined") {
      const stored = localStorage.getItem("serasa_email_notificacao");
      return stored === null ? true : stored === "true";
    }
    return true;
  });
  const [canManageUsers, setCanManageUsers] = useState(false);
  const [managedUsers, setManagedUsers] = useState<Array<{
    id: string;
    name: string;
    email: string;
    role: string;
    emailNotificacaoCedente: boolean;
    analista: boolean;
    comite: boolean;
    diretoria?: boolean;
    createdAt?: string | null;
  }>>([]);
  const [isLoadingUsers, setIsLoadingUsers] = useState(false);
  const [isDeletingUserId, setIsDeletingUserId] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [documentBasePath, setDocumentBasePath] = useState("");
  const [documentBasePathDraft, setDocumentBasePathDraft] = useState("");
  const [documentBasePathUpdatedBy, setDocumentBasePathUpdatedBy] = useState<string | null>(null);
  const [documentBasePathUpdatedAt, setDocumentBasePathUpdatedAt] = useState<string | null>(null);
  const [isLoadingDocumentSettings, setIsLoadingDocumentSettings] = useState(false);
  const [isSavingDocumentSettings, setIsSavingDocumentSettings] = useState(false);

  // Load fresh user profile from API on mount
  useEffect(() => {
    const token = localStorage.getItem("serasa_token");
    if (!token) return;
    fetch(`${API_URL.replace("/api/v1", "")}/api/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((r) => r.ok ? r.json() : null)
      .then((data) => {
        if (!data) return;
        if (data.name) setUserName(data.name);
        if (data.email) setUserEmail(data.email);
        setCanManageUsers(Boolean(data.canManageUsers));
        if (typeof data.emailNotificacaoCedente === "boolean") {
          setEmailNotificacaoCedente(data.emailNotificacaoCedente);
          localStorage.setItem("serasa_email_notificacao", String(data.emailNotificacaoCedente));
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!canManageUsers) {
      setManagedUsers([]);
      return;
    }

    const token = localStorage.getItem("serasa_token");
    if (!token) return;

    setIsLoadingUsers(true);
    fetch(`${API_URL.replace("/api/v1", "")}/api/auth/users`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((r) => (r.ok ? r.json() : []))
      .then((data) => setManagedUsers(Array.isArray(data) ? data : []))
      .catch(() => setManagedUsers([]))
      .finally(() => setIsLoadingUsers(false));
  }, [canManageUsers]);

  useEffect(() => {
    if (!canManageUsers) {
      setDocumentBasePath("");
      setDocumentBasePathDraft("");
      return;
    }

    const token = localStorage.getItem("serasa_token");
    if (!token) return;

    setIsLoadingDocumentSettings(true);
    fetch(`${API_URL}/document-storage/settings`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        if (!data) return;
        const basePath = data.basePath || "";
        setDocumentBasePath(basePath);
        setDocumentBasePathDraft(basePath);
        setDocumentBasePathUpdatedBy(data.updatedByName || null);
        setDocumentBasePathUpdatedAt(data.updatedAt || null);
      })
      .catch(() => {})
      .finally(() => setIsLoadingDocumentSettings(false));
  }, [canManageUsers]);

  // Consumo State & API Fetch
  const [period, setPeriod] = useState<"7" | "15" | "30" | "60" | "90">("30");
  const { logs, isLoading, isError } = useApiUsage();
  const { settings, isLoading: isLoadingBillingSettings } = useBillingSettings();

  const filteredLogs = useMemo(() => {
    const days = parseInt(period, 10);
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - days);
    return logs
      .filter((log) => new Date(log.timestamp) >= cutoffDate)
      .filter((log) => entityFilter === "ALL" ? true : log.entityType === entityFilter)
      .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
  }, [logs, period, entityFilter]);

  const totalCost = filteredLogs.reduce((acc, log) => acc + log.cost, 0);
  const totalPf = filteredLogs.filter((log) => log.entityType === "PF").length;
  const totalPj = filteredLogs.filter((log) => log.entityType === "PJ").length;
  const custoPf = filteredLogs.filter((log) => log.entityType === "PF").reduce((acc, log) => acc + log.cost, 0);
  const custoPj = filteredLogs.filter((log) => log.entityType === "PJ").reduce((acc, log) => acc + log.cost, 0);

  const formatDocument = (raw?: string) => {
    if (!raw) return "—";
    const digits = raw.replace(/\D/g, "");
    if (digits.length === 11) {
      return digits.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4");
    }
    if (digits.length === 14) {
      return digits.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
    }
    return raw;
  };

  const queryTypeLabel = (queryType: string, entityType: "PF" | "PJ") => {
    if (entityType === "PF" || queryType === "PF") return "CONSULTA PF";
    return queryType === "INITIAL" ? "INICIAL" : "ATUALIZAÇÃO";
  };

  const handleSaveProfile = async () => {
    setIsSaving(true);
    try {
      const token = localStorage.getItem("serasa_token");
      const headers: HeadersInit = { "Content-Type": "application/json" };
      if (token) headers["Authorization"] = `Bearer ${token}`;

      const res = await fetch(`${API_URL.replace("/api/v1", "")}/api/auth/profile`, {
        method: "PATCH",
        headers,
        body: JSON.stringify({ name: userName, emailNotificacaoCedente }),
      });

      if (res.ok) {
        localStorage.setItem("serasa_user_name", userName);
        localStorage.setItem("serasa_user_email", userEmail);
        localStorage.setItem("serasa_email_notificacao", String(emailNotificacaoCedente));
        alert("Preferências salvas com sucesso.");
      } else {
        // fallback: save locally only
        localStorage.setItem("serasa_user_name", userName);
        localStorage.setItem("serasa_user_email", userEmail);
        localStorage.setItem("serasa_email_notificacao", String(emailNotificacaoCedente));
        alert("Informações salvas localmente.");
      }
    } catch {
      localStorage.setItem("serasa_user_name", userName);
      localStorage.setItem("serasa_user_email", userEmail);
      localStorage.setItem("serasa_email_notificacao", String(emailNotificacaoCedente));
      alert("Informações salvas localmente.");
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteUser = async (userId: string, email: string) => {
    if (!confirm(`Apagar o usuário ${email}? Essa ação não pode ser desfeita.`)) return;

    setIsDeletingUserId(userId);
    try {
      const token = localStorage.getItem("serasa_token");
      const headers: HeadersInit = {};
      if (token) headers["Authorization"] = `Bearer ${token}`;

      const res = await fetch(`${API_URL.replace("/api/v1", "")}/api/auth/users/${userId}`, {
        method: "DELETE",
        headers,
      });

      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.error || "Não foi possível apagar o usuário.");
      }

      setManagedUsers((current) => current.filter((user) => user.id !== userId));
      alert("Usuário removido com sucesso.");
    } catch (error) {
      alert(error instanceof Error ? error.message : "Erro ao apagar usuário.");
    } finally {
      setIsDeletingUserId(null);
    }
  };

  const handleSaveDocumentSettings = async () => {
    setIsSavingDocumentSettings(true);
    try {
      const token = localStorage.getItem("serasa_token");
      const headers: HeadersInit = { "Content-Type": "application/json" };
      if (token) headers["Authorization"] = `Bearer ${token}`;

      const res = await fetch(`${API_URL}/document-storage/settings`, {
        method: "PUT",
        headers,
        body: JSON.stringify({ basePath: documentBasePathDraft }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.message || data.error || "Não foi possível salvar a pasta base.");
      }

      setDocumentBasePath(data.basePath || "");
      setDocumentBasePathDraft(data.basePath || "");
      setDocumentBasePathUpdatedBy(data.updatedByName || null);
      setDocumentBasePathUpdatedAt(data.updatedAt || null);
      alert("Pasta base dos documentos salva com sucesso.");
    } catch (error) {
      alert(error instanceof Error ? error.message : "Erro ao salvar pasta base dos documentos.");
    } finally {
      setIsSavingDocumentSettings(false);
    }
  };

  const moeda = (valor: number) => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(valor);
  const CARTAO = "rounded-cartao border border-linha bg-white p-6";
  const TITULO = "flex items-center gap-2 font-sans text-base font-bold text-primary";
  const ROTULO = "text-[11px] font-bold uppercase tracking-[0.1em] text-[#7F7C73]";
  const CAMPO =
    "w-full rounded-[10px] border border-linha bg-white px-3.5 py-2.5 text-sm text-grafite outline-none transition focus:border-primary focus:ring-4 focus:ring-primary/10";

  return (
    <div className="space-y-6 pb-10">
      <header className="flex flex-wrap items-end justify-between gap-5 animate-subir motion-reduce:animate-none">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.14em] text-laranja-texto">Sistema</p>
          <h1 className="mt-2 text-4xl font-semibold leading-none tracking-[-0.02em] text-grafite sm:text-[46px]">Configurações</h1>
          <p className="mt-3 text-[15px] text-suave">Seu perfil, os custos das consultas e a administração do portal.</p>
        </div>
        <Segmentos
          rotulo="Seção das configurações"
          valor={activeTab}
          onChange={setActiveTab}
          opcoes={[
            { valor: "profile", rotulo: "Perfil e sistema" },
            { valor: "consumo", rotulo: "Consumo Serasa" },
          ]}
        />
      </header>

      {activeTab === "profile" && (
        <form className="grid grid-cols-1 items-start gap-6 lg:grid-cols-2" onSubmit={(e) => e.preventDefault()}>
          {/* Nome e preferência de e-mail são gravados juntos pelo mesmo botão. */}
          <section className={`${CARTAO} flex flex-col gap-5 animate-subir motion-reduce:animate-none`} style={{ animationDelay: "60ms" }}>
            <h2 className={TITULO}>
              <Icon name="person" className="text-lg" /> Seu perfil
            </h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block">
                <span className={ROTULO}>Nome completo</span>
                <input type="text" value={userName || ""} onChange={(e) => setUserName(e.target.value)} className={`mt-1.5 ${CAMPO}`} />
              </label>
              <label className="block">
                <span className={ROTULO}>E-mail</span>
                <input type="email" value={userEmail || ""} onChange={(e) => setUserEmail(e.target.value)} className={`mt-1.5 ${CAMPO}`} />
              </label>
            </div>
            <div className="flex items-start justify-between gap-4 rounded-2xl border border-linha bg-[#FBFAF7] p-4">
              <div className="min-w-0">
                <p id="notificacao-cedente" className="text-sm font-semibold text-grafite">E-mail automático de Visão Cedente</p>
                <p className="mt-0.5 text-xs text-suave">
                  Avisa a equipe comercial por e-mail sempre que uma empresa sai com Visão Cedente = SIM.
                </p>
              </div>
              <Interruptor ligado={emailNotificacaoCedente} onAlternar={() => setEmailNotificacaoCedente((v) => !v)} rotuloId="notificacao-cedente" />
            </div>
            <div className="flex justify-end border-t border-linha pt-4">
              <button type="button" onClick={handleSaveProfile} disabled={isSaving} className={BOTAO.primario}>
                {isSaving ? "Salvando…" : "Salvar perfil"}
              </button>
            </div>
          </section>

          <section className={`${CARTAO} flex flex-col gap-4 animate-subir motion-reduce:animate-none`} style={{ animationDelay: "120ms" }}>
            <h2 className={TITULO}>
              <Icon name="account_balance_wallet" className="text-lg" /> Custos das consultas
              {isLoadingBillingSettings && <Icon name="progress_activity" size={16} weight="bold" className="ml-auto animate-spin text-primary" />}
            </h2>
            <div className="grid gap-3 sm:grid-cols-2">
              {[
                { rotulo: "Consulta PJ", valor: settings?.serasaCostPerQuery ?? 0, apoio: "Cada consulta de empresa no Serasa" },
                { rotulo: "Consulta PF", valor: settings?.serasaPfCostPerQuery ?? 0, apoio: "Cada consulta de pessoa física no Serasa" },
              ].map((custo) => (
                <div key={custo.rotulo} className="rounded-2xl border border-linha bg-[#FBFAF7] px-4 py-3.5">
                  <p className={ROTULO}>{custo.rotulo}</p>
                  <p className="mt-1.5 font-display text-[28px] font-semibold leading-none tracking-[-0.015em] text-grafite">{moeda(custo.valor)}</p>
                  <p className="mt-1.5 text-xs text-suave">{custo.apoio}</p>
                </div>
              ))}
            </div>
            <p className="text-xs text-suave">Valores definidos na configuração do servidor.</p>
          </section>

          {canManageUsers && (
            <section className={`${CARTAO} lg:col-span-2`}>
              <div className="mb-4 flex items-start justify-between gap-3">
                <div>
                  <h2 className={TITULO}>
                    <Icon name="folder_open" className="text-lg" /> Documentos das empresas
                  </h2>
                  <p className="mt-1 text-sm text-suave">Pasta base onde o sistema cria e busca as pastas padrão de cada empresa.</p>
                </div>
                {isLoadingDocumentSettings && <Icon name="progress_activity" size={16} weight="bold" className="animate-spin text-primary" />}
              </div>
              <label className={`block ${ROTULO}`} htmlFor="pasta-base">
                Caminho base
              </label>
              <div className="mt-1.5 flex flex-col gap-3 lg:flex-row">
                <input
                  id="pasta-base"
                  type="text"
                  value={documentBasePathDraft}
                  onChange={(e) => setDocumentBasePathDraft(e.target.value)}
                  placeholder="/Users/waltermagni/Desktop/Projeto Serasa/Gerenciador de Empresas"
                  className={`flex-1 font-mono ${CAMPO}`}
                />
                <button
                  type="button"
                  onClick={handleSaveDocumentSettings}
                  disabled={isSavingDocumentSettings || !documentBasePathDraft.trim()}
                  className={BOTAO.primario}
                >
                  {isSavingDocumentSettings ? "Salvando…" : "Salvar pasta base"}
                </button>
              </div>
              {documentBasePath && (
                <p className="mt-3 text-xs text-suave">
                  Atual: <strong className="font-mono text-grafite">{documentBasePath}</strong>
                  {documentBasePathUpdatedBy && <> · atualizado por {documentBasePathUpdatedBy}</>}
                  {documentBasePathUpdatedAt && <> em {new Date(documentBasePathUpdatedAt).toLocaleString("pt-BR")}</>}
                </p>
              )}
            </section>
          )}

          {canManageUsers && (
            <section className={`${CARTAO} lg:col-span-2`}>
              <h2 className={TITULO}>
                <Icon name="upload_file" className="text-lg" /> Importação de dados (4R)
              </h2>
              <p className="mt-1 text-sm text-suave">Importe o CSV exportado do ERP 4R.</p>
              <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2">
                {/* Importar SÓ os códigos — seguro p/ produção */}
                <div className="rounded-2xl border border-linha bg-[#FBFAF7] p-4">
                  <p className="text-sm font-bold text-grafite">Importar códigos do cliente (4R)</p>
                  <p className="mt-0.5 text-xs text-suave">
                    Atualiza apenas o <strong>código interno</strong> das empresas que já existem (por CNPJ).
                    Não cria empresas novas nem altera nome/endereço. Seguro para produção.
                  </p>
                  <input
                    type="file"
                    id="csv-codes-upload"
                    accept=".csv"
                    className="hidden"
                    onChange={(e) => { const f = e.target.files?.[0]; if (f) { importCodes(f); e.target.value = ""; } }}
                  />
                  <button
                    type="button"
                    onClick={() => document.getElementById("csv-codes-upload")?.click()}
                    disabled={isImportingCodes}
                    className={`mt-3 ${BOTAO.primario}`}
                  >
                    <Icon name={isImportingCodes ? "sync" : "tag"} className={isImportingCodes ? "animate-spin" : ""} />
                    {isImportingCodes ? "Importando…" : "Importar códigos"}
                  </button>
                </div>

                {/* Importação completa — legado */}
                <div className="rounded-2xl border border-linha bg-[#FBFAF7] p-4">
                  <p className="text-sm font-bold text-grafite">
                    Importar empresas (CSV completo){" "}
                    <span className="ml-1 rounded bg-secondary/15 px-1.5 py-0.5 text-[10px] font-bold text-laranja-texto">legado</span>
                  </p>
                  <p className="mt-0.5 text-xs text-suave">
                    Cria empresas novas e atualiza código + endereço das existentes (preserva nome/email/telefone).
                    Usado na carga inicial do sistema.
                  </p>
                  <input
                    type="file"
                    id="csv-full-upload"
                    accept=".csv"
                    className="hidden"
                    onChange={(e) => { const f = e.target.files?.[0]; if (f) { importCsv(f); e.target.value = ""; } }}
                  />
                  <button
                    type="button"
                    onClick={() => document.getElementById("csv-full-upload")?.click()}
                    disabled={isImportingCsv}
                    className={`mt-3 ${BOTAO.fantasma}`}
                  >
                    <Icon name={isImportingCsv ? "sync" : "upload_file"} className={isImportingCsv ? "animate-spin" : ""} />
                    {isImportingCsv ? "Importando…" : "Importar CSV completo"}
                  </button>
                </div>
              </div>
            </section>
          )}

          {canManageUsers && (
            <section className={`${CARTAO} lg:col-span-2`}>
              <div className="mb-4 flex items-center justify-between gap-3">
                <h2 className={TITULO}>
                  <Icon name="groups" className="text-lg" /> Usuários
                </h2>
                {isLoadingUsers && <Icon name="progress_activity" size={16} weight="bold" className="animate-spin text-primary" />}
              </div>
              <div className="overflow-x-auto rounded-2xl border border-linha">
                <table className="w-full text-sm">
                  <thead className="bg-[#FBFAF7]">
                    <tr>
                      <th className={`px-4 py-3 text-left ${ROTULO}`}>Nome</th>
                      <th className={`px-4 py-3 text-left ${ROTULO}`}>E-mail</th>
                      <th className={`px-4 py-3 text-left ${ROTULO}`}>Esteira de Liberação</th>
                      <th className={`px-4 py-3 text-left ${ROTULO}`}>Criado em</th>
                      <th className={`px-4 py-3 text-right ${ROTULO}`}>Ações</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#F0EEE8] bg-white">
                    {managedUsers.length === 0 && !isLoadingUsers ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-6 text-center text-suave">
                          Nenhum usuário disponível para gerenciamento.
                        </td>
                      </tr>
                    ) : (
                      managedUsers.map((user) => (
                        <tr key={user.id} className="transition-colors hover:bg-[#FBF7F1]">
                          <td className="px-4 py-3 font-medium text-grafite">{user.name}</td>
                          <td className="px-4 py-3 text-suave">{user.email}</td>
                          <td className="px-4 py-3">
                            <PapeisUsuario
                              usuarioId={user.id}
                              analista={user.analista}
                              comite={user.comite}
                              diretoria={!!user.diretoria}
                              onMudou={papeis =>
                                setManagedUsers(atuais => atuais.map(item => (item.id === user.id ? { ...item, ...papeis } : item)))
                              }
                            />
                          </td>
                          <td className="px-4 py-3 text-suave">
                            {user.createdAt ? new Date(user.createdAt).toLocaleDateString("pt-BR") : "—"}
                          </td>
                          <td className="px-4 py-3 text-right">
                            {user.email !== userEmail && (
                              <button
                                type="button"
                                onClick={() => handleDeleteUser(user.id, user.email)}
                                disabled={isDeletingUserId === user.id}
                                className="inline-flex items-center gap-1.5 rounded-lg border border-[#F0C9D2] bg-[#FBEEF1] px-3 py-1.5 text-xs font-bold text-[#8E1B37] transition-colors hover:bg-[#F8E1E6] disabled:opacity-50"
                              >
                                {isDeletingUserId === user.id ? "Apagando…" : "Apagar"}
                              </button>
                            )}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </form>
      )}

      {activeTab === "consumo" && (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className={TITULO}>
              <Icon name="analytics" className="text-lg" /> Consumo do Serasa
            </h2>
            <div className="flex flex-wrap gap-2.5">
              <Segmentos
                rotulo="Tipo de consulta"
                valor={entityFilter}
                onChange={setEntityFilter}
                opcoes={[
                  { valor: "ALL", rotulo: "Tudo" },
                  { valor: "PJ", rotulo: "PJ" },
                  { valor: "PF", rotulo: "PF" },
                ]}
              />
              <Segmentos
                rotulo="Período"
                valor={period}
                onChange={setPeriod}
                opcoes={(["7", "15", "30", "60", "90"] as const).map((p) => ({ valor: p, rotulo: `${p} dias` }))}
              />
            </div>
          </div>

          <section className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 xl:grid-cols-4">
            {[
              { rotulo: "Custo do período", valor: moeda(totalCost), cor: "#612035", apoio: `nos últimos ${period} dias` },
              { rotulo: "Consultas", valor: filteredLogs.length.toLocaleString("pt-BR"), cor: "#262833", apoio: entityFilter === "ALL" ? "PF e PJ" : `só ${entityFilter}` },
              { rotulo: "Empresas (PJ)", valor: totalPj.toLocaleString("pt-BR"), cor: "#262833", apoio: `${moeda(custoPj)} no período` },
              { rotulo: "Pessoas físicas (PF)", valor: totalPf.toLocaleString("pt-BR"), cor: "#262833", apoio: `${moeda(custoPf)} no período` },
            ].map((item, i) => (
              <div
                key={item.rotulo}
                className="rounded-cartao border border-linha bg-white px-5 py-4 animate-subir motion-reduce:animate-none"
                style={{ animationDelay: `${60 + i * 60}ms` }}
              >
                <p className="text-[12.5px] font-semibold text-suave">{item.rotulo}</p>
                <p
                  className="mt-1.5 font-display text-[30px] font-semibold leading-tight tracking-[-0.015em]"
                  style={{ color: item.cor }}
                >
                  {item.valor}
                </p>
                <p className="mt-1 text-xs text-suave">{item.apoio}</p>
              </div>
            ))}
          </section>

          <section className="overflow-hidden rounded-painel border border-linha bg-white">
            <div className={`relative overflow-x-auto ${isLoading || isError ? "min-h-[250px]" : ""}`}>
              {isLoading && (
                <div className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-white/60 backdrop-blur-sm">
                  <Icon name="progress_activity" size={32} weight="bold" className="mb-2 animate-spin text-primary" />
                  <p className="text-sm font-medium text-suave">Carregando consultas…</p>
                </div>
              )}
              {isError && !isLoading && (
                <div className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-white p-6 text-center">
                  <p className="mb-1 font-medium text-critico">Não foi possível carregar o histórico.</p>
                  <p className="text-sm text-suave">O servidor não respondeu agora. Tente de novo em instantes.</p>
                </div>
              )}
              <table className="w-full text-left text-sm">
                <thead className="border-b border-linha bg-[#FBFAF7]">
                  <tr>
                    <th className={`px-6 py-3.5 ${ROTULO}`}>Usuário</th>
                    <th className={`px-6 py-3.5 ${ROTULO}`}>Consultado</th>
                    <th className={`px-6 py-3.5 ${ROTULO}`}>Documento</th>
                    <th className={`px-6 py-3.5 ${ROTULO}`}>Entidade</th>
                    <th className={`px-6 py-3.5 ${ROTULO}`}>Data/Hora</th>
                    <th className={`px-6 py-3.5 ${ROTULO}`}>Tipo</th>
                    <th className={`px-6 py-3.5 text-right ${ROTULO}`}>Custo</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#F0EEE8]">
                  {!isLoading && !isError && filteredLogs.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-6 py-10 text-center text-suave">Nenhuma consulta neste período.</td>
                    </tr>
                  ) : (
                    filteredLogs.map((log) => (
                      <tr key={log.id} className="transition-colors hover:bg-[#FBF7F1]">
                        <td className="px-6 py-3.5 font-medium text-grafite">{log.userName}</td>
                        <td className="px-6 py-3.5 text-grafite">{log.companyName}</td>
                        <td className="px-6 py-3.5 font-mono text-xs text-suave">{formatDocument(log.documentNumber)}</td>
                        <td className="px-6 py-3.5">
                          <span
                            className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-bold ${
                              log.entityType === "PF" ? "bg-secondary/15 text-laranja-texto" : "bg-[#EFEDE6] text-[#5E5C55]"
                            }`}
                          >
                            {log.entityType}
                          </span>
                        </td>
                        <td className="px-6 py-3.5 text-suave">
                          {new Date(log.timestamp).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" })}
                        </td>
                        <td className="px-6 py-3.5">
                          <span
                            className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-bold ${
                              log.queryType === "INITIAL"
                                ? "bg-[#E2F0E9] text-[#17583F]"
                                : log.queryType === "UPDATE"
                                  ? "bg-[#2956E0]/10 text-[#1f43b8]"
                                  : "bg-secondary/15 text-laranja-texto"
                            }`}
                          >
                            {queryTypeLabel(log.queryType, log.entityType)}
                          </span>
                        </td>
                        <td className="px-6 py-3.5 text-right font-semibold text-grafite">{moeda(log.cost)}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

/** Chave liga/desliga das preferências. */
function Interruptor({ ligado, onAlternar, rotuloId }: { ligado: boolean; onAlternar: () => void; rotuloId: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={ligado}
      aria-labelledby={rotuloId}
      onClick={onAlternar}
      className={`relative h-7 w-12 shrink-0 rounded-full transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${
        ligado ? "bg-primary" : "bg-[#D6D2C8]"
      }`}
    >
      <span
        className={`absolute left-1 top-1 h-5 w-5 rounded-full bg-white shadow transition-transform duration-200 ${
          ligado ? "translate-x-5" : ""
        }`}
      />
    </button>
  );
}
