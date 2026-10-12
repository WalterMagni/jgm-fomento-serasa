import { useCallback, useSyncExternalStore } from "react";

const CHAVE = "jgm_novo_visual";
/** O evento "storage" só chega às outras abas; este avisa a própria aba que trocou. */
const EVENTO = "jgm-novo-visual";

/** Ligado por padrão desde 2026-10-12; só fica desligado para quem desligou de propósito. */
function ler() {
  try {
    return localStorage.getItem(CHAVE) !== "0";
  } catch {
    return true;
  }
}

function assinar(avisar: () => void) {
  window.addEventListener("storage", avisar);
  window.addEventListener(EVENTO, avisar);
  return () => {
    window.removeEventListener("storage", avisar);
    window.removeEventListener(EVENTO, avisar);
  };
}

/**
 * Interruptor "Novo visual" da repaginação, por usuário e por navegador. Ligado por padrão;
 * some quando a casca antiga for apagada.
 */
export function useNovoVisual(): [boolean, (ligado: boolean) => void] {
  const ligado = useSyncExternalStore(assinar, ler, () => true);
  const definir = useCallback((valor: boolean) => {
    try {
      localStorage.setItem(CHAVE, valor ? "1" : "0");
    } catch {
      // Sem armazenamento (aba privada restrita): o interruptor só não persiste.
    }
    window.dispatchEvent(new Event(EVENTO));
  }, []);
  return [ligado, definir];
}
