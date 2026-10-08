"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { useProspeccaoResumo } from "@/hooks/useProspeccao";
import { NAV_ITEMS, NavLink, type NavItem } from "./NavItems";

/**
 * Lista de navegação com o contador da esteira.
 *
 * <p>Existe separada do layout porque o layout é quem monta o QueryClientProvider — um hook de
 * query ali dentro ficaria fora do provider. É também o único lugar que pede dado para desenhar
 * o menu, e por isso o resumo é recarregado de minuto em minuto.</p>
 *
 * <p>Itens com filhos viram grupo: a seta ao lado abre e fecha. Sem escolha do usuário, o grupo
 * abre sozinho quando a página atual é o pai ou um dos filhos. A escolha fica só em memória —
 * o layout não remonta entre páginas, então vale até recarregar a página; persistir em localStorage
 * esbarraria na regra react-hooks/set-state-in-effect (mesmo motivo do filtro da esteira).</p>
 */
export default function NavList({
  pathname,
  showLabel,
  onNavigate,
}: {
  pathname: string;
  showLabel: "always" | "lg";
  onNavigate?: () => void;
}) {
  const { data: resumo } = useProspeccaoResumo();
  const precisamAtencao = resumo?.precisamAtencao ?? 0;

  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({});

  const isGroupActive = (item: NavItem) =>
    item.isActive(pathname) || !!item.children?.some(c => c.isActive(pathname));

  const toggle = (href: string, current: boolean) => {
    setOpenGroups(prev => ({ ...prev, [href]: !current }));
  };

  const labelVisibility = showLabel === "always" ? "" : "hidden lg:flex";

  return (
    <>
      {NAV_ITEMS.map(item => {
        const children = item.children ?? [];
        const open = openGroups[item.href] ?? isGroupActive(item);

        return (
          <li key={item.href}>
            <div className="relative">
              <NavLink
                item={item.href === "/prospeccao" ? { ...item, badge: precisamAtencao } : item}
                pathname={pathname}
                showLabel={showLabel}
                onNavigate={onNavigate}
              />
              {children.length > 0 && (
                <button
                  type="button"
                  onClick={() => toggle(item.href, open)}
                  aria-expanded={open}
                  aria-label={`${open ? "Fechar" : "Abrir"} ${item.label}`}
                  className={`absolute right-3 top-1/2 -translate-y-1/2 items-center justify-center rounded-lg
                    text-white/60 transition-colors hover:bg-white/10 hover:text-white
                    ${showLabel === "always" ? "flex h-11 w-11" : "h-8 w-8"} ${labelVisibility}`}
                >
                  <Icon
                    name="expand_more"
                    size={20}
                    className={`transition-transform duration-200 ${open ? "rotate-180" : ""}`}
                  />
                </button>
              )}
            </div>
            {children.length > 0 && open && (
              <ul className="mt-1 space-y-1">
                {children.map(child => (
                  <li key={child.href}>
                    <NavLink item={child} pathname={pathname} showLabel={showLabel} onNavigate={onNavigate} />
                  </li>
                ))}
              </ul>
            )}
          </li>
        );
      })}
    </>
  );
}
