import { textoPlano } from "@/components/ui/mencao/mencoes";
import type { LiberacaoCard, Resultado } from "@/types/liberacao";

/**
 * Filtros e ordenação do quadro.
 *
 * <p>Vivem na URL: recarregar a página mantém, e o link filtrado pode ir para a colega
 * ("os vencidos da Mychelly"). É também o que evita o localStorage, que a regra
 * react-hooks/set-state-in-effect deste projeto não deixa ler na montagem.</p>
 */

export type Ordem = "prazo" | "atividade" | "recentes" | "antigos" | "valor" | "nome";
export type FiltroPrazo = "vencido" | "hoje" | "semana";
/** Quanto tempo de finalizados aparece. "ocultar" tira Aprovado e Reprovado do quadro. */
export type Finalizados = "30" | "90" | "365" | "ocultar";

export type Filtros = {
  busca: string;
  membros: string[];
  etiquetas: string[];
  tipos: string[];
  resultados: Resultado[];
  criador: string | null;
  de: string | null;
  ate: string | null;
  prazo: FiltroPrazo | null;
  meus: boolean;
  finalizados: Finalizados;
  ordem: Ordem;
};

export const ROTULO_ORDEM: Record<Ordem, string> = {
  prazo: "Prazo mais próximo",
  atividade: "Última atividade",
  recentes: "Mais recentes",
  antigos: "Mais antigos",
  valor: "Maior valor",
  nome: "Nome do cedente",
};

export const ROTULO_FINALIZADOS: Record<Finalizados, string> = {
  "30": "últimos 30 dias",
  "90": "últimos 90 dias",
  "365": "último ano",
  ocultar: "ocultos",
};

export const ROTULO_PRAZO: Record<FiltroPrazo, string> = {
  vencido: "Vencido",
  hoje: "Vence hoje",
  semana: "Próximos 7 dias",
};

const lista = (valor: string | null) => (valor ? valor.split(",").filter(Boolean) : []);

export function lerFiltros(params: URLSearchParams): Filtros {
  const ordem = params.get("ordem") as Ordem | null;
  const finalizados = params.get("fin") as Finalizados | null;
  const prazo = params.get("prazo") as FiltroPrazo | null;
  return {
    busca: params.get("q") ?? "",
    membros: lista(params.get("membro")),
    etiquetas: lista(params.get("etiqueta")),
    // Tipo é texto livre e pode ter vírgula; separa por barra vertical.
    tipos: (params.get("tipo") ?? "").split("|").filter(Boolean),
    resultados: lista(params.get("resultado")).filter((valor): valor is Resultado => ["APROVADO", "REPROVADO", "PARCIAL"].includes(valor)),
    criador: params.get("criador"),
    de: params.get("de"),
    ate: params.get("ate"),
    prazo: prazo && prazo in ROTULO_PRAZO ? prazo : null,
    meus: params.get("meus") === "1",
    finalizados: finalizados && finalizados in ROTULO_FINALIZADOS ? finalizados : "30",
    ordem: ordem && ordem in ROTULO_ORDEM ? ordem : "prazo",
  };
}

/** Escreve os filtros por cima dos parâmetros atuais, preservando o card aberto. */
export function escreverFiltros(atuais: URLSearchParams, filtros: Filtros): URLSearchParams {
  const novos = new URLSearchParams(atuais.toString());
  const definir = (chave: string, valor: string | null | undefined) => {
    if (valor) novos.set(chave, valor);
    else novos.delete(chave);
  };
  definir("q", filtros.busca.trim());
  definir("membro", filtros.membros.join(","));
  definir("etiqueta", filtros.etiquetas.join(","));
  definir("tipo", filtros.tipos.join("|"));
  definir("resultado", filtros.resultados.join(","));
  definir("criador", filtros.criador);
  definir("de", filtros.de);
  definir("ate", filtros.ate);
  definir("prazo", filtros.prazo);
  definir("meus", filtros.meus ? "1" : null);
  definir("fin", filtros.finalizados === "30" ? null : filtros.finalizados);
  definir("ordem", filtros.ordem === "prazo" ? null : filtros.ordem);
  return novos;
}

/** Quantos filtros do painel estão ligados (busca, ordem e finalizados têm controle próprio). */
export function contarAtivos(filtros: Filtros) {
  return (
    filtros.membros.length +
    filtros.etiquetas.length +
    filtros.tipos.length +
    filtros.resultados.length +
    (filtros.criador ? 1 : 0) +
    (filtros.de || filtros.ate ? 1 : 0) +
    (filtros.prazo ? 1 : 0)
  );
}

