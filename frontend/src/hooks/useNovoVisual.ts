import { useCallback, useSyncExternalStore } from "react";

const CHAVE = "jgm_novo_visual";
/** O evento "storage" só chega às outras abas; este avisa a própria aba que trocou. */
const EVENTO = "jgm-novo-visual";

function ler() {
  try {
    return localStorage.getItem(CHAVE) === "1";
  } catch {
    return false;
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
 * Interruptor "Novo visual" da repaginação, por usuário e por navegador. Desligado por padrão;
 * some quando a casca nova for aprovada e a antiga apagada.
 */
export function useNovoVisual(): [boolean, (ligado: boolean) => void] {
  const ligado = useSyncExternalStore(assinar, ler, () => false);
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
