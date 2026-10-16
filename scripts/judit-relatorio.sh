#!/usr/bin/env bash
#
# judit-relatorio.sh — gera relatorio TXT legivel (para analista) a partir do
# payload cru salvo por judit-probe.sh.
#
# Nao chama a API. Le temp/judit/<digitos>-lawsuits.json e escreve
# temp/judit/relatorios/<digitos>.txt
#
# Uso:
#   scripts/judit-relatorio.sh 35782346000173
#   scripts/judit-relatorio.sh 35.782.346/0001-73 "D & M INDUSTRIA DE TUBOS"
#   scripts/judit-relatorio.sh --todos
#
# Tratamentos aplicados (ver docs/judit/01-mapa-de-campos.md):
#   - "NÃO INFORMADO" vira "—"
#   - deduplica por numero CNJ (a Judit repete o processo por instancia)
#   - amount 0 vira "nao informado" (nao e causa de valor zero)
#   - identidade pelo CNPJ (main_document), nunca pelo nome

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IN_DIR="$ROOT/temp/judit"
OUT_DIR="$IN_DIR/relatorios"

die() { echo "erro: $*" >&2; exit 1; }
command -v jq >/dev/null || die "jq nao encontrado"

if [ "${1:-}" = "--todos" ]; then
  ALVOS=()
  for f in "$IN_DIR"/*-lawsuits.json; do
    [ -e "$f" ] || die "nenhum payload em temp/judit/. Rode judit-probe.sh antes."
    b="$(basename "$f")"; ALVOS+=("${b%-lawsuits.json}")
  done
else
  [ $# -ge 1 ] || die "informe o CNPJ. Veja o cabecalho do script."
  ALVOS=("${1//[^0-9]/}")
  NOME_ARG="${2:-}"
fi

mkdir -p "$OUT_DIR"

for DOC in "${ALVOS[@]}"; do
  F="$IN_DIR/${DOC}-lawsuits.json"
  S="$IN_DIR/${DOC}-synthetic.json"
  [ -s "$F" ] || { echo "pulando $DOC: sem payload em $F"; continue; }

  OUT="$OUT_DIR/${DOC}.txt"

  # total declarado pela Judit — a rota /lawsuits corta em 1000 sem avisar
  TOTAL_DECLARADO="$(jq -r '.total // empty' "$IN_DIR/${DOC}-count.json" 2>/dev/null)"

  jq -r --arg doc "$DOC" --arg nome_arg "${NOME_ARG:-}" \
        --arg total_declarado "${TOTAL_DECLARADO:-}" \
        --arg gerado "$(date '+%d/%m/%Y %H:%M')" '
    # ── helpers ──────────────────────────────────────────────────────────────
    def ni: if . == null or . == "" or . == "NÃO INFORMADO" or . == "NAO INFORMADO" then "—" else . end;
    # separador de milhar: inverte, fatia de 3 em 3, junta com ponto, desinverte
    def brl: if . == null or . == 0 then "não informado"
             else ((. | floor | tostring | explode | reverse) as $r
                   | [ range(0; ($r | length); 3) as $i | $r[$i:$i+3] | implode ]
                   | join(".") | explode | reverse | implode
                   | "R$ " + .) end;
    def dt: if . == null or . == "" then "—" else (.[8:10] + "/" + .[5:7] + "/" + .[0:4]) end;
    def pad(n): (. + (" " * 200))[0:n];
    def lpad(n): ((" " * 200) + .)[-n:];
    def docs_da_parte: [ (.main_document // empty),
                         ((.documents // [])[] | if type == "object" then (.document // empty) else . end) ]
                       | map(tostring | gsub("[^0-9]"; ""));
    def polo_da_empresa: [ .parties[] | select(docs_da_parte | index($doc)) | .side ] | unique;
    def e_re: polo_da_empresa | index("Passive") != null;
    def lado_pt: if index("Passive") then "RÉ (está sendo processada)"
                 elif index("Active") then "AUTORA (ela processa)"
                 elif index("Interested") then "INTERESSADA"
                 else "—" end;
    def autores: [ .parties[] | select(.side == "Active")
                   | select((.person_type // "") | test("ADVOGAD") | not) | .name ] | unique;
    def linha: "-" * 78;
    def dlinha: "=" * 78;

    # ── deduplica por numero CNJ (Judit repete o processo por instancia) ─────
    # base = 1a instancia (data real de ajuizamento; a 2a traz a data do recurso)
    # mas o ultimo andamento vem da instancia mais avancada
    ( [ .lawsuits[] ] ) as $lawsuits_brutos
    | ( [ .lawsuits[] ] | group_by(.code) | map(
        (sort_by(.instance) | .[0]) as $b
        | $b + { _graus:   (map(.instance) | unique),
                 _ultimo:  (map(.last_step) | map(select(. != null)) | sort_by(.step_date) | last),
                 _abertura:(map(.distribution_date) | map(select(. != null)) | sort | first) }
      ) ) as $proc
    | ($proc | map(select(e_re))) as $passivos
    | ($proc | map(select(polo_da_empresa | index("Active")))) as $ativos
    | ( [ $proc[] | .parties[] | select(docs_da_parte | index($doc)) | .name ]
        | group_by(.) | sort_by(-length) | map(.[0]) ) as $nomes
    | (if $nome_arg != "" then $nome_arg else ($nomes[0] // "—") end) as $nome
    | ( $proc | map(._abertura[0:4]) | group_by(.) | sort_by(.[0]) ) as $anos
    | ( [ $proc[] | .crawler.updated_at // empty ] | sort | last ) as $coleta

    # ── documento ────────────────────────────────────────────────────────────
    | dlinha
    , "RELATÓRIO DE PROCESSOS JUDICIAIS"
    , dlinha
    , ""
    , "Empresa ......: \($nome)"
    , "CNPJ .........: \($doc[0:2]).\($doc[2:5]).\($doc[5:8])/\($doc[8:12])-\($doc[12:14])"
    , "Gerado em ....: \($gerado)"
    , "Fonte ........: Judit (consulta a tribunais brasileiros)"
    , "Dado coletado : até \($coleta | dt)"
    , ""
    , ( if ($nomes | length) > 1 then
          ( [ "ATENÇÃO: esta empresa aparece nos tribunais com \($nomes|length) nomes diferentes.",
              "Todos correspondem ao mesmo CNPJ. Nomes encontrados:" ]
            + ($nomes | map("   - " + .)) + [ "" ] ) | join("\n")
        else empty end )

    # RECUPERACAO JUDICIAL / FALENCIA aparece no NOME da parte, nao no assunto.
    # (o assunto "RECUPERAÇÃO JUDICIAL E FALÊNCIA" costuma ser a empresa habilitando
    #  credito na falencia de terceiro — sinal oposto. Ver docs/judit/01-mapa-de-campos.md)
    , ( ( [ $proc[] | { d: ._abertura,
                        n: ([ .parties[] | select(docs_da_parte | index($doc)) | .name ] | first // "") } ]
          | map(select(.n | test("RECUPERA[ÇC][ÃA]O JUDICIAL|EM RECUPERA|MASSA FALIDA|FAL[ÊE]NCIA"; "i")))
          | sort_by(.d) ) as $rj
        | if ($rj | length) > 0 then
            ([ "*" * 78,
               "SINAL CRÍTICO — RECUPERAÇÃO JUDICIAL / FALÊNCIA",
               "",
               "Em \($rj | length) processo(s) o próprio nome da empresa nos autos traz a marca",
               "de recuperação judicial ou falência. Exemplo:",
               "",
               "   \($rj | last | .n)",
               "   (processo aberto em \($rj | last | .d | dt))",
               "",
               "Isto NÃO se confunde com os processos em que ela habilita crédito na",
               "falência de terceiros — ali ela é credora. Aqui é a situação dela.",
               "*" * 78, "" ] | join("\n"))
          else empty end )

    , ( if ($total_declarado != "" and (($lawsuits_brutos | length) >= 1000)
            and (($total_declarado | tonumber) > ($lawsuits_brutos | length))) then
          ([ "!" * 78,
             "ATENÇÃO — ESTE RELATÓRIO ESTÁ INCOMPLETO",
             "",
             "A Judit informa \($total_declarado) registros para este CNPJ, mas a consulta",
             "devolve no máximo 1.000 por vez. Faltam aproximadamente",
             "\(($total_declarado | tonumber) - ($lawsuits_brutos | length)) registros, que NÃO estão neste documento.",
             "",
             "Os números abaixo são um PISO, não o total. A exposição real é maior.",
             "!" * 78, "" ] | join("\n"))
        else empty end )

    , dlinha
    , "RESUMO"
    , dlinha
    , ""
    , "Total de processos .................: \($proc | length)"
    , "  Em que a empresa é RÉ ............: \($passivos | length)"
    , "  Em que a empresa é AUTORA ........: \($ativos | length)"
    , ""
    , "Valor somado das causas em que é RÉ : \($passivos | map(.amount // 0) | add | brl)"
    , "Valor somado de todas as causas ....: \($proc | map(.amount // 0) | add | brl)"
    , ""
    , "Quando foram abertos:"
    , ( $anos | map("   \(.[0]) " + ("#" * (length | if . > 40 then 40 else . end)) + " \(length)") | join("\n") )
    , ""

    , dlinha
    , "DO QUE A EMPRESA É ACUSADA (processos em que é ré)"
    , dlinha
    , ""
    , ( [ $passivos[] | .subjects[]?.name // "—" ] | group_by(.) | sort_by(-length)
        | map("   \((length|tostring) | lpad(3))x  \(.[0])") | join("\n") )
    , ""
    , "Onde tramitam:"
    , ( [ $passivos[] | .tribunal_acronym ] | group_by(.) | sort_by(-length) | .[0:8]
        | map("   \((length|tostring) | lpad(3))x  \(.[0])") | join("\n") )
    , ""

    , dlinha
    , "LISTA DOS PROCESSOS EM QUE A EMPRESA É RÉ"
    , dlinha
    , ""
    , ( if ($passivos | length) == 0 then "   Nenhum." else
        ( $passivos | sort_by(._abertura) | reverse | to_entries | map(
            "\((.key + 1 | tostring) | lpad(3)). \(.value.subjects[0].name // "—")"
          + "\n     Processo ..: \(.value.code)"
          + "\n     Aberto em .: \(.value._abertura | dt)   \(.value.tribunal_acronym) — \(.value.city | ni)/\(.value.state | ni)"
          + "\n     Valor .....: \(.value.amount | brl)"
          + "\n     Quem move .: \(.value | autores | if length == 0 then "—" else join("; ") end)"
          + "\n     Último ato : \(.value._ultimo.content // "—" | ni) (\(.value._ultimo.step_date | dt))"
          + (if (.value._graus | length) > 1 then "\n     Situação ..: já houve recurso (chegou à 2ª instância)" else "" end)
          + (if .value.secrecy_level > 0 then "\n     *** SEGREDO DE JUSTIÇA — teor não divulgado ***" else "" end)
          ) | join("\n\n") ) end )
    , ""
    , ""

    , ( if ($ativos | length) > 0 then
        ([ dlinha, "PROCESSOS EM QUE A EMPRESA É AUTORA", dlinha, "" ]
        + ($ativos | sort_by(._abertura) | reverse | to_entries | map(
            "\((.key + 1 | tostring) | lpad(3)). \(.value.subjects[0].name // "—")"
          + "\n     Processo ..: \(.value.code)    Aberto em: \(.value._abertura | dt)"
          + "\n     Valor .....: \(.value.amount | brl)   \(.value.tribunal_acronym)"))
        + [ "" ]) | join("\n")
        else empty end )

    , dlinha
    , "COMO LER ESTE RELATÓRIO"
    , dlinha
    , ""
    , "RÉ x AUTORA"
    , "   Ré = alguém processou a empresa. Autora = a empresa processou alguém."
    , "   Para análise de risco, o que pesa é a coluna de RÉ."
    , ""
    , "PROTESTO INDEVIDO DE TÍTULO / SUSTAÇÃO DE PROTESTO"
    , "   Alguém foi protestado e foi à Justiça dizer que não devia aquele título."
    , "   Em volume, é sinal de atenção sobre a origem dos recebíveis da empresa."
    , ""
    , "INCLUSÃO INDEVIDA EM CADASTRO DE INADIMPLENTES"
    , "   Mesma lógica, mas a reclamação é da negativação (SPC/Serasa)."
    , ""
    , "EXECUÇÃO FISCAL / DÍVIDA ATIVA"
    , "   Cobrança de imposto não pago pela empresa."
    , ""
    , "VALOR DA CAUSA"
    , "   É o valor que o autor atribuiu ao processo, não o valor que a empresa"
    , "   vai pagar. Serve para dimensionar, não para somar como dívida."
    , ""
    , "\"não informado\""
    , "   O tribunal não publicou o dado. Não significa ausência ou zero."
    , ""
    , linha
    , "Dado público de tribunal, coletado pela Judit. Documento de uso interno."
    , linha
  ' "$F" > "$OUT"

  echo "gerado: temp/judit/relatorios/${DOC}.txt  ($(wc -l <"$OUT" | tr -d ' ') linhas)"
done
