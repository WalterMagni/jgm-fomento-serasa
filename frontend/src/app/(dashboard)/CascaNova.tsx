"use client";

import { Fragment, useEffect, useState, type ReactNode } from "react";
import Icon from "@/components/ui/Icon";
import SinoNotificacoes from "@/components/notificacoes/SinoNotificacoes";
import { GRUPOS_NAV } from "./NavItems";
import Sidebar from "./Sidebar";

/** "Carteira / Pessoas Físicas": seção do menu e página, para o topo. */
function caminhoDaPagina(pathname: string): string[] {
  if (pathname.startsWith("/clients/")) return ["Carteira", "Visão 360º do cliente"];
  if (pathname.startsWith("/individuals/")) return ["Carteira", "Pessoas Físicas", "Análise"];
  if (pathname === "/settings") return ["Sistema", "Configurações"];
  if (pathname.startsWith("/administracao")) return ["Sistema", "Administração"];
  for (const grupo of GRUPOS_NAV) {
    for (const item of grupo.itens) {
      const filho = item.children?.find(f => f.isActive(pathname));
      if (filho) return [grupo.titulo, item.label, filho.label];
      if (item.isActive(pathname)) return [grupo.titulo, item.label];
    }
  }
  return ["Portal"];
}

/**
 * Casca da repaginação: sidebar bordô com grupos à esquerda, topo enxuto com o caminho da
 * página e o sino, conteúdo sobre o papel. No tablet (abaixo de lg) a sidebar vira gaveta.
 */
export default function CascaNova({
  pathname,
  userName,
  userInitials,
  onSair,
  children,
}: {
  pathname: string;
  userName: string;
  userInitials: string;
  onSair: () => void;
  children: ReactNode;
}) {
  const [menuAberto, setMenuAberto] = useState(false);
  const caminho = caminhoDaPagina(pathname);

  // Gaveta aberta: Esc fecha e o fundo não rola.
  useEffect(() => {
    if (!menuAberto) return;
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === "Escape") setMenuAberto(false);
    };
    const overflowAntes = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", aoTeclar);
    return () => {
      document.body.style.overflow = overflowAntes;
      window.removeEventListener("keydown", aoTeclar);
    };
  }, [menuAberto]);

  return (
    <div className="flex h-screen bg-papel text-grafite print:block print:h-auto">
      <aside className="hidden w-[260px] shrink-0 lg:block print:hidden">
        <Sidebar pathname={pathname} userName={userName} userInitials={userInitials} onSair={onSair} />
      </aside>

      {menuAberto && (
        <div className="fixed inset-0 z-40 lg:hidden print:hidden">
          <button
            type="button"
            aria-label="Fechar menu"
            onClick={() => setMenuAberto(false)}
            className="absolute inset-0 h-full w-full cursor-default bg-grafite/50 backdrop-blur-sm animate-surgir motion-reduce:animate-none"
          />
          <aside className="absolute inset-y-0 left-0 w-72 max-w-[85vw] shadow-2xl animate-entrar-esquerda motion-reduce:animate-none">
            <Sidebar
              pathname={pathname}
              userName={userName}
              userInitials={userInitials}
              onSair={onSair}
              onNavigate={() => setMenuAberto(false)}
            />
            <button
              type="button"
              aria-label="Fechar menu"
              onClick={() => setMenuAberto(false)}
              className="absolute right-2.5 top-4 grid h-11 w-11 place-items-center rounded-xl text-white/80 transition-colors hover:bg-white/10 hover:text-white"
            >
              <Icon name="close" />
            </button>
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col print:block">
        {/* z-20: o <main> abaixo cria a própria camada (z-0) e vem depois no HTML; sem isso o
            popup do sino fica por baixo do conteúdo. */}
        <header className="relative z-20 flex h-16 shrink-0 items-center gap-3 border-b border-linha bg-papel/85 px-5 backdrop-blur-md lg:px-8 print:hidden">
          <button
            type="button"
            onClick={() => setMenuAberto(true)}
            aria-label="Abrir menu"
            aria-expanded={menuAberto}
            className="-ml-2 grid h-11 w-11 place-items-center rounded-xl text-grafite transition-colors hover:bg-white lg:hidden"
          >
            <Icon name="menu" />
          </button>
          <nav aria-label="Você está em" className="flex min-w-0 items-center gap-2 text-[13px] text-suave">
            {caminho.map((parte, i) =>
              i < caminho.length - 1 ? (
                <Fragment key={parte}>
                  <span className="hidden whitespace-nowrap sm:inline">{parte}</span>
                  <span aria-hidden className="hidden text-[#B5B1A6] sm:inline">/</span>
                </Fragment>
              ) : (
                <b key={parte} className="truncate font-semibold text-grafite">
                  {parte}
                </b>
              ),
            )}
          </nav>
          <div className="ml-auto">
            <SinoNotificacoes />
          </div>
        </header>

        <main className="relative z-0 min-w-0 flex-1 overflow-y-auto p-5 lg:p-8 print:overflow-visible print:p-0">
          {/* Só opacidade na troca de página: transform aqui viraria referência para os
              position:fixed dos modais das páginas enquanto a animação roda. */}
          <div key={pathname} className="animate-surgir motion-reduce:animate-none">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
