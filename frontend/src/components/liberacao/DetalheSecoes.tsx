"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import CampoNumero from "@/components/ui/CampoNumero";
import { CUSTO_CONSULTA_PF } from "@/types/pessoa-resumo";
import Icon from "@/components/ui/Icon";
import MentionTextarea from "@/components/ui/mencao/MentionTextarea";
import TextoRico from "@/components/ui/mencao/TextoRico";
import { useDecidirSacado, useOpinarSacado, useParecerSacado, useRegistrarParecer, useResponderPendencia } from "@/hooks/useLiberacao";
import {
  ROTULO_POSICAO,
  ROTULO_RESULTADO,
  ROTULO_RESULTADO_CURTO,
  type DecisaoAnterior,
  type LiberacaoCard,
  type OpiniaoOperador,
  type Resultado,
  type Parecer,
  type Pendencia,
  type PosicaoParecer,
  type QuadroOpiniao,
  type Sacado,
} from "@/types/liberacao";
import Avatar from "./Avatar";
import { CarteiraLinha } from "./PropostaAr";
import { BOTAO_PRIMARIO, BOTAO_SECUNDARIO } from "./Dialogo";
import {
  COR_POSICAO,
  COR_RESULTADO,
  ICONE_POSICAO,
  ICONE_RESULTADO,
  formatDataHora,
  formatDocumento,
  formatMoeda,
  formatMoedaCurta,
  moedaParaCampo,
  parseMoeda,
  tempoRelativo,
} from "./formatters";

const formatData = (iso: string) => new Date(iso).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" });

export function Secao({ titulo, icone, acao, children }: { titulo: string; icone: string; acao?: ReactNode; children: ReactNode }) {
  return (
    <section className="border-t border-slate-100 pt-5 first:border-t-0 first:pt-0 dark:border-slate-800">
      <header className="mb-3 flex items-center justify-between gap-2">
        <h3 className="inline-flex items-center gap-1.5 font-sans text-[11px] font-bold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400">
          <Icon name={icone} size={14} />
          {titulo}
        </h3>
        {acao}
      </header>
      {children}
    </section>
  );
}

// ------------------------------------------------------------------ sacados

