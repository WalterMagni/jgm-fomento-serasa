#!/usr/bin/env bash
# Cria cenários da esteira de liberação para validar a tela /liberacao.
#
# Monta três usuários de cenário (uma auxiliar e duas analistas do Comitê) e oito cards que
# cobrem o que a tela precisa mostrar: card novo, prazo vencido, Comitê esperando os pareceres,
# Comitê com um parecer e uma menção, pendência aberta, finalizado aprovado, finalizado
# parcialmente aprovado e reaberto em rodada nova. Nada aqui chama API externa paga.
#
# Os usuários de cenário entram no Comitê. Enquanto existirem, todo card que entrar no Comitê
# neste banco espera o parecer deles também — rode só em ambiente local e limpe depois.
#
# USO
#   scripts/liberacao-cenarios.sh            # cria os cenários
#   scripts/liberacao-cenarios.sh --limpar   # apaga só o que este script criou
#
# VARIÁVEIS
#   API_URL   padrão http://localhost:8080/api/v1
#   PSQL      comando para falar com o banco; padrão usa o container do compose
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080/api/v1}"
PSQL="${PSQL:-docker exec -i portal-serasa-db psql -U serasa -d portal_serasa}"
SENHA="Cenario@123"
DOMINIO="liberacao.test"

# CNPJs de teste com dígito verificador inválido de propósito: não colidem com empresa real da
# carteira e não viram link de empresa nos textos. A esteira aceita cedente fora da base.
CNPJS=(
  "20000000000101" # 1. novo na origem
  "20000000000202" # 2. prazo vencido
  "20000000000303" # 3. Comitê esperando os dois
  "20000000000404" # 4. Comitê com um parecer
  "20000000000505" # 5. pendência aberta
  "20000000000606" # 6. finalizado aprovado
  "20000000000707" # 7. finalizado parcial
  "20000000000808" # 8. reaberto
)

sql() { $PSQL -v ON_ERROR_STOP=1 -qtA; }

if [[ "${1:-}" == "--limpar" ]]; then
  echo "Apagando os cenários…"
  {
    printf "DELETE FROM liberacao_card WHERE cedente_cnpj IN ("
    printf "'%s'," "${CNPJS[@]}" | sed 's/,$//'
    printf ");\n"
    # Etiqueta criada pelo cenário e que nenhum card real usa.
    echo "DELETE FROM liberacao_etiqueta e WHERE e.criado_por_nome LIKE 'Cenário %' AND NOT EXISTS (SELECT 1 FROM liberacao_card_etiqueta v WHERE v.etiqueta_id = e.id);"
    echo "DELETE FROM users WHERE email LIKE 'cenario-%@$DOMINIO';"
  } | sql
  echo "Pronto."
  exit 0
fi

BASE_HOST="${API_URL%/api/v1}"
J="Content-Type: application/json"

registrar() { # nome, email
  curl -s -o /dev/null -X POST "$BASE_HOST/api/auth/register" -H "$J" \
    -d "{\"name\":\"$1\",\"email\":\"$2\",\"password\":\"$SENHA\"}"
}
entrar() { # email -> "token id"
  curl -sf -X POST "$BASE_HOST/api/auth/login" -H "$J" -d "{\"email\":\"$1\",\"password\":\"$SENHA\"}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d["token"], d["id"])'
}

echo "→ Usuários de cenário"
registrar "Cenário Auxiliar" "cenario-auxiliar@$DOMINIO"
registrar "Cenário Andressa" "cenario-andressa@$DOMINIO"
registrar "Cenário Mychelly" "cenario-mychelly@$DOMINIO"
# Marcar papel é ato de admin; aqui vai direto no banco para o script não precisar da senha dele.
echo "UPDATE users SET analista = true, comite = true WHERE email IN ('cenario-andressa@$DOMINIO','cenario-mychelly@$DOMINIO');" | sql >/dev/null

read -r AUX AUX_ID <<<"$(entrar "cenario-auxiliar@$DOMINIO")"
read -r AN1 AN1_ID <<<"$(entrar "cenario-andressa@$DOMINIO")"
read -r AN2 _ <<<"$(entrar "cenario-mychelly@$DOMINIO")"

chamar() { # token, método, caminho, [json] -> corpo
  curl -sf -X "$2" "$API_URL$3" -H "Authorization: Bearer $1" -H "$J" ${4:+-d "$4"}
}
campo() { python3 -c "import sys,json;print(json.load(sys.stdin)[\"$1\"])"; }

criar() { # cnpj, nome, tipo, valor, prazo(ISO ou vazio) -> id
  local prazo="null"
  [[ -n "${5:-}" ]] && prazo="\"$5\""
  chamar "$AUX" POST /liberacao "{\"cedenteCnpj\":\"$1\",\"cedenteNome\":\"$2\",\"tipoOperacao\":\"$3\",\"valor\":$4,\"prazo\":$prazo,
    \"parecerOrigem\":\"Cedente com histórico regular. Cenário de validação.\",
    \"sacados\":[{\"documento\":\"11222333000181\",\"valor\":$4},{\"documento\":\"11444777000161\",\"valor\":1000}]}" | campo id
}
mover() { # token, id, de, para, [json extra]
  chamar "$1" PATCH "/liberacao/$2/etapa" "{\"de\":\"$3\",\"para\":\"$4\"${5:+,$5}}" >/dev/null
}
parecer() { # token, id, posição, texto
  chamar "$1" POST "/liberacao/$2/parecer" "{\"posicao\":\"$3\",\"texto\":\"$4\"}" >/dev/null
}
data() { python3 -c "import datetime;print((datetime.datetime.now()+datetime.timedelta(hours=$1)).strftime('%Y-%m-%dT%H:%M'))"; }

