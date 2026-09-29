/**
 * Perfis vinculados (sócio, filial, empresa da pessoa) abrem em nova aba,
 * para a consulta de origem continuar aberta onde o analista parou.
 */
export function openInNewTab(path: string) {
  if (typeof window === "undefined") return;
  window.open(path, "_blank", "noopener");
}

/**
 * Para ações que só sabem o destino depois de uma chamada (ex.: criar o perfil).
 * A aba precisa nascer no clique, senão o bloqueador de pop-up barra o window.open
 * feito depois do await. Retorna quem aponta a aba para o destino ou a fecha no erro.
 */
export function reserveNewTab() {
  const win = typeof window === "undefined" ? null : window.open("", "_blank");
  return {
    go(path: string) {
      if (win && !win.closed) win.location.href = path;
      else openInNewTab(path);
    },
    cancel() {
      win?.close();
    },
  };
}
