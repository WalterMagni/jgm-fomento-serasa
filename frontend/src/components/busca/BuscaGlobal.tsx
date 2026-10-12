"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import Icon from "@/components/ui/Icon";
import { usePessoasResumo } from "@/hooks/usePersonProfile";
import { caixaTitulo, formatCnpj, normalizar } from "@/lib/formatadores";
import { GRUPOS_NAV, SETTINGS_ITEM } from "@/app/(dashboard)/NavItems";
import type { ClientProfile, PageResponse } from "@/types/company-detail";
import { formatCpf } from "@/types/person-analysis";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
const MINIMO = 2;
const POR_GRUPO = 5;
/** Id fixo: há um campo só no portal, e o useId mudava entre servidor e navegador. */
const ID_LISTA = "busca-global-resultados";

type Resultado = { chave: string; href: string; titulo: string; detalhe: string; icone: string };
type Grupo = { id: string; titulo: string; itens: Resultado[]; carregando?: boolean; verTodos?: { href: string; rotulo: string } };

/** Telas do menu, com os subitens da Praça, para quem busca "praça" ou "configurações". */
const TELAS: Resultado[] = [
  ...GRUPOS_NAV.flatMap(grupo =>
    grupo.itens.flatMap(item => [
      { chave: item.href, href: item.href, titulo: item.label, detalhe: grupo.titulo, icone: item.icon },
      ...(item.children ?? []).map(filho => ({
        chave: filho.href,
        href: filho.href,
        titulo: filho.label,
        detalhe: `${grupo.titulo} · ${item.label}`,
        icone: filho.icon,
      })),
    ]),
  ),
  { chave: SETTINGS_ITEM.href, href: SETTINGS_ITEM.href, titulo: "Configurações", detalhe: "Sistema · perfil e consumo Serasa", icone: SETTINGS_ITEM.icon },
];

