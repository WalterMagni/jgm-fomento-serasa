"use client";

import { useEffect, useRef, useState } from "react";
import Icon from "@/components/ui/Icon";

type Props = {
  /** "AAAA-MM-DD" ou, com hora, "AAAA-MM-DDTHH:mm". Vazio = sem data. */
  value: string;
  onChange: (valor: string) => void;
  comHora?: boolean;
  /** Hora usada quando a pessoa escolhe só o dia. */
  horaPadrao?: string;
  id?: string;
  "aria-label"?: string;
  className?: string;
};

const MESES = ["Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"];
const DIAS = ["D", "S", "T", "Q", "Q", "S", "S"];
const HORAS = ["09:00", "12:00", "16:00", "18:00"];

const doisDigitos = (n: number) => String(n).padStart(2, "0");
const isoDia = (data: Date) => `${data.getFullYear()}-${doisDigitos(data.getMonth() + 1)}-${doisDigitos(data.getDate())}`;

/** "2026-10-09T18:00" → "09/10/2026 18:00". */
function paraTexto(valor: string, comHora: boolean) {
  if (!valor) return "";
  const [dia, hora] = valor.split("T");
  const [a, m, d] = dia.split("-");
  return comHora ? `${d}/${m}/${a} ${(hora ?? "").slice(0, 5)}` : `${d}/${m}/${a}`;
}

/** Máscara progressiva: dd/mm/aaaa hh:mm. */
function mascarar(texto: string, comHora: boolean) {
  const d = texto.replace(/\D/g, "").slice(0, comHora ? 12 : 8);
  let saida = d.slice(0, 2);
  if (d.length > 2) saida += "/" + d.slice(2, 4);
  if (d.length > 4) saida += "/" + d.slice(4, 8);
  if (comHora && d.length > 8) saida += " " + d.slice(8, 10);
  if (comHora && d.length > 10) saida += ":" + d.slice(10, 12);
  return saida;
}

/** Texto completo e válido → valor ISO; senão nulo. Data inexistente (31/02) é recusada. */
function paraValor(texto: string, comHora: boolean, horaPadrao: string): string | null {
  const achado = /^(\d{2})\/(\d{2})\/(\d{4})(?: (\d{2}):(\d{2}))?$/.exec(texto.trim());
  if (!achado) return null;
  const [, d, m, a, h, min] = achado;
  const data = new Date(Number(a), Number(m) - 1, Number(d));
  if (data.getDate() !== Number(d) || data.getMonth() !== Number(m) - 1) return null;
  if (!comHora) return `${a}-${m}-${d}`;
  const hora = h !== undefined ? `${h}:${min}` : horaPadrao;
  const [hh, mm] = hora.split(":").map(Number);
  if (hh > 23 || mm > 59) return null;
  return `${a}-${m}-${d}T${hora}`;
}

/**
 * Campo de data em português, com calendário.
 *
 * <p>O {@code <input type="date">} nativo segue o idioma do navegador, e não o da página: num
 * Chrome em inglês aparece "mm/dd/yyyy" mesmo com {@code lang="pt-BR"}. Este campo aceita
 * digitar (com máscara) ou escolher no calendário, sempre em dd/mm/aaaa.</p>
 */
