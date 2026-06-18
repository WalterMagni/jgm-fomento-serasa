"use client";

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

const AUTH_API_BASE_URL =
  (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1').replace(/\/api\/v1\/?$/, '');

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('expired')) {
      setError('Sua sessão expirou (limite de 8h). Faça login novamente.');
    }
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const res = await fetch(`${AUTH_API_BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, password }),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.error || 'Falha na autenticação');
      }

      const data = await res.json();
      if (data.token) {
        // Saving the token and user name
        localStorage.setItem('serasa_token', data.token);
        if (data.name) {
           localStorage.setItem('serasa_user_name', data.name);
        }
        localStorage.setItem('serasa_user_email', email);
        if (typeof data.emailNotificacaoCedente === 'boolean') {
          localStorage.setItem('serasa_email_notificacao', String(data.emailNotificacaoCedente));
        } else {
          localStorage.setItem('serasa_email_notificacao', 'true');
        }
        
        // Navigate to dashboard
        router.push('/');
      } else {
        throw new Error('Token não recebido');
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Erro ao conectar com o servidor');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex h-screen w-full items-center justify-center bg-background-light dark:bg-background-dark relative overflow-hidden">
      {/* Premium Background Elements */}
      <div className="absolute top-[-10%] left-[-10%] w-96 h-96 bg-primary/20 dark:bg-primary/30 rounded-full blur-3xl opacity-50 pointer-events-none mix-blend-multiply dark:mix-blend-screen" />
      <div className="absolute bottom-[-10%] right-[-10%] w-96 h-96 bg-secondary/20 dark:bg-secondary/30 rounded-full blur-3xl opacity-50 pointer-events-none mix-blend-multiply dark:mix-blend-screen" />

      <div className="relative w-full max-w-md rounded-2xl bg-white/80 dark:bg-surface-dark/90 p-10 shadow-xl border border-white/40 dark:border-border-dark backdrop-blur-xl transition-all duration-300">
        <div className="flex flex-col items-center mb-10">
          <img src="/logo-light.png" alt="JGM Fomento" className="h-14 w-auto dark:hidden object-contain mb-6 drop-shadow-sm" />
          <img src="/logo-dark.png" alt="JGM Fomento" className="h-14 w-auto hidden dark:block object-contain mb-6 drop-shadow-sm" />
          <h1 className="text-4xl font-heading font-bold text-primary dark:text-white tracking-tight"><span className="font-light">Fomento</span></h1>
          <p className="text-sm font-sans text-gray-500 dark:text-gray-400 mt-3 text-center">Acesse o sistema de Análise de Crédito</p>
        </div>
        
        <form className="space-y-4" onSubmit={handleLogin}>
          {error && (
            <div className="p-3 bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-800 rounded-lg text-sm font-sans">
              {error}
            </div>
          )}
          
          <div>
            <label className="mb-2 block text-sm font-sans font-semibold text-grafite dark:text-gray-300">E-mail</label>
            <input 
              type="email" 
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800/50 py-3.5 px-4 text-sm text-grafite placeholder-gray-400 focus:border-primary focus:ring-2 focus:ring-primary/20 dark:text-white outline-none transition-all duration-200 shadow-sm"
              placeholder="seu.nome@empresa.com.br"
            />
          </div>
          <div>
            <label className="mb-2 block text-sm font-sans font-semibold text-grafite dark:text-gray-300">Senha</label>
            <input 
              type="password" 
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800/50 py-3.5 px-4 text-sm text-grafite placeholder-gray-400 focus:border-primary focus:ring-2 focus:ring-primary/20 dark:text-white outline-none transition-all duration-200 shadow-sm"
              placeholder="••••••••"
            />
          </div>
          
          <div className="flex items-center justify-between text-sm font-sans pt-2">
            <label className="flex items-center space-x-2 cursor-pointer group">
              <input type="checkbox" className="rounded border-gray-300 dark:border-gray-600 text-primary focus:ring-primary/50 transition-colors" />
              <span className="text-gray-600 dark:text-gray-400 group-hover:text-grafite dark:group-hover:text-gray-300 transition-colors">Lembrar-me</span>
            </label>
            <a href="#" className="font-semibold text-primary hover:text-primary-hover transition-colors">Esqueceu a senha?</a>
          </div>
          
          <button 
            type="submit" 
            disabled={loading}
            className="w-full mt-2 rounded-xl bg-primary py-3.5 font-sans font-semibold text-white transition-all duration-200 hover:bg-primary-hover hover:shadow-lg hover:-translate-y-0.5 active:translate-y-0 active:shadow-sm disabled:opacity-70 disabled:hover:translate-y-0 disabled:cursor-not-allowed shadow-md"
          >
            {loading ? 'Entrando...' : 'Entrar'}
          </button>
        </form>
        
        <div className="mt-8 text-center text-sm text-gray-500 dark:text-gray-400 font-sans">
          Não possui uma conta?{' '}
          <Link href="/register" className="font-bold text-primary hover:text-primary-hover transition-colors underline decoration-primary/30 underline-offset-4 hover:decoration-primary">
            Criar conta
          </Link>
        </div>
      </div>
    </div>
  );
}
