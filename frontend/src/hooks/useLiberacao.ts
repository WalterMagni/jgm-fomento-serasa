import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import type {
  CorLiberacao,
  DadosCard,
  Etiqueta,
  EmpresaEncontrada,
  EtapaLiberacao,
  LiberacaoCard,
  LiberacaoDetalhe,
  LiberacaoResumo,
  NovaPendencia,
  PessoaDiretorio,
  PosicaoParecer,
  UsuarioAtual,
} from "../types/liberacao";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
/** As rotas de autenticação ficam fora de /api/v1. */
const AUTH_BASE_URL = API_BASE_URL.replace(/\/api\/v1\/?$/, "");

const LISTA_KEY = ["liberacao"] as const;
const RESUMO_KEY = ["liberacaoResumo"] as const;
const detalheKey = (id: string) => ["liberacaoDetalhe", id] as const;

function headers(json = true): HeadersInit {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("serasa_token");
  return {
    ...(json ? { "Content-Type": "application/json" } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

/** Erro com o status HTTP, para a tela tratar o 409 (alguém salvou antes) diferente do resto. */
export class ErroApi extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

/**
 * A mensagem do backend é o que a tela mostra: em 409 ela diz quem salvou antes ou de quem falta
 * o parecer. Cair num texto genérico perderia justamente a informação útil.
 */
async function falha(response: Response, fallback: string): Promise<never> {
  const text = await response.text().catch(() => "");
  let message = fallback;
  if (text) {
    try {
      const json = JSON.parse(text);
      message = json.message || json.error || fallback;
    } catch {
      message = text.length > 300 ? fallback : text;
    }
  }
  throw new ErroApi(message, response.status);
}

async function pedir<T>(url: string, init: RequestInit, fallback: string): Promise<T> {
  const res = await fetch(url, { ...init, headers: headers(init.body !== undefined) });
  if (!res.ok) return falha(res, fallback);
  if (res.status === 204) return undefined as T;
  return res.json();
}

// ------------------------------------------------------------------ leitura

/** @param tempoReal canal de notificações ligado: o quadro se atualiza por evento, sem polling. */
export function useLiberacaoCards(finalizadosDesde?: string, tempoReal = false) {
  const query = finalizadosDesde ? `?finalizadosDesde=${finalizadosDesde}` : "";
  return useQuery<LiberacaoCard[]>({
    queryKey: [...LISTA_KEY, finalizadosDesde ?? ""],
    queryFn: () => pedir(`${API_BASE_URL}/liberacao${query}`, {}, "Falha ao carregar a esteira"),
    staleTime: 15 * 1000,
    // Com o canal ligado, cada mudança chega como evento. Sem ele (reconectando), o quadro volta
    // a se atualizar sozinho a cada 30 s para quem deixa a tela aberta o dia todo.
    refetchInterval: tempoReal ? false : 30 * 1000,
  });
}

export function useLiberacaoResumo() {
  return useQuery<LiberacaoResumo>({
    queryKey: RESUMO_KEY,
    queryFn: () => pedir(`${API_BASE_URL}/liberacao/resumo`, {}, "Falha ao carregar o resumo"),
    staleTime: 30 * 1000,
    refetchInterval: 60 * 1000,
  });
}

export function useLiberacaoDetalhe(id?: string | null) {
  return useQuery<LiberacaoDetalhe>({
    queryKey: detalheKey(id ?? ""),
    enabled: Boolean(id),
    queryFn: () => pedir(`${API_BASE_URL}/liberacao/${id}`, {}, "Falha ao carregar o card"),
  });
}

/** Todos os usuários do portal, para escolher destinatário. Muda raramente. */
export function useDiretorio() {
  return useQuery<PessoaDiretorio[]>({
    queryKey: ["usuariosDiretorio"],
    queryFn: () => pedir(`${API_BASE_URL}/usuarios/diretorio`, {}, "Falha ao carregar os usuários"),
    staleTime: 5 * 60 * 1000,
  });
}

/** Quem está logado, relido do servidor: as marcas de analista mudam sem novo login. */
export function useUsuarioAtual() {
  return useQuery<UsuarioAtual>({
    queryKey: ["usuarioAtual"],
    queryFn: () => pedir(`${AUTH_BASE_URL}/api/auth/me`, {}, "Falha ao carregar o usuário"),
    staleTime: 60 * 1000,
  });
}

/** Busca de empresa cadastrada. O debounce fica com quem chama. */
export function useEmpresaBusca(termo: string) {
  const limpo = termo.trim();
  return useQuery<EmpresaEncontrada[]>({
    queryKey: ["empresaBusca", limpo],
    enabled: limpo.length >= 2,
    queryFn: () =>
      pedir(`${API_BASE_URL}/company/busca?q=${encodeURIComponent(limpo)}`, {}, "Falha na busca de empresas"),
    staleTime: 60 * 1000,
  });
}

// ------------------------------------------------------------------ escrita

/** Troca o card na lista em cache pela versão do servidor, sem rebuscar o quadro inteiro. */
function useAtualizarCache() {
  const queryClient = useQueryClient();
  return (card?: LiberacaoCard) => {
    if (card) {
      queryClient.setQueriesData<LiberacaoCard[]>({ queryKey: LISTA_KEY }, lista =>
        lista?.some(item => item.id === card.id)
          ? lista.map(item => (item.id === card.id ? card : item))
          : lista ? [card, ...lista] : lista,
      );
    }
    queryClient.invalidateQueries({ queryKey: LISTA_KEY });
    queryClient.invalidateQueries({ queryKey: RESUMO_KEY });
    if (card) queryClient.invalidateQueries({ queryKey: detalheKey(card.id) });
  };
}

/** Recarrega o card aberto quando o servidor recusa por versão desatualizada. */
function avisarConflito(error: Error, recarregar: () => void) {
  if (error instanceof ErroApi && error.status === 409) {
    toast.error(error.message, { action: { label: "Recarregar", onClick: recarregar }, duration: 10000 });
  } else {
    toast.error(error.message);
  }
}

export function useCriarCard() {
  const atualizar = useAtualizarCache();
  const queryClient = useQueryClient();
  return useMutation<LiberacaoCard, Error, DadosCard>({
    mutationFn: body =>
      pedir(`${API_BASE_URL}/liberacao`, { method: "POST", body: JSON.stringify(body) }, "Falha ao criar o card"),
    onSuccess: card => {
      atualizar(card);
      toast.success(`Card #${card.numero} criado`);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["liberacaoTipos"] }),
    onError: error => toast.error(error.message),
  });
}

export function useEditarCard() {
  const atualizar = useAtualizarCache();
  const queryClient = useQueryClient();
  return useMutation<LiberacaoCard, Error, { id: string; dados: DadosCard }>({
    mutationFn: ({ id, dados }) =>
      pedir(`${API_BASE_URL}/liberacao/${id}`, { method: "PUT", body: JSON.stringify(dados) }, "Falha ao salvar"),
    onSuccess: card => {
      atualizar(card);
      toast.success("Card salvo");
      queryClient.invalidateQueries({ queryKey: ["liberacaoTipos"] });
    },
    onError: (error, { id }) =>
      avisarConflito(error, () => {
        queryClient.invalidateQueries({ queryKey: detalheKey(id) });
        queryClient.invalidateQueries({ queryKey: LISTA_KEY });
      }),
  });
}

type Mover = { id: string; de: EtapaLiberacao; para: EtapaLiberacao; pendencias?: NovaPendencia[]; observacao?: string };

/**
 * Move o card na hora e desfaz se o servidor recusar.
 *
 * <p>Arrastar e esperar a resposta para o card pular de coluna parece travado. O risco do otimista
 * é mostrar um movimento que não aconteceu — por isso o rollback restaura o quadro anterior e o
 * motivo da recusa aparece no aviso.</p>
 */
export function useMoverCard() {
  const queryClient = useQueryClient();
  const atualizar = useAtualizarCache();
  return useMutation<LiberacaoCard, Error, Mover, { anteriores: [readonly unknown[], LiberacaoCard[] | undefined][] }>({
    mutationFn: ({ id, ...body }) =>
      pedir(`${API_BASE_URL}/liberacao/${id}/etapa`, { method: "PATCH", body: JSON.stringify(body) }, "Falha ao mover o card"),
    onMutate: async ({ id, para }) => {
      await queryClient.cancelQueries({ queryKey: LISTA_KEY });
      const anteriores = queryClient.getQueriesData<LiberacaoCard[]>({ queryKey: LISTA_KEY });
      queryClient.setQueriesData<LiberacaoCard[]>({ queryKey: LISTA_KEY }, lista =>
        lista?.map(card => (card.id === id ? { ...card, etapa: para, etapaDesde: new Date().toISOString() } : card)),
      );
      return { anteriores };
    },
    onError: (error, _vars, contexto) => {
      contexto?.anteriores.forEach(([key, dados]) => queryClient.setQueryData(key, dados));
      toast.error(error.message);
      queryClient.invalidateQueries({ queryKey: LISTA_KEY });
    },
    onSuccess: card => atualizar(card),
  });
}

function useAtualizarDetalhe() {
  const queryClient = useQueryClient();
  return (detalhe: LiberacaoDetalhe) => {
    queryClient.setQueryData(detalheKey(detalhe.card.id), detalhe);
    queryClient.setQueriesData<LiberacaoCard[]>({ queryKey: LISTA_KEY }, lista =>
      lista?.map(card => (card.id === detalhe.card.id ? detalhe.card : card)),
    );
    queryClient.invalidateQueries({ queryKey: RESUMO_KEY });
  };
}

export function useRegistrarParecer() {
  const atualizar = useAtualizarDetalhe();
  return useMutation<LiberacaoDetalhe, Error, { id: string; posicao: PosicaoParecer; texto: string }>({
    mutationFn: ({ id, ...body }) =>
      pedir(`${API_BASE_URL}/liberacao/${id}/parecer`, { method: "POST", body: JSON.stringify(body) }, "Falha ao registrar o parecer"),
    onSuccess: detalhe => {
      atualizar(detalhe);
      const faltam = detalhe.card.pareceres.filter(parecer => !parecer.posicao && parecer.usuarioId).length;
      toast.success(faltam === 0 ? "Parecer registrado. Comitê completo: o card pode ser movido." : "Parecer registrado");
    },
    onError: error => toast.error(error.message),
  });
}

export function useNovaPendencia() {
  const atualizar = useAtualizarDetalhe();
  return useMutation<LiberacaoDetalhe, Error, { id: string } & NovaPendencia>({
    mutationFn: ({ id, ...body }) =>
      pedir(`${API_BASE_URL}/liberacao/${id}/pendencias`, { method: "POST", body: JSON.stringify(body) }, "Falha ao abrir a pendência"),
    onSuccess: detalhe => {
      atualizar(detalhe);
      toast.success("Pendência aberta");
    },
    onError: error => toast.error(error.message),
  });
}

export function useResponderPendencia() {
  const atualizar = useAtualizarDetalhe();
  return useMutation<LiberacaoDetalhe, Error, { id: string; pendenciaId: string; resposta: string }>({
    mutationFn: ({ id, pendenciaId, resposta }) =>
      pedir(
        `${API_BASE_URL}/liberacao/${id}/pendencias/${pendenciaId}/resposta`,
        { method: "PATCH", body: JSON.stringify({ resposta }) },
        "Falha ao responder a pendência",
      ),
    onSuccess: detalhe => {
      atualizar(detalhe);
      toast.success("Pendência respondida");
    },
    onError: error => toast.error(error.message),
  });
}

export function useComentar() {
  const atualizar = useAtualizarDetalhe();
  return useMutation<LiberacaoDetalhe, Error, { id: string; texto: string }>({
    mutationFn: ({ id, texto }) =>
      pedir(`${API_BASE_URL}/liberacao/${id}/comentarios`, { method: "POST", body: JSON.stringify({ texto }) }, "Falha ao comentar"),
    onSuccess: atualizar,
    onError: error => toast.error(error.message),
  });
}

export function useEditarComentario() {
  const atualizar = useAtualizarDetalhe();
  return useMutation<LiberacaoDetalhe, Error, { id: string; comentarioId: string; texto: string }>({
    mutationFn: ({ id, comentarioId, texto }) =>
      pedir(
        `${API_BASE_URL}/liberacao/${id}/comentarios/${comentarioId}`,
        { method: "PATCH", body: JSON.stringify({ texto }) },
        "Falha ao editar o comentário",
      ),
    onSuccess: atualizar,
    onError: error => toast.error(error.message),
  });
}

export function useApagarComentario() {
  const atualizar = useAtualizarDetalhe();
  return useMutation<LiberacaoDetalhe, Error, { id: string; comentarioId: string }>({
    mutationFn: ({ id, comentarioId }) =>
      pedir(`${API_BASE_URL}/liberacao/${id}/comentarios/${comentarioId}`, { method: "DELETE" }, "Falha ao apagar o comentário"),
    onSuccess: detalhe => {
      atualizar(detalhe);
      toast.success("Comentário apagado");
    },
    onError: error => toast.error(error.message),
  });
}

export function useExcluirCard() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, { id: string; numero: number }>({
    mutationFn: ({ id }) => pedir(`${API_BASE_URL}/liberacao/${id}`, { method: "DELETE" }, "Falha ao apagar o card"),
    onSuccess: (_resultado, { id, numero }) => {
      queryClient.setQueriesData<LiberacaoCard[]>({ queryKey: LISTA_KEY }, lista => lista?.filter(card => card.id !== id));
      queryClient.invalidateQueries({ queryKey: LISTA_KEY });
      queryClient.invalidateQueries({ queryKey: RESUMO_KEY });
      toast.success(`Card #${numero} apagado`);
    },
    onError: error => toast.error(error.message),
  });
}

/** Tipos de operação: os padrões e os que o time já criou. */
export function useTiposOperacao() {
  return useQuery<string[]>({
    queryKey: ["liberacaoTipos"],
    queryFn: () => pedir(`${API_BASE_URL}/liberacao/tipos`, {}, "Falha ao carregar os tipos de operação"),
    staleTime: 60 * 1000,
  });
}

// --------------------------------------------------------- etiquetas, cor, membros

const ETIQUETAS_KEY = ["liberacaoEtiquetas"] as const;

export function useEtiquetas() {
  return useQuery<Etiqueta[]>({
    queryKey: ETIQUETAS_KEY,
    queryFn: () => pedir(`${API_BASE_URL}/liberacao/etiquetas`, {}, "Falha ao carregar as etiquetas"),
    staleTime: 60 * 1000,
  });
}

export function useCriarEtiqueta() {
  const queryClient = useQueryClient();
  return useMutation<Etiqueta, Error, { nome: string; cor: CorLiberacao }>({
    mutationFn: body =>
      pedir(`${API_BASE_URL}/liberacao/etiquetas`, { method: "POST", body: JSON.stringify(body) }, "Falha ao criar a etiqueta"),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ETIQUETAS_KEY }),
    onError: error => toast.error(error.message),
  });
}

