"use client";

/**
 * Itens de navegação do menu, iguais na sidebar do desktop e na gaveta do tablet.
 */
export type NavItem = {
  href: string;
  icon: string;
  label: string;
  /** Rótulo na casca nova, onde o grupo já diz o contexto ("Esteiras" › "Prospecção"). */
  rotuloCurto?: string;
  /** Sub-item de um grupo: recuado e com fonte menor. */
  sub?: boolean;
  /** Sub-itens do grupo, mostrados abaixo do pai quando o grupo está aberto. */
  children?: NavItem[];
  /** Marca o item como ativo a partir do pathname. */
  isActive: (pathname: string) => boolean;
  /**
   * Contador vermelho ao lado do rótulo, para o que exige ação hoje: cards atrasados na
   * prospecção; na liberação, os de prazo vencido mais o parecer e a pendência do usuário.
   */
  badge?: number;
  /** Marca a área como em avaliação, para o time saber que o fluxo ainda pode mudar. */
  beta?: boolean;
};

const INFORMACOES_COMERCIAIS: NavItem = {
  href: "/commercial-information",
  icon: "business_center",
  label: "Informações Comerciais",
  isActive: p => p === "/commercial-information",
};

const PESSOAS_FISICAS: NavItem = {
  href: "/individuals",
  icon: "person_search",
  label: "Pessoas Físicas",
  isActive: p => p === "/individuals" || p.startsWith("/individuals/"),
};

const GESTAO_CARTEIRA: NavItem = {
  href: "/",
  icon: "folder_shared",
  label: "Gestão de Carteira",
  isActive: p => p === "/" || p.startsWith("/clients/"),
};

const VISAO_CEDENTE: NavItem = {
  href: "/reports/visao-cedente",
  icon: "swap_horiz",
  label: "Visão Cedente",
  isActive: p => p.startsWith("/reports"),
};

const PROSPECCAO: NavItem = {
  href: "/prospeccao",
  icon: "conveyor_belt",
  label: "Esteira de Prospecção",
  rotuloCurto: "Prospecção",
  beta: true,
  isActive: p => p.startsWith("/prospeccao"),
};

const LIBERACAO: NavItem = {
  href: "/liberacao",
  icon: "fact_check",
  label: "Esteira de Liberação",
  rotuloCurto: "Liberação",
  beta: true,
  isActive: p => p.startsWith("/liberacao"),
};

const PRACA_PAGAMENTO: NavItem = {
  href: "/praca-pagamento",
  icon: "account_balance",
  label: "Praça de Pagamento",
  isActive: p => p === "/praca-pagamento",
  children: [
    {
      href: "/praca-pagamento/inconclusivos",
      icon: "rule",
      label: "Inconclusivos",
      sub: true,
      isActive: p => p === "/praca-pagamento/inconclusivos",
    },
    {
      href: "/praca-pagamento/padroes",
      icon: "psychology",
      label: "Padrões",
      sub: true,
      isActive: p => p === "/praca-pagamento/padroes",
    },
    {
      href: "/praca-pagamento/historico",
      icon: "history",
      label: "Histórico",
      sub: true,
      isActive: p => p === "/praca-pagamento/historico",
    },
  ],
};

/** Itens agrupados por seção do menu; a Praça tem subitens. */
export const GRUPOS_NAV: { titulo: string; itens: NavItem[] }[] = [
  { titulo: "Carteira", itens: [GESTAO_CARTEIRA, INFORMACOES_COMERCIAIS, PESSOAS_FISICAS, VISAO_CEDENTE] },
  { titulo: "Operação", itens: [PRACA_PAGAMENTO] },
  { titulo: "Esteiras", itens: [PROSPECCAO, LIBERACAO] },
];

export const SETTINGS_ITEM: NavItem = {
  href: "/settings",
  icon: "settings",
  label: "Sistema",
  isActive: p => p === "/settings",
};