/** Decisões anteriores sobre o mesmo sacado, em outros cards. Reprovação primeiro chama atenção. */
export function HistoricoSacado({ historico, compacto = false }: { historico: DecisaoAnterior[]; compacto?: boolean }) {
  if (historico.length === 0) return null;
  const visiveis = compacto ? historico.slice(0, 2) : historico.slice(0, 4);
  return (
    <div className="mt-1 flex flex-wrap gap-1">
      {visiveis.map(decisao => (
        <a
          key={`${decisao.cardId}-${decisao.situacao}`}
          href={`/liberacao?card=${decisao.cardId}`}
          target="_blank"
          rel="noopener noreferrer"
          title={`${ROTULO_RESULTADO[decisao.situacao]} no card #${decisao.numero} (${decisao.cedenteNome})${decisao.decididoPor ? ` por ${decisao.decididoPor}` : ""}`}
          className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[10.5px] font-medium hover:underline ${COR_RESULTADO[decisao.situacao]}`}
        >
          <Icon name={ICONE_RESULTADO[decisao.situacao]} size={10} />
          {ROTULO_RESULTADO_CURTO[decisao.situacao]} em #{decisao.numero}
          {decisao.decididoEm && <span className="opacity-75">· {formatData(decisao.decididoEm)}</span>}
        </a>
      ))}
      {historico.length > visiveis.length && <span className="text-[10.5px] text-slate-400">+{historico.length - visiveis.length}</span>}
    </div>
  );
}

const DECISOES: Resultado[] = ["APROVADO", "PARCIAL", "REPROVADO"];

/** O quadrado O tem uma opção a mais: Amostragem, que não é decisão (pedido do time em 2026-10-26). */
const OPINIOES_OPERADOR: OpiniaoOperador[] = [...DECISOES, "AMOSTRAGEM"];

/** Cor cheia de cada opção no quadrado: verde, amarelo e vermelho, pedido do time; Amostragem em azul. */
const COR_QUADRADO: Record<OpiniaoOperador, string> = {
  APROVADO: "border-emerald-600 bg-emerald-500 text-white",
  PARCIAL: "border-amber-500 bg-amber-400 text-amber-950",
  REPROVADO: "border-rose-700 bg-rose-600 text-white",
  AMOSTRAGEM: "border-sky-700 bg-sky-500 text-white",
};

const ROTULO_OPCAO: Record<OpiniaoOperador, string> = { ...ROTULO_RESULTADO_CURTO, AMOSTRAGEM: "Amostragem" };
const ROTULO_OPCAO_LONGO: Record<OpiniaoOperador, string> = { ...ROTULO_RESULTADO, AMOSTRAGEM: "Amostragem" };

type Quadro = "O" | "C" | "D";
const NOME_QUADRO: Record<Quadro, string> = { O: "Operador", C: "Comitê", D: "Diretoria" };

/** Só o fundo da cor da opção, para a amostra pequena dos menus. */
const fundoDecisao = (opcao: OpiniaoOperador) => COR_QUADRADO[opcao].split(" ").filter(classe => classe.startsWith("bg-")).join(" ");

/**
 * Um quadrado (O, C ou D) do sacado. Clicável quando a pessoa pode marcar: abre as três decisões
 * e o valor aprovado do parcial — ou, no D, aprovar ou reprovar o card. Fora disso só mostra a
 * cor e, ao passar o mouse, quem marcou e quando.
 */
function Quadrado({
  letra,
  situacao,
  porNome,
  em,
  editavel,
  opcoes = DECISOES,
  pedirValor,
  valorAtual,
  ocupado,
  onMarcar,
  decidirCard,
  dica,
  parecer,
  salvandoParecer,
  onParecer,
}: {
  letra: Quadro;
  situacao: OpiniaoOperador | null;
  porNome: string | null;
  em: string | null;
  editavel: boolean;
  /** O que o menu oferece: as três decisões, ou também Amostragem no O. */
  opcoes?: OpiniaoOperador[];
  /** Parcial pede o valor aprovado. */
  pedirValor?: boolean;
  valorAtual?: number | null;
  ocupado: boolean;
  onMarcar: (situacao: OpiniaoOperador | null, valorAprovado?: number) => void;
  /** No lugar das três decisões, aprovar ou reprovar o card inteiro (o D). */
  decidirCard?: (para: "APROVADO" | "REPROVADO") => void;
  /** Como o quadrado se preenche, no título, enquanto vazio e travado. */
  dica?: string;
  parecer: string | null;
  salvandoParecer: boolean;
  onParecer: (texto: string, aoSalvar: () => void) => void;
}) {
  const [aberto, setAberto] = useState(false);
  const [texto, setTexto] = useState(parecer ?? "");
  const [parcial, setParcial] = useState(false);
  const [valor, setValor] = useState(moedaParaCampo(valorAtual));
  const caixa = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!aberto) return;
    function fora(event: MouseEvent) {
      if (caixa.current && !caixa.current.contains(event.target as Node)) {
        setAberto(false);
        setParcial(false);
      }
    }
    document.addEventListener("mousedown", fora);
    return () => document.removeEventListener("mousedown", fora);
  }, [aberto]);

  const titulo = `${letra} · ${NOME_QUADRO[letra]}: ${situacao ? ROTULO_OPCAO_LONGO[situacao] : "sem decisão"}${
    situacao && porNome ? ` — ${porNome}${em ? `, ${formatDataHora(em)}` : ""}` : ""
  }${!situacao && !editavel && dica ? ` (${dica})` : ""}`;

  function escolher(opcao: OpiniaoOperador | null) {
    if (opcao === "PARCIAL" && pedirValor) {
      setParcial(true);
      return;
    }
    onMarcar(opcao === situacao ? null : opcao);
  }

  return (
    <div className="relative" ref={caixa}>
      <button
        type="button"
        disabled={!editavel || ocupado}
        onClick={() => {
          setTexto(parecer ?? "");
          setAberto(atual => !atual);
        }}
        title={titulo}
        aria-label={titulo}
        aria-haspopup={editavel ? "menu" : undefined}
        aria-expanded={editavel ? aberto : undefined}
        className={`inline-flex h-8 w-8 items-center justify-center rounded-md border-2 font-display text-[13px] font-bold transition
          focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] focus-visible:ring-offset-1 ${
            situacao
              ? COR_QUADRADO[situacao]
              : editavel
                ? "border-dashed border-slate-400 bg-white text-slate-500 dark:border-slate-500 dark:bg-slate-800 dark:text-slate-300"
                : "border-dashed border-slate-200 text-slate-300 dark:border-slate-700 dark:text-slate-600"
          } ${editavel ? "cursor-pointer hover:-translate-y-px hover:shadow-md" : "cursor-default"}`}
      >
        {letra}
      </button>
      {aberto && (
        <div
          role="menu"
          className="absolute right-0 top-10 z-20 w-80 rounded-xl border border-slate-200 bg-white p-1 shadow-xl
            dark:border-slate-700 dark:bg-slate-800"
        >
          <p className="px-2 pb-1 pt-1.5 text-[10px] font-bold uppercase tracking-wide text-slate-400">
            {letra} · {NOME_QUADRO[letra]}
          </p>
          {decidirCard ? (
            <>
              <p className="px-2 pb-1.5 text-[11px] leading-snug text-slate-500 dark:text-slate-400">
                A Diretoria decide o card inteiro: aprovando, vale o C de cada sacado.
              </p>
              {(["APROVADO", "REPROVADO"] as const).map(opcao => (
                <button
                  key={opcao}
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setAberto(false);
                    decidirCard(opcao);
                  }}
                  className="flex min-h-8 w-full cursor-pointer items-center gap-2 rounded-lg px-2 text-left text-xs font-medium text-slate-700
                    hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-700"
                >
                  <span className={`h-3.5 w-3.5 rounded ${fundoDecisao(opcao)}`} />
                  {opcao === "APROVADO" ? "Aprovar o card" : "Reprovar o card"}
                </button>
              ))}
            </>
          ) : parcial ? (
            <form
              className="flex items-center gap-1 p-1"
              onSubmit={event => {
                event.preventDefault();
                const numero = parseMoeda(valor);
                if (!numero) return;
                onMarcar("PARCIAL", numero);
                setParcial(false);
              }}
            >
              <div className="relative flex-1">
                <span className="pointer-events-none absolute left-2 top-1/2 -translate-y-1/2 text-[11px] text-slate-400">R$</span>
                <CampoNumero
                  autoFocus
                  valor={valor}
                  onMudar={setValor}
                  placeholder="aprovado"
                  aria-label="Valor aprovado"
                  className="w-full rounded-md border border-amber-300 bg-white py-1 pl-6 pr-1.5 text-right text-xs tabular-nums focus:outline-none
                    dark:bg-slate-900 dark:text-white"
                />
              </div>
              <button type="submit" className="h-7 cursor-pointer rounded-md bg-amber-500 px-2 text-[11px] font-semibold text-white">
                ok
              </button>
            </form>
          ) : (
            <>
              {opcoes.map(opcao => (
                <button
                  key={opcao}
                  type="button"
                  role="menuitemradio"
                  aria-checked={situacao === opcao}
                  onClick={() => escolher(opcao)}
                  className="flex min-h-8 w-full cursor-pointer items-center gap-2 rounded-lg px-2 text-left text-xs font-medium text-slate-700
                    hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-700"
                >
                  <span className={`h-3.5 w-3.5 rounded ${fundoDecisao(opcao)}`} />
                  {ROTULO_OPCAO[opcao]}
                  {situacao === opcao && <Icon name="check" size={12} className="ml-auto text-slate-400" />}
                </button>
              ))}
              {situacao && (
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => escolher(null)}
                  className="flex min-h-8 w-full cursor-pointer items-center gap-2 rounded-lg px-2 text-left text-xs text-slate-500 hover:bg-slate-50
                    dark:hover:bg-slate-700"
                >
                  <Icon name="close" size={12} /> Limpar
                </button>
              )}
            </>
          )}
          <div className="mt-1 border-t border-slate-100 p-2 dark:border-slate-700">
            <label htmlFor={`parecer-${letra}`} className="mb-1 block text-[10px] font-bold uppercase tracking-wide text-slate-400">
              Parecer {letra}
            </label>
            <MentionTextarea
              id={`parecer-${letra}`}
              value={texto}
              onChange={setTexto}
              rows={3}
              placeholder="Por que esta decisão? Use @ para marcar."
            />
            <div className="mt-1.5 flex justify-end">
              <button
                type="button"
                disabled={salvandoParecer || texto.trim() === (parecer ?? "").trim()}
                onClick={() => onParecer(texto, () => setAberto(false))}
                className="h-7 cursor-pointer rounded-md bg-[#612035] px-3 text-[11px] font-semibold text-white disabled:cursor-not-allowed
                  disabled:opacity-50"
              >
                {salvandoParecer ? "salvando…" : "Salvar parecer"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/** Pareceres escritos nos quadrados, um por linha, com a letra na cor da decisão daquele quadrado. */
function PareceresSacado({ sacado }: { sacado: Sacado }) {
  const linhas: { letra: Quadro; situacao: OpiniaoOperador | null; texto: string | null }[] = [
    { letra: "O", situacao: sacado.opiniaoOrigem, texto: sacado.parecerOrigem },
    { letra: "C", situacao: sacado.opiniaoComite, texto: sacado.parecerComite },
    { letra: "D", situacao: sacado.decisaoDiretoria, texto: sacado.parecerDiretoria },
  ];
  const escritos = linhas.filter(linha => linha.texto);
  if (escritos.length === 0) return null;
  return (
    <ul className="mt-1.5 space-y-1">
      {escritos.map(linha => (
        <li key={linha.letra} className="flex gap-2 text-[12.5px] leading-snug text-slate-700 dark:text-slate-300">
          <span
            title={`Parecer ${linha.letra} · ${NOME_QUADRO[linha.letra]}`}
            className={`mt-px inline-flex h-4 w-4 shrink-0 items-center justify-center rounded border text-[9px] font-bold ${
              linha.situacao ? COR_QUADRADO[linha.situacao] : "border-slate-300 text-slate-500 dark:border-slate-600"
            }`}
          >
            {linha.letra}
          </span>
          <div className="min-w-0 flex-1">
            <TextoRico texto={linha.texto} />
          </div>
        </li>
      ))}
    </ul>
  );
}

/**
 * Os quadrados do sacado: O (operador), C (decisão do Comitê) e D (Diretoria). O C se marca por
 * sacado; o D é preenchido quando a Diretoria aprova (repete o C) ou reprova o card — com o card na
 * Diretoria, clicar no D leva a essa decisão.
 */
function QuadradosSacado({
  cardId,
  sacado,
  podeOperador,
  podeComite,
  onDecidirCard,
}: {
  cardId: string;
  sacado: Sacado;
  podeOperador: boolean;
  podeComite: boolean;
  onDecidirCard?: (para: "APROVADO" | "REPROVADO") => void;
}) {
  const decidir = useDecidirSacado();
  const opinar = useOpinarSacado();
  const escrever = useParecerSacado();
  const salvar = (quadro: QuadroOpiniao) => (texto: string, aoSalvar: () => void) =>
    escrever.mutate({ id: cardId, documento: sacado.documento, quadro, texto }, { onSuccess: aoSalvar });
  // O C só oferece as três decisões: Amostragem nunca chega aqui.
  const marcar = (situacao: OpiniaoOperador | null, valorAprovado?: number) =>
    decidir.mutate({
      id: cardId,
      documento: sacado.documento,
      quadro: "COMITE",
      situacao: situacao as Resultado | null,
      valorAprovado: situacao === "PARCIAL" ? valorAprovado : null,
    });
  return (
    <div className="flex items-center gap-1.5" role="group" aria-label={`Decisões de ${sacado.nome ?? sacado.documento}`}>
      {/* O: parecer dos operadores. Opinião, não entra no resultado (C e D decidem). */}
      <Quadrado
        letra="O"
        situacao={sacado.opiniaoOrigem}
        porNome={sacado.opiniaoOrigemPorNome}
        em={sacado.opiniaoOrigemEm}
        editavel={podeOperador}
        opcoes={OPINIOES_OPERADOR}
        ocupado={opinar.isPending}
        onMarcar={situacao => opinar.mutate({ id: cardId, documento: sacado.documento, quadro: "ORIGEM", situacao })}
        parecer={sacado.parecerOrigem}
        salvandoParecer={escrever.isPending}
        onParecer={salvar("ORIGEM")}
      />
      <Quadrado
        letra="C"
        situacao={sacado.opiniaoComite}
        porNome={sacado.opiniaoComitePorNome}
        em={sacado.opiniaoComiteEm}
        editavel={podeComite}
        pedirValor
        valorAtual={sacado.valorAprovadoComite}
        ocupado={decidir.isPending}
        onMarcar={marcar}
        parecer={sacado.parecerComite}
        salvandoParecer={escrever.isPending}
        onParecer={salvar("COMITE")}
      />
      <Quadrado
        letra="D"
        situacao={sacado.decisaoDiretoria}
        porNome={sacado.decisaoDiretoriaPorNome}
        em={sacado.decisaoDiretoriaEm}
        editavel={Boolean(onDecidirCard)}
        valorAtual={sacado.valorAprovadoDiretoria}
        ocupado={false}
        onMarcar={() => undefined}
        decidirCard={onDecidirCard}
        dica="preenchido quando a Diretoria aprova ou reprova o card"
        parecer={sacado.parecerDiretoria}
        salvandoParecer={escrever.isPending}
        onParecer={salvar("DIRETORIA")}
      />
    </div>
  );
}

export function SacadosLista({
  cardId,
  sacados,
  valorOperacao,
  podeOperador,
  podeComite,
  onDecidirCard,
  quadro,
}: {
  cardId: string;
  sacados: Sacado[];
  valorOperacao: number | null;
  /** Analista antes da decisão (Origem, Comitê ou Pendência): marca o O. */
  podeOperador: boolean;
  /** Analista com o card no Comitê ou em Pendência: marca o C. */
  podeComite: boolean;
  /** Perfil Diretoria com o card na Diretoria: o D abre aprovar ou reprovar o card. */
  onDecidirCard?: (para: "APROVADO" | "REPROVADO") => void;
  /** Quem decide agora, para a conta de decididos; nulo depois da decisão (vale a situação). */
  quadro: "COMITE" | "DIRETORIA" | null;
}) {
  if (sacados.length === 0) {
    return <p className="text-sm text-slate-400">Nenhum sacado informado.</p>;
  }
  const comValor = sacados.filter(sacado => sacado.valor != null);
  const soma = comValor.reduce((total, sacado) => total + (sacado.valor ?? 0), 0);
  const divergente = valorOperacao != null && comValor.length > 0 && Math.abs(soma - valorOperacao) >= 0.01;
  const decisao = (sacado: Sacado) =>
    quadro === "COMITE"
      ? { resultado: sacado.opiniaoComite, parcial: sacado.valorAprovadoComite }
      : quadro === "DIRETORIA"
        ? { resultado: sacado.decisaoDiretoria, parcial: sacado.valorAprovadoDiretoria }
        : { resultado: sacado.situacao, parcial: sacado.valorAprovado };
  const decididos = sacados.filter(sacado => decisao(sacado).resultado);
  // Aprovados inteiros mais o valor aprovado dos parciais, pela decisão que vale agora: a mesma
  // conta do valor aprovado do card, que só existe depois da decisão.
  const aprovado = decididos.reduce((total, sacado) => {
    const { resultado, parcial } = decisao(sacado);
    return total + (resultado === "APROVADO" ? (sacado.valor ?? 0) : resultado === "PARCIAL" ? (parcial ?? 0) : 0);
  }, 0);

  return (
    <div className="rounded-xl border border-slate-200 dark:border-slate-700">
      <ul className="divide-y divide-slate-100 dark:divide-slate-800">
        {sacados.map(sacado => (
          <li key={sacado.documento} className="flex flex-col gap-2 px-3 py-2.5 sm:flex-row sm:items-center sm:gap-3">
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-slate-800 dark:text-slate-100">
                {sacado.nome ?? <span className="font-normal italic text-slate-400">sem nome na base</span>}
              </p>
              <p className="text-[11px] text-slate-500">
                <span className="font-mono">{formatDocumento(sacado.documento)}</span>
                {sacado.praca && <span className="text-slate-400"> · {sacado.praca}</span>}
              </p>
              <CarteiraLinha carteira={sacado.carteira} />
              {sacado.pracaPagamento && (
                <p
                  title="Praça de Pagamento: títulos decididos como pagos pelo cedente em que este sacado aparece"
                  className="mt-1 inline-flex items-center gap-1 rounded-md bg-rose-100 px-1.5 py-0.5 text-[10.5px] font-semibold text-rose-800
                    dark:bg-rose-900/40 dark:text-rose-200"
                >
                  <Icon name="alt_route" size={11} />
                  praça de pagamento · {sacado.pracaPagamento.titulos} título{sacado.pracaPagamento.titulos === 1 ? "" : "s"} ·{" "}
                  {formatMoedaCurta(sacado.pracaPagamento.valor)}
                </p>
              )}
              <HistoricoSacado historico={sacado.historico} />
              <PareceresSacado sacado={sacado} />
            </div>
            <div className="flex shrink-0 items-center gap-2 sm:justify-end">
              {sacado.valor != null && (
                <span className="text-sm tabular-nums text-slate-700 dark:text-slate-200">{formatMoeda(sacado.valor)}</span>
              )}
              <QuadradosSacado
                cardId={cardId}
                sacado={sacado}
                podeOperador={podeOperador}
                podeComite={podeComite}
                onDecidirCard={onDecidirCard}
              />
              {(sacado.documento.length === 14 || sacado.documento.length === 11) && (
                <a
                  // CPF vai para a ficha de Pessoas Físicas; sem ficha, a página oferece a consulta Serasa PF (com custo).
                  href={sacado.documento.length === 11 ? `/individuals/${sacado.documento}` : `/clients/${sacado.documento}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  title={
                    sacado.documento.length === 11
                      ? sacado.cadastrado
                        ? "Abrir a ficha da pessoa física em nova aba"
                        : `Pessoa física sem ficha: abre a página para consultar o Serasa PF (${CUSTO_CONSULTA_PF})`
                      : sacado.cadastrado
                        ? "Abrir a página da empresa em nova aba"
                        : "Empresa não cadastrada: abre a página para cadastrar"
                  }
                  aria-label={`Abrir ${sacado.nome ?? formatDocumento(sacado.documento)} em nova aba`}
                  className={`inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg transition-colors focus:outline-none
                    focus-visible:ring-2 focus-visible:ring-[#612035] ${
                      sacado.cadastrado
                        ? "text-slate-500 hover:bg-slate-100 hover:text-[#612035] dark:hover:bg-slate-800"
                        : "border border-dashed border-amber-400 text-amber-600 hover:bg-amber-50 dark:hover:bg-amber-900/30"
                    }`}
                >
                  <Icon name="open_in_new" size={14} />
                </a>
              )}
            </div>
            {sacado.situacao === "PARCIAL" && sacado.valorAprovado != null && (
              <span className="text-[10.5px] text-amber-700 sm:hidden dark:text-amber-300">aprovado {formatMoeda(sacado.valorAprovado)}</span>
            )}
          </li>
        ))}
      </ul>
      <div className={`flex flex-wrap items-center gap-x-3 gap-y-1 rounded-b-xl border-t border-slate-100 bg-slate-50 px-3 py-2 text-[11px] dark:border-slate-800 dark:bg-slate-800/50
        ${divergente ? "text-amber-700 dark:text-amber-300" : "text-slate-500"}`}>
        {comValor.length > 0 && (
          <span className="flex items-center gap-1">
            {divergente && <Icon name="warning" size={12} />}
            Soma dos sacados <strong className="tabular-nums">{formatMoeda(soma)}</strong>
            {divergente && <span>≠ {formatMoeda(valorOperacao)}</span>}
          </span>
        )}
        {decididos.length > 0 && (
          <span className="flex items-center gap-1 text-slate-500" title="Aprovados inteiros mais o valor aprovado dos parciais">
            Aprovado{" "}
            <strong className={`tabular-nums ${aprovado > 0 ? "text-emerald-700 dark:text-emerald-300" : ""}`}>{formatMoeda(aprovado)}</strong>
          </span>
        )}
        <span className="ml-auto text-slate-500">
          {decididos.length} de {sacados.length} decidido{sacados.length === 1 ? "" : "s"}
        </span>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ Comitê

