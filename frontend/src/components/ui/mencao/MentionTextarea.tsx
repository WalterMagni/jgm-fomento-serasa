"use client";

import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import Icon from "@/components/ui/Icon";
import { useDiretorio, useEmpresaBusca, useUsuarioAtual } from "@/hooks/useLiberacao";
import { deMarcacao, gatilhoAtual, inserirMencao, normalizar, paraMarcacao, reposicionar, type Mencao } from "./mencoes";

type Props = {
  /** Texto com marcação, como é salvo. */
  value: string;
  onChange: (marcacao: string) => void;
  placeholder?: string;
  rows?: number;
  id?: string;
  "aria-label"?: string;
  autoFocus?: boolean;
  /** Ctrl/Cmd + Enter. Só dispara com a lista de sugestões fechada. */
  onEnviar?: () => void;
  className?: string;
};

type Opcao =
  | { tipo: "user"; id: string; rotulo: string; detalhe: string | null; iniciais: string }
  | { tipo: "cnpj"; id: string; rotulo: string; detalhe: string };

/** Mesma tipografia e espaçamento nas duas camadas: o destaque só alinha se forem idênticas. */
const TIPOGRAFIA = "px-3 py-2 text-sm leading-relaxed whitespace-pre-wrap break-words [overflow-wrap:anywhere]";

function formatCnpj(cnpj: string) {
  return cnpj.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}

/**
 * Campo de texto com menção por `@`: pessoas do portal e empresas da base.
 *
 * <p>É um textarea comum, não editor rico — contenteditable traz problema de cursor, de colagem
 * e de corretor. O destaque das menções é uma camada desenhada por trás com o mesmo texto e a
 * mesma tipografia; o textarea por cima é transparente e cresce com o conteúdo, para as duas
 * camadas nunca rolarem descompassadas.</p>
 */