export function useEditarEtiqueta() {
  const queryClient = useQueryClient();
  return useMutation<Etiqueta, Error, { id: string; nome: string; cor: CorLiberacao }>({
    mutationFn: ({ id, ...body }) =>
      pedir(`${API_BASE_URL}/liberacao/etiquetas/${id}`, { method: "PATCH", body: JSON.stringify(body) }, "Falha ao alterar a etiqueta"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ETIQUETAS_KEY });
      queryClient.invalidateQueries({ queryKey: LISTA_KEY });
    },
    onError: error => toast.error(error.message),
  });
}

export function useApagarEtiqueta() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, string>({
    mutationFn: id => pedir(`${API_BASE_URL}/liberacao/etiquetas/${id}`, { method: "DELETE" }, "Falha ao apagar a etiqueta"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ETIQUETAS_KEY });
      queryClient.invalidateQueries({ queryKey: LISTA_KEY });
    },
    onError: error => toast.error(error.message),
  });
}

/** Ações rápidas do card que devolvem o card atualizado. */
function useAcaoCard<T>(montar: (variaveis: T) => { url: string; init: RequestInit }, falhaPadrao: string) {
  const atualizar = useAtualizarCache();
  return useMutation<LiberacaoCard, Error, T>({
    mutationFn: variaveis => {
      const { url, init } = montar(variaveis);
      return pedir(url, init, falhaPadrao);
    },
    onSuccess: card => atualizar(card),
    onError: error => toast.error(error.message),
  });
}