const POSICOES: PosicaoParecer[] = ["FAVORAVEL", "COM_RESSALVAS", "DESFAVORAVEL"];

function FormParecer({ card, atual, onCancelar }: { card: LiberacaoCard; atual?: Parecer; onCancelar?: () => void }) {
  const registrar = useRegistrarParecer();
  const [posicao, setPosicao] = useState<PosicaoParecer | null>(atual?.posicao ?? null);
  const [texto, setTexto] = useState(atual?.texto ?? "");

  return (
    <div className="mt-3 rounded-xl border border-[#612035]/25 bg-[#612035]/[0.03] p-3 dark:border-[#e8a3b6]/25 dark:bg-[#612035]/10">
      <p className="mb-2 text-xs font-semibold text-[#612035] dark:text-[#e8a3b6]">
        {atual?.posicao ? "Rever meu parecer" : "Seu parecer é esperado"}
      </p>
      <div role="radiogroup" aria-label="Posição" className="grid grid-cols-3 gap-1.5">
        {POSICOES.map(opcao => (
          <button
            key={opcao}
            type="button"
            role="radio"
            aria-checked={posicao === opcao}
            onClick={() => setPosicao(opcao)}
            className={`flex min-h-10 cursor-pointer items-center justify-center gap-1 rounded-lg border px-2 text-xs font-medium transition-colors
              focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] ${
                posicao === opcao
                  ? `${COR_POSICAO[opcao]} border-transparent`
                  : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
              }`}
          >
            <Icon name={ICONE_POSICAO[opcao]} size={13} />
            {ROTULO_POSICAO[opcao]}
          </button>
        ))}
      </div>
      <div className="mt-2">
        <MentionTextarea
          value={texto}
          onChange={setTexto}
          rows={3}
          placeholder="Fundamente: o que pesou a favor, o que preocupa. Use @ para marcar."
          aria-label="Texto do parecer"
        />
      </div>
      <div className="mt-2 flex justify-end gap-2">
        {onCancelar && (
          <button type="button" onClick={onCancelar} className={BOTAO_SECUNDARIO}>
            Cancelar
          </button>
        )}
        <button
          type="button"
          disabled={!posicao || registrar.isPending}
          onClick={() =>
            posicao && registrar.mutate({ id: card.id, posicao, texto }, { onSuccess: () => onCancelar?.() })
          }
          className={BOTAO_PRIMARIO}
        >
          {registrar.isPending && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />}
          Registrar parecer
        </button>
      </div>
    </div>
  );
}

