"use client";

import Icon from "@/components/ui/Icon";
import { formatCpf } from "@/types/person-analysis";
import { DIAS_CONSULTA_VELHA, SITUACAO, TIPO_RESTRICAO, type PessoaResumo } from "@/types/pessoa-resumo";
import { caixaTitulo, formatData, formatMoeda, iniciais, tempoRelativo, tomDoAvatar } from "./formatadores";

/**
 * Colunas da lista. Abaixo de lg somem vínculo e dívida; abaixo de md fica só a pessoa —
 * a bolinha no avatar continua dizendo a situação.
 */
export const GRADE_PESSOAS =
  "grid grid-cols-[minmax(0,1fr)_20px] items-center gap-4 " +
  "md:grid-cols-[minmax(0,2.2fr)_minmax(150px,1.2fr)_120px_20px] " +
  "lg:grid-cols-[minmax(0,2.3fr)_minmax(0,1.6fr)_minmax(150px,1.2fr)_112px_126px_20px]";

export type PessoaNaLista = PessoaResumo & { dias: number };

export function CabecalhoPessoas() {
  return (
    <div
      className={`${GRADE_PESSOAS} border-b border-linha bg-[#FBFAF7] px-5 py-3 text-[11px] font-bold uppercase tracking-[0.1em] text-[#7F7C73] sm:px-6`}
    >
      <span>Pessoa</span>
      <span className="hidden lg:block">Vínculo na carteira</span>
      <span className="hidden md:block">Restrições</span>
      <span className="hidden lg:block">Dívida</span>
      <span className="hidden md:block">Consultado</span>
      <span />
    </div>
  );
}

export default function LinhaPessoa({
  pessoa,
  atraso,
  onAbrir,
}: {
  pessoa: PessoaNaLista;
  /** Atraso da entrada, em ms: as linhas chegam em cascata. */
  atraso: number;
  onAbrir: () => void;
}) {
  const tom = tomDoAvatar(pessoa.cpf);
  const situacao = SITUACAO[pessoa.situacao];
  const [primeiro, ...outros] = pessoa.vinculos;
  const velha = pessoa.dias > DIAS_CONSULTA_VELHA;
  const local = [caixaTitulo(pessoa.cidade), pessoa.uf].filter(Boolean).join(" · ");

  return (
    <button
      type="button"
      onClick={onAbrir}
      aria-label={`Abrir ${caixaTitulo(pessoa.nome) || formatCpf(pessoa.cpf)}`}
      className={`${GRADE_PESSOAS} group w-full border-b border-[#F0EEE8] bg-white px-5 py-3 text-left transition-colors last:border-b-0 hover:bg-[#FBF7F1] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary sm:px-6 animate-subir motion-reduce:animate-none`}
      style={{ animationDelay: `${atraso}ms` }}
    >
      <span className="flex min-w-0 items-center gap-3">
        <span className="relative shrink-0">
          <span
            className="grid h-10 w-10 place-items-center rounded-xl text-[13.5px] font-bold transition-transform duration-300 ease-saida group-hover:-rotate-6 group-hover:scale-105 motion-reduce:group-hover:transform-none"
            style={{ background: tom.fundo, color: tom.texto }}
          >
            {iniciais(pessoa.nome)}
          </span>
          <span
            className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full ring-2 ring-white"
            style={{ background: situacao.cor }}
            title={situacao.rotulo}
          />
        </span>
        <span className="min-w-0">
          <span className="block truncate text-[14.5px] font-semibold text-grafite">
            {caixaTitulo(pessoa.nome) || "Nome não informado"}
          </span>
          <span className="mt-0.5 block truncate font-mono text-xs text-suave">
            {formatCpf(pessoa.cpf)}
            {local && ` · ${local}`}
          </span>
        </span>
      </span>

      <span className="hidden min-w-0 items-center lg:flex">
        {primeiro ? (
          <>
            <span className="inline-flex h-7 min-w-0 items-center gap-1.5 rounded-lg bg-[#F4F2EC] px-2.5 text-xs font-medium text-[#45443F]">
              <Icon name="business" size={15} className="shrink-0 text-[#8A877E]" />
              <span className="truncate">{caixaTitulo(primeiro.razaoSocial) || primeiro.cnpj}</span>
            </span>
            {outros.length > 0 && (
              <span className="ml-1.5 shrink-0 text-[11.5px] font-bold text-suave">+{outros.length}</span>
            )}
          </>
        ) : (
          <span className="text-xs italic text-[#8A877E]">consulta avulsa</span>
        )}
      </span>

      <span className="hidden md:block">
        {pessoa.totalOcorrencias === 0 ? (
          <span className="inline-flex items-center gap-1.5 text-[12.5px] font-semibold text-ok">
            <Icon name="check_circle" size={17} />
            Nada consta
          </span>
        ) : (
          <>
            <span className="flex h-1.5 w-[110px] gap-0.5 overflow-hidden rounded-full bg-[#F0EEE8]">
              {pessoa.restricoes.map(r => (
                <span
                  key={r.tipo}
                  className="block h-full"
                  style={{ width: `${(r.quantidade / pessoa.totalOcorrencias) * 100}%`, background: TIPO_RESTRICAO[r.tipo].cor }}
                />
              ))}
            </span>
            <span className="mt-1.5 block text-[12.5px] font-semibold" style={{ color: situacao.cor }}>
              {pessoa.totalOcorrencias === 1 ? "1 ocorrência" : `${pessoa.totalOcorrencias} ocorrências`}
            </span>
          </>
        )}
      </span>

      <span className={`hidden text-sm font-semibold lg:block ${pessoa.dividaTotal > 0 ? "text-grafite" : "text-[#B0ACA1]"}`}>
        {pessoa.dividaTotal > 0 ? formatMoeda(pessoa.dividaTotal) : "—"}
      </span>

      <span className="hidden md:block">
        <span className={`block text-[13px] font-semibold ${velha ? "text-laranja-texto" : "text-grafite"}`}>
          {tempoRelativo(pessoa.dias)}
        </span>
        <span className="mt-0.5 block text-[11.5px] text-suave">{formatData(pessoa.consultaEm)}</span>
      </span>

      <Icon
        name="chevron_right"
        size={18}
        className="text-[#B5B1A6] transition-[transform,color] duration-200 group-hover:translate-x-1 group-hover:text-primary"
      />
    </button>
  );
}

/** Linhas de mentira com brilho, no formato da lista, enquanto o resumo chega. */
export function LinhasEsqueleto({ quantidade = 8 }: { quantidade?: number }) {
  return (
    <div aria-hidden>
      {Array.from({ length: quantidade }, (_, i) => (
        <div key={i} className={`${GRADE_PESSOAS} border-b border-[#F0EEE8] px-5 py-3.5 sm:px-6`}>
          <span className="flex items-center gap-3">
            <span className="esqueleto h-10 w-10 shrink-0 rounded-xl" />
            <span className="flex flex-col gap-2">
              <span className="esqueleto h-3.5 w-44" />
              <span className="esqueleto h-3 w-28" />
            </span>
          </span>
          <span className="esqueleto hidden h-7 w-36 lg:block" />
          <span className="esqueleto hidden h-3 w-24 md:block" />
          <span className="esqueleto hidden h-3.5 w-16 lg:block" />
          <span className="esqueleto hidden h-3.5 w-14 md:block" />
          <span />
        </div>
      ))}
    </div>
  );
}