export function useDefinirEtiquetas() {
  return useAcaoCard<{ id: string; ids: string[] }>(
    ({ id, ids }) => ({ url: `${API_BASE_URL}/liberacao/${id}/etiquetas`, init: { method: "PUT", body: JSON.stringify({ ids }) } }),
    "Falha ao mudar as etiquetas",
  );
}

export function useDefinirCor() {
  return useAcaoCard<{ id: string; cor: CorLiberacao | null }>(
    ({ id, cor }) => ({ url: `${API_BASE_URL}/liberacao/${id}/cor`, init: { method: "PATCH", body: JSON.stringify({ cor }) } }),
    "Falha ao mudar a cor",
  );
}

export function useAdicionarMembro() {
  return useAcaoCard<{ id: string; usuarioId: string }>(
    ({ id, usuarioId }) => ({ url: `${API_BASE_URL}/liberacao/${id}/membros`, init: { method: "POST", body: JSON.stringify({ usuarioId }) } }),
    "Falha ao adicionar membro",
  );
}

export function useRemoverMembro() {
  return useAcaoCard<{ id: string; usuarioId: string }>(
    ({ id, usuarioId }) => ({ url: `${API_BASE_URL}/liberacao/${id}/membros/${usuarioId}`, init: { method: "DELETE" } }),
    "Falha ao remover membro",
  );
}

