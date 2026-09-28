"use client";

import { useProspeccaoResumo } from "@/hooks/useProspeccao";
import { NAV_ITEMS, NavLink } from "./NavItems";

/**
 * Lista de navegação com o contador da esteira.
 *
 * <p>Existe separada do layout porque o layout é quem monta o QueryClientProvider — um hook de
 * query ali dentro ficaria fora do provider. É também o único lugar que pede dado para desenhar
 * o menu, e por isso o resumo é recarregado de minuto em minuto.</p>
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

  return (
    <>
      {NAV_ITEMS.map(item => (
        <li key={item.href}>
          <NavLink
            item={item.href === "/prospeccao" ? { ...item, badge: precisamAtencao } : item}
            pathname={pathname}
            showLabel={showLabel}
            onNavigate={onNavigate}
          />
        </li>
      ))}
    </>
  );
}
