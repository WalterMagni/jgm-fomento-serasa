"use client";

import { useState, type ReactNode } from "react";
import Icon from "@/components/ui/Icon";
import { BOTAO } from "@/components/ui/botao";
import type {
  JuditContagem,
  JuditPolo,
  JuditPontoDeAtencao,
  JuditProcesso,
  JuditProcessos,
  JuditSituacao,
} from "@/types/judit";

/**
 * Peças da Judit usadas no card da Visão 360º e na página de processos. Borda e destaques no verde
 * do logo da Judit (judit.io), para não se confundir com os dados do Serasa.
 */

export const VERDE = "#15BE62";

/** A partir daqui avisa: vários sacados dizendo que o título protestado não era devido. */
const ALERTA_TITULO_CONTESTADO = 3;

export function moeda(valor: number) {
  return valor.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

export function data(valor: string | null | undefined) {
  if (!valor) return "—";
  const [ano, mes, dia] = valor.slice(0, 10).split("-");
  return ano && mes && dia ? `${dia}/${mes}/${ano}` : "—";
}

/** "14:32" de um LocalDateTime do backend (sem fuso: é a hora de Brasília, a mesma do navegador). */
export function hora(valor: string | null | undefined) {
  if (!valor) return "—";
  return valor.slice(11, 16) || "—";
}

/** Sem acento e em minúsculas: "SÃO PAULO" casa com "sao paulo". */
export function normalizar(texto: string) {
  return texto.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

export function SelosJudit() {
  return (
    <>
      <span className="rounded-full border border-[#C6F1D6] px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-[#047857] dark:border-[#15BE62]/40 dark:text-[#15BE62]">
        Judit
      </span>
      <span
        className="rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-[#05301A]"
        style={{ backgroundColor: VERDE }}
        title="Em teste com o time: preços, limite e telas ainda podem mudar depois da conversa com a Judit."
      >
        Beta
      </span>
    </>
  );
}

export function Numero({ rotulo, valor, destaque, dica }: { rotulo: string; valor: number | string; destaque?: boolean; dica?: string }) {
  return (
    <div className="rounded-lg border border-border-light px-3 py-2.5 dark:border-border-dark" title={dica}>
      <div className="text-[11px] font-bold uppercase tracking-wide text-gray-400">{rotulo}</div>
      <div className={`text-xl font-bold ${destaque ? "text-primary" : "text-grafite dark:text-white"}`}>{valor}</div>
    </div>
  );
}

/** @param onEscolher com ele, cada item vira botão (na página de processos, filtra pelo tipo) */
export function ListaContagem({
  titulo,
  itens,
  onEscolher,
}: {
  titulo: string;
  itens: JuditContagem[];
  onEscolher?: (valor: string) => void;
}) {
  if (itens.length === 0) return null;
  return (
    <div>
      <div className="mb-1.5 text-[11px] font-bold uppercase tracking-wide text-gray-400">{titulo}</div>
      <ul className="space-y-1">
        {itens.slice(0, 5).map(item => {
          const rotulo = (
            <span className={item.valor ? "text-grafite dark:text-gray-200" : "italic text-gray-400"}>
              {item.valor ?? "Não informado"}
            </span>
          );
          return (
            <li key={item.valor ?? "—"} className="flex items-baseline justify-between gap-3 text-xs">
              {onEscolher && item.valor ? (
                <button
                  type="button"
                  onClick={() => onEscolher(item.valor!)}
                  title="Filtrar a lista por este tipo"
                  className="text-left hover:text-[#047857] hover:underline"
                >
                  {rotulo}
                </button>
              ) : (
                rotulo
              )}
              <span className="font-semibold tabular-nums text-gray-500">{item.quantidade}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

export function Carimbo({
  rotulo,
  consultadoEm,
  consultadoPor,
  custo,
  desatualizado,
  validadeDias,
}: {
  rotulo: string;
  consultadoEm: string;
  consultadoPor: string | null;
  custo: number;
  desatualizado: boolean;
  validadeDias: number;
}) {
  return (
    <span className="inline-flex flex-wrap items-center gap-1.5 text-[11px] text-gray-500">
      {rotulo} em {data(consultadoEm)}
      {consultadoPor && ` por ${consultadoPor}`} · {moeda(custo)}
      {desatualizado && (
        <span
          className="rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 font-semibold text-amber-800 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200"
          title={`Consultado há mais de ${validadeDias} dias. Não atualiza sozinho: use o botão se precisar.`}
        >
          desatualizado
        </span>
      )}
    </span>
  );
}

/** Os números da lista (processos únicos pelo CNJ), o alerta de título contestado e os assuntos como ré. */
export function TotaisDaLista({
  dados,
  totalNoResumo,
  onAssunto,
}: {
  dados: JuditProcessos;
  totalNoResumo: number | null;
  /** Com ele, os assuntos mais comuns viram botões que filtram a lista. */
  onAssunto?: (valor: string) => void;
}) {
  return (
    <div className="space-y-3">
      {dados.tituloContestadoComoReu >= ALERTA_TITULO_CONTESTADO && (
        <div className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100">
          <Icon name="warning" size={18} className="mt-0.5 shrink-0" />
          <span>
            <strong>{dados.tituloContestadoComoReu} processos</strong> com a empresa como ré por protesto, negativação ou
            duplicata contestada. Vários sacados dizendo que o título não era devido é o desenho de duplicata sem lastro —
            não é prova, pede verificação antes de operar.
          </span>
        </div>
      )}

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Numero rotulo="Processos" valor={dados.quantidade} dica="Processos únicos pelo número CNJ, com as instâncias juntas." />
        <Numero rotulo="Como ré" valor={dados.comoReu} destaque />
        <Numero rotulo="Como autora" valor={dados.comoAutor} />
        <Numero
          rotulo="Valor das causas (ré)"
          valor={dados.valorComoReu > 0 ? moeda(dados.valorComoReu) : "—"}
          dica="Soma do valor da causa dos processos em que a empresa é ré. Não é dívida: é o que o autor pediu."
        />
      </div>

      <NotaContagem registros={dados.registros} processos={dados.quantidade} totalNoResumo={totalNoResumo} />

      {dados.assuntosComoReu.length > 0 && (
        <div className="flex flex-wrap items-center gap-1.5 text-xs">
          <span className="font-semibold text-gray-500">Mais comuns como ré:</span>
          {dados.assuntosComoReu.map(a =>
            onAssunto && a.valor ? (
              <button
                key={a.valor}
                type="button"
                onClick={() => onAssunto(a.valor!)}
                title="Filtrar a lista por este assunto"
                className="rounded-full bg-[#EFEDE6] px-2 py-0.5 text-[#5E5C55] transition-colors hover:bg-[#C6F1D6] hover:text-[#047857] dark:bg-white/10 dark:text-gray-300"
              >
                {a.valor} · {a.quantidade}
              </button>
            ) : (
              <span key={a.valor} className="rounded-full bg-[#EFEDE6] px-2 py-0.5 text-[#5E5C55] dark:bg-white/10 dark:text-gray-300">
                {a.valor} · {a.quantidade}
              </span>
            ),
          )}
        </div>
      )}
    </div>
  );
}

/**
 * Por que o número da Judit não bate com o da lista: ela conta um registro por instância, e no
 * recurso a empresa pode trocar de polo (ré na 1ª, recorrente na 2ª).
 */
export function NotaContagem({ registros, processos, totalNoResumo }: { registros: number; processos: number; totalNoResumo: number | null }) {
  const juntados = registros - processos;
  const faltam = totalNoResumo !== null && totalNoResumo > registros ? totalNoResumo - registros : 0;
  if (juntados <= 0 && faltam <= 0) return null;
  return (
    <p className="flex gap-1.5 text-[11px] text-gray-500">
      <Icon name="info" size={13} className="mt-px shrink-0" />
      <span>
        A Judit conta um registro por instância: {registros} registros viraram {processos} processos, porque {juntados}{" "}
        {juntados === 1 ? "aparece" : "aparecem"} em 1ª e 2ª instância. Por isso o resumo da Judit mostra números maiores —
        e no recurso a empresa pode trocar de polo (ré na 1ª, recorrente na 2ª); aqui conta como ré.
        {faltam > 0 && ` O resumo informa ${totalNoResumo} registros e a lista trouxe ${registros}; a diferença é da Judit.`}
      </span>
    </p>
  );
}

const MAX_CONTRAPARTES = 3;

/**
 * O selo é sobre a empresa consultada, e o nome embaixo é da outra parte — com "Ré" solto em cima do
 * nome da JGM, parecia que a JGM era a ré. Cada um ganha o seu papel por escrito.
 */
const SELO_EMPRESA: Record<JuditPolo, string> = {
  REU: "Empresa ré",
  AUTOR: "Empresa autora",
  TERCEIRO: "Empresa terceira",
  DESCONHECIDO: "Polo não identificado",
};

const PAPEL_DA_OUTRA_PARTE: Record<JuditPolo, string> = {
  REU: "Autor",
  AUTOR: "Réu",
  TERCEIRO: "Partes",
  DESCONHECIDO: "Partes",
};

export type CampoOrdem = "DISTRIBUICAO" | "VALOR" | "ANDAMENTO";
export type Ordem = { campo: CampoOrdem; decrescente: boolean };

/** Ordena sem mexer na lista original. Sem data ou sem valor vai sempre para o fim, em qualquer sentido. */
export function ordenar(lista: JuditProcesso[], { campo, decrescente }: Ordem) {
  const chave = (p: JuditProcesso): number | string | null =>
    campo === "VALOR" ? p.valor : campo === "ANDAMENTO" ? (p.ultimoAndamento?.data ?? null) : p.distribuidoEm;
  return [...lista].sort((a, b) => {
    const x = chave(a);
    const y = chave(b);
    if (x === null || y === null) return x === y ? 0 : x === null ? 1 : -1;
    const comparacao = x < y ? -1 : x > y ? 1 : 0;
    return decrescente ? -comparacao : comparacao;
  });
}

/**
 * Cabeçalho da tabela. Com `ordem`, Distribuição, Valor e Último andamento ordenam ao clicar.
 * `comAcao` abre a coluna do botão de detalhe (fora da impressão).
 */
export function CabecalhoTabela({
  ordem,
  onOrdenar,
  comAcao,
}: { ordem?: Ordem; onOrdenar?: (campo: CampoOrdem) => void; comAcao?: boolean } = {}) {
  const coluna = (campo: CampoOrdem, rotulo: string, direita = false) => {
    if (!ordem || !onOrdenar) return <th className={`px-3 py-2 ${direita ? "text-right" : ""}`}>{rotulo}</th>;
    const ativa = ordem.campo === campo;
    return (
      <th className={`px-3 py-2 ${direita ? "text-right" : ""}`} aria-sort={ativa ? (ordem.decrescente ? "descending" : "ascending") : "none"}>
        <button
          type="button"
          onClick={() => onOrdenar(campo)}
          className={`inline-flex items-center gap-1 uppercase tracking-wide hover:text-[#047857] ${ativa ? "text-[#047857] dark:text-[#15BE62]" : ""}`}
          title="Ordenar"
        >
          {rotulo}
          <Icon name={ativa ? (ordem.decrescente ? "arrow_downward" : "arrow_upward") : "unfold_more"} size={12} />
        </button>
      </th>
    );
  };
  return (
    <thead className="bg-[#FBFAF7] text-left text-[11px] uppercase tracking-wide text-gray-400 dark:bg-white/5">
      <tr>
        {coluna("DISTRIBUICAO", "Distribuição")}
        <th className="px-3 py-2">Processo</th>
        <th className="px-3 py-2">Empresa × outra parte</th>
        <th className="px-3 py-2">Foro</th>
        {coluna("VALOR", "Valor da causa", true)}
        {coluna("ANDAMENTO", "Último andamento")}
        {comAcao && <th className="px-3 py-2 print:hidden">Detalhe</th>}
      </tr>
    </thead>
  );
}

export function LinhaProcesso({ processo: p, acao }: { processo: JuditProcesso; acao?: ReactNode }) {
  const corPolo =
    p.polo === "REU"
      ? "bg-primary/10 text-primary"
      : p.polo === "AUTOR"
        ? "bg-[#2956E0]/10 text-[#2956E0]"
        : "bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-gray-400";
  const extras = p.contrapartes.length - MAX_CONTRAPARTES;
  return (
    <tr className="break-inside-avoid align-top">
      <td className="whitespace-nowrap px-3 py-2.5 text-gray-500">{data(p.distribuidoEm)}</td>
      <td className="px-3 py-2.5">
        <div className="font-mono text-[11px] text-grafite dark:text-gray-200">
          <span className="whitespace-nowrap">{p.cnj ?? "sem número"}</span>
          {p.instancias.length > 0 && <span className="ml-1 text-gray-400">({p.instancias.map(i => `${i}ª`).join(", ")})</span>}
          {p.emSegredo && (
            <span className="ml-1 inline-flex items-center gap-0.5 text-gray-400" title="Segredo de justiça">
              <Icon name="lock" size={11} />
            </span>
          )}
        </div>
        <div className="font-semibold text-grafite dark:text-white">{p.classe ?? "—"}</div>
        {p.assuntos.length > 0 && <div className="text-gray-500">{p.assuntos.join(" · ")}</div>}
      </td>
      <td className="px-3 py-2.5">
        <span className={`whitespace-nowrap rounded-full px-2 py-0.5 text-[11px] font-semibold ${corPolo}`}>{SELO_EMPRESA[p.polo]}</span>
        {p.contrapartes.length > 0 && (
          <div className="mt-1 text-gray-500" title={extras > 0 ? p.contrapartes.join("\n") : undefined}>
            <span className="font-semibold text-gray-400">{PAPEL_DA_OUTRA_PARTE[p.polo]}: </span>
            {p.contrapartes.slice(0, MAX_CONTRAPARTES).join(", ")}
            {extras > 0 && ` +${extras}`}
          </div>
        )}
      </td>
      <td className="px-3 py-2.5 text-gray-500">
        <div className="whitespace-nowrap font-semibold text-grafite dark:text-gray-200">
          {p.tribunal ?? "—"}
          {(p.cidade || p.uf) && (
            <span className="ml-1 font-normal text-gray-500">
              {[p.cidade, p.uf].filter(Boolean).join("/")}
            </span>
          )}
        </div>
        {p.orgaos.length > 0 && <div title={p.orgaos.join("\n")}>{p.orgaos[0]}</div>}
      </td>
      <td className="whitespace-nowrap px-3 py-2.5 text-right tabular-nums text-grafite dark:text-gray-200">
        {p.valor !== null ? moeda(p.valor) : <span className="text-gray-400">não informado</span>}
      </td>
      <td className="px-3 py-2.5 text-gray-500">
        {p.ultimoAndamento ? (
          <>
            <div className="whitespace-nowrap">{data(p.ultimoAndamento.data)}</div>
            <div className="line-clamp-2">{p.ultimoAndamento.texto ?? "—"}</div>
          </>
        ) : (
          "—"
        )}
      </td>
      {acao !== undefined && <td className="whitespace-nowrap px-3 py-2.5 print:hidden">{acao}</td>}
    </tr>
  );
}

const ICONE_DO_PONTO: Record<JuditPontoDeAtencao["tipo"], string> = {
  CRIMINAL: "report",
  RECUPERACAO_FALENCIA: "error",
  EXECUCAO_FISCAL: "account_balance",
  TITULO_CONTESTADO: "receipt_long",
  TRABALHISTA: "groups",
};

/**
 * O que a pré-consulta acusou: criminal, falência, execução fiscal, título contestado, trabalhista.
 * Os agregados da Judit não se cruzam com o polo — a ressalva vai junto, sempre.
 */
export function PontosDeAtencao({ pontos }: { pontos: JuditPontoDeAtencao[] }) {
  if (pontos.length === 0) {
    return (
      <p className="flex items-center gap-1.5 text-xs text-gray-500">
        <Icon name="check_circle" size={14} className="text-[#047857] dark:text-[#15BE62]" />
        Nenhum ponto de atenção no resumo: sem criminal, falência, execução fiscal ou título contestado.
      </p>
    );
  }
  return (
    <div className="space-y-2">
      <div className="text-[11px] font-bold uppercase tracking-wide text-gray-400">Pontos de atenção</div>
      <ul className="flex flex-wrap gap-1.5">
        {pontos.map(p => (
          <li
            key={p.tipo}
            className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${
              p.tipo === "CRIMINAL" || p.tipo === "RECUPERACAO_FALENCIA"
                ? "bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300"
                : "bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-200"
            }`}
          >
            <Icon name={ICONE_DO_PONTO[p.tipo]} size={14} />
            {p.titulo} · {p.quantidade}
          </li>
        ))}
      </ul>
      <p className="text-[11px] text-gray-500">
        O resumo não cruza área com polo: um processo criminal pode ter a empresa como vítima, e uma falência pode ser de
        um cliente em que ela é credora. O On Demand mostra cada processo.
      </p>
    </div>
  );
}

/** Onde o mês fica com esta consulta: dentro da franquia, no excedente ou passando do limite. */
export function faixaDoMes(consumo: JuditSituacao["consumo"], custo: number) {
  const depois = consumo.gastoMes + custo;
  if (depois > consumo.limite) {
    return {
      tom: "limite" as const,
      texto: `Passa do limite do mês (${moeda(consumo.limite)}): o mês ficaria em ${moeda(depois)}. Peça ao admin para liberar um valor a mais.`,
    };
  }
  if (depois > consumo.franquia) {
    return {
      tom: "excedente" as const,
      texto: `Entra no excedente: o mês fica em ${moeda(depois)}, ${moeda(depois - consumo.franquia)} acima da franquia de ${moeda(consumo.franquia)}.`,
    };
  }
  return {
    tom: "franquia" as const,
    texto: `Dentro da franquia: o mês fica em ${moeda(depois)} de ${moeda(consumo.franquia)}, que já estão pagos.`,
  };
}

/** Por que um botão que cobra está travado. Nulo = pode consultar. O servidor confere de novo. */
export function motivoBloqueio(situacao: JuditSituacao, preco: number): string | null {
  if (!situacao.configurada) return "A chave da Judit não está configurada no servidor.";
  if (!situacao.podeUsar) return "Só quem tem a marca Judit (Configurações › Usuários) consulta.";
  if (situacao.consumo.gastoMes + preco > situacao.consumo.limite) {
    return `O limite de ${moeda(situacao.consumo.limite)} do mês seria ultrapassado. Peça ao admin para liberar um valor a mais.`;
  }
  return null;
}

/** Botão que gasta: o primeiro clique só pede confirmação, com o preço à vista. */
export function BotaoCobrado({
  rotulo,
  preco,
  bloqueio,
  pendente,
  onConfirmar,
  discreto,
}: {
  rotulo: string;
  preco: number;
  bloqueio: string | null;
  pendente: boolean;
  onConfirmar: () => void;
  /** Atualizar algo já consultado: botão neutro, para não competir com o principal. */
  discreto?: boolean;
}) {
  const [confirmando, setConfirmando] = useState(false);

  if (pendente) {
    return (
      <span className="inline-flex items-center gap-2 text-sm font-semibold text-suave">
        <Icon name="progress_activity" size={16} className="animate-spin" /> Consultando a Judit…
      </span>
    );
  }

  if (confirmando) {
    return (
      <span className="inline-flex flex-wrap items-center gap-2">
        <span className="text-xs font-semibold text-grafite dark:text-white">Gastar {moeda(preco)}?</span>
        <button
          type="button"
          className={BOTAO.judit}
          onClick={() => {
            setConfirmando(false);
            onConfirmar();
          }}
        >
          Confirmar
        </button>
        <button type="button" className={BOTAO.fantasma} onClick={() => setConfirmando(false)}>
          Cancelar
        </button>
      </span>
    );
  }

  return (
    <button
      type="button"
      className={discreto ? BOTAO.fantasma : BOTAO.judit}
      disabled={bloqueio !== null}
      title={bloqueio ?? undefined}
      onClick={() => setConfirmando(true)}
    >
      <Icon name={discreto ? "refresh" : "gavel"} size={16} />
      {rotulo} · {moeda(preco)}
    </button>
  );
}
