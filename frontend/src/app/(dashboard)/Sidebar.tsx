"use client";

import Image from "next/image";
import Link from "next/link";
import Icon from "@/components/ui/Icon";
import { useLiberacaoResumo } from "@/hooks/useLiberacao";
import { useProspeccaoResumo } from "@/hooks/useProspeccao";
import { GRUPOS_NAV, SETTINGS_ITEM, type NavItem } from "./NavItems";

/**
 * Menu da casca nova, igual no desktop e na gaveta do tablet: bordô em degradê com um brilho
 * laranja que respira, itens por seção e o usuário no rodapé.
 *
 * <p>Os contadores são os mesmos da casca antiga (atrasados da prospecção, pendências da
 * liberação). Item com contador mostra o número no lugar do selo beta: o que pede ação ganha.</p>
 */
export default function Sidebar({
  pathname,
  userName,
  userInitials,
  onSair,
  onNavigate,
}: {
  pathname: string;
  userName: string;
  userInitials: string;
  onSair: () => void;
  /** A gaveta do tablet fecha ao navegar. */
  onNavigate?: () => void;
}) {
  const { data: prospeccao } = useProspeccaoResumo();
  const { data: liberacao } = useLiberacaoResumo();
  const contador = (href: string) =>
    href === "/prospeccao" ? (prospeccao?.precisamAtencao ?? 0) : href === "/liberacao" ? (liberacao?.precisamAtencao ?? 0) : 0;
  const naConfiguracao = SETTINGS_ITEM.isActive(pathname);

  return (
    <div className="sidebar-marca relative flex h-full flex-col gap-5 overflow-hidden bg-[linear-gradient(170deg,#6E263D_0%,#5A1E32_45%,#43162A_100%)] px-3.5 py-5 text-white">
      <span
        aria-hidden
        className="pointer-events-none absolute -right-[90px] -top-[90px] h-[280px] w-[280px] rounded-full bg-[radial-gradient(circle,rgba(209,115,44,0.32),transparent_65%)] animate-respirar motion-reduce:animate-none"
      />
      <span
        aria-hidden
        className="pointer-events-none absolute -left-[120px] bottom-16 h-[300px] w-[300px] rounded-full bg-[radial-gradient(circle,rgba(255,255,255,0.07),transparent_65%)]"
      />

      <Link href="/" onClick={onNavigate} className="relative flex items-center gap-1 px-1.5">
        {/* O PNG tem muita margem; object-cover recorta o vazio em volta das letras. */}
        <Image src="/logo-dark.png" alt="JGM" width={92} height={32} priority className="h-8 w-[92px] object-cover" />
        <span className="font-display text-[19px] font-semibold tracking-[-0.01em]">Fomento</span>
      </Link>

      <nav aria-label="Menu principal" className="relative -mx-1 flex flex-1 flex-col gap-5 overflow-y-auto px-1">
        {GRUPOS_NAV.map(grupo => (
          <div key={grupo.titulo}>
            <p className="px-3 pb-1.5 text-[10.5px] font-bold uppercase tracking-[0.14em] text-white/60">{grupo.titulo}</p>
            <ul className="flex flex-col gap-0.5">
              {grupo.itens.map(item => (
                <ItemMenu key={item.href} item={item} pathname={pathname} contador={contador(item.href)} onNavigate={onNavigate} />
              ))}
            </ul>
          </div>
        ))}
      </nav>

      <div className="relative flex items-center gap-2 rounded-[14px] bg-black/20 p-2.5">
        <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-[#F0A46B] text-[13px] font-extrabold text-[#3A1608]">
          {userInitials}
        </span>
        {/* Primeiro nome: com as duas ações ao lado, o nome inteiro não cabe. */}
        <span className="min-w-0 flex-1 truncate text-[13.5px] font-bold" title={userName}>
          {userName.split(" ")[0]}
        </span>
        <Link
          href={SETTINGS_ITEM.href}
          onClick={onNavigate}
          aria-label="Configurações"
          aria-current={naConfiguracao ? "page" : undefined}
          className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl transition-colors ${
            naConfiguracao ? "nav-ativo bg-white text-primary" : "text-white/80 hover:bg-white/10 hover:text-white"
          }`}
        >
          <Icon name="settings" size={20} />
        </Link>
        <button
          type="button"
          onClick={onSair}
          aria-label="Sair"
          className="grid h-10 w-10 shrink-0 place-items-center rounded-xl text-white/80 transition-colors hover:bg-white/10 hover:text-white"
        >
          <Icon name="logout" size={20} />
        </button>
      </div>
    </div>
  );
}

function ItemMenu({
  item,
  pathname,
  contador,
  onNavigate,
}: {
  item: NavItem;
  pathname: string;
  contador: number;
  onNavigate?: () => void;
}) {
  const ativo = item.isActive(pathname);
  const filhos = item.children ?? [];
  const abertos = filhos.length > 0 && (ativo || filhos.some(f => f.isActive(pathname)));

  return (
    <li>
      <Link
        href={item.href}
        onClick={onNavigate}
        aria-current={ativo ? "page" : undefined}
        className={`group flex min-h-[42px] items-center gap-3 rounded-xl px-3 text-sm transition-[background-color,color,transform] duration-200 ${
          ativo
            ? "nav-ativo bg-white font-bold text-primary shadow-[0_10px_24px_-12px_rgba(0,0,0,0.55)]"
            : "font-medium text-white/85 hover:translate-x-0.5 hover:bg-white/10 hover:text-white motion-reduce:hover:translate-x-0"
        }`}
      >
        <Icon
          name={item.icon}
          size={20}
          className="shrink-0 transition-transform duration-300 ease-saida group-hover:-rotate-6 group-hover:scale-110 motion-reduce:transform-none"
        />
        <span className="min-w-0 flex-1 truncate">{item.rotuloCurto ?? item.label}</span>
        {contador > 0 ? (
          <span
            className="grid h-[21px] min-w-[21px] place-items-center rounded-full bg-[#F0A46B] px-1.5 text-[11px] font-extrabold text-[#3A1608] animate-pulo motion-reduce:animate-none"
            style={{ animationDelay: "300ms" }}
            title={`${contador} card(s) precisando de atenção`}
          >
            {contador > 99 ? "99+" : contador}
          </span>
        ) : (
          item.beta && (
            <span
              className={`rounded-md border px-1.5 py-px text-[9.5px] font-bold uppercase tracking-[0.08em] ${
                ativo ? "border-primary/30 text-primary" : "border-white/30 text-white/80"
              }`}
              title="Em avaliação: o fluxo ainda pode mudar"
            >
              beta
            </span>
          )
        )}
      </Link>

      {abertos && (
        <ul className="ml-[22px] mt-0.5 flex flex-col gap-0.5 border-l border-white/15 pl-2.5 animate-cair motion-reduce:animate-none">
          {filhos.map(filho => {
            const filhoAtivo = filho.isActive(pathname);
            return (
              <li key={filho.href}>
                <Link
                  href={filho.href}
                  onClick={onNavigate}
                  aria-current={filhoAtivo ? "page" : undefined}
                  className={`flex min-h-10 items-center gap-2.5 rounded-[10px] px-2.5 text-[13px] transition-colors ${
                    filhoAtivo ? "bg-white/15 font-semibold text-white" : "font-medium text-white/75 hover:bg-white/10 hover:text-white"
                  }`}
                >
                  <Icon name={filho.icon} size={18} className="shrink-0" />
                  {filho.label}
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </li>
  );
}