URGENTE=$(chamar "$AUX" POST /liberacao/etiquetas '{"nome":"Cenário urgente","cor":"VERMELHO"}' | campo id)

echo "→ 1/8 novo na Origem, prazo amanhã"
ID1=$(criar "${CNPJS[0]}" "ALFA CENARIO COMERCIO LTDA" DUPLICATA 45000 "$(data 26)")

echo "→ 2/8 prazo vencido, etiqueta e cor"
ID2=$(criar "${CNPJS[1]}" "BETA CENARIO TRANSPORTES LTDA" CHEQUE 18000 "$(data -5)")
chamar "$AUX" PUT "/liberacao/$ID2/etiquetas" "{\"ids\":[\"$URGENTE\"]}" >/dev/null
chamar "$AUX" PATCH "/liberacao/$ID2/cor" '{"cor":"VERMELHO"}' >/dev/null

echo "→ 3/8 Comitê esperando os dois pareceres"
ID3=$(criar "${CNPJS[2]}" "GAMA CENARIO INDUSTRIA LTDA" COMISSARIA 320000 "$(data 48)")
mover "$AUX" "$ID3" ORIGEM COMITE

echo "→ 4/8 Comitê com um parecer e a auxiliar marcada"
ID4=$(criar "${CNPJS[3]}" "DELTA CENARIO DISTRIBUIDORA LTDA" DUPLICATA 87000 "")
mover "$AUX" "$ID4" ORIGEM COMITE
parecer "$AN1" "$ID4" FAVORAVEL "Concentração baixa. @[Cenário Auxiliar](user:$AUX_ID), confirme o endereço do sacado."

# Sair do Comitê exige o parecer de todo mundo marcado como Comitê neste banco. Com gente real
# no Comitê, o script não tem como dar o parecer dela — para nos quatro primeiros cenários.
OUTROS=$(echo "SELECT count(*) FROM users WHERE comite AND email NOT LIKE 'cenario-%@$DOMINIO';" | sql)
if [[ "$OUTROS" != "0" ]]; then
  echo
  echo "Há $OUTROS pessoa(s) no Comitê além das de cenário. Os cenários 5 a 8 decidem o Comitê e"
  echo "precisariam do parecer delas; ficaram de fora. Criados: 1 a 4."
  echo "Para apagar: scripts/liberacao-cenarios.sh --limpar"
  exit 0
fi

echo "→ 5/8 pendência aberta para a auxiliar"
ID5=$(criar "${CNPJS[4]}" "EPSILON CENARIO ALIMENTOS LTDA" INTERCOMPANY 56000 "")
mover "$AUX" "$ID5" ORIGEM COMITE
parecer "$AN1" "$ID5" FAVORAVEL "Ok."
parecer "$AN2" "$ID5" COM_RESSALVAS "Falta aceite."
mover "$AN1" "$ID5" COMITE PENDENCIA "\"pendencias\":[{\"destinatarioId\":\"$AUX_ID\",\"texto\":\"Confirmar aceite das duplicatas com o sacado.\"}]"

APROVA_TUDO='"decisoes":[{"documento":"11222333000181","situacao":"APROVADO"},{"documento":"11444777000161","situacao":"APROVADO"}]'

echo "→ 6/8 finalizado aprovado"
ID6=$(criar "${CNPJS[5]}" "ZETA CENARIO CONFECCOES LTDA" DUPLICATA 210000 "")
mover "$AUX" "$ID6" ORIGEM COMITE
parecer "$AN1" "$ID6" FAVORAVEL "Ok."
parecer "$AN2" "$ID6" FAVORAVEL "Ok."
mover "$AN2" "$ID6" COMITE FINALIZADO "$APROVA_TUDO"',"observacao":"Liberado."'

echo "→ 7/8 finalizado parcial: só um parecer, um sacado reprovado e um parcial"
ID7=$(criar "${CNPJS[6]}" "ETA CENARIO ATACADISTA LTDA" CHEQUE 39000 "")
mover "$AUX" "$ID7" ORIGEM COMITE
parecer "$AN1" "$ID7" COM_RESSALVAS "Um sacado com restrição recente."
mover "$AN1" "$ID7" COMITE FINALIZADO '"decisoes":[{"documento":"11222333000181","situacao":"PARCIAL","valorAprovado":20000},{"documento":"11444777000161","situacao":"REPROVADO"}],"observacao":"Sem o parecer da Mychelly, em férias."'

echo "→ 8/8 aprovado e reaberto: rodada 2 no Comitê"
ID8=$(criar "${CNPJS[7]}" "TETA CENARIO METAIS LTDA" COMISSARIA 150000 "")
mover "$AUX" "$ID8" ORIGEM COMITE
parecer "$AN1" "$ID8" FAVORAVEL "Ok."
parecer "$AN2" "$ID8" FAVORAVEL "Ok."
mover "$AN1" "$ID8" COMITE FINALIZADO "$APROVA_TUDO"
mover "$AN1" "$ID8" FINALIZADO COMITE '"observacao":"Cliente pediu aumento de limite."'

echo
echo "Pronto. Entre com cenario-auxiliar@$DOMINIO, cenario-andressa@$DOMINIO ou"
echo "cenario-mychelly@$DOMINIO (senha $SENHA) e abra /liberacao."
echo "Para apagar: scripts/liberacao-cenarios.sh --limpar"
