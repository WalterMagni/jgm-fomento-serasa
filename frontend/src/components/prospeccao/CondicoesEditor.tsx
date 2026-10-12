"use client";

import Icon from "@/components/ui/Icon";
import { CAMPO } from "@/components/liberacao/Dialogo";
import { useProdutos } from "@/hooks/useProspeccao";
import InputMoeda from "./InputMoeda";
import { formatMoeda } from "./formatters";

/** Linha em edição: o valor pode estar vazio enquanto a analista digita. */
export type LinhaCondicao = { chave: number; produto: string; valor: number | null };

let proximaChave = 1;
export function novaLinha(produto = "", valor: number | null = null): LinhaCondicao {
  return { chave: proximaChave++, produto, valor };
}

/** Linhas completas, no formato que o servidor recebe. */
export function condicoesValidas(linhas: LinhaCondicao[]) {
  return linhas
    .filter(linha => linha.produto.trim() && linha.valor != null && linha.valor > 0)
    .map(linha => ({ produto: linha.produto.trim(), valor: linha.valor as number }));
}

/**
 * Condições de crédito: produto do catálogo e valor, quantas linhas forem.
 *
 * <p>Mostra a soma ao lado do limite e avisa quando passa, sem bloquear: o limite é teto
 * informativo e a casa às vezes aprova condições que, somadas, ultrapassam o teto global.</p>
 */
export default function CondicoesEditor({
  linhas,
  onMudar,
  limite,
}: {
  linhas: LinhaCondicao[];
  onMudar: (linhas: LinhaCondicao[]) => void;
  limite: number | null;
}) {
  const { data: produtos = [] } = useProdutos();
  const ativos = produtos.filter(produto => produto.ativo);
  const soma = linhas.reduce((total, linha) => total + (linha.valor ?? 0), 0);
  const passou = limite != null && limite > 0 && soma > limite;

  function trocar(chave: number, parcial: Partial<LinhaCondicao>) {
    onMudar(linhas.map(linha => (linha.chave === chave ? { ...linha, ...parcial } : linha)));
  }

  return (
    <div className="space-y-2">
      {linhas.map((linha, i) => (
        <div key={linha.chave} className="grid grid-cols-[1fr_10rem_auto] items-center gap-2">
          <select
            value={linha.produto}
            onChange={event => trocar(linha.chave, { produto: event.target.value })}
            aria-label={`Produto da condição ${i + 1}`}
            className={`${CAMPO} cursor-pointer`}
          >
            <option value="">Produto…</option>
            {ativos.map(produto => (
              <option key={produto.id} value={produto.nome}>{produto.nome}</option>
            ))}
            {/* Produto desativado depois de usado continua aparecendo na linha que já o tinha. */}
            {linha.produto && !ativos.some(produto => produto.nome === linha.produto) && (
              <option value={linha.produto}>{linha.produto}</option>
            )}
          </select>
          <InputMoeda valor={linha.valor} onMudar={valor => trocar(linha.chave, { valor })} rotulo={`Valor da condição ${i + 1}`} />
          <button
            type="button"
            onClick={() => onMudar(linhas.filter(item => item.chave !== linha.chave))}
            disabled={linhas.length === 1}
            aria-label={`Remover condição ${i + 1}`}
            className="inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-lg text-slate-400 transition-colors
              hover:bg-rose-50 hover:text-rose-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
              disabled:cursor-not-allowed disabled:opacity-30 dark:hover:bg-rose-950/40"
          >
            <Icon name="close" size={16} />
          </button>
        </div>
      ))}
      <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
        <button
          type="button"
          onClick={() => onMudar([...linhas, novaLinha()])}
          className="inline-flex min-h-8 cursor-pointer items-center gap-1 rounded-lg px-2 text-xs font-medium text-[#612035]
            hover:bg-[#612035]/5 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] dark:text-[#e8a3b6]"
        >
          <Icon name="add" size={14} /> Adicionar produto
        </button>
        <span className={`text-xs tabular-nums ${passou ? "font-medium text-[#D1732C]" : "text-slate-500"}`}>
          Soma {formatMoeda(soma)}
          {limite != null && limite > 0 && ` de ${formatMoeda(limite)}`}
          {passou && " — passa do limite"}
        </span>
      </div>
    </div>
  );
}