/** Baixa a planilha dos cards que a tela está mostrando. */
export async function exportarLiberacao(ids: string[]) {
  const res = await fetch(`${API_BASE_URL}/liberacao/exportar`, {
    method: "POST",
    headers: headers(true),
    body: JSON.stringify({ ids }),
  });
  if (!res.ok) return falha(res, "Falha ao gerar o relatório");
  const blob = await res.blob();
  const nome = /filename="?([^";]+)"?/.exec(res.headers.get("Content-Disposition") ?? "")?.[1] ?? "esteira-liberacao.xlsx";
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = nome;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/**
 * Cadastra a empresa pelo CNPJ Já, o mesmo caminho da Gestão de Carteira.
 *
 * <p>A mensagem de erro da API externa já chega tratada pelo backend — nunca crua na tela.</p>
 */
export function useCadastrarEmpresa() {
  const queryClient = useQueryClient();
  return useMutation<unknown, Error, { cnpj: string; cardId?: string }>({
    mutationFn: ({ cnpj }) =>
      pedir(`${API_BASE_URL}/company/enrich/cnpja/${cnpj}`, { method: "POST" }, "Falha ao cadastrar pelo CNPJ Já"),
    onSuccess: (_resultado, { cardId }) => {
      queryClient.invalidateQueries({ queryKey: LISTA_KEY });
      if (cardId) queryClient.invalidateQueries({ queryKey: detalheKey(cardId) });
      toast.success("Empresa cadastrada");
    },
    onError: error => toast.error(error.message),
  });
}

/** Marcas de analista e Comitê. Só admin; o servidor recusa os demais. */
export function useDefinirPapeis() {
  const queryClient = useQueryClient();
  return useMutation<{ id: string; analista: boolean; comite: boolean }, Error, { id: string; analista: boolean; comite: boolean }>({
    mutationFn: ({ id, ...body }) =>
      pedir(`${AUTH_BASE_URL}/api/auth/users/${id}/papeis`, { method: "PATCH", body: JSON.stringify(body) }, "Falha ao alterar papéis"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["usuariosDiretorio"] });
      queryClient.invalidateQueries({ queryKey: ["usuarioAtual"] });
      queryClient.invalidateQueries({ queryKey: LISTA_KEY });
    },
    onError: error => toast.error(error.message),
  });
}
