/**
 * Toque do sino, gerado no navegador — sem arquivo de áudio para servir nem para o antivírus da
 * rede bloquear.
 *
 * <p>O navegador só libera áudio depois de um clique ou tecla na página. O contexto é criado no
 * primeiro gesto; antes disso a notificação chega muda, mas chega (sino e toast continuam).</p>
 */

let contexto: AudioContext | null = null;
let ultimoToque = 0;
const INTERVALO_MINIMO_MS = 3000;

function criarContexto() {
  if (contexto) return contexto;
  const Construtor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Construtor) return null;
  contexto = new Construtor();
  return contexto;
}

/** Chamado uma vez, ao montar o provider: espera o primeiro gesto para liberar o áudio. */
export function prepararSom() {
  const liberar = () => {
    const ctx = criarContexto();
    ctx?.resume().catch(() => undefined);
  };
  window.addEventListener("pointerdown", liberar, { once: true });
  window.addEventListener("keydown", liberar, { once: true });
  return () => {
    window.removeEventListener("pointerdown", liberar);
    window.removeEventListener("keydown", liberar);
  };
}

/** Duas notas curtas, subindo (Lá 5 → Mi 6). Uma vez a cada 3 s no máximo, para rajada não virar sirene. */
export function tocarSino() {
  const agora = Date.now();
  if (agora - ultimoToque < INTERVALO_MINIMO_MS) return;
  const ctx = criarContexto();
  if (!ctx || ctx.state !== "running") return;
  ultimoToque = agora;

  const inicio = ctx.currentTime;
  [
    { frequencia: 880, atraso: 0 },
    { frequencia: 1318.5, atraso: 0.11 },
  ].forEach(({ frequencia, atraso }) => {
    const oscilador = ctx.createOscillator();
    const volume = ctx.createGain();
    oscilador.type = "sine";
    oscilador.frequency.value = frequencia;
    const t0 = inicio + atraso;
    volume.gain.setValueAtTime(0.0001, t0);
    volume.gain.exponentialRampToValueAtTime(0.14, t0 + 0.015);
    volume.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.38);
    oscilador.connect(volume).connect(ctx.destination);
    oscilador.start(t0);
    oscilador.stop(t0 + 0.4);
  });
}

/**
 * Com o portal aberto em várias abas, só uma toca.
 *
 * <p>A aba visível reivindica na hora; as escondidas esperam um pouco antes de tentar, então a
 * visível ganha quando existe. A trava fica no localStorage por 10 s, o bastante para as outras
 * abas verem que o aviso já foi dado.</p>
 */
export function reivindicarAviso(id: string): Promise<boolean> {
  const espera = document.visibilityState === "visible" ? 0 : 250 + Math.random() * 200;
  return new Promise(resolver => {
    setTimeout(() => {
      try {
        const chave = `notificacao-avisada:${id}`;
        if (localStorage.getItem(chave)) return resolver(false);
        localStorage.setItem(chave, String(Date.now()));
        setTimeout(() => {
          try {
            localStorage.removeItem(chave);
          } catch {
            // sem storage, nada a limpar
          }
        }, 10000);
        resolver(true);
      } catch {
        // Storage bloqueado: melhor tocar em duas abas do que em nenhuma.
        resolver(true);
      }
    }, espera);
  });
}