export default function CampoData({ value, onChange, comHora = false, horaPadrao = "18:00", id, className = "", ...rest }: Props) {
  // Enquanto a pessoa digita, o texto vale mais que o valor; ao sair do campo, volta ao valor.
  const [rascunho, setRascunho] = useState<string | null>(null);
  const [aberto, setAberto] = useState(false);
  const [mes, setMes] = useState(() => new Date());
  // Perto do fim da tela (ou do diálogo), o calendário abre para cima em vez de ficar cortado.
  const [paraCima, setParaCima] = useState(false);
  const caixa = useRef<HTMLDivElement>(null);
  const calendario = useRef<HTMLDivElement>(null);
  const texto = rascunho ?? paraTexto(value, comHora);
  const invalido = rascunho !== null && rascunho.length > 0 && paraValor(rascunho, comHora, horaPadrao) === null;
  const diaEscolhido = value ? value.slice(0, 10) : null;
  const horaEscolhida = comHora && value ? value.slice(11, 16) : null;

  // Dentro de um diálogo que rola, traz o calendário inteiro para a vista ao abrir.
  useEffect(() => {
    if (aberto) calendario.current?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [aberto]);

  useEffect(() => {
    if (!aberto) return;
    function fora(event: MouseEvent) {
      if (caixa.current && !caixa.current.contains(event.target as Node)) setAberto(false);
    }
    function esc(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.stopPropagation();
        setAberto(false);
      }
    }
    document.addEventListener("mousedown", fora);
    window.addEventListener("keydown", esc, true);
    return () => {
      document.removeEventListener("mousedown", fora);
      window.removeEventListener("keydown", esc, true);
    };
  }, [aberto]);

  function abrir() {
    const base = value ? new Date(`${value.slice(0, 10)}T00:00:00`) : new Date();
    setMes(new Date(base.getFullYear(), base.getMonth(), 1));
    const caixaRect = caixa.current?.getBoundingClientRect();
    const rolagem = caixa.current?.closest("[role=dialog] .overflow-y-auto")?.getBoundingClientRect();
    const limite = rolagem ? rolagem.bottom : window.innerHeight;
    const altura = comHora ? 360 : 290;
    setParaCima(caixaRect ? limite - caixaRect.bottom < altura && caixaRect.top - (rolagem?.top ?? 0) > altura : false);
    setAberto(atual => !atual);
  }

  function escolherDia(data: Date) {
    setRascunho(null);
    onChange(comHora ? `${isoDia(data)}T${horaEscolhida ?? horaPadrao}` : isoDia(data));
    if (!comHora) setAberto(false);
  }

  function escolherHora(hora: string) {
    setRascunho(null);
    onChange(`${diaEscolhido ?? isoDia(new Date())}T${hora}`);
    setAberto(false);
  }

  const primeiroDia = new Date(mes.getFullYear(), mes.getMonth(), 1);
  const dias: (Date | null)[] = [
    ...Array.from({ length: primeiroDia.getDay() }, () => null),
    ...Array.from({ length: new Date(mes.getFullYear(), mes.getMonth() + 1, 0).getDate() }, (_, i) => new Date(mes.getFullYear(), mes.getMonth(), i + 1)),
  ];
  const hoje = isoDia(new Date());
  const amanha = new Date();
  amanha.setDate(amanha.getDate() + 1);

  return (
    <div className="relative" ref={caixa}>
      <div className="relative">
        <input
          id={id}
          aria-label={rest["aria-label"]}
          aria-invalid={invalido || undefined}
          inputMode="numeric"
          value={texto}
          placeholder={comHora ? "dd/mm/aaaa hh:mm" : "dd/mm/aaaa"}
          onChange={event => {
            const mascarado = mascarar(event.target.value, comHora);
            setRascunho(mascarado);
            if (!mascarado) onChange("");
            const valor = paraValor(mascarado, comHora, horaPadrao);
            // Com hora, só a data completa ainda não basta: espera a hora ou a saída do campo.
            if (valor && (!comHora || mascarado.length === 16)) onChange(valor);
          }}
          onBlur={() => {
            if (rascunho !== null) {
              const valor = paraValor(rascunho, comHora, horaPadrao);
              if (valor) onChange(valor);
              else if (!rascunho) onChange("");
            }
            setRascunho(null);
          }}
          className={`w-full rounded-lg border bg-white py-2 pl-3 pr-10 text-sm tabular-nums text-slate-900 placeholder:text-slate-400
            transition-colors focus:outline-none focus:ring-2 dark:bg-slate-800 dark:text-white ${
              invalido
                ? "border-rose-400 focus:border-rose-500 focus:ring-rose-500/20"
                : "border-slate-200 focus:border-[#612035] focus:ring-[#612035]/20 dark:border-slate-700 dark:focus:border-[#e8a3b6]"
            } ${className}`}
        />
        <button
          type="button"
          onClick={abrir}
          aria-label="Abrir calendário"
          aria-expanded={aberto}
          className="absolute right-1 top-1/2 flex h-8 w-8 -translate-y-1/2 cursor-pointer items-center justify-center rounded-md text-slate-400
            hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] dark:hover:bg-slate-700"
        >
          <Icon name="calendar_today" size={15} />
        </button>
      </div>

      {aberto && (
        <div
          ref={calendario}
          role="dialog"
          aria-label="Calendário"
          className={`esteira-modal-in absolute left-0 z-40 w-72 rounded-xl ${paraCima ? "bottom-full mb-1" : "mt-1"} border border-slate-200 bg-white p-3 shadow-xl dark:border-slate-700 dark:bg-slate-800`}
        >
          <div className="mb-1 flex items-center justify-between">
            <button
              type="button"
              aria-label="Mês anterior"
              onClick={() => setMes(new Date(mes.getFullYear(), mes.getMonth() - 1, 1))}
              className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-md text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-700"
            >
              <Icon name="chevron_left" size={16} />
            </button>
            <span className="text-sm font-semibold text-slate-800 dark:text-slate-100">
              {MESES[mes.getMonth()]} de {mes.getFullYear()}
            </span>
            <button
              type="button"
              aria-label="Próximo mês"
              onClick={() => setMes(new Date(mes.getFullYear(), mes.getMonth() + 1, 1))}
              className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-md text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-700"
            >
              <Icon name="chevron_right" size={16} />
            </button>
          </div>

          <div className="grid grid-cols-7 gap-0.5 text-center">
            {DIAS.map((dia, indice) => (
              <span key={indice} className="py-0.5 text-[10px] font-bold text-slate-400">
                {dia}
              </span>
            ))}
            {dias.map((data, indice) =>
              data ? (
                <button
                  key={indice}
                  type="button"
                  onClick={() => escolherDia(data)}
                  aria-pressed={isoDia(data) === diaEscolhido}
                  className={`h-7 cursor-pointer rounded-md text-xs tabular-nums transition-colors ${
                    isoDia(data) === diaEscolhido
                      ? "bg-[#612035] font-semibold text-white"
                      : isoDia(data) === hoje
                        ? "font-semibold text-[#612035] ring-1 ring-inset ring-[#612035]/40 hover:bg-[#612035]/10 dark:text-[#e8a3b6]"
                        : "text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-700"
                  }`}
                >
                  {data.getDate()}
                </button>
              ) : (
                <span key={indice} />
              ),
            )}
          </div>

          {comHora && (
            <div className="mt-2 border-t border-slate-100 pt-2 dark:border-slate-700">
              <p className="mb-1 text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">
                Horário <span className="font-normal normal-case tracking-normal">· outro: digite no campo</span>
              </p>
              <div className="grid grid-cols-4 gap-1">
                {HORAS.map(hora => (
                  <button
                    key={hora}
                    type="button"
                    onClick={() => escolherHora(hora)}
                    className={`h-7 cursor-pointer rounded-md text-xs tabular-nums ${
                      hora === horaEscolhida
                        ? "bg-[#612035] font-semibold text-white"
                        : "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
                    }`}
                  >
                    {hora}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="mt-2 flex items-center justify-between border-t border-slate-100 pt-2 text-xs dark:border-slate-700">
            <span className="flex gap-1">
              <button type="button" onClick={() => escolherDia(new Date())} className="cursor-pointer rounded-md px-2 py-1 font-medium text-[#612035] hover:bg-[#612035]/10 dark:text-[#e8a3b6]">
                Hoje
              </button>
              <button type="button" onClick={() => escolherDia(amanha)} className="cursor-pointer rounded-md px-2 py-1 font-medium text-[#612035] hover:bg-[#612035]/10 dark:text-[#e8a3b6]">
                Amanhã
              </button>
            </span>
            <span className="flex gap-1">
              {value && (
                <button
                  type="button"
                  onClick={() => {
                    onChange("");
                    setRascunho(null);
                    setAberto(false);
                  }}
                  className="cursor-pointer rounded-md px-2 py-1 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-700"
                >
                  Limpar
                </button>
              )}
              {comHora && (
                <button type="button" onClick={() => setAberto(false)} className="cursor-pointer rounded-md bg-slate-900 px-2.5 py-1 font-medium text-white dark:bg-white dark:text-slate-900">
                  Pronto
                </button>
              )}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
