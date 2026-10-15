import { NOTA, type NotaEmpresa } from "@/types/nota-empresa";
import SeloNota from "./SeloNota";

/**
 * Score da empresa no card das esteiras: selo compacto e o rótulo ao lado. Fica numa linha só
 * dele, abaixo do CNPJ — ao lado do nome, num card estreito, espremia a razão social.
 */
export default function NotaNoCard({ nota, className = "" }: { nota: NotaEmpresa; className?: string }) {
  return (
    <p className={`flex items-center gap-1.5 ${className}`} title={`Score: ${NOTA[nota].descricao}`}>
      <SeloNota nota={nota} tamanho="compacto" />
      <span className="text-[10.5px] font-bold" style={{ color: NOTA[nota].texto }}>
        {NOTA[nota].rotulo}
      </span>
    </p>
  );
}