/** `aberto`: o card está no Comitê e o parecer ainda pode chegar. Fora dele, quem não deu fica "sem parecer". */
function LinhaParecer({ parecer, aberto = false }: { parecer: Parecer; aberto?: boolean }) {
  return (
    <li className="flex gap-3 py-3 first:pt-0">
      <Avatar nome={parecer.usuarioNome} iniciais={parecer.iniciais} tamanho="md" aguardando={!parecer.posicao} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="text-sm font-semibold text-slate-800 dark:text-slate-100">{parecer.usuarioNome}</span>
          {parecer.posicao ? (
            <span className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-medium ${COR_POSICAO[parecer.posicao]}`}>
              <Icon name={ICONE_POSICAO[parecer.posicao]} size={11} />
              {ROTULO_POSICAO[parecer.posicao]}
            </span>
          ) : (
            <span className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-500 dark:bg-slate-800">{aberto ? "aguardando" : "sem parecer"}</span>
          )}
          {parecer.registradoEm && (
            <span className="text-[11px] text-slate-400" title={formatDataHora(parecer.registradoEm)}>
              {tempoRelativo(parecer.registradoEm)}
            </span>
          )}
        </div>
        {parecer.texto && (
          <p className="mt-1 text-sm leading-relaxed text-slate-700 dark:text-slate-300">
            <TextoRico texto={parecer.texto} />
          </p>
        )}
      </div>
    </li>
  );
}

export function PareceresComite({ card, pareceres, euId }: { card: LiberacaoCard; pareceres: Parecer[]; euId?: string }) {
  const [revendo, setRevendo] = useState(false);
  const [verAnteriores, setVerAnteriores] = useState(false);
  const vigentes = pareceres.filter(parecer => parecer.rodada === card.rodada);
  const anteriores = pareceres.filter(parecer => parecer.rodada !== card.rodada && parecer.posicao);
  const meu = vigentes.find(parecer => parecer.usuarioId === euId);
  const noComite = card.etapa === "COMITE";

  if (vigentes.length === 0 && anteriores.length === 0) {
    return <p className="text-sm text-slate-400">O Comitê é convocado quando o card entra na coluna Comitê.</p>;
  }

  return (
    <div>
      {vigentes.length > 0 && (
        <>
          {card.rodada > 1 && <p className="mb-2 text-[11px] font-medium text-slate-400">Rodada {card.rodada}</p>}
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {vigentes.map(parecer => (
              <LinhaParecer key={parecer.id} parecer={parecer} aberto={noComite} />
            ))}
          </ul>
        </>
      )}

      {noComite && meu && !meu.posicao && <FormParecer card={card} />}
      {noComite && meu?.posicao && !revendo && (
        <button
          type="button"
          onClick={() => setRevendo(true)}
          className="mt-1 inline-flex cursor-pointer items-center gap-1 text-xs font-medium text-[#612035] hover:underline dark:text-[#e8a3b6]"
        >
          <Icon name="edit" size={12} /> rever meu parecer
        </button>
      )}
      {noComite && meu?.posicao && revendo && <FormParecer card={card} atual={meu} onCancelar={() => setRevendo(false)} />}

      {anteriores.length > 0 && (
        <div className="mt-3">
          <button
            type="button"
            onClick={() => setVerAnteriores(atual => !atual)}
            aria-expanded={verAnteriores}
            className="inline-flex cursor-pointer items-center gap-1 text-xs text-slate-500 hover:text-slate-800 dark:hover:text-slate-200"
          >
            <Icon name={verAnteriores ? "expand_less" : "expand_more"} size={14} />
            {anteriores.length} parecer(es) de rodadas anteriores
          </button>
          {verAnteriores && (
            <div className="mt-2 divide-y divide-slate-100 rounded-xl bg-slate-50 px-3 pt-3 dark:divide-slate-800 dark:bg-slate-800/40">
              {anteriores.map(parecer => (
                <div key={parecer.id} className="pt-2 first:pt-0">
                  <p className="text-[10px] font-medium uppercase tracking-wide text-slate-400">Rodada {parecer.rodada}</p>
                  <ul>
                    <LinhaParecer parecer={parecer} />
                  </ul>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ pendências

function ItemPendencia({ cardId, pendencia }: { cardId: string; pendencia: Pendencia }) {
  const responder = useResponderPendencia();
  const [resposta, setResposta] = useState("");
  const aberta = !pendencia.respondidaEm;

  return (
    <li
      className={`rounded-xl border p-3 ${
        aberta
          ? "border-[#D1732C]/40 bg-[#D1732C]/[0.05] dark:border-[#D1732C]/40 dark:bg-[#D1732C]/10"
          : "border-slate-200 dark:border-slate-700"
      }`}
    >
      <div className="flex flex-wrap items-center gap-x-1.5 text-xs text-slate-500 dark:text-slate-400">
        <strong className="font-semibold text-slate-700 dark:text-slate-200">{pendencia.abertaPorNome}</strong>
        <Icon name="arrow_forward" size={12} />
        <strong className="font-semibold text-slate-700 dark:text-slate-200">{pendencia.destinatarioNome}</strong>
        <span title={formatDataHora(pendencia.abertaEm)}>· {tempoRelativo(pendencia.abertaEm)}</span>
        <span
          className={`ml-auto rounded-md px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${
            aberta ? "bg-[#D1732C] text-white" : "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200"
          }`}
        >
          {aberta ? "aberta" : "respondida"}
        </span>
      </div>
      <p className="mt-1.5 text-sm text-slate-800 dark:text-slate-100">
        <TextoRico texto={pendencia.texto} />
      </p>

      {pendencia.resposta && (
        <div className="mt-2 rounded-lg border-l-2 border-emerald-500 bg-white px-3 py-2 dark:bg-slate-900">
          <p className="text-[11px] text-slate-500">
            {pendencia.respondidaPorNome} respondeu {tempoRelativo(pendencia.respondidaEm)}
          </p>
          <p className="mt-0.5 text-sm text-slate-700 dark:text-slate-200">
            <TextoRico texto={pendencia.resposta} />
          </p>
        </div>
      )}

      {pendencia.podeResponder && (
        <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-end">
          <div className="min-w-0 flex-1">
            <MentionTextarea value={resposta} onChange={setResposta} rows={2} placeholder="Responder a pendência" aria-label="Resposta da pendência" />
          </div>
          <button
            type="button"
            disabled={!resposta.trim() || responder.isPending}
            onClick={() =>
              responder.mutate({ id: cardId, pendenciaId: pendencia.id, resposta }, { onSuccess: () => setResposta("") })
            }
            className={`${BOTAO_PRIMARIO} shrink-0`}
          >
            <Icon name="reply" size={14} /> Responder
          </button>
        </div>
      )}
    </li>
  );
}

export function PendenciasLista({ cardId, pendencias }: { cardId: string; pendencias: Pendencia[] }) {
  if (pendencias.length === 0) return <p className="text-sm text-slate-400">Nenhuma pendência.</p>;
  const ordenadas = [...pendencias].sort((a, b) => Number(Boolean(a.respondidaEm)) - Number(Boolean(b.respondidaEm)));
  return (
    <ul className="space-y-2">
      {ordenadas.map(pendencia => (
        <ItemPendencia key={pendencia.id} cardId={cardId} pendencia={pendencia} />
      ))}
    </ul>
  );
}
