/**
 * Menções nos textos do portal.
 *
 * <p>O texto salvo usa marcação — `@[Andressa Lima](user:<uuid>)` e `@[ACME](cnpj:<14 dígitos>)` —
 * mas quem digita vê só `@Andressa Lima`. Este módulo converte entre as duas formas e mantém as
 * posições das menções enquanto a pessoa edita. A regra do backend é a mesma: ver MencaoParser.</p>
 */

export type TipoMencao = "user" | "cnpj";

export type Mencao = { inicio: number; fim: number; tipo: TipoMencao; id: string; rotulo: string };

const MARCACAO = /@\[([^\]]+)\]\((user|cnpj):([^)\s]+)\)/g;

/** CNPJ solto, com ou sem pontuação, sem dígito colado antes ou depois. */
const CNPJ_SOLTO = /(?<![\d:])(\d{2}\.?\d{3}\.?\d{3}\/?\d{4}-?\d{2})(?!\d)/g;

/** Rótulo sem os caracteres que fechariam a marcação. */
function limparRotulo(rotulo: string) {
  return rotulo.replace(/[\[\]()]/g, "").trim();
}

/** Marcação salva → texto que a pessoa vê, mais a posição de cada menção nele. */
export function deMarcacao(marcacao: string): { texto: string; mencoes: Mencao[] } {
  let texto = "";
  const mencoes: Mencao[] = [];
  let ultimo = 0;
  for (const achado of marcacao.matchAll(MARCACAO)) {
    texto += marcacao.slice(ultimo, achado.index);
    const exibido = `@${achado[1]}`;
    mencoes.push({ inicio: texto.length, fim: texto.length + exibido.length, tipo: achado[2] as TipoMencao, id: achado[3], rotulo: achado[1] });
    texto += exibido;
    ultimo = (achado.index ?? 0) + achado[0].length;
  }
  texto += marcacao.slice(ultimo);
  return { texto, mencoes };
}

/** Texto visto + menções → marcação para salvar. Menção cujo trecho foi alterado vira texto comum. */
export function paraMarcacao(texto: string, mencoes: Mencao[]): string {
  let resultado = "";
  let ultimo = 0;
  for (const mencao of [...mencoes].sort((a, b) => a.inicio - b.inicio)) {
    if (mencao.inicio < ultimo || texto.slice(mencao.inicio, mencao.fim) !== `@${mencao.rotulo}`) continue;
    resultado += texto.slice(ultimo, mencao.inicio) + `@[${mencao.rotulo}](${mencao.tipo}:${mencao.id})`;
    ultimo = mencao.fim;
  }
  return resultado + texto.slice(ultimo);
}

/**
 * Reposiciona as menções depois de uma edição qualquer (digitar, colar, recortar).
 *
 * <p>Acha o trecho alterado pelo maior prefixo e sufixo comuns. Menção antes dele fica onde está,
 * depois dele anda junto, e menção que o trecho tocou deixa de ser menção — o nome continua no
 * texto, só não aponta mais para ninguém.</p>
 */
export function reposicionar(antigo: string, novo: string, mencoes: Mencao[]): Mencao[] {
  let prefixo = 0;
  while (prefixo < antigo.length && prefixo < novo.length && antigo[prefixo] === novo[prefixo]) prefixo++;
  let sufixo = 0;
  while (
    sufixo < antigo.length - prefixo &&
    sufixo < novo.length - prefixo &&
    antigo[antigo.length - 1 - sufixo] === novo[novo.length - 1 - sufixo]
  ) sufixo++;
  const fimAntigo = antigo.length - sufixo;
  const delta = novo.length - antigo.length;
  return mencoes.flatMap(mencao => {
    if (mencao.fim <= prefixo) return [mencao];
    if (mencao.inicio >= fimAntigo) return [{ ...mencao, inicio: mencao.inicio + delta, fim: mencao.fim + delta }];
    return [];
  });
}

/** Troca o trecho [inicio, fim) por uma menção seguida de espaço. Devolve o novo estado e o cursor. */
export function inserirMencao(
  texto: string,
  mencoes: Mencao[],
  inicio: number,
  fim: number,
  nova: { tipo: TipoMencao; id: string; rotulo: string },
) {
  const rotulo = limparRotulo(nova.rotulo);
  const exibido = `@${rotulo}`;
  // Já havia espaço depois do trecho: não dobra.
  const espaco = texto[fim] === " " ? "" : " ";
  const novoTexto = texto.slice(0, inicio) + exibido + espaco + texto.slice(fim);
  const delta = exibido.length + espaco.length - (fim - inicio);
  const ajustadas = mencoes
    .filter(mencao => mencao.fim <= inicio || mencao.inicio >= fim)
    .map(mencao => (mencao.inicio >= fim ? { ...mencao, inicio: mencao.inicio + delta, fim: mencao.fim + delta } : mencao));
  return {
    texto: novoTexto,
    mencoes: [...ajustadas, { inicio, fim: inicio + exibido.length, tipo: nova.tipo, id: nova.id, rotulo }],
    cursor: inicio + exibido.length + 1,
  };
}

