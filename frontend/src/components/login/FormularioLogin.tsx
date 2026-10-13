"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import Icon from "@/components/ui/Icon";
import { AUTH_API_BASE_URL } from "./comum";


export const CAMPO =
  "h-[50px] w-full rounded-[14px] border border-linha bg-white pl-11 pr-4 text-[14.5px] text-grafite outline-none transition-[border-color,box-shadow] placeholder:text-[#A29E93] focus:border-primary focus:ring-4 focus:ring-primary/10";

/**
 * Formulário de entrada. Guarda token, nome, e-mail e a preferência de e-mail da Visão Cedente,
 * como antes, e leva para a Carteira. Sem "lembrar-me" de propósito: com dados sensíveis, a sessão
 * sempre vence em 8h e pede login de novo.
 */
/** Erro do login, ou o motivo de ter caído aqui: sessão vencida, senha trocada, senha redefinida. */
function avisoDaTela(erro: string | null, params: URLSearchParams): { tom: "erro" | "ok"; texto: string } | null {
  if (erro) return { tom: "erro", texto: erro };
  if (params.get("senha") === "redefinida") return { tom: "ok", texto: "Senha redefinida. Entre com a senha nova." };
  if (params.get("sessao") === "encerrada")
    return { tom: "erro", texto: "Sua sessão foi encerrada porque a senha da conta foi alterada. Entre com a senha nova." };
  if (params.get("expired")) return { tom: "erro", texto: "Sua sessão expirou (limite de 8h). Faça login novamente." };
  return null;
}

export default function FormularioLogin() {
  const router = useRouter();
  const params = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [verSenha, setVerSenha] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const aviso = avisoDaTela(error, params);

  const handleLogin = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const res = await fetch(`${AUTH_API_BASE_URL}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.error || "Falha na autenticação");
      }

      const data = await res.json();
      if (!data.token) throw new Error("Token não recebido");

      localStorage.setItem("serasa_token", data.token);
      if (data.name) localStorage.setItem("serasa_user_name", data.name);
      localStorage.setItem("serasa_user_email", email);
      localStorage.setItem(
        "serasa_email_notificacao",
        typeof data.emailNotificacaoCedente === "boolean" ? String(data.emailNotificacaoCedente) : "true",
      );
      router.push("/");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Erro ao conectar com o servidor");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full">
      <h1 className="font-display text-[34px] font-semibold leading-[1.05] tracking-[-0.02em] text-grafite">Bem-vindo de volta</h1>
      <p className="mt-2 text-[15px] text-suave">Entre para acessar a análise de crédito.</p>

      <form className="mt-8 flex flex-col gap-4" onSubmit={handleLogin}>
        {aviso && (
          <p
            role={aviso.tom === "erro" ? "alert" : "status"}
            className={`flex items-start gap-2.5 rounded-[14px] px-3.5 py-3 text-[13.5px] animate-cair motion-reduce:animate-none ${
              aviso.tom === "erro" ? "bg-[#F8E1E6] text-[#8E1B37]" : "bg-[#E2F0E9] text-[#17583F]"
            }`}
          >
            <Icon name={aviso.tom === "erro" ? "error_outline" : "check_circle"} size={19} className="mt-px shrink-0" />
            {aviso.texto}
          </p>
        )}

        <label className="block">
          <span className="mb-1.5 block text-[13px] font-semibold text-grafite">E-mail</span>
          <span className="relative block">
            <Icon name="mail" size={19} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#8A877E]" />
            <input
              type="email"
              autoComplete="username"
              value={email}
              onChange={e => setEmail(e.target.value)}
              required
              placeholder="seu.nome@empresa.com.br"
              className={CAMPO}
            />
          </span>
        </label>

        <div>
          <div className="mb-1.5 flex items-center justify-between text-[13px] font-semibold text-grafite">
            <label htmlFor="senha-login">Senha</label>
            {/* Leva o e-mail já digitado, para não pedir de novo. */}
            <Link
              href={`/esqueci-senha${email.trim() ? `?email=${encodeURIComponent(email.trim())}` : ""}`}
              className="font-semibold text-primary hover:underline"
            >
              Esqueceu a senha?
            </Link>
          </div>
          <span className="relative block">
            <Icon name="lock" size={19} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#8A877E]" />
            <input
              id="senha-login"
              type={verSenha ? "text" : "password"}
              autoComplete="current-password"
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
        </div>

        <button
          type="submit"
          disabled={loading}
          className="group relative mt-1 inline-flex h-[52px] items-center justify-center gap-2 overflow-hidden rounded-[14px] bg-primary text-[15px] font-semibold text-white shadow-[0_14px_30px_-12px_rgba(97,32,53,0.65)] transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-[0_18px_36px_-12px_rgba(97,32,53,0.75)] active:translate-y-0 disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0"
        >
          {/* Brilho que atravessa o botão ao passar o mouse. */}
          <span
            aria-hidden
            className="pointer-events-none absolute inset-y-0 -left-1/2 w-1/2 -skew-x-12 bg-gradient-to-r from-transparent via-white/25 to-transparent transition-transform duration-700 group-hover:translate-x-[300%] motion-reduce:hidden"
          />
          {loading ? (
            <>
              <Icon name="progress_activity" size={18} weight="bold" className="animate-spin" />
              Entrando…
            </>
          ) : (
            <>
              Entrar
              <Icon name="arrow_forward" size={18} className="transition-transform duration-200 group-hover:translate-x-1" />
            </>
          )}
        </button>
      </form>

      <p className="mt-7 text-center text-[13.5px] text-suave">
        Não possui uma conta?{" "}
        <Link href="/register" className="font-bold text-primary underline decoration-primary/30 underline-offset-4 hover:decoration-primary">
          Criar conta
        </Link>
      </p>
    </div>
  );
}
