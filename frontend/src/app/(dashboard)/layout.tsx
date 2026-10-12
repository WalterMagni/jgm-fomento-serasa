"use client";

import { usePathname, useRouter } from "next/navigation";
import { ReactNode, useState, useEffect } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";
import NotificacoesProvider from "@/components/notificacoes/NotificacoesProvider";
import CascaNova from "./CascaNova";

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

/**
 * Sessão, dados e notificações de todas as páginas logadas, dentro da casca (menu e topo).
 */
export default function DashboardLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();

  const [userName, setUserName] = useState<string>("Usuário");
  const [userInitials, setUserInitials] = useState<string>("US");

  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: { queries: { staleTime: 60 * 1000 } },
  }));

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

  const logout = () => {
    localStorage.removeItem('serasa_token');
    router.push('/login');
  };

  return (
    // O provider envolve a casca inteira, não só o conteúdo: o menu também consulta a API,
    // para o contador das esteiras aparecer.
    <QueryClientProvider client={queryClient}>
    {/* Canal de notificações aberto enquanto o portal estiver aberto, em qualquer página. */}
    <NotificacoesProvider>
      <CascaNova pathname={pathname} userName={userName} userInitials={userInitials} onSair={logout}>
        {children}
      </CascaNova>
      {/* Fora do <main> (z-0 + overflow) para a notificação não ficar presa atrás do topo. */}
      <Toaster richColors position="top-right" offset={80} toastOptions={{ style: { zIndex: 9999 } }} />
    </NotificacoesProvider>
    </QueryClientProvider>
  );
}
