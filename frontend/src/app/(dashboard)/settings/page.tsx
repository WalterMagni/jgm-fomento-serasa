"use client";

import { useState, useEffect, useMemo } from "react";
import Icon from "@/components/ui/Icon";
import { useApiUsage, useBillingSettings } from "@/hooks/useApiUsage";
import { useImportCsv } from "@/hooks/useImportCsv";
import { useImportClientCodes } from "@/hooks/useImportClientCodes";
import { Loader2 } from "lucide-react";

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

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      <div className="mb-8">
        <h1 className="text-2xl font-sans font-bold text-grafite dark:text-white mb-1">Configurações do Sistema</h1>
        <p className="text-gray-500 font-sans">Gerencie suas preferências e custos da plataforma.</p>
      </div>

      <div className="bg-white dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 shadow-sm overflow-hidden">
        {/* Tabs Header */}
        <div className="flex border-b border-gray-200 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-900/50">
          <button
            onClick={() => setActiveTab("profile")}
            className={`px-6 py-4 text-sm font-medium border-b-2 transition-colors duration-200 ${activeTab === "profile" ? "text-primary border-primary" : "text-gray-500 border-transparent hover:text-grafite dark:text-gray-400 dark:hover:text-white"}`}
          >
            Perfil do Usuário
          </button>
          <button
            onClick={() => setActiveTab("consumo")}
            className={`px-6 py-4 text-sm font-medium border-b-2 transition-colors duration-200 ${activeTab === "consumo" ? "text-primary border-primary" : "text-gray-500 border-transparent hover:text-grafite dark:text-gray-400 dark:hover:text-white"}`}
          >
            Consumo
          </button>
        </div>

        {/* Tab Content */}
        <div className="p-6">
          {activeTab === "profile" && (
            <div className="animate-in fade-in slide-in-from-bottom-2 duration-300">
              <h2 className="text-lg font-sans font-bold text-grafite dark:text-white mb-4">Informações Pessoais</h2>
              <form className="space-y-6 max-w-2xl">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-sans font-bold text-gray-500 uppercase tracking-wide mb-2 opacity-80">Nome Completo</label>
                    <input
                      type="text"
                      value={userName || ""}
                      onChange={(e) => setUserName(e.target.value)}
                      className="w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg px-4 py-2 text-sm font-sans focus:outline-none focus:ring-2 focus:ring-primary/50 text-grafite dark:text-white transition-all"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-sans font-bold text-gray-500 uppercase tracking-wide mb-2 opacity-80">E-mail</label>
                    <input
                      type="email"
                      value={userEmail || ""}
                      onChange={(e) => setUserEmail(e.target.value)}
                      className="w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg px-4 py-2 text-sm font-sans focus:outline-none focus:ring-2 focus:ring-primary/50 text-grafite dark:text-white transition-all"
                    />
                  </div>
                </div>

                {/* Email Notification Toggle */}
                <div className="pt-4 border-t border-gray-100 dark:border-gray-800">
                  <h3 className="text-sm font-sans font-bold text-grafite dark:text-white mb-3">Notificações por E-mail</h3>
                  <div className="flex items-start justify-between gap-4 p-4 rounded-lg bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700">
                    <div className="flex-1">
                      <p className="text-sm font-medium text-grafite dark:text-white">Envio automático de e-mail — Visão Cedente</p>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                        Quando ativado, um e-mail é enviado automaticamente para a equipe comercial sempre que uma empresa for identificada com Visão Cedente = SIM.
                      </p>
                    </div>
                    <button
                      type="button"
                      role="switch"
                      aria-checked={emailNotificacaoCedente}
                      onClick={() => setEmailNotificacaoCedente((v) => !v)}
                      className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary/50 ${emailNotificacaoCedente ? "bg-primary" : "bg-gray-300 dark:bg-gray-600"}`}
                    >
                      <span
                        className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${emailNotificacaoCedente ? "translate-x-5" : "translate-x-0"}`}
                      />
                    </button>
                  </div>
                </div>

                <div className="pt-4 border-t border-gray-100 dark:border-gray-800">
                  <div className="flex items-center justify-between gap-3 mb-3">
                    <h3 className="text-sm font-sans font-bold text-grafite dark:text-white">Custos configurados</h3>
                    {isLoadingBillingSettings && <Loader2 className="h-4 w-4 animate-spin text-primary" />}
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="p-4 rounded-lg bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700">
                      <p className="text-xs font-sans font-bold text-gray-500 uppercase tracking-wide mb-1">Consulta PJ</p>
                      <p className="text-2xl font-serif font-bold text-grafite dark:text-white">
                        {new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(settings?.serasaCostPerQuery ?? 0)}
                      </p>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">Valor atual aplicado nas consultas empresariais.</p>
                    </div>
                    <div className="p-4 rounded-lg bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700">
                      <p className="text-xs font-sans font-bold text-gray-500 uppercase tracking-wide mb-1">Consulta PF</p>
                      <p className="text-2xl font-serif font-bold text-grafite dark:text-white">
                        {new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(settings?.serasaPfCostPerQuery ?? 0)}
                      </p>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">Valor atual aplicado nas consultas de pessoa física.</p>
                    </div>
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-3">
                    Esses valores refletem a configuração atual do backend. Se quiser tornar isso editável pelo portal, o próximo passo é persistir essas configurações em banco.
                  </p>
                </div>

                {canManageUsers && (
                  <div className="pt-4 border-t border-gray-100 dark:border-gray-800">
                    <div className="flex items-center justify-between gap-3 mb-3">
                      <div>
                        <h3 className="text-sm font-sans font-bold text-grafite dark:text-white">Documentos das empresas</h3>
                        <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                          Pasta base onde o sistema cria e busca as pastas padrão de cada empresa.
                        </p>
                      </div>
                      {isLoadingDocumentSettings && <Loader2 className="h-4 w-4 animate-spin text-primary" />}
                    </div>
                    <div className="rounded-lg bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700 p-4">
                      <label className="block text-xs font-sans font-bold text-gray-500 uppercase tracking-wide mb-2">
                        Caminho base
                      </label>
                      <div className="flex flex-col lg:flex-row gap-3">
                        <input
                          type="text"
                          value={documentBasePathDraft}
                          onChange={(e) => setDocumentBasePathDraft(e.target.value)}
                          placeholder="/Users/waltermagni/Desktop/Projeto Serasa/Gerenciador de Empresas"
                          className="flex-1 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg px-4 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-primary/50 text-grafite dark:text-white transition-all"
                        />
                        <button
                          type="button"
                          onClick={handleSaveDocumentSettings}
                          disabled={isSavingDocumentSettings || !documentBasePathDraft.trim()}
                          className="inline-flex items-center justify-center gap-2 bg-primary text-white px-4 py-2 rounded-lg text-sm font-medium shadow-[0_1px_2px_rgba(0,0,0,0.1),_0_2px_4px_rgba(97,32,53,0.3)] transition-all duration-200 hover:-translate-y-[1px] hover:bg-primary-hover active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none"
                        >
                          {isSavingDocumentSettings ? "Salvando..." : "Salvar pasta base"}
                        </button>
                      </div>
                      {documentBasePath && (
                        <p className="mt-3 text-xs text-gray-500 dark:text-gray-400">
                          Atual: <strong className="font-mono text-grafite dark:text-white">{documentBasePath}</strong>
                          {documentBasePathUpdatedBy && <> · atualizado por {documentBasePathUpdatedBy}</>}
                          {documentBasePathUpdatedAt && <> em {new Date(documentBasePathUpdatedAt).toLocaleString("pt-BR")}</>}
                        </p>
                      )}
                    </div>
                  </div>
                )}

                {canManageUsers && (
                  <div className="pt-4 border-t border-gray-100 dark:border-gray-800">
                    <h3 className="text-sm font-sans font-bold text-grafite dark:text-white mb-1">Importação de dados (4R)</h3>
                    <p className="text-xs text-gray-500 dark:text-gray-400 mb-3">
                      Importe o CSV exportado do ERP 4R.
                    </p>
                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                      {/* Importar SÓ os códigos — seguro p/ produção */}
                      <div className="rounded-lg bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700 p-4">
                        <p className="text-sm font-bold text-grafite dark:text-white">Importar códigos do cliente (4R)</p>
                        <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
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
                          className="mt-3 inline-flex items-center justify-center gap-2 bg-primary text-white px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 hover:-translate-y-[1px] hover:bg-primary-hover active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          <Icon name={isImportingCodes ? "sync" : "tag"} className={`text-lg ${isImportingCodes ? "animate-spin" : ""}`} />
                          {isImportingCodes ? "Importando..." : "Importar códigos"}
                        </button>
                      </div>

                      {/* Importação completa — legado */}
                      <div className="rounded-lg bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700 p-4">
                        <p className="text-sm font-bold text-grafite dark:text-white">Importar empresas (CSV completo) <span className="ml-1 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">legado</span></p>
                        <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
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
                          className="mt-3 inline-flex items-center justify-center gap-2 border border-border-light bg-white px-4 py-2 rounded-lg text-sm font-medium text-grafite transition-all duration-200 hover:bg-gray-50 active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed dark:border-gray-700 dark:bg-gray-800 dark:text-white"
                        >
                          <Icon name={isImportingCsv ? "sync" : "upload_file"} className={`text-lg ${isImportingCsv ? "animate-spin" : ""}`} />
                          {isImportingCsv ? "Importando..." : "Importar CSV completo"}
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                {canManageUsers && (
                  <div className="pt-4 border-t border-gray-100 dark:border-gray-800">
                    <div className="flex items-center justify-between gap-3 mb-3">
                      <h3 className="text-sm font-sans font-bold text-grafite dark:text-white">Gerenciamento de usuários</h3>
                      {isLoadingUsers && <Loader2 className="h-4 w-4 animate-spin text-primary" />}
                    </div>
                    <div className="rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
                      <table className="w-full text-sm">
                        <thead className="bg-gray-50 dark:bg-gray-800/70">
                          <tr>
                            <th className="px-4 py-3 text-left text-[11px] font-sans font-bold text-gray-500 uppercase tracking-wide">Nome</th>
                            <th className="px-4 py-3 text-left text-[11px] font-sans font-bold text-gray-500 uppercase tracking-wide">E-mail</th>
                            <th className="px-4 py-3 text-left text-[11px] font-sans font-bold text-gray-500 uppercase tracking-wide">Criado em</th>
                            <th className="px-4 py-3 text-right text-[11px] font-sans font-bold text-gray-500 uppercase tracking-wide">Ações</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100 dark:divide-gray-800 bg-white dark:bg-gray-900">
                          {managedUsers.length === 0 && !isLoadingUsers ? (
                            <tr>
                              <td colSpan={4} className="px-4 py-6 text-center text-gray-500">
                                Nenhum usuário disponível para gerenciamento.
                              </td>
                            </tr>
                          ) : (
                            managedUsers.map((user) => (
                              <tr key={user.id}>
                                <td className="px-4 py-3 text-grafite dark:text-gray-200">{user.name}</td>
                                <td className="px-4 py-3 text-gray-600 dark:text-gray-300">{user.email}</td>
                                <td className="px-4 py-3 text-gray-500">
                                  {user.createdAt ? new Date(user.createdAt).toLocaleDateString("pt-BR") : "—"}
                                </td>
                                <td className="px-4 py-3 text-right">
                                  {user.email !== userEmail && (
                                    <button
                                      type="button"
                                      onClick={() => handleDeleteUser(user.id, user.email)}
                                      disabled={isDeletingUserId === user.id}
                                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-sans font-bold text-red-700 bg-red-50 border border-red-200 hover:bg-red-100 transition-colors disabled:opacity-50"
                                    >
                                      {isDeletingUserId === user.id ? "Apagando..." : "Apagar"}
                                    </button>
                                  )}
                                </td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                <div className="pt-4 border-t border-gray-100 dark:border-gray-800 flex justify-end">
                  <button
                    type="button"
                    onClick={handleSaveProfile}
                    disabled={isSaving}
                    className="bg-primary text-white px-6 py-2.5 rounded-lg text-sm font-medium shadow-[0_1px_2px_rgba(0,0,0,0.1),_0_2px_4px_rgba(97,32,53,0.3)] transition-all duration-200 hover:-translate-y-[1px] hover:bg-primary-hover active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none"
                  >
                    {isSaving ? "Salvando..." : "Salvar Alterações"}
                  </button>
                </div>
              </form>
            </div>
          )}

          {activeTab === "consumo" && (
            <div className="animate-in fade-in slide-in-from-bottom-2 duration-300 space-y-8">
              {/* Header with Filter */}
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                <h2 className="text-lg font-sans font-bold text-grafite dark:text-white">Relatório de Consumo Serasa</h2>
                <div className="flex flex-col sm:flex-row gap-3">
                  <div className="flex items-center bg-gray-100 dark:bg-gray-800 rounded-lg p-1">
                    {(["ALL", "PJ", "PF"] as const).map((item) => (
                      <button
                        key={item}
                        onClick={() => setEntityFilter(item)}
                        className={`px-4 py-1.5 text-xs font-semibold rounded-md transition-all duration-200 ${entityFilter === item ? "bg-white dark:bg-gray-700 text-primary shadow-sm" : "text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"}`}
                      >
                        {item === "ALL" ? "Tudo" : item}
                      </button>
                    ))}
                  </div>
                  <div className="flex items-center bg-gray-100 dark:bg-gray-800 rounded-lg p-1">
                    {(["7", "15", "30", "60", "90"] as const).map((p) => (
                      <button
                        key={p}
                        onClick={() => setPeriod(p)}
                        className={`px-4 py-1.5 text-xs font-semibold rounded-md transition-all duration-200 ${period === p ? "bg-white dark:bg-gray-700 text-primary shadow-sm" : "text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"}`}
                      >
                        {p} dias
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* Summary Cards */}
              <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                <div className="px-6 py-5 rounded-xl border border-red-100 bg-red-50/50 dark:bg-red-900/10 dark:border-red-900/30 shadow-sm flex flex-col justify-center">
                  <p className="text-sm font-medium text-red-600 dark:text-red-400 mb-1">Custo Total do Período</p>
                  <p className="text-3xl font-bold font-serif text-red-700 dark:text-red-500">
                    {new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(totalCost)}
                  </p>
                </div>
                <div className="px-6 py-5 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-sm flex flex-col justify-center">
                  <p className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">Total de Consultas</p>
                  <p className="text-3xl font-bold font-serif text-grafite dark:text-white">{filteredLogs.length}</p>
                </div>
                <div className="px-6 py-5 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-sm flex flex-col justify-center">
                  <p className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">Distribuição PF vs. PJ</p>
                  <div className="flex items-baseline gap-2 mt-1">
                    <span className="text-3xl font-bold font-serif text-grafite dark:text-white">{totalPf}</span>
                    <span className="text-sm text-gray-500">PF</span>
                    <span className="text-gray-300 dark:text-gray-700 font-light mx-1">/</span>
                    <span className="text-xl font-bold font-serif text-gray-700 dark:text-gray-300">{totalPj}</span>
                    <span className="text-sm text-gray-500">PJ</span>
                  </div>
                </div>
                <div className="px-6 py-5 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-sm flex flex-col justify-center">
                  <p className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">Custo PF vs. PJ</p>
                  <div className="flex flex-col gap-1 mt-1">
                    <span className="text-sm text-grafite dark:text-white">PF: {new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(custoPf)}</span>
                    <span className="text-sm text-grafite dark:text-white">PJ: {new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(custoPj)}</span>
                  </div>
                </div>
              </div>

              {/* Data Table */}
              <div className="border border-gray-200 dark:border-gray-800 rounded-xl overflow-hidden shadow-sm">
                <div className="overflow-x-auto min-h-[250px] relative">
                  {isLoading && (
                    <div className="absolute inset-0 z-10 bg-white/50 dark:bg-gray-900/50 flex flex-col items-center justify-center backdrop-blur-sm">
                      <Loader2 className="h-8 w-8 animate-spin text-primary mb-2" />
                      <p className="text-sm font-medium text-gray-500 dark:text-gray-400">Carregando consultas...</p>
                    </div>
                  )}
                  {isError && !isLoading && (
                    <div className="absolute inset-0 z-10 bg-white dark:bg-gray-900 flex flex-col items-center justify-center p-6 text-center">
                      <p className="text-red-500 font-medium mb-1">Erro ao carregar o histórico.</p>
                      <p className="text-sm text-gray-500">O servidor pode estar indisponível ou a rota não concluída no backend.</p>
                    </div>
                  )}
                  <table className="w-full text-sm text-left">
                    <thead className="bg-gray-50/80 dark:bg-gray-800/50 text-gray-500 dark:text-gray-400 border-b border-gray-200 dark:border-gray-800">
                      <tr>
                        <th className="px-6 py-4 font-semibold">Usuário</th>
                        <th className="px-6 py-4 font-semibold">Consultado</th>
                        <th className="px-6 py-4 font-semibold">Documento</th>
                        <th className="px-6 py-4 font-semibold">Entidade</th>
                        <th className="px-6 py-4 font-semibold">Data/Hora</th>
                        <th className="px-6 py-4 font-semibold">Tipo</th>
                        <th className="px-6 py-4 font-semibold text-right">Custo</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100 dark:divide-gray-800 bg-white dark:bg-gray-900">
                      {!isLoading && !isError && filteredLogs.length === 0 ? (
                        <tr>
                          <td colSpan={7} className="px-6 py-8 text-center text-gray-500">Nenhuma consulta encontrada neste período.</td>
                        </tr>
                      ) : (
                        filteredLogs.map((log) => (
                          <tr key={log.id} className="hover:bg-gray-50/50 dark:hover:bg-gray-800/30 transition-colors">
                            <td className="px-6 py-4 font-medium text-grafite dark:text-gray-200">{log.userName}</td>
                            <td className="px-6 py-4 text-gray-600 dark:text-gray-300">{log.companyName}</td>
                            <td className="px-6 py-4 text-gray-500">{formatDocument(log.documentNumber)}</td>
                            <td className="px-6 py-4">
                              <span className={`inline-flex items-center px-2 py-1 rounded-md text-xs font-semibold ${log.entityType === "PF" ? "bg-fuchsia-100 text-fuchsia-700 dark:bg-fuchsia-900/30 dark:text-fuchsia-400" : "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300"}`}>
                                {log.entityType}
                              </span>
                            </td>
                            <td className="px-6 py-4 text-gray-500">
                              {new Date(log.timestamp).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" })}
                            </td>
                            <td className="px-6 py-4">
                              <span className={`inline-flex items-center px-2 py-1 rounded-md text-xs font-semibold ${log.queryType === "INITIAL" ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400" : log.queryType === "UPDATE" ? "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400" : "bg-fuchsia-100 text-fuchsia-700 dark:bg-fuchsia-900/30 dark:text-fuchsia-400"}`}>
                                {queryTypeLabel(log.queryType, log.entityType)}
                              </span>
                            </td>
                            <td className="px-6 py-4 text-right font-medium text-grafite dark:text-gray-200">
                              {new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(log.cost)}
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
