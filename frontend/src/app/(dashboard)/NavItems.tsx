"use client";

import Link from "next/link";
import Icon from "@/components/ui/Icon";

/**
 * Itens de navegação compartilhados entre a sidebar do desktop (lg+) e o
 * drawer do tablet (< lg). Fonte única — antes o markup era duplicado por item.
 */
export type NavItem = {
  href: string;
  icon: string;
  label: string;
  /** Sub-item de um grupo: recuado e com fonte menor. */
  sub?: boolean;
  /** Sub-itens do grupo, mostrados abaixo do pai quando o grupo está aberto. */
  children?: NavItem[];
  /** Marca o item como ativo a partir do pathname. */
  isActive: (pathname: string) => boolean;
  /**
   * Contador vermelho ao lado do rótulo, para o que exige ação hoje: cards atrasados na
   * prospecção, e na liberação o parecer e a pendência que esperam pelo usuário.
   */
  badge?: number;
  /** Marca a área como em avaliação, para o time saber que o fluxo ainda pode mudar. */
  beta?: boolean;
};

export const NAV_ITEMS: NavItem[] = [
  {
    href: "/",
    icon: "folder_shared",
    label: "Gestão de Carteira",
    isActive: p => p === "/" || p.startsWith("/clients/"),
    children: [
      {
        href: "/commercial-information",
        icon: "business_center",
        label: "Informações Comerciais",
        sub: true,
        isActive: p => p === "/commercial-information",
      },
      {
        href: "/individuals",
        icon: "person_search",
        label: "Pessoas Físicas",
        sub: true,
        isActive: p => p === "/individuals" || p.startsWith("/individuals/"),
      },
    ],
  },
  {
    href: "/prospeccao",
    icon: "conveyor_belt",
    label: "Esteira de Prospecção",
    beta: true,
    isActive: p => p.startsWith("/prospeccao"),
  },
  {
    href: "/liberacao",
    icon: "fact_check",
    label: "Esteira de Liberação",
    beta: true,
    isActive: p => p.startsWith("/liberacao"),
  },
  {
    href: "/reports/visao-cedente",
    icon: "swap_horiz",
    label: "Visão Cedente",
    isActive: p => p.startsWith("/reports"),
  },
  {
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
  },
];

export const SETTINGS_ITEM: NavItem = {
  href: "/settings",
  icon: "settings",
  label: "Sistema",
  isActive: p => p === "/settings",
};

/**
 * Link de navegação. `showLabel` é fixo no drawer (sempre com rótulo) e
 * responsivo na sidebar do desktop.
 */
export function NavLink({
  item,
  pathname,
  showLabel,
  onNavigate,
}: {
  item: NavItem;
  pathname: string;
  /** "always" no drawer; "lg" mantém o comportamento atual do desktop. */
  showLabel: "always" | "lg";
  onNavigate?: () => void;
}) {
  const active = item.isActive(pathname);
  const labelVisibility = showLabel === "always" ? "" : "hidden lg:block";

  return (
    <Link
      href={item.href}
      onClick={onNavigate}
      aria-current={active ? "page" : undefined}
      className={`flex items-center gap-3 rounded-xl mx-2 group transition-all duration-200 ${
        item.sub
          // No drawer o sub-item usa py-3 para chegar aos 44px de alvo de toque.
          ? `px-3 ${showLabel === "always" ? "py-3 ml-6" : "py-2.5 lg:ml-6"}`
          : "px-3 py-3"
      } ${item.children?.length ? (showLabel === "always" ? "pr-16" : "pr-10") : ""} ${
        active
          ? "bg-white/10 text-white shadow-sm"
          : `${item.sub ? "text-white/60" : "text-white/70"} hover:bg-white/5 hover:text-white`
      }`}
    >
      <Icon
        name={item.icon}
        size={item.sub ? 20 : undefined}
        className={`transition-transform duration-200 ${
          active
            ? "text-white scale-110"
            : `${item.sub ? "text-white/60" : "text-white/70"} group-hover:text-white group-hover:scale-110`
        }`}
      />
      <span
        className={`font-sans ${item.sub ? "text-sm" : ""} ${
          active ? "font-bold" : "font-medium"
        } ${labelVisibility}`}
      >
        {item.label}
      </span>
      {item.beta && (
        <span
          className={`shrink-0 rounded border border-white/30 px-1 py-px text-[9px] font-bold uppercase
            tracking-wider text-white/70 ${labelVisibility}`}
          title="Em avaliação: o fluxo ainda pode mudar"
        >
          beta
        </span>
      )}
      {item.badge != null && item.badge > 0 && (
        <span
          // Na sidebar recolhida o rótulo desaparece, mas o contador fica: é o único aviso
          // de que existe card atrasado esperando.
          className="ml-auto shrink-0 rounded-full bg-red-600 px-1.5 py-0.5 text-[10px] font-bold text-white"
          title={`${item.badge} card(s) precisando de atenção`}
        >
          {item.badge > 99 ? "99+" : item.badge}
        </span>
      )}
    </Link>
  );
}
