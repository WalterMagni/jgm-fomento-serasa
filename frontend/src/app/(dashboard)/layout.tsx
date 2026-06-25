"use client";

import Link from "next/link";
import Icon from "@/components/ui/Icon";
import Image from "next/image";
import { usePathname, useRouter } from "next/navigation";
import { ReactNode, useState, useEffect } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";

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
              title={isDark ? 'Mudar para modo claro' : 'Mudar para modo escuro'}
              className="p-2 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 dark:text-gray-300 transition-colors"
            >
              <Icon name={isDark ? 'light_mode' : 'dark_mode'} />
            </button>
            <div className="relative group">
              <button className="flex items-center gap-3 pl-4 border-l border-gray-200 dark:border-gray-700 hover:opacity-80 transition-opacity">
                <div className="text-right hidden md:block">
                  <p className="text-sm font-sans font-bold text-grafite dark:text-white leading-tight">{userName}</p>
                </div>
                <div className="w-9 h-9 rounded-full bg-secondary/10 dark:bg-secondary/20 border border-secondary/20 overflow-hidden flex items-center justify-center text-secondary font-bold font-sans shadow-sm">
                  {userInitials}
                </div>
              </button>
              {/* Dropdown Menu */}
              <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-surface-dark border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200 z-50">
                <button
                  onClick={() => {
                    localStorage.removeItem('serasa_token');
                    router.push('/login');
                  }}
                  className="w-full text-left px-4 py-3 text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 flex items-center gap-2 rounded-lg"
                >
                  <Icon name="logout" size={18} />
                  Sair
                </button>
              </div>
            </div>
          </div>
        </div>
      </nav>

      <div className="flex pt-16 h-screen">
        {/* Sidebar */}
        <aside className="w-20 lg:w-64 bg-primary dark:bg-[#0a0b0f] border-r border-primary dark:border-gray-800/50 hidden md:flex flex-col flex-shrink-0 transition-all duration-300 print:hidden relative z-20 overflow-hidden shadow-[4px_0_24px_rgba(0,0,0,0.02)]">
          {/* Decorative element in sidebar */}
          <div className="absolute top-0 right-0 w-32 h-32 bg-white/5 rounded-full blur-2xl pointer-events-none" />
          
          <div className="flex-1 overflow-y-auto py-6">
            <ul className="space-y-1 px-3">
              <li>
                <Link
                  href="/"
                  className={`flex items-center gap-3 px-3 py-3 rounded-xl mx-2 group transition-all duration-200 ${
                    pathname === '/' || pathname.startsWith('/clients/')
                      ? 'bg-white/10 text-white shadow-sm'
                      : 'text-white/70 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  <Icon name="folder_shared" className={`transition-transform duration-200 ${pathname === '/' || pathname.startsWith('/clients/') ? 'text-white scale-110' : 'text-white/70 group-hover:text-white group-hover:scale-110'}`} />
                  <span className={`font-sans ${pathname === '/' || pathname.startsWith('/clients/') ? 'font-bold' : 'font-medium'} hidden lg:block`}>
                    Gestão de Carteira
                  </span>
                </Link>
              </li>
              <li>
                <Link
                  href="/commercial-information"
                  className={`flex items-center gap-3 px-3 py-3 mx-2 rounded-xl group transition-all duration-200 ${
                    pathname === '/commercial-information'
                      ? 'bg-white/10 text-white shadow-sm'
                      : 'text-white/70 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  <Icon name="business_center" className={`transition-transform duration-200 ${pathname === '/commercial-information' ? 'text-white scale-110' : 'text-white/70 group-hover:text-white group-hover:scale-110'}`} />
                  <span className={`font-sans ${pathname === '/commercial-information' ? 'font-bold' : 'font-medium'} hidden lg:block`}>
                    Informações Comerciais
                  </span>
                </Link>
              </li>
              <li>
                <Link
                  href="/reports/visao-cedente"
                  className={`flex items-center gap-3 px-3 py-3 mx-2 rounded-xl group transition-all duration-200 ${
                    pathname.startsWith('/reports')
                      ? 'bg-white/10 text-white shadow-sm'
                      : 'text-white/70 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  <Icon name="swap_horiz" className={`transition-transform duration-200 ${pathname.startsWith('/reports') ? 'text-white scale-110' : 'text-white/70 group-hover:text-white group-hover:scale-110'}`} />
                  <span className={`font-sans ${pathname.startsWith('/reports') ? 'font-bold' : 'font-medium'} hidden lg:block`}>
                    Visão Cedente
                  </span>
                </Link>
              </li>
              <li>
                <Link
                  href="/praca-pagamento"
                  className={`flex items-center gap-3 px-3 py-3 mx-2 rounded-xl group transition-all duration-200 ${
                    pathname === '/praca-pagamento'
                      ? 'bg-white/10 text-white shadow-sm'
                      : 'text-white/70 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  <Icon name="account_balance" className={`transition-transform duration-200 ${pathname === '/praca-pagamento' ? 'text-white scale-110' : 'text-white/70 group-hover:text-white group-hover:scale-110'}`} />
                  <span className={`font-sans ${pathname === '/praca-pagamento' ? 'font-bold' : 'font-medium'} hidden lg:block`}>
                    Praça de Pagamento
                  </span>
                </Link>
              </li>
              <li>
                <Link
                  href="/praca-pagamento/inconclusivos"
                  className={`flex items-center gap-3 px-3 py-2.5 mx-2 lg:ml-6 rounded-xl group transition-all duration-200 ${
                    pathname === '/praca-pagamento/inconclusivos'
                      ? 'bg-white/10 text-white shadow-sm'
                      : 'text-white/60 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  <Icon name="rule" size={20} className={`transition-transform duration-200 ${pathname === '/praca-pagamento/inconclusivos' ? 'text-white scale-110' : 'text-white/60 group-hover:text-white group-hover:scale-110'}`} />
                  <span className={`font-sans text-sm ${pathname === '/praca-pagamento/inconclusivos' ? 'font-bold' : 'font-medium'} hidden lg:block`}>
                    Inconclusivos
                  </span>
                </Link>
              </li>
              <li>
                <Link
                  href="/praca-pagamento/historico"
                  className={`flex items-center gap-3 px-3 py-2.5 mx-2 lg:ml-6 rounded-xl group transition-all duration-200 ${
                    pathname === '/praca-pagamento/historico'
                      ? 'bg-white/10 text-white shadow-sm'
                      : 'text-white/60 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  <Icon name="history" size={20} className={`transition-transform duration-200 ${pathname === '/praca-pagamento/historico' ? 'text-white scale-110' : 'text-white/60 group-hover:text-white group-hover:scale-110'}`} />
                  <span className={`font-sans text-sm ${pathname === '/praca-pagamento/historico' ? 'font-bold' : 'font-medium'} hidden lg:block`}>
                    Histórico
                  </span>
                </Link>
              </li>
              <li>
                <Link
                  href="/individuals"
                  className={`flex items-center gap-3 px-3 py-3 mx-2 rounded-xl group transition-all duration-200 ${
                    pathname === '/individuals' || pathname.startsWith('/individuals/')
                      ? 'bg-white/10 text-white shadow-sm'
                      : 'text-white/70 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  <Icon name="person_search" className={`transition-transform duration-200 ${pathname === '/individuals' || pathname.startsWith('/individuals/') ? 'text-white scale-110' : 'text-white/70 group-hover:text-white group-hover:scale-110'}`} />
                  <span className={`font-sans ${pathname === '/individuals' || pathname.startsWith('/individuals/') ? 'font-bold' : 'font-medium'} hidden lg:block`}>
                    Pessoas Físicas
                  </span>
                </Link>
              </li>
              <li className="pt-4 mt-4 border-t border-white/10 relative">
                <span className="px-5 text-xs font-sans font-bold text-white/50 uppercase tracking-widest hidden lg:block mb-3">Configurações</span>
                <Link
                  href="/settings"
                  className={`flex items-center gap-3 px-3 py-3 mx-2 rounded-xl group transition-all duration-200 ${
                    pathname === '/settings'
                      ? 'bg-white/10 text-white shadow-sm'
                      : 'text-white/70 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  <Icon name="settings" className={`transition-transform duration-200 ${pathname === '/settings' ? 'text-white scale-110' : 'text-white/70 group-hover:text-white group-hover:scale-110'}`} />
                  <span className={`font-sans ${pathname === '/settings' ? 'font-bold' : 'font-medium'} hidden lg:block`}>Sistema</span>
                </Link>
              </li>
            </ul>
          </div>
        </aside>

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
