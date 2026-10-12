import Image from "next/image";
import type { ReactNode } from "react";
import MapaFomento from "./MapaFomento";

const LINHAS = [
  { texto: "Fomentando", cor: "text-white" },
  { texto: "quem produz.", cor: "text-[#F0A46B]" },
];

/**
 * Moldura do login e do cadastro: à esquerda o bordô da marca com o mapa do fomento saindo de
 * São Paulo; à direita o formulário sobre o papel. No celular o mapa vira uma faixa no topo.
 */
export default function TelaEntrada({ children }: { children: ReactNode }) {
  return (
    <main className="grid min-h-screen bg-papel lg:grid-cols-[1.25fr_1fr]">
      <section className="relative h-[42vh] overflow-hidden bg-[linear-gradient(160deg,#6E263D_0%,#5A1E32_45%,#3A1224_100%)] text-white lg:h-auto">
        <span
          aria-hidden
          className="pointer-events-none absolute -right-[10%] -top-[15%] h-[60%] w-[60%] rounded-full bg-[radial-gradient(circle,rgba(209,115,44,0.35),transparent_65%)] animate-respirar motion-reduce:animate-none"
        />
        <MapaFomento />
        <span aria-hidden className="login-grao pointer-events-none absolute inset-0 opacity-[0.1] mix-blend-overlay" />

        <div className="relative flex h-full flex-col justify-between p-7 lg:p-12 xl:p-14">
          <div className="flex items-center gap-1">
            {/* O PNG tem muita margem; object-cover recorta o vazio em volta das letras. */}
            <Image src="/logo-dark.png" alt="JGM" width={110} height={38} priority className="h-[38px] w-[110px] object-cover" />
            <span className="font-display text-[23px] font-semibold tracking-[-0.01em]">Fomento</span>
          </div>
          <div className="hidden lg:block">
            <h2 className="font-display text-[clamp(44px,4.6vw,76px)] font-semibold leading-[1] tracking-[-0.03em]">
              {LINHAS.map((linha, i) => (
                <span key={linha.texto} className="block overflow-hidden pb-[0.06em]">
                  <span
                    className={`login-anima block ${linha.cor}`}
                    style={{ animation: `login-revelar 900ms cubic-bezier(0.2,0.8,0.2,1) ${300 + i * 160}ms both` }}
                  >
                    {linha.texto}
                  </span>
                </span>
              ))}
            </h2>
            <p
              className="login-anima mt-5 text-[17px] font-medium text-white/70"
              style={{ animation: "login-surgir 900ms ease 900ms both" }}
            >
              JGM Empresarial 40 anos.
            </p>
          </div>
        </div>
      </section>

      <section className="flex items-center justify-center px-6 py-10 sm:px-10">
        <div className="w-full max-w-[420px] animate-subir motion-reduce:animate-none">{children}</div>
      </section>
    </main>
  );
}
