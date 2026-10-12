"use client";

import { useEffect, useRef } from "react";

// Caixa do Brasil, em graus: o mapa se ajusta a ela.
const OESTE = -74.2;
const LESTE = -34.6;
const NORTE = 5.4;
const SUL = -33.9;
const ACHATAR = Math.cos((-15 * Math.PI) / 180);
/** Sede da JGM: São Paulo, capital. */
const SP = { lat: -23.5505, lng: -46.6333 };

type Estrela = { para: number; inicio: number; duracao: number; curva: number };
type Faisca = { x: number; y: number; vx: number; vy: number; inicio: number };
type Aceso = { em: number; inicio: number };

const LARANJA = "240, 164, 107";
const CLARO = "255, 226, 200";

/**
 * Mapa do Brasil feito dos 5.570 municípios, em pontos. De São Paulo, sempre acesa e pulsando,
 * saem estrelas cadentes para cidades do país inteiro; cada cidade alcançada fica acesa por um
 * tempo, e o mapa vai se iluminando aos poucos.
 */
export default function MapaFomento() {
  const tela = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = tela.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;
    const parado = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let vivo = true;
    let quadro = 0;
    let pontos = new Float32Array(0);
    let xs = new Float32Array(0);
    let ys = new Float32Array(0);
    let spX = 0;
    let spY = 0;
    let fundo: HTMLCanvasElement | null = null;
    let dpr = 1;
    let proxima = 0;
    const estrelas: Estrela[] = [];
    const faiscas: Faisca[] = [];
    const acesos: Aceso[] = [];

    const projetar = () => {
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      const { width, height } = canvas.getBoundingClientRect();
      canvas.width = Math.round(width * dpr);
      canvas.height = Math.round(height * dpr);
      const largura = (LESTE - OESTE) * ACHATAR;
      const altura = NORTE - SUL;
      // No painel grande o título ocupa a base: o mapa fica acima dele.
      const comTitulo = width >= 700;
      const alturaUtil = canvas.height * (comTitulo ? 0.64 : 0.84);
      const escala = Math.min((canvas.width * 0.86) / largura, alturaUtil / altura);
      const sobraX = (canvas.width - largura * escala) / 2;
      const sobraY = comTitulo ? canvas.height * 0.1 : (canvas.height - altura * escala) / 2;
      const x = (lng: number) => sobraX + (lng - OESTE) * ACHATAR * escala;
      const y = (lat: number) => sobraY + (NORTE - lat) * escala;
      const n = pontos.length / 2;
      xs = new Float32Array(n);
      ys = new Float32Array(n);
      for (let i = 0; i < n; i++) {
        xs[i] = x(pontos[i * 2] / 100);
        ys[i] = y(pontos[i * 2 + 1] / 100);
      }
      spX = x(SP.lng);
      spY = y(SP.lat);
      // Os pontos não mudam: desenha uma vez numa camada e só copia a cada quadro.
      fundo = document.createElement("canvas");
      fundo.width = canvas.width;
      fundo.height = canvas.height;
      const f = fundo.getContext("2d")!;
      const lado = 1.5 * dpr;
      f.fillStyle = "rgba(255, 236, 224, 0.26)";
      for (let i = 0; i < n; i++) f.fillRect(xs[i] - lado / 2, ys[i] - lado / 2, lado, lado);
    };

    /** Destino em qualquer canto do país; os distantes têm mais chance, senão tudo fica no Sudeste. */
    const lancar = (agora: number) => {
      const n = xs.length;
      if (!n) return;
      let para = 0;
      for (let t = 0; t < 24; t++) {
        para = Math.floor(Math.random() * n);
        const d = Math.hypot(xs[para] - spX, ys[para] - spY) / dpr;
        if (d > 40 && Math.random() < Math.min(1, d / 320)) break;
      }
      const d = Math.hypot(xs[para] - spX, ys[para] - spY) / dpr;
      estrelas.push({ para, inicio: agora, duracao: 700 + d * 3.2 + Math.random() * 300, curva: 0.18 + Math.random() * 0.22 });
    };

    const posicao = (e: Estrela, t: number): [number, number] => {
      const x2 = xs[e.para], y2 = ys[e.para];
      const d = Math.hypot(x2 - spX, y2 - spY) || 1;
      // Controle na perpendicular, sempre para cima: a estrela "voa" sobre o mapa.
      let nx = -(y2 - spY) / d;
      let ny = (x2 - spX) / d;
      if (ny > 0) {
        nx = -nx;
        ny = -ny;
      }
      const cx = (spX + x2) / 2 + nx * d * e.curva;
      const cy = (spY + y2) / 2 + ny * d * e.curva;
      const u = 1 - t;
      return [u * u * spX + 2 * u * t * cx + t * t * x2, u * u * spY + 2 * u * t * cy + t * t * y2];
    };

    const brilho = (x: number, y: number, raio: number, alfa: number) => {
      const g = ctx.createRadialGradient(x, y, 0, x, y, raio);
      g.addColorStop(0, `rgba(${CLARO}, ${alfa})`);
      g.addColorStop(1, `rgba(${LARANJA}, 0)`);
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(x, y, raio, 0, Math.PI * 2);
      ctx.fill();
    };

    const desenhar = (agora: number) => {
      if (!vivo) return;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      if (fundo) ctx.drawImage(fundo, 0, 0);
      ctx.globalCompositeOperation = "lighter";

      if (!parado && agora >= proxima && estrelas.length < 18) {
        lancar(agora);
        proxima = agora + 110 + Math.random() * 220;
      }

      // Cidades já alcançadas: acesas, apagando devagar.
      for (let i = acesos.length - 1; i >= 0; i--) {
        const a = acesos[i];
        const p = parado ? 0.3 : (agora - a.inicio) / 9000;
        if (p >= 1) {
          acesos.splice(i, 1);
          continue;
        }
        brilho(xs[a.em], ys[a.em], 4 * dpr, 0.75 * (1 - p));
      }

      for (let i = estrelas.length - 1; i >= 0; i--) {
        const e = estrelas[i];
        const p = parado ? 1 : Math.min(1, (agora - e.inicio) / e.duracao);
        // Cauda longa e afinando, como estrela cadente.
        const cauda = Math.max(0, p - 0.45);
        const passos = 30;
        let [px, py] = posicao(e, cauda);
        for (let s = 1; s <= passos; s++) {
          const k = s / passos;
          const [x, y] = posicao(e, cauda + (p - cauda) * k);
          ctx.strokeStyle = `rgba(${k > 0.85 ? CLARO : LARANJA}, ${k * k * (parado ? 0.5 : 0.95)})`;
          ctx.lineWidth = (0.4 + k * 2.2) * dpr;
          ctx.beginPath();
          ctx.moveTo(px, py);
          ctx.lineTo(x, y);
          ctx.stroke();
          px = x;
          py = y;
        }
        if (!parado) {
          brilho(px, py, 8 * dpr, 1);
          if (Math.random() < 0.45 && faiscas.length < 240) {
            faiscas.push({ x: px, y: py, vx: (Math.random() - 0.5) * 0.5 * dpr, vy: (Math.random() - 0.2) * 0.5 * dpr, inicio: agora });
          }
        }
        if (p >= 1) {
          acesos.push({ em: e.para, inicio: agora });
          if (!parado) estrelas.splice(i, 1);
        }
      }

      // Faíscas soltas pela cauda.
      for (let i = faiscas.length - 1; i >= 0; i--) {
        const f = faiscas[i];
        const p = (agora - f.inicio) / 700;
        if (p >= 1) {
          faiscas.splice(i, 1);
          continue;
        }
        f.x += f.vx;
        f.y += f.vy;
        ctx.fillStyle = `rgba(${CLARO}, ${(1 - p) * 0.8})`;
        ctx.fillRect(f.x, f.y, 1.4 * dpr, 1.4 * dpr);
      }

      // São Paulo: núcleo aceso e duas ondas saindo, defasadas.
      for (let onda = 0; onda < 2; onda++) {
        const p = parado ? 0.5 : ((agora / 2600 + onda / 2) % 1);
        ctx.strokeStyle = `rgba(${LARANJA}, ${(1 - p) * 0.8})`;
        ctx.lineWidth = 1.5 * dpr;
        ctx.beginPath();
        ctx.arc(spX, spY, (4 + p * 26) * dpr, 0, Math.PI * 2);
        ctx.stroke();
      }
      brilho(spX, spY, 16 * dpr, 0.9);
      ctx.globalCompositeOperation = "source-over";
      ctx.fillStyle = "#FFF4EA";
      ctx.beginPath();
      ctx.arc(spX, spY, 3 * dpr, 0, Math.PI * 2);
      ctx.fill();
      ctx.font = `600 ${11.5 * dpr}px ui-sans-serif, system-ui, sans-serif`;
      ctx.fillStyle = "rgba(255, 236, 224, 0.8)";
      ctx.fillText("São Paulo", spX + 12 * dpr, spY + 18 * dpr);

      if (!parado) quadro = requestAnimationFrame(desenhar);
    };

    const recomecar = () => {
      estrelas.length = 0;
      faiscas.length = 0;
      acesos.length = 0;
      projetar();
      if (parado) {
        // Sem animação: algumas estrelas já completas, paradas.
        for (let i = 0; i < 12; i++) lancar(0);
        desenhar(0);
      }
    };

    fetch("/login/municipios.json")
      .then(r => r.json())
      .then((lista: number[]) => {
        if (!vivo) return;
        pontos = Float32Array.from(lista);
        recomecar();
        if (!parado) quadro = requestAnimationFrame(desenhar);
      })
      .catch(() => {});

    const observador = new ResizeObserver(() => {
      if (pontos.length) recomecar();
    });
    observador.observe(canvas);

    return () => {
      vivo = false;
      cancelAnimationFrame(quadro);
      observador.disconnect();
    };
  }, []);

  return (
    <canvas
      ref={tela}
      aria-hidden
      className="login-anima absolute inset-0 h-full w-full"
      style={{ animation: "login-surgir 1200ms ease both" }}
    />
  );
}
