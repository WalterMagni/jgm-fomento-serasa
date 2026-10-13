"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import Icon from "@/components/ui/Icon";
import { CAMPO } from "./FormularioLogin";
import { AUTH_API_BASE_URL, BOTAO_ENTRADA } from "./comum";

const MINIMO = 8;

type Estado = "conferindo" | "valido" | "invalido";

function Requisito({ ok, children }: { ok: boolean; children: string }) {
  return (
    <li className={`flex items-center gap-2 text-[13px] transition-colors ${ok ? "text-ok" : "text-suave"}`}>
      <Icon name={ok ? "check_circle" : "radio_button_unchecked"} size={16} weight={ok ? undefined : "regular"} />
      {children}
    </li>
  );
}

/**
 * Cria a senha nova a partir do link do e-mail. Confere o link antes de mostrar o formulário;
 * vencido ou já usado, oferece pedir outro. Depois de trocar, volta ao login.
 */
export default function FormularioRedefinir() {
  const router = useRouter();
  const token = useSearchParams().get("token") ?? "";
  const [estado, setEstado] = useState<Estado>(token ? "conferindo" : "invalido");
  const [senha, setSenha] = useState("");
  const [confirmacao, setConfirmacao] = useState("");
  const [verSenha, setVerSenha] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    let vivo = true;
    fetch(`${AUTH_API_BASE_URL}/api/auth/senha/validar`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token }),
    })
      .then(r => (r.ok ? r.json() : { valido: false }))
      .then(d => vivo && setEstado(d.valido ? "valido" : "invalido"))
      .catch(() => vivo && setEstado("invalido"));
    return () => {
      vivo = false;
    };
  }, [token]);

  const longaOk = senha.length >= MINIMO;
  const iguais = senha.length > 0 && senha === confirmacao;

  const salvar = async (e: FormEvent) => {
    e.preventDefault();
    if (!longaOk || !iguais) return;
    setErro(null);
    setSalvando(true);
    try {
      const res = await fetch(`${AUTH_API_BASE_URL}/api/auth/senha/redefinir`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, senha }),
      });
      const dados = await res.json().catch(() => ({}));
      if (!res.ok) {
        const mensagem: string = dados.error || "Não foi possível redefinir a senha.";
        if (/expirou|usado/i.test(mensagem)) setEstado("invalido");
        throw new Error(mensagem);
      }
      // O login guardado neste navegador era da senha antiga e não vale mais.
      localStorage.removeItem("serasa_token");
      router.replace("/login?senha=redefinida");
    } catch (err: unknown) {
      setErro(err instanceof Error ? err.message : "Erro ao conectar com o servidor");
    } finally {
      setSalvando(false);
    }
  };

  if (estado === "conferindo") {
    return (
      <div className="flex items-center gap-3 text-[15px] text-suave">
        <Icon name="progress_activity" size={20} weight="bold" className="animate-spin text-primary" />
        Conferindo o link…
      </div>
    );
  }

  if (estado === "invalido") {
    return (
      <div className="w-full">
        <span className="grid h-14 w-14 place-items-center rounded-[18px] bg-[#F8E1E6] text-[#8E1B37]">
          <Icon name="schedule" size={28} />
        </span>
        <h1 className="mt-6 font-display text-[34px] font-semibold leading-[1.05] tracking-[-0.02em] text-grafite">Link expirado</h1>
        <p className="mt-3 text-[15px] leading-relaxed text-suave">
          Este link já foi usado ou passou dos 30 minutos. Por segurança, cada link vale uma vez só. Peça um novo para continuar.
        </p>
        <div className="mt-8 flex flex-col gap-2.5">
          <Link href="/esqueci-senha" className={BOTAO_ENTRADA}>
            Pedir um link novo
          </Link>
          <Link href="/login" className="grid h-11 place-items-center text-[13.5px] font-bold text-primary hover:underline">
            Voltar para o login
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full">
      <h1 className="font-display text-[34px] font-semibold leading-[1.05] tracking-[-0.02em] text-grafite">Criar senha nova</h1>
      <p className="mt-2 text-[15px] text-suave">Ao salvar, os logins abertos em outros computadores são encerrados.</p>

      <form className="mt-8 flex flex-col gap-4" onSubmit={salvar}>
        {erro && (
          <p role="alert" className="flex items-start gap-2.5 rounded-[14px] bg-[#F8E1E6] px-3.5 py-3 text-[13.5px] text-[#8E1B37] animate-cair motion-reduce:animate-none">
            <Icon name="error_outline" size={19} className="mt-px shrink-0" />
            {erro}
          </p>
        )}

        <label className="block">
          <span className="mb-1.5 block text-[13px] font-semibold text-grafite">Senha nova</span>
          <span className="relative block">
            <Icon name="lock" size={19} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#8A877E]" />
            <input
              type={verSenha ? "text" : "password"}
              autoComplete="new-password"
              value={senha}
              onChange={e => setSenha(e.target.value)}
              required
              autoFocus
              maxLength={72}
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

        <label className="block">
          <span className="mb-1.5 block text-[13px] font-semibold text-grafite">Repita a senha nova</span>
          <span className="relative block">
            <Icon name="lock" size={19} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#8A877E]" />
            <input
              type={verSenha ? "text" : "password"}
              autoComplete="new-password"
              value={confirmacao}
              onChange={e => setConfirmacao(e.target.value)}
              required
              maxLength={72}
              className={CAMPO}
            />
          </span>
        </label>

        <ul className="flex flex-col gap-1.5" aria-live="polite">
          <Requisito ok={longaOk}>{`Pelo menos ${MINIMO} caracteres`}</Requisito>
          <Requisito ok={iguais}>As duas senhas iguais</Requisito>
        </ul>

        <button type="submit" disabled={salvando || !longaOk || !iguais} className={`mt-1 ${BOTAO_ENTRADA}`}>
          {salvando ? (
            <>
              <Icon name="progress_activity" size={18} weight="bold" className="animate-spin" />
              Salvando…
            </>
          ) : (
            <>
              Salvar senha nova
              <Icon name="arrow_forward" size={18} className="transition-transform duration-200 group-hover:translate-x-1" />
            </>
          )}
        </button>
      </form>
    </div>
  );
}
