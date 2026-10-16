#!/usr/bin/env bash
#
# judit-probe.sh — sonda exploratoria da API da Judit (dados juridicos BR)
#
# Fase exploratoria: NAO integra nada no backend. Só bate na API com documentos
# reais, salva os payloads crus em temp/judit/ (gitignored) e imprime um resumo,
# para decidirmos campo a campo o que é pertinente antes de escrever codigo.
#
# Ordem dos passos é crescente em custo. Nada que debita credito roda sem flag
# explicita (--live).
#
# Uso:
#   scripts/judit-probe.sh 12345678000190
#   scripts/judit-probe.sh --count-only 12345678000190 98765432000100
#   scripts/judit-probe.sh --side Passive 12345678000190
#   scripts/judit-probe.sh --live 12345678000190      # debita credito!
#
# Requer: curl, jq. Le JUDIT_API_KEY do .env na raiz do projeto.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT/temp/judit"

LAWSUITS_HOST="https://lawsuits.production.judit.io"
REQUESTS_HOST="https://requests.production.judit.io"

COUNT_ONLY=0
LIVE=0
SIDE=""
DOCS=()

# ─── cores (desligadas se nao for tty) ────────────────────────────────────────
if [ -t 1 ]; then
  R=$'\033[0m'; B=$'\033[1m'; DIM=$'\033[2m'
  RED=$'\033[31m'; GRN=$'\033[32m'; YLW=$'\033[33m'; CYN=$'\033[36m'
else
  R=""; B=""; DIM=""; RED=""; GRN=""; YLW=""; CYN=""
fi

die() { echo "${RED}erro:${R} $*" >&2; exit 1; }

# ─── args ─────────────────────────────────────────────────────────────────────
while [ $# -gt 0 ]; do
  case "$1" in
    --count-only) COUNT_ONLY=1; shift ;;
    --live)       LIVE=1; shift ;;
    --side)       SIDE="${2:-}"; [ -n "$SIDE" ] || die "--side exige valor (Active|Passive|Interested|Unknown)"; shift 2 ;;
    -h|--help)    sed -n '2,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*)           die "flag desconhecida: $1" ;;
    *)            DOCS+=("$1"); shift ;;
  esac
done

[ ${#DOCS[@]} -gt 0 ] || die "informe ao menos um CNPJ ou CPF. Veja --help"
command -v curl >/dev/null || die "curl nao encontrado"
command -v jq   >/dev/null || die "jq nao encontrado (brew install jq)"

# ─── credencial ───────────────────────────────────────────────────────────────
if [ -z "${JUDIT_API_KEY:-}" ] && [ -f "$ROOT/.env" ]; then
  # le so a linha da chave; nao da source no .env inteiro (tem senha de banco la)
  JUDIT_API_KEY="$(grep -E '^JUDIT_API_KEY=' "$ROOT/.env" | head -1 | cut -d= -f2- | tr -d '"'"'"' \r')"
fi
[ -n "${JUDIT_API_KEY:-}" ] || die "JUDIT_API_KEY nao definida. Adicione no .env (que ja e gitignored)."

mkdir -p "$OUT_DIR"

# ─── helpers ──────────────────────────────────────────────────────────────────

# so digitos -> decide search_type pelo tamanho
detect_type() {
  local digits="${1//[^0-9]/}"
  case "${#digits}" in
    11) echo "cpf" ;;
    14) echo "cnpj" ;;
    *)  echo "" ;;
  esac
}

# monta o filtro opcional (--side). Judit exige o objeto search_params.filter
filter_json() {
  if [ -n "$SIDE" ]; then
    printf '{"filter":{"side":"%s"}%s}' "$SIDE" "${1:-}"
  else
    if [ -n "${1:-}" ]; then printf '{%s}' "${1#,}"; else printf 'null'; fi
  fi
}

# call <slug> <metodo> <url> [body]
# Salva body em temp/judit/<doc>-<slug>.json e headers em .headers.txt.
# Ecoa o status. Retorna 1 se status >= 400 (404 e tratado pelo chamador).
DOC_TAG=""
LAST_STATUS=""
call() {
  local slug="$1" method="$2" url="$3" body="${4:-}"
  local base="$OUT_DIR/${DOC_TAG}-${slug}"
  local status

  if [ "$method" = "POST" ]; then
    status=$(curl -sS -X POST "$url" \
      -H "api-key: $JUDIT_API_KEY" \
      -H 'Content-Type: application/json' \
      -D "$base.headers.txt" \
      -o "$base.json" \
      -w '%{http_code}' \
      --max-time 180 \
      --data "$body" 2>"$base.curlerr") || true
  else
    status=$(curl -sS "$url" \
      -H "api-key: $JUDIT_API_KEY" \
      -D "$base.headers.txt" \
      -o "$base.json" \
      -w '%{http_code}' \
      --max-time 180 2>"$base.curlerr") || true
  fi

  LAST_STATUS="${status:-000}"

  # guarda o corpo enviado, pra saber depois o que gerou cada payload
  [ -n "$body" ] && printf '%s\n' "$body" | jq . > "$base.request.json" 2>/dev/null

  # rate limit, se o header vier
  local reset
  reset=$(grep -i '^x-ratelimit' "$base.headers.txt" 2>/dev/null | tr -d '\r' | paste -sd' ' -)
  [ -n "$reset" ] && echo "      ${DIM}$reset${R}"

  if [ "$LAST_STATUS" = "000" ]; then
    echo "      ${RED}falha de rede${R}: $(head -1 "$base.curlerr" 2>/dev/null)"
    return 1
  fi

  [ "$LAST_STATUS" -lt 400 ]
}

