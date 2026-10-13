"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import Icon from "@/components/ui/Icon";
import { AUTH_API_BASE_URL } from "./comum";
import { CAMPO } from "./FormularioLogin";


/** Cadastro de conta, igual ao de antes: cria e volta para o login depois de 2 segundos. */
export default function FormularioCadastro() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [verSenha, setVerSenha] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const handleRegister = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    setSuccess(false);

    try {
      const res = await fetch(`${AUTH_API_BASE_URL}/api/auth/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, email, password }),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.error || "Falha ao registrar");
      }

      setSuccess(true);
      setTimeout(() => router.push("/login"), 2000);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Erro ao conectar com o servidor");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full">
      <h1 className="font-display text-[34px] font-semibold leading-[1.05] tracking-[-0.02em] text-grafite">Criar conta</h1>
      <p className="mt-2 text-[15px] text-suave">Cadastre-se para acessar a análise de crédito.</p>

      <form className="mt-8 flex flex-col gap-4" onSubmit={handleRegister}>
        {error && (
          <p role="alert" className="flex items-start gap-2.5 rounded-[14px] bg-[#F8E1E6] px-3.5 py-3 text-[13.5px] text-[#8E1B37] animate-cair motion-reduce:animate-none">
            <Icon name="error_outline" size={19} className="mt-px shrink-0" />
            {error}
          </p>
        )}
        {success && (
          <p role="status" className="flex items-start gap-2.5 rounded-[14px] bg-[#E2F0E9] px-3.5 py-3 text-[13.5px] text-[#17583F] animate-cair motion-reduce:animate-none">
            <Icon name="check_circle" size={19} className="mt-px shrink-0" />
            Conta criada com sucesso! Redirecionando…
          </p>
        )}

        <label className="block">
          <span className="mb-1.5 block text-[13px] font-semibold text-grafite">Nome completo</span>
          <span className="relative block">
            <Icon name="person" size={19} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#8A877E]" />
            <input
              type="text"
              autoComplete="name"
              value={name}
              onChange={e => setName(e.target.value)}
              required
              placeholder="Maria Almeida Silva"
              className={CAMPO}
            />
          </span>
        </label>

        <label className="block">
          <span className="mb-1.5 block text-[13px] font-semibold text-grafite">E-mail corporativo</span>
          <span className="relative block">
            <Icon name="mail" size={19} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#8A877E]" />
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              required
              placeholder="maria@suaempresa.com.br"
              className={CAMPO}
            />
          </span>
        </label>

        <label className="block">
          <span className="mb-1.5 block text-[13px] font-semibold text-grafite">Senha</span>
          <span className="relative block">
            <Icon name="lock" size={19} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#8A877E]" />
            <input
              type={verSenha ? "text" : "password"}
              autoComplete="new-password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              required
              placeholder="••••••••"
              className={`${CAMPO} pr-12`}
            />
            <button
              type="button"
              onClick={() => setVerSenha(v => !v)}
              aria-label={verSenha ? "Esconder senha" : "Mostrar senha"}
              className="absolute right-1.5 top-1/2 grid h-10 w-10 -translate-y-1/2 place-items-center rounded-xl text-[#8A877E] transition-colors hover:bg-[#F4F2EC] hover:text-grafite"
            >
              <Icon name={verSenha ? "visibility_off" : "visibility"} size={19} />
            </button>
          </span>
        </label>

        <button
          type="submit"
          disabled={loading || success}
          className="group relative mt-2 inline-flex h-[52px] items-center justify-center gap-2 overflow-hidden rounded-[14px] bg-primary text-[15px] font-semibold text-white shadow-[0_14px_30px_-12px_rgba(97,32,53,0.65)] transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-[0_18px_36px_-12px_rgba(97,32,53,0.75)] active:translate-y-0 disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0"
        >
          <span
            aria-hidden
            className="pointer-events-none absolute inset-y-0 -left-1/2 w-1/2 -skew-x-12 bg-gradient-to-r from-transparent via-white/25 to-transparent transition-transform duration-700 group-hover:translate-x-[300%] motion-reduce:hidden"
          />
          {loading ? (
            <>
              <Icon name="progress_activity" size={18} weight="bold" className="animate-spin" />
              Cadastrando…
            </>
          ) : (
            <>
              Criar conta
              <Icon name="arrow_forward" size={18} className="transition-transform duration-200 group-hover:translate-x-1" />
            </>
          )}
        </button>
      </form>

      <p className="mt-7 text-center text-[13.5px] text-suave">
        Já possui uma conta?{" "}
        <Link href="/login" className="font-bold text-primary underline decoration-primary/30 underline-offset-4 hover:decoration-primary">
          Fazer login
        </Link>
      </p>
    </div>
  );
}