export default function MentionTextarea({
  value,
  onChange,
  placeholder,
  rows = 3,
  id,
  autoFocus,
  onEnviar,
  className = "",
  ...rest
}: Props) {
  const campo = useRef<HTMLTextAreaElement>(null);
  const listaId = useId();
  const { texto, mencoes } = useMemo(() => deMarcacao(value), [value]);
  const [gatilho, setGatilho] = useState<{ inicio: number; consulta: string } | null>(null);
  const [destaque, setDestaque] = useState(0);
  const [consultaEmpresa, setConsultaEmpresa] = useState("");

  const { data: pessoas = [] } = useDiretorio();
  const { data: eu } = useUsuarioAtual();

  // Empresa vai ao servidor; espera a pessoa parar de digitar.
  const consultaAtual = gatilho?.consulta ?? "";
  useEffect(() => {
    const timer = setTimeout(() => setConsultaEmpresa(consultaAtual), 200);
    return () => clearTimeout(timer);
  }, [consultaAtual]);
  const buscaEmpresa = useEmpresaBusca(gatilho && consultaEmpresa === consultaAtual ? consultaEmpresa : "");

  const opcoes: Opcao[] = useMemo(() => {
    if (!gatilho) return [];
    const termo = normalizar(gatilho.consulta.trim());
    const soDigitos = /^\d[\d./-]*$/.test(gatilho.consulta.trim());
    const deGente: Opcao[] = soDigitos
      ? []
      : pessoas
          .filter(pessoa => pessoa.id !== eu?.id)
          .filter(pessoa => !termo || normalizar(pessoa.nome).split(/\s+/).some(parte => parte.startsWith(termo)) || normalizar(pessoa.nome).startsWith(termo))
          .slice(0, 6)
          .map(pessoa => ({
            tipo: "user" as const,
            id: pessoa.id,
            rotulo: pessoa.nome,
            detalhe: pessoa.comite ? "Comitê" : pessoa.analista ? "Analista" : null,
            iniciais: pessoa.iniciais,
          }));
    const deEmpresa: Opcao[] = (buscaEmpresa.data ?? []).slice(0, 5).map(empresa => ({
      tipo: "cnpj" as const,
      id: empresa.cnpj,
      rotulo: empresa.nome,
      detalhe: formatCnpj(empresa.cnpj) + (empresa.cidade ? ` · ${empresa.cidade}/${empresa.uf}` : ""),
    }));
    return [...deGente, ...deEmpresa];
  }, [gatilho, pessoas, eu?.id, buscaEmpresa.data]);

  // Cresce com o conteúdo em vez de rolar: rolagem interna desalinharia a camada do destaque.
  useLayoutEffect(() => {
    const elemento = campo.current;
    if (!elemento) return;
    elemento.style.height = "auto";
    elemento.style.height = `${elemento.scrollHeight}px`;
  }, [texto]);

  function emitir(novoTexto: string, novasMencoes: Mencao[]) {
    onChange(paraMarcacao(novoTexto, novasMencoes));
  }

  function atualizarGatilho(novoTexto: string, cursor: number, novasMencoes: Mencao[]) {
    const achado = gatilhoAtual(novoTexto, cursor, novasMencoes);
    setGatilho(achado);
    setDestaque(0);
  }

  function escolher(opcao: Opcao) {
    if (!gatilho || !campo.current) return;
    const cursor = campo.current.selectionStart;
    const resultado = inserirMencao(texto, mencoes, gatilho.inicio, cursor, { tipo: opcao.tipo, id: opcao.id, rotulo: opcao.rotulo });
    emitir(resultado.texto, resultado.mencoes);
    setGatilho(null);
    requestAnimationFrame(() => {
      campo.current?.focus();
      campo.current?.setSelectionRange(resultado.cursor, resultado.cursor);
    });
  }

  function aoTeclar(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (gatilho && opcoes.length > 0) {
      if (event.key === "ArrowDown") {
        event.preventDefault();
        setDestaque(atual => (atual + 1) % opcoes.length);
        return;
      }
      if (event.key === "ArrowUp") {
        event.preventDefault();
        setDestaque(atual => (atual - 1 + opcoes.length) % opcoes.length);
        return;
      }
      if (event.key === "Enter" || event.key === "Tab") {
        event.preventDefault();
        escolher(opcoes[Math.min(destaque, opcoes.length - 1)]);
        return;
      }
    }
    if (event.key === "Escape" && gatilho) {
      // Fecha só a lista; o diálogo em volta continua aberto.
      event.preventDefault();
      event.stopPropagation();
      event.nativeEvent.stopImmediatePropagation();
      setGatilho(null);
      return;
    }
    // Backspace logo depois de uma menção apaga a menção inteira, como num chip.
    if (event.key === "Backspace" && campo.current && campo.current.selectionStart === campo.current.selectionEnd) {
      const cursor = campo.current.selectionStart;
      const alvo = mencoes.find(mencao => mencao.fim === cursor);
      if (alvo) {
        event.preventDefault();
        const novoTexto = texto.slice(0, alvo.inicio) + texto.slice(alvo.fim);
        emitir(novoTexto, reposicionar(texto, novoTexto, mencoes.filter(mencao => mencao !== alvo)));
        requestAnimationFrame(() => campo.current?.setSelectionRange(alvo.inicio, alvo.inicio));
        return;
      }
    }
    if (event.key === "Enter" && (event.metaKey || event.ctrlKey) && onEnviar) {
      event.preventDefault();
      onEnviar();
    }
  }

  // Camada de destaque: o mesmo texto, com as menções pintadas e o resto transparente.
  const camadas = useMemo(() => {
    const partes: { texto: string; mencao?: Mencao }[] = [];
    let ultimo = 0;
    for (const mencao of [...mencoes].sort((a, b) => a.inicio - b.inicio)) {
      if (mencao.inicio > ultimo) partes.push({ texto: texto.slice(ultimo, mencao.inicio) });
      partes.push({ texto: texto.slice(mencao.inicio, mencao.fim), mencao });
      ultimo = mencao.fim;
    }
    partes.push({ texto: texto.slice(ultimo) + "​" });
    return partes;
  }, [texto, mencoes]);

  const aberto = gatilho !== null && (opcoes.length > 0 || gatilho.consulta.trim().length >= 2);

  return (
    <div className="relative">
      <div
        className={`relative rounded-lg border border-slate-200 bg-white transition-colors focus-within:border-[#612035]
          focus-within:ring-2 focus-within:ring-[#612035]/20 dark:border-slate-700 dark:bg-slate-800
          dark:focus-within:border-[#e8a3b6] dark:focus-within:ring-[#e8a3b6]/20 ${className}`}
      >
        <div aria-hidden className={`pointer-events-none absolute inset-0 overflow-hidden text-transparent ${TIPOGRAFIA}`}>
          {camadas.map((parte, indice) =>
            parte.mencao ? (
              <mark
                key={indice}
                className={`rounded-[4px] text-transparent ${
                  parte.mencao.tipo === "user"
                    ? "bg-[#612035]/12 dark:bg-[#e8a3b6]/25"
                    : "bg-[#2956E0]/12 dark:bg-[#7ea0ff]/25"
                }`}
              >
                {parte.texto}
              </mark>
            ) : (
              <span key={indice}>{parte.texto}</span>
            ),
          )}
        </div>
        <textarea
          ref={campo}
          id={id}
          aria-label={rest["aria-label"]}
          autoFocus={autoFocus}
          rows={rows}
          value={texto}
          placeholder={placeholder}
          role="combobox"
          aria-expanded={aberto}
          aria-controls={listaId}
          aria-autocomplete="list"
          onChange={event => {
            const novoTexto = event.target.value;
            const novasMencoes = reposicionar(texto, novoTexto, mencoes);
            emitir(novoTexto, novasMencoes);
            atualizarGatilho(novoTexto, event.target.selectionStart, novasMencoes);
          }}
          onSelect={event => {
            const alvo = event.currentTarget;
            if (alvo.selectionStart === alvo.selectionEnd) atualizarGatilho(texto, alvo.selectionStart, mencoes);
          }}
          onBlur={() => setTimeout(() => setGatilho(null), 150)}
          onKeyDown={aoTeclar}
          className={`relative block w-full resize-none overflow-hidden bg-transparent text-slate-900 placeholder:text-slate-400
            focus:outline-none dark:text-white ${TIPOGRAFIA}`}
        />
      </div>

      {aberto && (
        <div
          id={listaId}
          role="listbox"
          className="absolute left-0 right-0 z-30 mt-1 max-h-72 overflow-y-auto rounded-xl border border-slate-200 bg-white py-1
            shadow-xl dark:border-slate-700 dark:bg-slate-800"
        >
          {opcoes.length === 0 && (
            <p className="px-3 py-2.5 text-xs text-slate-500">
              {buscaEmpresa.isFetching || consultaEmpresa !== consultaAtual ? "Buscando…" : "Ninguém nem nenhuma empresa com esse nome."}
            </p>
          )}
          {opcoes.map((opcao, indice) => {
            const primeiraDoGrupo = indice === 0 || opcoes[indice - 1].tipo !== opcao.tipo;
            return (
              <div key={`${opcao.tipo}-${opcao.id}`}>
                {primeiraDoGrupo && (
                  <p className="px-3 pb-1 pt-2 text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
                    {opcao.tipo === "user" ? "Pessoas" : "Empresas"}
                  </p>
                )}
                <button
                  type="button"
                  role="option"
                  aria-selected={indice === destaque}
                  // mousedown, e não click: o blur do textarea fecharia a lista antes do clique.
                  onMouseDown={event => {
                    event.preventDefault();
                    escolher(opcao);
                  }}
                  onMouseEnter={() => setDestaque(indice)}
                  className={`flex min-h-10 w-full cursor-pointer items-center gap-2.5 px-3 py-1.5 text-left ${
                    indice === destaque ? "bg-slate-100 dark:bg-slate-700" : ""
                  }`}
                >
                  {opcao.tipo === "user" ? (
                    <span className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#612035] text-[10px] font-semibold text-white">
                      {opcao.iniciais}
                    </span>
                  ) : (
                    <span className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-[#2956E0]/10 text-[#2956E0] dark:text-[#9db5ff]">
                      <Icon name="business" size={13} />
                    </span>
                  )}
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm text-slate-800 dark:text-slate-100">{opcao.rotulo}</span>
                    {opcao.detalhe && <span className="block truncate text-[11px] text-slate-500">{opcao.detalhe}</span>}
                  </span>
                </button>
              </div>
            );
          })}
          {gatilho && gatilho.consulta.trim().length < 2 && opcoes.some(opcao => opcao.tipo === "user") && (
            <p className="border-t border-slate-100 px-3 py-1.5 text-[10px] text-slate-400 dark:border-slate-700">
              continue digitando para buscar empresas por nome ou CNPJ
            </p>
          )}
        </div>
      )}
    </div>
  );
}
