"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import Icon from "@/components/ui/Icon";
import { CAMPO } from "./FormularioLogin";
import { AUTH_API_BASE_URL, BOTAO_ENTRADA } from "./comum";

/**
 * Pede o link de redefinição. A resposta é sempre "confira seu e-mail", tenha conta ou não: o
 * servidor não conta quem está cadastrado.
 */
export default function FormularioEsqueci() {
  const params = useSearchParams();
  const [email, setEmail] = useState(() => params.get("email") ?? "");
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const pedir = async (e: FormEvent) => {
    e.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      const res = await fetch(`${AUTH_API_BASE_URL}/api/auth/senha/esqueci`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim() }),
      });
      if (!res.ok) throw new Error();
      setEnviado(true);
    } catch {
      setErro("Não foi possível falar com o servidor agora. Tente de novo em instantes.");
    } finally {
      setEnviando(false);
    }
  };

  if (enviado) {
    return (
      <div className="w-full">
        <span className="grid h-14 w-14 place-items-center rounded-[18px] bg-primary/10 text-primary animate-surgir motion-reduce:animate-none">
          <Icon name="mail" size={28} />
        </span>
        <h1 className="mt-6 font-display text-[34px] font-semibold leading-[1.05] tracking-[-0.02em] text-grafite">Confira seu e-mail</h1>
        <p className="mt-3 text-[15px] leading-relaxed text-suave">
          Se <b className="text-grafite">{email.trim()}</b> estiver cadastrado, você vai receber um link para criar uma senha nova. Ele
          vale por <b className="text-grafite">30 minutos</b> e só pode ser usado uma vez.
        </p>
        <p className="mt-4 rounded-[14px] bg-[#F4F2EC] px-3.5 py-3 text-[13.5px] text-[#5E5C55]">
          Não chegou em alguns minutos? Veja a caixa de spam ou peça de novo.
        </p>
        <div className="mt-8 flex flex-col gap-2.5">
          <Link href="/login" className={BOTAO_ENTRADA}>
            Voltar para o login
          </Link>
          <button type="button" onClick={() => setEnviado(false)} className="h-11 text-[13.5px] font-bold text-primary hover:underline">
            Enviar de novo
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full">
      <h1 className="font-display text-[34px] font-semibold leading-[1.05] tracking-[-0.02em] text-grafite">Esqueceu a senha?</h1>
      <p className="mt-2 text-[15px] text-suave">Digite o e-mail da sua conta. Enviamos um link para criar uma senha nova.</p>

      <form className="mt-8 flex flex-col gap-4" onSubmit={pedir}>
        {erro && (
          <p role="alert" className="flex items-start gap-2.5 rounded-[14px] bg-[#F8E1E6] px-3.5 py-3 text-[13.5px] text-[#8E1B37] animate-cair motion-reduce:animate-none">
            <Icon name="error_outline" size={19} className="mt-px shrink-0" />
            {erro}
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
              autoFocus
              placeholder="seu.nome@empresa.com.br"
              className={CAMPO}
            />
          </span>
        </label>
        <button type="submit" disabled={enviando} className={`mt-1 ${BOTAO_ENTRADA}`}>
          {enviando ? (
            <>
              <Icon name="progress_activity" size={18} weight="bold" className="animate-spin" />
              Enviando…
            </>
          ) : (
            <>
              Enviar link
              <Icon name="arrow_forward" size={18} className="transition-transform duration-200 group-hover:translate-x-1" />
            </>
          )}
        </button>
      </form>

      <p className="mt-7 text-center text-[13.5px] text-suave">
        Lembrou a senha?{" "}
        <Link href="/login" className="font-bold text-primary underline decoration-primary/30 underline-offset-4 hover:decoration-primary">
          Voltar para o login
        </Link>
      </p>
    </div>
  );
}