/**
 * O `@` sendo digitado agora, se houver: começo do trecho e o que vem depois dele até o cursor.
 *
 * <p>Nome tem espaço ("@Andressa Li"), então a consulta aceita espaço simples; dois espaços,
 * quebra de linha ou 40 caracteres encerram. O `@` precisa abrir palavra, para e-mail digitado
 * no texto não abrir a lista.</p>
 */
export function gatilhoAtual(texto: string, cursor: number, mencoes: Mencao[]): { inicio: number; consulta: string } | null {
  if (mencoes.some(mencao => cursor > mencao.inicio && cursor <= mencao.fim)) return null;
  for (let i = cursor - 1; i >= 0 && cursor - i <= 41; i--) {
    const caractere = texto[i];
    if (caractere === "\n") return null;
    if (caractere === "@") {
      const anterior = i > 0 ? texto[i - 1] : " ";
      if (!/[\s(]/.test(anterior)) return null;
      const consulta = texto.slice(i + 1, cursor);
      if (/\s{2}/.test(consulta) || consulta.startsWith(" ")) return null;
      return { inicio: i, consulta };
    }
  }
  return null;
}

export function cnpjValido(cnpj: string) {
  if (!/^\d{14}$/.test(cnpj) || /^(\d)\1+$/.test(cnpj)) return false;
  const digito = (posicao: number) => {
    let soma = 0;
    let peso = posicao - 7;
    for (let i = 0; i < posicao; i++) {
      soma += Number(cnpj[i]) * peso--;
      if (peso < 2) peso = 9;
    }
    const resto = soma % 11;
    return resto < 2 ? 0 : 11 - resto;
  };
  return digito(12) === Number(cnpj[12]) && digito(13) === Number(cnpj[13]);
}

export type Segmento =
  | { tipo: "texto"; texto: string }
  | { tipo: "user"; id: string; rotulo: string }
  | { tipo: "cnpj"; cnpj: string; rotulo: string | null };

/** Quebra a marcação em pedaços para desenhar: texto, pessoa, empresa (inclusive CNPJ solto válido). */
export function segmentar(marcacao: string): Segmento[] {
  const segmentos: Segmento[] = [];
  const empurrarTexto = (trecho: string) => {
    let ultimo = 0;
    for (const achado of trecho.matchAll(CNPJ_SOLTO)) {
      const digitos = achado[1].replace(/\D/g, "");
      if (!cnpjValido(digitos)) continue;
      if ((achado.index ?? 0) > ultimo) segmentos.push({ tipo: "texto", texto: trecho.slice(ultimo, achado.index) });
      segmentos.push({ tipo: "cnpj", cnpj: digitos, rotulo: null });
      ultimo = (achado.index ?? 0) + achado[0].length;
    }
    if (ultimo < trecho.length) segmentos.push({ tipo: "texto", texto: trecho.slice(ultimo) });
  };

  let ultimo = 0;
  for (const achado of marcacao.matchAll(MARCACAO)) {
    if ((achado.index ?? 0) > ultimo) empurrarTexto(marcacao.slice(ultimo, achado.index));
    if (achado[2] === "user") segmentos.push({ tipo: "user", id: achado[3], rotulo: achado[1] });
    else segmentos.push({ tipo: "cnpj", cnpj: achado[3].replace(/\D/g, ""), rotulo: achado[1] });
    ultimo = (achado.index ?? 0) + achado[0].length;
  }
  if (ultimo < marcacao.length) empurrarTexto(marcacao.slice(ultimo));
  return segmentos;
}

/** Texto sem marcação, com @Nome no lugar. Para busca e para exibir em uma linha. */
export function textoPlano(marcacao: string | null | undefined) {
  return marcacao ? marcacao.replace(MARCACAO, (_trecho, rotulo: string) => `@${rotulo}`) : "";
}

/** Sem acento e em minúsculas, para filtrar nomes como a pessoa digita. */
export function normalizar(texto: string) {
  return texto.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
}
