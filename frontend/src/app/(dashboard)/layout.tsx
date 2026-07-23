"use client";

import Link from "next/link";
import Icon from "@/components/ui/Icon";
import Image from "next/image";
import { usePathname, useRouter } from "next/navigation";
import { ReactNode, useState, useEffect, useRef } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";
import { NAV_ITEMS, SETTINGS_ITEM, NavLink } from "./NavItems";

/** Decodifica o JWT e diz se já expirou (ou é inválido). */
function isTokenExpired(token: string | null): boolean {
  if (!token) return true;
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    if (!payload.exp) return false;
    return Date.now() >= payload.exp * 1000;
  } catch {
    return true;
  }
}

export default function DashboardLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();

  const [isDark, setIsDark] = useState(false);
  const [themeReady, setThemeReady] = useState(false);

  const [userName, setUserName] = useState<string>("Usuário");
  const [userInitials, setUserInitials] = useState<string>("US");

  // Modo tablet: o menu vira drawer abaixo de lg. No desktop (lg+) nada muda.
  const [menuOpen, setMenuOpen] = useState(false);
  // Dropdown do usuário por clique — em tela de toque não existe hover.
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);

  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: { queries: { staleTime: 60 * 1000 } },
  }));

  useEffect(() => {
    setTimeout(() => {
      const saved = localStorage.getItem('credanalyze_theme');
      const shouldUseDark = saved === 'dark'
        ? true
        : saved === 'light'
        ? false
        : window.matchMedia('(prefers-color-scheme: dark)').matches;

      setIsDark(shouldUseDark);
      setThemeReady(true);
      document.documentElement.classList.toggle('dark', shouldUseDark);
    }, 0);
  }, []);

  // Update external DOM + persist after the browser theme has been read.
  useEffect(() => {
    if (!themeReady) return;
    if (isDark) {
      document.documentElement.classList.add('dark');
      localStorage.setItem('credanalyze_theme', 'dark');
    } else {
      document.documentElement.classList.remove('dark');
      localStorage.setItem('credanalyze_theme', 'light');
    }
  }, [isDark, themeReady]);

  // Auth Guard — valida presença E expiração do token (sessão de 8h).
  useEffect(() => {
    const token = localStorage.getItem('serasa_token');
    const storedName = localStorage.getItem('serasa_user_name');

    if (isTokenExpired(token)) {
      localStorage.removeItem('serasa_token');
      router.replace('/login?expired=1');
      return;
    }
    if (storedName) {
      setTimeout(() => {
        setUserName(storedName);
        const parts = storedName.split(' ');
        if (parts.length > 1) {
          setUserInitials((parts[0][0] + parts[1][0]).toUpperCase());
        } else {
          setUserInitials(storedName.substring(0, 2).toUpperCase());
        }
      }, 0);
    }
  }, [router]);

  // Verifica a expiração periodicamente — derruba a sessão ao passar das 8h mesmo com a aba aberta.
  useEffect(() => {
    const id = setInterval(() => {
      if (isTokenExpired(localStorage.getItem('serasa_token'))) {
        localStorage.removeItem('serasa_token');
        router.replace('/login?expired=1');
      }
    }, 60_000);
    return () => clearInterval(id);
  }, [router]);

  // Fecha o drawer ao trocar de rota (o Link do drawer também fecha na hora).
  useEffect(() => {
    setMenuOpen(false);
    setUserMenuOpen(false);
  }, [pathname]);

  // Esc fecha drawer e menu do usuário.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      setMenuOpen(false);
      setUserMenuOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  // Clique/toque fora fecha o menu do usuário.
  useEffect(() => {
    if (!userMenuOpen) return;
    const onDown = (e: MouseEvent | TouchEvent) => {
      if (userMenuRef.current?.contains(e.target as Node)) return;
      setUserMenuOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("touchstart", onDown);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("touchstart", onDown);
    };
  }, [userMenuOpen]);

  // Trava o scroll do fundo enquanto o drawer está aberto.
  useEffect(() => {
    if (!menuOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = prev; };
  }, [menuOpen]);

  const logout = () => {
    localStorage.removeItem('serasa_token');
    router.push('/login');
  };

  const pageLabel = pathname === '/'
    ? 'Gestão de Carteira'
    : pathname.startsWith('/clients/')
    ? 'Visão 360º do Cliente'
    : pathname === '/commercial-information'
    ? 'Informações Comerciais'
    : pathname === '/praca-pagamento'
    ? 'Praça de Pagamento'
    : pathname === '/settings'
    ? 'Configurações'
    : pathname.startsWith('/administracao')
    ? 'Administração'
    : pathname.startsWith('/reports/visao-cedente')
    ? 'Relatório — Visão Cedente'
    : pathname === '/individuals'
    ? 'Pessoas Físicas'
    : pathname.startsWith('/individuals/')
    ? 'Análise — Pessoa Física'
    : 'Portal';

  return (
    <div className="flex flex-col min-h-screen">
      {/* Top Navbar using Glassmorphism */}
      <nav className="bg-white/80 dark:bg-surface-dark/80 backdrop-blur-md border-b border-gray-200/50 dark:border-gray-800/50 h-16 flex items-center px-6 fixed w-full z-30 top-0 transition-colors duration-300 print:hidden shadow-[0_4px_30px_rgba(0,0,0,0.03)] dark:shadow-[0_4px_30px_rgba(0,0,0,0.2)]">
        <div className="flex items-center gap-4 w-full">
          {/* Hamburguer — só no modo tablet (< lg). Desktop segue com a sidebar fixa. */}
          <button
            onClick={() => setMenuOpen(true)}
            aria-label="Abrir menu"
            aria-expanded={menuOpen}
            className="lg:hidden -ml-2 flex h-11 w-11 items-center justify-center rounded-xl text-grafite transition-colors hover:bg-gray-100 dark:text-white dark:hover:bg-gray-700"
          >
            <Icon name="menu" />
          </button>

          <Link href="/" className="flex items-center gap-2 hover:opacity-80 transition-opacity">
            {isDark ? (
              <Image src="/logo-dark.png" alt="JGM Fomento" width={140} height={32} className="h-8 w-auto object-contain hidden dark:block" />
            ) : (
              <Image src="/logo-light.png" alt="JGM Fomento" width={140} height={32} className="h-8 w-auto object-contain dark:hidden" />
            )}
            <span className="font-heading font-bold text-xl text-primary dark:text-white tracking-tight">
              Fomento
            </span>
          </Link>

          <div className="h-6 w-px bg-gray-300 dark:bg-gray-600 mx-2 hidden md:block" />

          <div className="hidden md:flex items-center gap-2">
            <span className="font-sans font-medium text-grafite dark:text-white">{pageLabel}</span>
          </div>



          <div className="ml-auto flex items-center gap-4">
            <button
              onClick={() => setIsDark(prev => !prev)}
              aria-label={isDark ? 'Mudar para modo claro' : 'Mudar para modo escuro'}
              title={isDark ? 'Mudar para modo claro' : 'Mudar para modo escuro'}
              className="flex h-11 w-11 items-center justify-center rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 dark:text-gray-300 transition-colors"
            >
              <Icon name={isDark ? 'light_mode' : 'dark_mode'} />
            </button>
            <div className="relative" ref={userMenuRef}>
              {/* Clique (não hover) — em tela de toque hover não existe. */}
              <button
                onClick={() => setUserMenuOpen(o => !o)}
                aria-label="Menu do usuário"
                aria-expanded={userMenuOpen}
                aria-haspopup="menu"
                className="flex min-h-11 items-center gap-3 pl-4 border-l border-gray-200 dark:border-gray-700 hover:opacity-80 transition-opacity"
              >
                <div className="text-right hidden md:block">
                  <p className="text-sm font-sans font-bold text-grafite dark:text-white leading-tight">{userName}</p>
                </div>
                <div className="w-9 h-9 rounded-full bg-secondary/10 dark:bg-secondary/20 border border-secondary/20 overflow-hidden flex items-center justify-center text-secondary font-bold font-sans shadow-sm">
                  {userInitials}
                </div>
              </button>
              {/* Dropdown Menu */}
              {userMenuOpen && (
                <div role="menu" className="absolute right-0 mt-2 w-48 bg-white dark:bg-surface-dark border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-50">
                  <button
                    role="menuitem"
                    onClick={logout}
                    className="w-full text-left px-4 py-3.5 text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 flex items-center gap-2 rounded-lg"
                  >
                    <Icon name="logout" size={18} />
                    Sair
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      </nav>

      <div className="flex pt-16 h-screen">
        {/* Sidebar — desktop (lg+). Modo tablet usa o drawer abaixo. */}
        <aside className="w-64 bg-primary dark:bg-[#0a0b0f] border-r border-primary dark:border-gray-800/50 hidden lg:flex flex-col flex-shrink-0 transition-all duration-300 print:hidden relative z-20 overflow-hidden shadow-[4px_0_24px_rgba(0,0,0,0.02)]">
          {/* Decorative element in sidebar */}
          <div className="absolute top-0 right-0 w-32 h-32 bg-white/5 rounded-full blur-2xl pointer-events-none" />

          <div className="flex-1 overflow-y-auto py-6">
            <ul className="space-y-1 px-3">
              {NAV_ITEMS.map(item => (
                <li key={item.href}>
                  <NavLink item={item} pathname={pathname} showLabel="lg" />
                </li>
              ))}
              <li className="pt-4 mt-4 border-t border-white/10 relative">
                <span className="px-5 text-xs font-sans font-bold text-white/50 uppercase tracking-widest hidden lg:block mb-3">Configurações</span>
                <NavLink item={SETTINGS_ITEM} pathname={pathname} showLabel="lg" />
              </li>
            </ul>
          </div>
        </aside>

        {/* Drawer — modo tablet (< lg). Rótulos sempre visíveis, alvos de toque grandes. */}
        {menuOpen && (
          <div className="fixed inset-0 z-40 lg:hidden print:hidden">
            <div
              className="absolute inset-0 bg-black/50 backdrop-blur-sm"
              onClick={() => setMenuOpen(false)}
            />
            <aside className="absolute left-0 top-0 flex h-full w-72 max-w-[85vw] flex-col overflow-hidden bg-primary shadow-2xl dark:bg-[#0a0b0f]">
              <div className="flex h-16 flex-shrink-0 items-center justify-between border-b border-white/10 px-4">
                <span className="font-heading text-lg font-bold text-white">Menu</span>
                <button
                  onClick={() => setMenuOpen(false)}
                  aria-label="Fechar menu"
                  className="flex h-11 w-11 items-center justify-center rounded-xl text-white/80 transition-colors hover:bg-white/10 hover:text-white"
                >
                  <Icon name="close" />
                </button>
              </div>
              <nav className="flex-1 overflow-y-auto py-4">
                <ul className="space-y-1 px-3">
                  {NAV_ITEMS.map(item => (
                    <li key={item.href}>
                      <NavLink item={item} pathname={pathname} showLabel="always" onNavigate={() => setMenuOpen(false)} />
                    </li>
                  ))}
                  <li className="mt-4 border-t border-white/10 pt-4">
                    <span className="mb-3 block px-5 text-xs font-sans font-bold uppercase tracking-widest text-white/50">Configurações</span>
                    <NavLink item={SETTINGS_ITEM} pathname={pathname} showLabel="always" onNavigate={() => setMenuOpen(false)} />
                  </li>
                </ul>
              </nav>
              <div className="flex-shrink-0 border-t border-white/10 p-3">
                <button
                  onClick={logout}
                  className="flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left font-sans font-medium text-white/70 transition-colors hover:bg-white/5 hover:text-white"
                >
                  <Icon name="logout" size={20} />
                  Sair
                </button>
              </div>
            </aside>
          </div>
        )}

        {/* Main Content */}
        <main className="flex-1 min-w-0 overflow-y-auto bg-background-light dark:bg-background-dark p-6 lg:p-8 transition-colors duration-300 relative z-0">
          <QueryClientProvider client={queryClient}>
            {children}
          </QueryClientProvider>
        </main>
      </div>
      {/* Fora do <main> (z-0 + overflow) para a notificação não ficar presa atrás do header. */}
      <Toaster richColors position="top-right" offset={80} toastOptions={{ style: { zIndex: 9999 } }} />
    </div>
  );
}