function useEmpresas(termo: string) {
  return useQuery<PageResponse<ClientProfile>>({
    queryKey: ["buscaGlobal", "empresas", termo],
    enabled: termo.length >= MINIMO,
    placeholderData: keepPreviousData,
    staleTime: 30 * 1000,
    queryFn: async ({ signal }) => {
      const token = localStorage.getItem("serasa_token");
      const params = new URLSearchParams({ search: termo, size: "8", ordem: "nome" });
      const res = await fetch(`${API_URL}/company/profiles?${params}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        signal,
      });
      if (!res.ok) throw new Error("Não foi possível buscar as empresas.");
      return res.json();
    },
  });
}

/** Trecho que bateu com a busca em negrito; sem acento dos dois lados, então os índices batem. */
function Destaque({ texto, termo }: { texto: string; termo: string }) {
  const i = termo ? normalizar(texto).indexOf(normalizar(termo)) : -1;
  if (i < 0) return <>{texto}</>;
  return (
    <>
      {texto.slice(0, i)}
      <mark className="rounded-[3px] bg-secondary/20 px-px text-inherit">{texto.slice(i, i + termo.length)}</mark>
      {texto.slice(i + termo.length)}
    </>
  );
}

/**
 * Campo de busca do topo: empresas (nome, CNPJ ou código do cliente), pessoas físicas (nome ou
 * CPF) e telas do menu, num painel só. Setas escolhem, Enter abre, Esc fecha.
 */
export default function BuscaGlobal() {
  const router = useRouter();
  const caixa = useRef<HTMLDivElement>(null);
  const campo = useRef<HTMLInputElement>(null);
  const [termo, setTermo] = useState("");
  const [enviado, setEnviado] = useState("");
  const [aberto, setAberto] = useState(false);
  const [ativo, setAtivo] = useState(0);

  // A busca de empresas vai ao servidor: espera a pessoa parar de digitar.
  useEffect(() => {
    const espera = setTimeout(() => setEnviado(termo.trim()), 250);
    return () => clearTimeout(espera);
  }, [termo]);

  // Clique fora fecha o painel.
  useEffect(() => {
    if (!aberto) return;
    const aoClicar = (e: MouseEvent) => {
      if (!caixa.current?.contains(e.target as Node)) setAberto(false);
    };
    document.addEventListener("mousedown", aoClicar);
    return () => document.removeEventListener("mousedown", aoClicar);
  }, [aberto]);

  const limpo = termo.trim();
  const buscando = aberto && limpo.length >= MINIMO;
  const empresas = useEmpresas(aberto ? enviado : "");
  const pessoas = usePessoasResumo(buscando);

  const grupos = useMemo<Grupo[]>(() => {
    if (limpo.length < MINIMO) return [];
    const alvo = normalizar(limpo);
    const digitos = /^[\d.\-/\s]+$/.test(limpo) ? limpo.replace(/\D/g, "") : "";
    const codigo = digitos.replace(/^0+/, "");

    const telas = TELAS.filter(t => normalizar(t.titulo).includes(alvo)).slice(0, 4);

    // Código exato ou CNPJ exato vêm antes dos nomes que só contêm o termo.
    const listaEmpresas = [...(empresas.data?.content ?? [])]
      .filter(p => p.client?.documentNumber)
      .sort((a, b) => {
        const exato = (p: ClientProfile) =>
          (codigo && p.client?.clientCode === codigo) || (digitos.length === 14 && p.client?.documentNumber === digitos) ? 0 : 1;
        return exato(a) - exato(b);
      })
      .slice(0, POR_GRUPO)
      .map<Resultado>(p => {
        const cnpj = p.client!.documentNumber;
        const codigoCliente = p.client?.clientCode;
        return {
          chave: `pj-${cnpj}`,
          href: `/clients/${cnpj}`,
          titulo: caixaTitulo(p.companyDetail?.companyName || p.client?.name || cnpj),
          detalhe: `${formatCnpj(cnpj)}${codigoCliente ? ` · cód. ${codigoCliente}` : ""}`,
          icone: "business",
        };
      });

    const listaPessoas = (pessoas.data ?? [])
      .filter(p => (p.nome && normalizar(p.nome).includes(alvo)) || (digitos.length >= 3 && p.cpf.includes(digitos)))
      .slice(0, POR_GRUPO)
      .map<Resultado>(p => ({
        chave: `pf-${p.cpf}`,
        href: `/individuals/${p.cpf}`,
        titulo: caixaTitulo(p.nome) || "Nome não disponível",
        detalhe: [formatCpf(p.cpf), p.cidade && p.uf ? `${caixaTitulo(p.cidade)}/${p.uf}` : null].filter(Boolean).join(" · "),
        icone: "person",
      }));

    const q = encodeURIComponent(limpo);
    return [
      { id: "telas", titulo: "Telas", itens: telas },
      {
        id: "empresas",
        titulo: "Empresas",
        itens: listaEmpresas,
        carregando: empresas.isFetching || enviado !== limpo,
        verTodos: (empresas.data?.totalElements ?? 0) > POR_GRUPO ? { href: `/?q=${q}`, rotulo: `Ver as ${empresas.data!.totalElements} na Gestão de Carteira` } : undefined,
      },
      { id: "pessoas", titulo: "Pessoas físicas", itens: listaPessoas, carregando: pessoas.isLoading, verTodos: listaPessoas.length === POR_GRUPO ? { href: `/individuals?q=${q}`, rotulo: "Ver todas em Pessoas Físicas" } : undefined },
    ];
  }, [limpo, enviado, empresas.data, empresas.isFetching, pessoas.data, pessoas.isLoading]);

  const opcoes = grupos.flatMap(g => g.itens);
  const posicaoDe = new Map(opcoes.map((o, i) => [o.chave, i]));
  const indiceAtivo = Math.min(ativo, Math.max(opcoes.length - 1, 0));
  const carregando = grupos.some(g => g.carregando);

  const fechar = () => {
    setAberto(false);
    setTermo("");
    setAtivo(0);
    campo.current?.blur();
  };

  const aoTeclar = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Escape") {
      if (aberto && limpo) setTermo("");
      else fechar();
      return;
    }
    if (!opcoes.length) return;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setAberto(true);
      setAtivo((indiceAtivo + 1) % opcoes.length);
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setAtivo((indiceAtivo - 1 + opcoes.length) % opcoes.length);
    } else if (e.key === "Enter") {
      e.preventDefault();
      router.push(opcoes[indiceAtivo].href);
      fechar();
    }
  };

  const idOpcao = (i: number) => `${ID_LISTA}-op-${i}`;

  return (
    <div ref={caixa} className="relative min-w-0 flex-1 sm:w-[340px] sm:flex-none xl:w-[420px]">
      <Icon name="search" size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#8A877E]" />
      <input
        ref={campo}
        type="text"
        role="combobox"
        aria-label="Buscar empresa, pessoa ou tela"
        aria-expanded={aberto && limpo.length >= MINIMO}
        aria-controls={ID_LISTA}
        aria-autocomplete="list"
        aria-activedescendant={aberto && opcoes.length ? idOpcao(indiceAtivo) : undefined}
        autoComplete="off"
        spellCheck={false}
        value={termo}
        onChange={e => {
          setTermo(e.target.value);
          setAtivo(0);
          setAberto(true);
        }}
        onFocus={() => setAberto(true)}
        onKeyDown={aoTeclar}
        placeholder="Buscar empresa, pessoa, CNPJ ou CPF"
        className="h-10 w-full text-ellipsis rounded-xl border border-linha bg-white/80 pl-10 pr-9 text-sm text-grafite outline-none transition placeholder:text-[#8A877E] hover:bg-white focus:border-primary focus:bg-white focus:ring-4 focus:ring-primary/10"
      />
      {termo && (
        <button
          type="button"
          aria-label="Limpar busca"
          onClick={() => {
            setTermo("");
            campo.current?.focus();
          }}
          className="absolute right-1.5 top-1/2 grid h-7 w-7 -translate-y-1/2 place-items-center rounded-lg text-[#8A877E] transition-colors hover:bg-[#F0EEE8] hover:text-grafite"
        >
          <Icon name="close" size={14} />
        </button>
      )}

      {aberto && (
        <div
          className="z-30 overflow-hidden rounded-painel border border-linha bg-white shadow-elevado animate-cair motion-reduce:animate-none max-sm:fixed max-sm:inset-x-4 max-sm:top-[72px] sm:absolute sm:right-0 sm:top-full sm:mt-2 sm:w-[min(560px,calc(100vw-2.5rem))]"
        >
          {limpo.length < MINIMO ? (
            <p className="px-5 py-4 text-sm text-suave">
              Digite o nome, o CNPJ ou o código do cliente, o nome ou CPF de uma pessoa, ou o nome de uma tela.
            </p>
          ) : (
            <div id={ID_LISTA} role="listbox" aria-label="Resultados da busca" className="max-h-[min(70vh,560px)] overflow-y-auto py-2">
              {grupos.map(grupo => {
                if (!grupo.itens.length && !grupo.carregando) return null;
                return (
                  <div key={grupo.id} role="group" aria-labelledby={`${ID_LISTA}-${grupo.id}`} className="pb-1">
                    <p id={`${ID_LISTA}-${grupo.id}`} className="flex items-center gap-2 px-5 pb-1.5 pt-2.5 text-[11px] font-bold uppercase tracking-[0.1em] text-[#7F7C73]">
                      {grupo.titulo}
                      {grupo.carregando && <Icon name="progress_activity" size={12} weight="bold" className="animate-spin text-primary" />}
                    </p>
                    {grupo.itens.map(item => {
                      const i = posicaoDe.get(item.chave) ?? 0;
                      const selecionado = i === indiceAtivo;
                      return (
                        <Link
                          key={item.chave}
                          id={idOpcao(i)}
                          role="option"
                          aria-selected={selecionado}
                          href={item.href}
                          onClick={fechar}
                          onMouseEnter={() => setAtivo(i)}
                          className={`mx-2 flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors ${selecionado ? "bg-[#F6EFEA]" : ""}`}
                        >
                          <span className={`grid h-9 w-9 shrink-0 place-items-center rounded-[11px] ${selecionado ? "bg-white text-primary" : "bg-[#F4F2EC] text-[#6E6B63]"}`}>
                            <Icon name={item.icone} size={18} />
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-sm font-semibold text-grafite">
                              <Destaque texto={item.titulo} termo={limpo} />
                            </span>
                            <span className="block truncate text-xs text-suave">{item.detalhe}</span>
                          </span>
                          <Icon name="chevron_right" size={14} className={`shrink-0 transition-opacity ${selecionado ? "text-primary opacity-100" : "opacity-0"}`} />
                        </Link>
                      );
                    })}
                    {grupo.verTodos && (
                      <Link
                        href={grupo.verTodos.href}
                        onClick={fechar}
                        className="mx-2 flex items-center gap-1.5 rounded-xl px-3 py-2 text-xs font-bold text-primary transition-colors hover:bg-[#F6EFEA]"
                      >
                        {grupo.verTodos.rotulo}
                        <Icon name="arrow_forward" size={12} />
                      </Link>
                    )}
                  </div>
                );
              })}
              {!opcoes.length && !carregando && (
                <div className="px-5 py-6 text-center">
                  <p className="text-sm font-semibold text-grafite">Nada encontrado para “{limpo}”.</p>
                  <p className="mt-1 text-xs text-suave">Tente parte do nome, o CNPJ, o CPF ou o código do cliente.</p>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