# imprime erro de forma legivel.
# A Judit aninha o erro: {"error":{"name":...,"message":...,"data":["detalhe"]}}
show_error() {
  local slug="$1"
  local base="$OUT_DIR/${DOC_TAG}-${slug}"   # linha separada: referenciar $slug
  local msg                                  # no mesmo 'local' quebra com set -u
  msg=$(jq -r '
    [ (.error.message? // empty),
      (.error.data? // [] | if type == "array" then .[] else . end),
      (.message? // empty), (.detail? // empty) ]
    | map(select(. != null and . != "")) | unique | join(" — ")' \
    "$base.json" 2>/dev/null | head -3)
  [ -n "$msg" ] && echo "      ${DIM}${msg}${R}"
  return 0
}

# ─── passos ───────────────────────────────────────────────────────────────────

probe_count() {
  local type="$1" key="$2"
  local body
  body=$(jq -nc --arg t "$type" --arg k "$key" --argjson sp "$(filter_json)" \
    '{search:({search_type:$t,search_key:$k} + (if $sp == null then {} else {search_params:$sp} end))}')

  echo "  ${B}1. /lawsuits/count${R} ${DIM}(barato)${R}"
  if call count POST "$LAWSUITS_HOST/lawsuits/count" "$body"; then
    local total
    total=$(jq -r '.total // empty' "$OUT_DIR/${DOC_TAG}-count.json")
    if [ -z "$total" ]; then
      echo "      ${YLW}HTTP $LAST_STATUS mas sem campo .total${R} — ver ${DOC_TAG}-count.json"
      return 0
    fi
    if [ "$total" -eq 0 ]; then
      echo "      ${GRN}total: 0${R} — sem exposicao judicial"
    else
      echo "      ${GRN}total: $total${R} processos"
    fi
    echo "$total"> "$OUT_DIR/${DOC_TAG}.total"
  elif [ "$LAST_STATUS" = "404" ]; then
    echo "      ${GRN}404${R} — sem processos (resultado valido, nao e erro)"
    echo "0" > "$OUT_DIR/${DOC_TAG}.total"
  else
    echo "      ${RED}HTTP $LAST_STATUS${R}"
    show_error count
    return 1
  fi
}

probe_synthetic() {
  local type="$1" key="$2"
  local body
  body=$(jq -nc --arg t "$type" --arg k "$key" --argjson sp "$(filter_json)" \
    '{search:({search_type:$t,search_key:$k} + (if $sp == null then {} else {search_params:$sp} end))}')

  echo "  ${B}2. /lawsuits/synthetic${R} ${DIM}(agregados)${R}"
  if call synthetic POST "$LAWSUITS_HOST/lawsuits/synthetic" "$body"; then
    local f="$OUT_DIR/${DOC_TAG}-synthetic.json"
    echo "      ${DIM}lawsuits_count:${R} $(jq -r '.lawsuits_count // "—"' "$f")"
    for bucket in tribunals areas classifications subjects; do
      local top
      top=$(jq -r --arg b "$bucket" \
        'if (.[$b]|type) == "array" then (.[$b] | sort_by(-.count) | .[0:4] | map("\(.value) (\(.count))") | join(", ")) else empty end' \
        "$f" 2>/dev/null)
      [ -n "$top" ] && printf '      %-16s %s\n' "$bucket:" "$top"
    done
  else
    echo "      ${YLW}HTTP $LAST_STATUS${R}"; show_error synthetic
  fi
}

probe_lawsuits() {
  local type="$1" key="$2" inferred="$3" slug="$4" label="$5"
  local body
  body=$(jq -nc --arg t "$type" --arg k "$key" \
    --argjson sp "$(filter_json ",\"with_inferred_parties\":$inferred")" \
    '{search:{search_type:$t,search_key:$k,search_params:$sp}}')

  echo "  ${B}${label}${R}"
  if call "$slug" POST "$LAWSUITS_HOST/lawsuits?page=1&page_size=10" "$body"; then
    local f="$OUT_DIR/${DOC_TAG}-${slug}.json"
    local n
    n=$(jq -r '(.response_data // .lawsuits // .page_data // []) | length' "$f")
    echo "      ${GRN}$n processos no payload${R} ${DIM}($(wc -c <"$f" | tr -d ' ') bytes)${R}"
    echo "      ${DIM}chaves de topo:${R} $(jq -r 'keys | join(", ")' "$f" 2>/dev/null)"
  else
    [ "$LAST_STATUS" = "404" ] && echo "      ${GRN}404${R} — sem processos" || { echo "      ${RED}HTTP $LAST_STATUS${R}"; show_error "$slug"; }
  fi
}

probe_live() {
  local type="$1" key="$2"
  local body
  body=$(jq -nc --arg t "$type" --arg k "$key" \
    '{search:{search_type:$t,search_key:$k,response_type:"lawsuits"},with_attachments:false}')

  echo "  ${B}5. /requests${R} ${YLW}(ao vivo no tribunal — DEBITA CREDITO)${R}"
  if ! call request-create POST "$REQUESTS_HOST/requests" "$body"; then
    echo "      ${RED}HTTP $LAST_STATUS${R}"; show_error request-create; return 1
  fi

  local rid
  rid=$(jq -r '.request_id // empty' "$OUT_DIR/${DOC_TAG}-request-create.json")
  [ -n "$rid" ] || { echo "      ${RED}sem request_id na resposta${R}"; return 1; }
  echo "      request_id: ${CYN}$rid${R} — aguardando tribunal"

  local waited=0 st=""
  while [ $waited -lt 180 ]; do
    sleep 5; waited=$((waited + 5))
    call request-status GET "$REQUESTS_HOST/requests/$rid" >/dev/null
    st=$(jq -r '.status // empty' "$OUT_DIR/${DOC_TAG}-request-status.json")
    printf '\r      %ss  status: %s          ' "$waited" "${st:-?}"
    case "$st" in completed|failed) break ;; esac
  done
  echo

  if [ "$st" != "completed" ]; then
    echo "      ${YLW}nao concluiu em ${waited}s (status: ${st:-?})${R} — request_id salvo, pode buscar depois"
    return 0
  fi

  if call responses GET "$REQUESTS_HOST/responses?request_id=$rid&page=1&page_size=100"; then
    local f="$OUT_DIR/${DOC_TAG}-responses.json"
    echo "      ${GRN}$(jq -r '.all_count // 0' "$f") resultados ao vivo${R}"
    echo "      ${DIM}cache?${R} $(jq -r '[.page_data[]?.cached_response] | unique | join(", ")' "$f" 2>/dev/null)"
  fi
}

# ─── main ─────────────────────────────────────────────────────────────────────

echo
echo "${B}Judit — sonda exploratoria${R}"
echo "${DIM}saida: temp/judit/  (gitignored — contem dado real de cliente)${R}"
[ -n "$SIDE" ] && echo "${DIM}filtro: side=$SIDE${R}"
[ $LIVE -eq 1 ] && echo "${YLW}modo --live: o passo 5 debita credito${R}"
echo

for doc in "${DOCS[@]}"; do
  digits="${doc//[^0-9]/}"
  type="$(detect_type "$doc")"
  DOC_TAG="$digits"

  echo "${B}${CYN}${doc}${R} ${DIM}[${type:-indefinido}]${R}"

  if [ -z "$type" ]; then
    echo "  ${RED}nao e CPF (11) nem CNPJ (14): ${#digits} digitos${R}"; echo; continue
  fi

  probe_count "$type" "$digits" || { echo; continue; }

  total=$(cat "$OUT_DIR/${DOC_TAG}.total" 2>/dev/null || echo 0)
  if [ "$COUNT_ONLY" -eq 1 ]; then echo; continue; fi
  if [ "$total" -eq 0 ]; then
    echo "  ${DIM}zero processos — pulando os passos seguintes${R}"; echo; continue
  fi

  probe_synthetic "$type" "$digits"
  probe_lawsuits "$type" "$digits" false lawsuits          "3. /lawsuits"
  probe_lawsuits "$type" "$digits" true  lawsuits-inferred "4. /lawsuits (with_inferred_parties)"

  # quanto do resultado so aparece por vinculo inferido pela Judit (risco de homonimo)
  base_f="$OUT_DIR/${DOC_TAG}-lawsuits.json"
  inf_f="$OUT_DIR/${DOC_TAG}-lawsuits-inferred.json"
  if [ -s "$base_f" ] && [ -s "$inf_f" ]; then
    extra=$(jq -rs '
      def codes: (.response_data // .lawsuits // []) | map(.code) | unique;
      ((.[1] | codes) - (.[0] | codes)) | length' "$base_f" "$inf_f" 2>/dev/null)
    [ -n "${extra:-}" ] && [ "$extra" != "0" ] && \
      echo "      ${YLW}+$extra processo(s) so aparecem via inferencia${R} ${DIM}— checar was_inferred antes de usar${R}"
  fi

  [ $LIVE -eq 1 ] && probe_live "$type" "$digits"

  echo
done

echo "${B}payloads salvos em${R} temp/judit/"
ls -1 "$OUT_DIR"/*.json 2>/dev/null | sed "s|$OUT_DIR/|  |" || echo "  (nenhum)"
echo
