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
  /** Sub-item da Praça de Pagamento: recuado e com fonte menor. */
  sub?: boolean;
  /** Marca o item como ativo a partir do pathname. */
  isActive: (pathname: string) => boolean;
};

export const NAV_ITEMS: NavItem[] = [
  {
    href: "/",
    icon: "folder_shared",
    label: "Gestão de Carteira",
    isActive: p => p === "/" || p.startsWith("/clients/"),
  },
  {
    href: "/commercial-information",
    icon: "business_center",
    label: "Informações Comerciais",
    isActive: p => p === "/commercial-information",
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
  },
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
  {
    href: "/individuals",
    icon: "person_search",
    label: "Pessoas Físicas",
    isActive: p => p === "/individuals" || p.startsWith("/individuals/"),
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
      } ${
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
    </Link>
  );
}