/** Data para a API trazer finalizados mais antigos que o padrão de 30 dias. */
export function finalizadosDesde(finalizados: Finalizados): string | undefined {
  if (finalizados === "30" || finalizados === "ocultar") return undefined;
  const data = new Date();
  data.setDate(data.getDate() - Number(finalizados));
  return data.toISOString().slice(0, 10);
}

function inicioDoDia(data: Date) {
  const copia = new Date(data);
  copia.setHours(0, 0, 0, 0);
  return copia;
}

export function aplicarFiltros(cards: LiberacaoCard[], filtros: Filtros, euId: string | undefined, agora = new Date()) {
  const termo = filtros.busca.trim().toLowerCase();
  const digitos = filtros.busca.replace(/\D/g, "");
  const hoje = inicioDoDia(agora);
  const amanha = new Date(hoje);
  amanha.setDate(hoje.getDate() + 1);
  const semana = new Date(agora.getTime() + 7 * 24 * 60 * 60 * 1000);
  const de = filtros.de ? new Date(`${filtros.de}T00:00:00`) : null;
  const ate = filtros.ate ? new Date(`${filtros.ate}T23:59:59`) : null;

  return cards.filter(card => {
    if (filtros.finalizados === "ocultar" && card.etapa === "FINALIZADO") return false;
    if (filtros.resultados.length > 0 && (!card.resultado || !filtros.resultados.includes(card.resultado))) return false;

    if (termo) {
      const naBusca =
        card.cedenteNome.toLowerCase().includes(termo) ||
        (digitos.length >= 3 && card.cedenteCnpj.includes(digitos)) ||
        String(card.numero) === termo.replace("#", "") ||
        card.sacados.some(
          sacado => (digitos.length >= 3 && sacado.documento.includes(digitos)) || (sacado.nome ?? "").toLowerCase().includes(termo),
        ) ||
        textoPlano(card.parecerOrigem).toLowerCase().includes(termo) ||
        card.etiquetas.some(etiqueta => etiqueta.nome.toLowerCase().includes(termo));
      if (!naBusca) return false;
    }

    if (filtros.meus && euId) {
      const meu =
        card.criadoPorId === euId ||
        card.membros.some(membro => membro.id === euId) ||
        card.pareceres.some(parecer => parecer.usuarioId === euId);
      if (!meu) return false;
    }

    if (filtros.membros.length > 0 && !filtros.membros.some(id => card.membros.some(membro => membro.id === id))) return false;
    if (filtros.etiquetas.length > 0 && !filtros.etiquetas.some(id => card.etiquetas.some(etiqueta => etiqueta.id === id))) return false;
    if (filtros.tipos.length > 0 && (!card.tipoOperacao || !filtros.tipos.includes(card.tipoOperacao))) return false;
    if (filtros.criador && card.criadoPorId !== filtros.criador) return false;

    const criado = new Date(card.criadoEm);
    if (de && criado < de) return false;
    if (ate && criado > ate) return false;

    if (filtros.prazo) {
      if (!card.prazo || card.finalizadoEm) return false;
      const prazo = new Date(card.prazo);
      if (filtros.prazo === "vencido" && prazo >= agora) return false;
      if (filtros.prazo === "hoje" && (prazo < hoje || prazo >= amanha)) return false;
      if (filtros.prazo === "semana" && prazo > semana) return false;
    }
    return true;
  });
}

/** Ordena uma coluna. Empate cai no número do card, para a ordem não pular a cada recarga. */
export function ordenarCards(cards: LiberacaoCard[], ordem: Ordem) {
  const desempate = (a: LiberacaoCard, b: LiberacaoCard) => a.numero - b.numero;
  return [...cards].sort((a, b) => {
    switch (ordem) {
      case "prazo":
        if (a.prazo && b.prazo) return a.prazo.localeCompare(b.prazo) || desempate(a, b);
        if (a.prazo) return -1;
        if (b.prazo) return 1;
        return a.etapaDesde.localeCompare(b.etapaDesde) || desempate(a, b);
      case "atividade":
        return (b.ultimaAtividade ?? b.atualizadoEm).localeCompare(a.ultimaAtividade ?? a.atualizadoEm) || desempate(a, b);
      case "recentes":
        return b.criadoEm.localeCompare(a.criadoEm) || desempate(a, b);
      case "antigos":
        return a.criadoEm.localeCompare(b.criadoEm) || desempate(a, b);
      case "valor":
        return (b.valor ?? -1) - (a.valor ?? -1) || desempate(a, b);
      case "nome":
        return a.cedenteNome.localeCompare(b.cedenteNome, "pt-BR") || desempate(a, b);
    }
  });
}
