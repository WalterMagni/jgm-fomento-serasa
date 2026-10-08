"use client";

import { useState, type FormEvent } from "react";
import Icon from "@/components/ui/Icon";
import CampoData from "@/components/ui/CampoData";
import MentionTextarea from "@/components/ui/mencao/MentionTextarea";
import { normalizar } from "@/components/ui/mencao/mencoes";
import { useCadastrarEmpresa, useEmpresasConhecidas, useTiposOperacao } from "@/hooks/useLiberacao";
import {
  ROTULO_POSICAO,
  type CarteiraSacado,
  type DadosCard,
  type EmpresaConhecida,
  type PosicaoParecer,
  type PropostaAr,
  type PropostaImportada,
  type Sacado,
  type TipoOperacao,
} from "@/types/liberacao";
import CedentePicker, { type CedenteEscolhido } from "./CedentePicker";
import { CarteiraLinha, ImportarProposta } from "./PropostaAr";
import { HistoricoSacado } from "./DetalheSecoes";
import { BOTAO_PRIMARIO, BOTAO_SECUNDARIO, CAMPO, ROTULO } from "./Dialogo";
import {
  COR_POSICAO,
  ICONE_POSICAO,
  confirmar,
  formatDocumento,
  formatMoeda,
  isoParaCampoData,
  mascararDocumento,
  moedaParaCampo,
  parseMoeda,
} from "./formatters";

/** `carteira`: linha do sacado na AR; cai quando o documento é editado à mão. */
type LinhaSacado = { chave: number; documento: string; nome: string | null; valor: string; carteira: CarteiraSacado | null };

export type ValoresIniciais = {
  cedente: CedenteEscolhido | null;
  tipoOperacao: TipoOperacao | null;
  valor: number | null;
  prazo: string | null;
  parecerOrigem: string | null;
  posicaoOrigem: PosicaoParecer | null;
  sacados: Sacado[];
  proposta?: PropostaAr | null;
};

type Props = {
  inicial?: ValoresIniciais;
  enviando: boolean;
  rotuloEnviar: string;
  /** Card finalizado: o resultado sai dos sacados, então a lista só muda depois de reabrir. */
  sacadosTravados?: boolean;
  onEnviar: (dados: DadosCard) => void;
  onCancelar: () => void;
};

let proximaChave = 1;
const novaLinha = (documento = "", nome: string | null = null, valor = "", carteira: CarteiraSacado | null = null): LinhaSacado => ({
  chave: proximaChave++,
  documento,
  nome,
  valor,
  carteira,
});

/**
 * O que se sabe do sacado da linha: nome e praça da base (CNPJ Já ou Serasa), ou o aviso de que
 * a empresa não está no portal, com o atalho para cadastrar — o mesmo tratamento do cedente.
 */
function InfoSacado({
  documento,
  nomeInformado,
  empresa,
  cadastrando,
  onCadastrar,
}: {
  documento: string;
  nomeInformado: string | null;
  empresa: EmpresaConhecida | undefined;
  cadastrando: boolean;
  onCadastrar: (cnpj: string) => void;
}) {
  const digitos = documento.replace(/\D/g, "");
  if (digitos.length !== 14) {
    return nomeInformado ? <p className="truncate px-2 text-[11px] text-slate-500">{nomeInformado}</p> : null;
  }
  if (!empresa) return <p className="px-2 text-[11px] text-slate-400">consultando…</p>;
  if (!empresa.cadastrada) {
    return (
      <p className="flex flex-wrap items-center gap-x-1.5 px-2 text-[11px] text-amber-700 dark:text-amber-300">
        {nomeInformado && <span className="max-w-full truncate text-slate-500">{nomeInformado} ·</span>}
        não cadastrada no portal
        <button
          type="button"
          disabled={cadastrando}
          onClick={() => onCadastrar(digitos)}
          className="cursor-pointer font-medium text-[#2956E0] hover:underline disabled:opacity-60"
        >
          {cadastrando ? "cadastrando…" : "cadastrar via CNPJ Já"}
        </button>
      </p>
    );
  }
  const nome = nomeInformado ?? empresa.nome;
  // Reprovação ou parcial em card anterior: o time pediu para ver na hora de incluir o sacado.
  const alertas = empresa.historico.filter(decisao => decisao.situacao !== "APROVADO");
  return (
    <div className="px-2">
      <p className="truncate text-[11px] text-slate-500">
        {nome ?? <span className="italic">consultada, sem razão social na base</span>}
        {empresa.praca && <span className="text-slate-400"> · {empresa.praca}</span>}
      </p>
      {alertas.length > 0 && <HistoricoSacado historico={alertas} compacto />}
    </div>
  );
}

