"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import Dialogo, { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "@/components/liberacao/Dialogo";
import { useCriarProspeccao } from "@/hooks/useProspeccao";
import { useDiretorio } from "@/hooks/useLiberacao";
import { DADOS_COMERCIAIS_VAZIOS, type DadosComerciais, type Prospeccao } from "@/types/prospeccao";
import DadosComerciaisForm, { camposPreenchidos } from "./DadosComerciaisForm";
import { formatCnpj } from "./formatters";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

/** Sem comercial: cliente direto. Outro: nome digitado, para quem ainda não tem login. */
const DIRETO = "__direto__";
const OUTRO = "__outro__";

/**
 * Abertura de card, pelo quadro ou pela página da empresa.
 *
 * <p>O CNPJ é obrigatório: é a chave que amarra esteira, análise, partes ligadas e filiais, e é o
 * nome da pasta no compartilhamento. Comercial e dados comerciais são opcionais — a equipe
 * interna abre prospecção sem depender de ninguém de fora, e o que faltar vira pendência.</p>
 */
export default function NovaProspeccaoDialog({
  onFechar,
  onCriado,
  inicial,
}: {
  onFechar: () => void;
  onCriado?: (card: Prospeccao) => void;
  /** Vindo da página da empresa: CNPJ e nome já conhecidos. */
  inicial?: { cnpj: string; razaoSocial?: string };
}) {
  const criar = useCriarProspeccao();
  const { data: diretorio = [] } = useDiretorio();
  const [cnpj, setCnpj] = useState(inicial ? formatCnpj(inicial.cnpj) : "");
  const [razaoSocial, setRazaoSocial] = useState(inicial?.razaoSocial ?? "");
  const [buscando, setBuscando] = useState(false);
  const [comercial, setComercial] = useState<string>(DIRETO);
  const [comercialNome, setComercialNome] = useState("");
  const [dados, setDados] = useState<DadosComerciais>(DADOS_COMERCIAIS_VAZIOS);

  const digitos = cnpj.replace(/\D/g, "");
  const cnpjTravado = Boolean(inicial?.cnpj);

  /** Puxa a razão social do que o portal já conhece, para não digitar o nome à mão. */
  async function buscarNome() {
    if (digitos.length !== 14 || razaoSocial.trim()) return;
    setBuscando(true);
    try {
      const token = typeof window === "undefined" ? null : localStorage.getItem("serasa_token");
      const res = await fetch(`${API_BASE_URL}/company/${digitos}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (res.ok) {
        const json = await res.json();
        const nome = json.companyName || json.tradeName;
        if (nome) setRazaoSocial(nome);
      }
    } catch {
      // Empresa fora da base ainda pode entrar na esteira; o nome vai à mão.
    } finally {
      setBuscando(false);
    }
  }

  const comercialValido = comercial !== OUTRO || comercialNome.trim().length > 0;
  const podeCriar = digitos.length === 14 && razaoSocial.trim().length > 0 && comercialValido && !criar.isPending;
  const preenchidos = camposPreenchidos(dados);

  function enviar() {
    if (!podeCriar) return;
    criar.mutate(
      {
        cnpj: digitos,
        razaoSocial: razaoSocial.trim(),
        comercialId: comercial !== DIRETO && comercial !== OUTRO ? comercial : undefined,
        comercialNome: comercial === OUTRO ? comercialNome.trim() : undefined,
        dadosComerciais: preenchidos > 0 ? dados : undefined,
      },
      {
        onSuccess: card => {
          onFechar();
          onCriado?.(card);
        },
      },
    );
  }

  return (
    <Dialogo
      titulo="Nova prospecção"
      subtitulo="O card entra em Nova, com o checklist de documentos já montado."
      onFechar={onFechar}
      largura="lg"
      rodape={
        <>
          <button type="button" onClick={onFechar} className={BOTAO_SECUNDARIO}>Cancelar</button>
          <button type="button" onClick={enviar} disabled={!podeCriar} className={BOTAO_PRIMARIO}>
            <Icon name="add" size={16} />
            {criar.isPending ? "Abrindo…" : "Abrir prospecção"}
          </button>
        </>
      }
    >
      <form
        className="space-y-4 px-5 py-4"
        onSubmit={event => {
          event.preventDefault();
          enviar();
        }}
      >
        <div className="grid gap-3 sm:grid-cols-[14rem_1fr]">
          <label className="block">
            <span className={ROTULO}>CNPJ</span>
            <input
              data-autofocus={cnpjTravado ? undefined : true}
              value={cnpj}
              readOnly={cnpjTravado}
              onChange={event => setCnpj(event.target.value)}
              onBlur={buscarNome}
              inputMode="numeric"
              placeholder="00.000.000/0000-00"
              className={`${CAMPO} font-mono ${cnpjTravado ? "bg-slate-50 dark:bg-slate-800/60" : ""}`}
            />
            {digitos.length > 0 && digitos.length !== 14 && (
              <span className="mt-1 block text-[11px] text-[#D1732C]">Faltam {14 - digitos.length} dígito(s).</span>
            )}
          </label>
          <label className="block">
            <span className={ROTULO}>Razão social</span>
            <input
              data-autofocus={cnpjTravado ? true : undefined}
              value={razaoSocial}
              onChange={event => setRazaoSocial(event.target.value)}
              placeholder={buscando ? "buscando…" : "nome da empresa"}
              className={CAMPO}
            />
          </label>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className={ROTULO}>Comercial</span>
            <select value={comercial} onChange={event => setComercial(event.target.value)} className={`${CAMPO} cursor-pointer`}>
              <option value={DIRETO}>Sem comercial (cliente direto)</option>
              {diretorio.map(pessoa => (
                <option key={pessoa.id} value={pessoa.id}>{pessoa.nome}</option>
              ))}
              <option value={OUTRO}>Outro — digitar o nome</option>
            </select>
          </label>
          {comercial === OUTRO && (
            <label className="block">
              <span className={ROTULO}>Nome do comercial</span>
              <input
                value={comercialNome}
                onChange={event => setComercialNome(event.target.value)}
                placeholder="quem trouxe a empresa"
                className={CAMPO}
              />
            </label>
          )}
        </div>

        <details className="group rounded-xl border border-slate-200 dark:border-slate-700">
          <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-2 px-4 text-sm font-medium
            text-slate-700 dark:text-slate-200 [&::-webkit-details-marker]:hidden">
            <span className="inline-flex items-center gap-2">
              <Icon name="storefront" size={16} className="text-slate-400" />
              Dados comerciais
              <span className="text-xs font-normal text-slate-400">
                {preenchidos > 0 ? `${preenchidos} preenchido${preenchidos > 1 ? "s" : ""}` : "opcional — dá para completar depois"}
              </span>
            </span>
            <Icon name="expand_more" size={16} className="text-slate-400 transition-transform group-open:rotate-180" />
          </summary>
          <div className="border-t border-slate-100 px-4 py-4 dark:border-slate-800">
            <DadosComerciaisForm valor={dados} onMudar={setDados} />
          </div>
        </details>
        {/* Enter no formulário envia; o botão de verdade fica no rodapé do diálogo. */}
        <button type="submit" className="hidden" aria-hidden tabIndex={-1} />
      </form>
    </Dialogo>
  );
}
