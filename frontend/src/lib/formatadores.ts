const MS_DIA = 86_400_000;

const moeda = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", maximumFractionDigits: 0 });

export function formatMoeda(valor: number) {
  return moeda.format(valor);
}

export function formatCnpj(cnpj: string) {
  return cnpj.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}

export function formatData(iso: string) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleDateString("pt-BR");
}

/** Dias inteiros desde a data, contando por dia de calendário (ontem às 23h é "ontem"). */
export function diasDesde(iso: string, hoje = new Date()) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return 0;
  const inicio = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  return Math.max(0, Math.round((inicio(hoje) - inicio(d)) / MS_DIA));
}

export function tempoRelativo(dias: number) {
  if (dias === 0) return "hoje";
  if (dias === 1) return "ontem";
  if (dias < 30) return `há ${dias} dias`;
  if (dias < 60) return "há 1 mês";
  if (dias < 365) return `há ${Math.floor(dias / 30)} meses`;
  return "há mais de 1 ano";
}

export function iniciais(nome: string | null) {
  const partes = (nome ?? "").trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return "?";
  if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase();
  return (partes[0][0] + partes[partes.length - 1][0]).toUpperCase();
}

/** Busca sem acento e sem caixa: "joao" acha "JOÃO". */
export function normalizar(texto: string) {
  return texto.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

/** Nomes do Serasa vêm em caixa alta; na lista ficam em caixa de título. */
export function caixaTitulo(texto: string | null) {
  if (!texto) return "";
  const minusculas = new Set(["da", "de", "do", "das", "dos", "e"]);
  return texto
    .toLowerCase()
    .split(/\s+/)
    .map((p, i) => (i > 0 && minusculas.has(p) ? p : p.charAt(0).toUpperCase() + p.slice(1)))
    .join(" ");
}

/** Tons das iniciais: mesma pessoa, mesma cor, sem precisar guardar nada. */
const TONS = [
  ["#F3E3E8", "#612035"],
  ["#F8E6D8", "#8F4714"],
  ["#E3E9F2", "#2C4A73"],
  ["#E1EFE8", "#1F5E47"],
  ["#ECEAE3", "#4A4842"],
] as const;

export function tomDoAvatar(cpf: string) {
  let soma = 0;
  for (const c of cpf) soma += c.charCodeAt(0);
  const [fundo, texto] = TONS[soma % TONS.length];
  return { fundo, texto };
}
