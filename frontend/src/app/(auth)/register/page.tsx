"use client";

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

const AUTH_API_BASE_URL =
  (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1').replace(/\/api\/v1\/?$/, '');

export default function RegisterPage() {
  const router = useRouter();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    setSuccess(false);

    try {
      const res = await fetch(`${AUTH_API_BASE_URL}/api/auth/register`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ name, email, password }),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.error || 'Falha ao registrar');
      }

      setSuccess(true);
      setTimeout(() => {
        router.push('/login');
      }, 2000);

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
          <p className="text-sm font-sans text-gray-500 dark:text-gray-400 mt-3 text-center">Crie sua conta para acessar o sistema</p>
        </div>
        
        <form className="space-y-4" onSubmit={handleRegister}>
          {error && (
            <div className="p-3 bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-800 rounded-lg text-sm font-sans">
              {error}
            </div>
          )}
          {success && (
            <div className="p-3 bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 border border-green-200 dark:border-green-800 rounded-lg text-sm font-sans">
              Conta criada com sucesso! Redirecionando...
            </div>
          )}

          <div>
            <label className="mb-2 block text-sm font-sans font-semibold text-grafite dark:text-gray-300">Nome Completo</label>
            <input 
              type="text" 
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800/50 py-3.5 px-4 text-sm text-grafite placeholder-gray-400 focus:border-primary focus:ring-2 focus:ring-primary/20 dark:text-white outline-none transition-all duration-200 shadow-sm"
              placeholder="Maria Almeida Silva"
            />
          </div>
          <div>
            <label className="mb-2 block text-sm font-sans font-semibold text-grafite dark:text-gray-300">E-mail Corporativo</label>
            <input 
              type="email" 
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800/50 py-3.5 px-4 text-sm text-grafite placeholder-gray-400 focus:border-primary focus:ring-2 focus:ring-primary/20 dark:text-white outline-none transition-all duration-200 shadow-sm"
              placeholder="maria@suaempresa.com.br"
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
          
          <button 
            type="submit" 
            disabled={loading || success}
            className="w-full mt-6 rounded-xl bg-primary py-3.5 font-sans font-semibold text-white transition-all duration-200 hover:bg-primary-hover hover:shadow-lg hover:-translate-y-0.5 active:translate-y-0 active:shadow-sm disabled:opacity-70 disabled:hover:translate-y-0 disabled:cursor-not-allowed shadow-md"
          >
            {loading ? 'Cadastrando...' : 'Criar Conta'}
          </button>
        </form>
        
        <div className="mt-8 text-center text-sm text-gray-500 dark:text-gray-400 font-sans">
          Já possui uma conta?{' '}
          <Link href="/login" className="font-bold text-primary hover:text-primary-hover transition-colors underline decoration-primary/30 underline-offset-4 hover:decoration-primary">
            Fazer login
          </Link>
        </div>
      </div>
    </div>
  );
}