/** Todos os CPFs e CNPJs de um texto colado, na ordem em que aparecem. */
function documentosColados(texto: string) {
  return (texto.match(/\d[\d./-]{9,17}\d/g) ?? [])
    .map(trecho => trecho.replace(/\D/g, ""))
    .filter(digitos => digitos.length === 11 || digitos.length === 14);
}

/**
 * Campos do card, iguais na criação e na edição.
 *
 * <p>Sacados aceitam colar uma lista: a auxiliar costuma ter os CNPJs num e-mail ou planilha, e
 * digitar um a um é onde nasce o erro de dígito.</p>
 */
export default function CardForm({ inicial, enviando, rotuloEnviar, sacadosTravados = false, onEnviar, onCancelar }: Props) {
  const [cedente, setCedente] = useState<CedenteEscolhido | null>(inicial?.cedente ?? null);
  const [tipo, setTipo] = useState<TipoOperacao | null>(inicial?.tipoOperacao ?? null);
  const { data: tiposSalvos = [] } = useTiposOperacao();
  // Tipo novo digitado agora ainda não está na lista do servidor; aparece junto até salvar.
  const tipos = tipo && !tiposSalvos.some(item => normalizar(item) === normalizar(tipo)) ? [...tiposSalvos, tipo] : tiposSalvos;
  const [criandoTipo, setCriandoTipo] = useState(false);
  const [tipoNovo, setTipoNovo] = useState("");

  /** Mesma regra do servidor: casa com um tipo existente sem olhar maiúscula nem acento. */
  function confirmarTipoNovo() {
    const limpo = tipoNovo.trim().replace(/\s+/g, " ");
    if (limpo) {
      const chave = normalizar(limpo);
      const existente = tiposSalvos.find(item => normalizar(item) === chave);
      setTipo(existente ?? limpo.charAt(0).toUpperCase() + limpo.slice(1));
    }
    setTipoNovo("");
    setCriandoTipo(false);
  }
  const [valor, setValor] = useState(moedaParaCampo(inicial?.valor));
  const [prazo, setPrazo] = useState(isoParaCampoData(inicial?.prazo));
  const [parecer, setParecer] = useState(inicial?.parecerOrigem ?? "");
  const [posicao, setPosicao] = useState<PosicaoParecer | null>(inicial?.posicaoOrigem ?? null);
  const cadastrar = useCadastrarEmpresa();
  const [linhas, setLinhas] = useState<LinhaSacado[]>(() =>
    inicial?.sacados.length
      ? inicial.sacados.map(sacado => novaLinha(formatDocumento(sacado.documento), sacado.nome, moedaParaCampo(sacado.valor), sacado.carteira))
      : [novaLinha()],
  );
  const [proposta, setProposta] = useState<PropostaAr | null>(inicial?.proposta ?? null);
  /** PDF lido nesta abertura do formulário: a AR vai junto ao salvar e o PDF vira anexo. */
  const [importacao, setImportacao] = useState<{ arquivo: File; avisos: string[] } | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const { data: conhecidas = [] } = useEmpresasConhecidas(linhas.map(linha => linha.documento.replace(/\D/g, "")));
  const conhecida = (documento: string) => conhecidas.find(empresa => empresa.documento === documento.replace(/\D/g, ""));

  const valorNumero = parseMoeda(valor);
  const valoresSacados = linhas.map(linha => parseMoeda(linha.valor)).filter((v): v is number => v != null);
  const somaSacados = valoresSacados.reduce((total, v) => total + v, 0);
  const divergente = valorNumero != null && valoresSacados.length > 0 && Math.abs(somaSacados - valorNumero) >= 0.01;

  function atualizarLinha(chave: number, campo: "documento" | "valor", texto: string) {
    setLinhas(atuais =>
      atuais.map(linha => (linha.chave === chave ? { ...linha, [campo]: texto, ...(campo === "documento" ? { nome: null, carteira: null } : {}) } : linha)),
    );
  }

  /** Preenche cedente, valor e sacados com a AR. Sacados digitados antes são trocados, com confirmação. */
  function aplicarImportacao(resultado: PropostaImportada, arquivo: File) {
    const digitados = linhas.filter(linha => linha.documento.replace(/\D/g, ""));
    if (digitados.length > 0 && !importacao && !confirmar("Trocar os sacados já digitados pelos da proposta?")) return;
    if (resultado.cedenteCnpj) {
      setCedente({ cnpj: resultado.cedenteCnpj, nome: resultado.cedenteNome ?? resultado.clienteNome ?? "", cadastrado: resultado.cedenteCadastrado });
    }
    if (resultado.proposta.faceLiberados != null) setValor(moedaParaCampo(resultado.proposta.faceLiberados));
    setLinhas(
      resultado.sacados.length > 0
        ? resultado.sacados.map(sacado => novaLinha(formatDocumento(sacado.documento), sacado.nome, moedaParaCampo(sacado.valor), sacado.carteira))
        : [novaLinha()],
    );
    setProposta(resultado.proposta);
    setImportacao({ arquivo, avisos: resultado.avisos });
    setErro(null);
  }

  function colar(chave: number, texto: string) {
    const documentos = documentosColados(texto);
    if (documentos.length < 2) return false;
    setLinhas(atuais => {
      const semVazias = atuais.filter(linha => linha.chave !== chave && linha.documento.trim());
      const existentes = new Set(semVazias.map(linha => linha.documento.replace(/\D/g, "")));
      const novas = documentos.filter(doc => !existentes.has(doc)).map(doc => novaLinha(formatDocumento(doc)));
      return [...semVazias, ...novas];
    });
    return true;
  }

  function enviar(event: FormEvent) {
    event.preventDefault();
    if (!cedente) {
      setErro("Escolha o cedente.");
      return;
    }
    if (!cedente.cadastrado && !cedente.nome.trim()) {
      setErro("Empresa não cadastrada: informe a razão social.");
      return;
    }
    const invalida = linhas.find(linha => {
      const d = linha.documento.replace(/\D/g, "");
      return d.length > 0 && d.length !== 11 && d.length !== 14;
    });
    if (invalida) {
      setErro(`Documento de sacado incompleto: ${invalida.documento}`);
      return;
    }
    setErro(null);
    onEnviar({
      cedenteCnpj: cedente.cnpj,
      cedenteNome: cedente.cadastrado ? null : cedente.nome.trim(),
      tipoOperacao: tipo,
      valor: valorNumero,
      prazo: prazo || null,
      parecerOrigem: parecer.trim() || null,
      posicaoOrigem: posicao,
      sacados: linhas
        .filter(linha => linha.documento.replace(/\D/g, ""))
        .map(linha => ({ documento: linha.documento.replace(/\D/g, ""), nome: linha.nome, valor: parseMoeda(linha.valor), carteira: linha.carteira })),
      // Na edição, sem reimportar, a AR do card fica como está.
      proposta: importacao ? proposta : undefined,
      arquivoProposta: importacao?.arquivo ?? null,
    });
  }

  return (
    <form onSubmit={enviar} className="flex min-h-0 flex-1 flex-col">
      <div className="space-y-5 px-5 py-5">
        {!sacadosTravados && (
          <ImportarProposta
            proposta={proposta}
            arquivo={importacao?.arquivo.name ?? null}
            avisos={importacao?.avisos ?? []}
            onLida={aplicarImportacao}
            onRemover={() => {
              setProposta(inicial?.proposta ?? null);
              setImportacao(null);
            }}
          />
        )}

        <CedentePicker valor={cedente} onMudar={setCedente} />

        <fieldset>
          <legend className={ROTULO}>Tipo de operação</legend>
          <div className="flex flex-wrap gap-1.5">
            {tipos.map(opcao => (
              <button
                key={opcao}
                type="button"
                aria-pressed={tipo === opcao}
                onClick={() => setTipo(atual => (atual === opcao ? null : opcao))}
                className={`min-h-9 cursor-pointer rounded-lg border px-3 text-xs font-medium transition-colors focus:outline-none
                  focus-visible:ring-2 focus-visible:ring-[#612035] ${
                    tipo === opcao
                      ? "border-[#612035] bg-[#612035] text-white"
                      : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
                  }`}
              >
                {opcao}
              </button>
            ))}
            {criandoTipo ? (
              <span className="inline-flex items-center gap-1">
                <input
                  autoFocus
                  value={tipoNovo}
                  maxLength={40}
                  onChange={event => setTipoNovo(event.target.value)}
                  onKeyDown={event => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      confirmarTipoNovo();
                    } else if (event.key === "Escape") {
                      event.preventDefault();
                      event.stopPropagation();
                      setTipoNovo("");
                      setCriandoTipo(false);
                    }
                  }}
                  onBlur={confirmarTipoNovo}
                  placeholder="Nome do tipo"
                  aria-label="Novo tipo de operação"
                  className="min-h-9 w-44 rounded-lg border border-[#612035] bg-white px-3 text-xs text-slate-800 focus:outline-none focus:ring-2
                    focus:ring-[#612035]/20 dark:bg-slate-800 dark:text-white"
                />
              </span>
            ) : (
              <button
                type="button"
                onClick={() => setCriandoTipo(true)}
                className="inline-flex min-h-9 cursor-pointer items-center gap-1 rounded-lg border border-dashed border-slate-300 px-3 text-xs
                  font-medium text-slate-500 transition-colors hover:border-[#612035] hover:text-[#612035] focus:outline-none
                  focus-visible:ring-2 focus-visible:ring-[#612035] dark:border-slate-600 dark:text-slate-400"
              >
                <Icon name="add" size={13} /> Novo tipo
              </button>
            )}
          </div>
          <p className="mt-1 text-[11px] text-slate-400">Opcional. Tipo novo fica disponível para todos.</p>
        </fieldset>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="card-valor" className={ROTULO}>
              Valor da operação <span className="font-normal text-slate-400">(opcional)</span>
            </label>
            <div className="relative">
              <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm text-slate-400">R$</span>
              <input
                id="card-valor"
                inputMode="decimal"
                value={valor}
                onChange={event => setValor(event.target.value)}
                onBlur={() => setValor(moedaParaCampo(parseMoeda(valor)))}
                placeholder="0,00"
                className={`${CAMPO} pl-9 text-right tabular-nums`}
              />
            </div>
          </div>
          <div>
            <label htmlFor="card-prazo" className={ROTULO}>
              Prazo para decisão <span className="font-normal text-slate-400">(opcional)</span>
            </label>
            <CampoData id="card-prazo" value={prazo} onChange={setPrazo} comHora />
          </div>
        </div>

        <div>
          <div className="mb-1 flex items-end justify-between">
            <span className={ROTULO}>Sacados</span>
            <span className="text-[11px] text-slate-400">
              {sacadosTravados ? "card finalizado: reabra no Comitê para mudar" : "cole vários CNPJs de uma vez"}
            </span>
          </div>
          <fieldset
            disabled={sacadosTravados}
            className="overflow-hidden rounded-xl border border-slate-200 disabled:opacity-60 dark:border-slate-700"
          >
            {linhas.map((linha, indice) => (
              <div
                key={linha.chave}
                className="flex items-center gap-2 border-b border-slate-100 px-2 py-1.5 last:border-b-0 dark:border-slate-800"
              >
                <span className="w-5 shrink-0 text-center text-[11px] text-slate-400">{indice + 1}</span>
                <div className="min-w-0 flex-1">
                  <input
                    value={linha.documento}
                    onChange={event => atualizarLinha(linha.chave, "documento", mascararDocumento(event.target.value))}
                    onPaste={event => {
                      if (colar(linha.chave, event.clipboardData.getData("text"))) event.preventDefault();
                    }}
                    onKeyDown={event => {
                      if (event.key === "Enter") {
                        event.preventDefault();
                        setLinhas(atuais => [...atuais, novaLinha()]);
                      }
                    }}
                    placeholder="CNPJ ou CPF do sacado"
                    aria-label={`Documento do sacado ${indice + 1}`}
                    className="w-full rounded-md bg-transparent px-2 py-1.5 font-mono text-sm text-slate-800 placeholder:font-sans
                      placeholder:text-slate-400 focus:bg-slate-50 focus:outline-none dark:text-slate-100 dark:focus:bg-slate-800"
                  />
                  <InfoSacado
                    documento={linha.documento}
                    nomeInformado={linha.nome}
                    empresa={conhecida(linha.documento)}
                    cadastrando={cadastrar.isPending && cadastrar.variables?.cnpj === linha.documento.replace(/\D/g, "")}
                    onCadastrar={cnpj => cadastrar.mutate({ cnpj })}
                  />
                  {linha.carteira && (
                    <div className="px-2">
                      <CarteiraLinha carteira={linha.carteira} />
                    </div>
                  )}
                </div>
                <div className="relative w-32 shrink-0">
                  <span className="pointer-events-none absolute left-2 top-1/2 -translate-y-1/2 text-xs text-slate-400">R$</span>
                  <input
                    inputMode="decimal"
                    value={linha.valor}
                    onChange={event => atualizarLinha(linha.chave, "valor", event.target.value)}
                    onBlur={() => atualizarLinha(linha.chave, "valor", moedaParaCampo(parseMoeda(linha.valor)))}
                    placeholder="valor"
                    aria-label={`Valor do sacado ${indice + 1}`}
                    className="w-full rounded-md bg-transparent py-1.5 pl-7 pr-2 text-right text-sm tabular-nums text-slate-800
                      placeholder:text-slate-400 focus:bg-slate-50 focus:outline-none dark:text-slate-100 dark:focus:bg-slate-800"
                  />
                </div>
                <button
                  type="button"
                  onClick={() => setLinhas(atuais => (atuais.length > 1 ? atuais.filter(item => item.chave !== linha.chave) : [novaLinha()]))}
                  aria-label={`Remover sacado ${indice + 1}`}
                  className="inline-flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-md text-slate-400
                    hover:bg-rose-50 hover:text-rose-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 dark:hover:bg-rose-900/30"
                >
                  <Icon name="close" size={14} />
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() => setLinhas(atuais => [...atuais, novaLinha()])}
              className="flex min-h-10 w-full cursor-pointer items-center justify-center gap-1 bg-slate-50 text-xs font-medium text-slate-600
                hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#612035]
                dark:bg-slate-800/60 dark:text-slate-300 dark:hover:bg-slate-800"
            >
              <Icon name="add" size={14} /> adicionar sacado
            </button>
          </fieldset>
          {valoresSacados.length > 0 && (
            <p className={`mt-1.5 flex items-center gap-1 text-[11px] ${divergente ? "text-amber-700 dark:text-amber-300" : "text-slate-500"}`}>
              {divergente && <Icon name="warning" size={12} />}
              Soma dos sacados: <strong className="tabular-nums">{formatMoeda(somaSacados)}</strong>
              {divergente && <> · diferente do valor da operação ({formatMoeda(valorNumero)})</>}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="card-parecer" className={ROTULO}>
            Parecer da origem
          </label>
          <div role="radiogroup" aria-label="Posição da origem" className="mb-2 grid grid-cols-3 gap-1.5">
            {(Object.keys(ROTULO_POSICAO) as PosicaoParecer[]).map(opcao => (
              <button
                key={opcao}
                type="button"
                role="radio"
                aria-checked={posicao === opcao}
                onClick={() => setPosicao(atual => (atual === opcao ? null : opcao))}
                className={`flex min-h-9 cursor-pointer items-center justify-center gap-1 rounded-lg border px-2 text-xs font-medium transition-colors
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
          <MentionTextarea
            id="card-parecer"
            value={parecer}
            onChange={setParecer}
            rows={5}
            placeholder="Use @ para marcar pessoas ou empresas."
          />
        </div>

        {erro && (
          <p role="alert" className="flex items-center gap-1.5 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:bg-rose-900/30 dark:text-rose-200">
            <Icon name="error" size={16} />
            {erro}
          </p>
        )}
      </div>

      <div className="sticky bottom-0 mt-auto flex justify-end gap-2 border-t border-slate-100 bg-white/95 px-5 py-3 backdrop-blur
        dark:border-slate-800 dark:bg-slate-900/95">
        <button type="button" onClick={onCancelar} className={BOTAO_SECUNDARIO}>
          Cancelar
        </button>
        <button type="submit" disabled={enviando} className={BOTAO_PRIMARIO}>
          {enviando && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />}
          {rotuloEnviar}
        </button>
      </div>
    </form>
  );
}
