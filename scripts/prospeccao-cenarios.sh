#!/usr/bin/env bash
# Cria cenários da esteira de prospecção para validar a tela /prospeccao.
#
# Monta oito cards que cobrem o que a tela precisa mostrar: card no prazo, perto do prazo,
# atrasado, cliente em silêncio, checklist parcial, sócio fora do checklist, empresa fora de SP
# (certidão da JUCESP não se aplica) e os dois desfechos terminais.
#
# Os cards nascem sempre com a data de hoje, então o semáforo de SLA só aparece se as datas
# forem recuadas no banco — é o que as chamadas em SQL fazem. Nada aqui chama API externa paga.
#
# USO
#   scripts/prospeccao-cenarios.sh                    # cria os cenários
#   scripts/prospeccao-cenarios.sh --limpar           # apaga só o que este script criou
#
# VARIÁVEIS
#   API_URL   padrão http://localhost:8080/api/v1
#   EMAIL     padrão admin@jgm.com.br
#   SENHA     padrão admin123
#   PSQL      comando para falar com o banco; padrão usa o container do compose
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080/api/v1}"
EMAIL="${EMAIL:-admin@jgm.com.br}"
SENHA="${SENHA:-admin123}"
PSQL="${PSQL:-docker exec -i portal-serasa-db psql -U serasa -d portal_serasa}"

# CNPJs de teste, inválidos de propósito quanto ao dígito verificador para não colidirem com
# empresa real da carteira. O portal não valida dígito na entrada da esteira.
CNPJS=(
  "10000000000101" # 1. no prazo
  "10000000000202" # 2. perto do prazo
  "10000000000303" # 3. atrasado na análise
  "10000000000404" # 4. documentação atrasada
  "10000000000505" # 5. cliente em silêncio
  "10000000000606" # 6. empresa de MG, com sócios
  "10000000000707" # 7. reprovado
  "10000000000808" # 8. removido do radar
)

sql() { $PSQL -v ON_ERROR_STOP=1 -qtA; }

if [[ "${1:-}" == "--limpar" ]]; then
  echo "Apagando os cenários…"
  {
    printf "DELETE FROM prospeccao WHERE cnpj IN ("
    printf "'%s'," "${CNPJS[@]}" | sed 's/,$//'
    printf ");\n"
    echo "DELETE FROM company_details WHERE document_number = '10000000000606';"
    echo "DELETE FROM shareholders WHERE document_mask IN ('***111111**','***222222**','10000000000606');"
  } | sql
  echo "Pronto. Os arquivos enviados continuam no compartilhamento, porque a remoção lá é manual."
  exit 0
fi

echo "→ Autenticando em $API_URL"
# O login mora fora do /v1: /api/auth/login.
BASE_HOST="${API_URL%/api/v1}"
TOKEN=$(curl -sf -X POST "$BASE_HOST/api/auth/login" -H 'Content-Type: application/json' \
          -d "{\"email\":\"$EMAIL\",\"password\":\"$SENHA\"}" \
        | python3 -c 'import sys,json;print(json.load(sys.stdin).get("token",""))')
[[ -n "$TOKEN" ]] || { echo "ERRO: não consegui autenticar $EMAIL em $BASE_HOST/api/auth/login"; exit 1; }
H="Authorization: Bearer $TOKEN"
J="Content-Type: application/json"

criar() { # cnpj, razão social, comercial -> id
  curl -sf -X POST "$API_URL/prospeccao" -H "$H" -H "$J" \
    -d "{\"cnpj\":\"$1\",\"razaoSocial\":\"$2\",\"comercialNome\":\"$3\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])'
}
assumir() { curl -sf -o /dev/null -X PATCH "$API_URL/prospeccao/$1/assumir" -H "$H"; }
mover() {  # id, estagio, [json extra]
  curl -sf -o /dev/null -X PATCH "$API_URL/prospeccao/$1/estagio" -H "$H" -H "$J" \
    -d "{\"estagio\":\"$2\"${3:+,$3}}"
}
cobrar() {
  curl -sf -o /dev/null -X POST "$API_URL/prospeccao/$1/eventos" -H "$H" -H "$J" \
    -d "{\"canal\":\"$2\",\"texto\":\"$3\"}"
}
# Data de N dias ÚTEIS atrás. O SLA conta dias úteis, então recuar dias corridos deixaria o
# semáforo dependendo do dia da semana em que o script roda. Feriado nacional é ignorado aqui,
# e por isso os alvos abaixo têm margem: um feriado só encurta a contagem em um dia.
dias_uteis_atras() {
  python3 -c '
import datetime, sys
faltam = int(sys.argv[1]); d = datetime.date.today()
while faltam > 0:
    d -= datetime.timedelta(days=1)
    if d.weekday() < 5:
        faltam -= 1
print(d.isoformat())' "$1"
}

# Recua a entrada no estágio, que é o que faz o semáforo acender.
envelhecer() { echo "UPDATE prospeccao SET estagio_desde = DATE '$(dias_uteis_atras "$2")' + TIME '09:00' WHERE id = '$1';" | sql >/dev/null; }
docs() { curl -sf "$API_URL/prospeccao/$1/documentos" -H "$H"; }

echo "→ 1/8 no prazo, recém-chegado na triagem"
ID1=$(criar "${CNPJS[0]}" "ALFA COMERCIO DE ALIMENTOS LTDA" "Gustavo")

# Em análise o prazo é de 2 dias úteis, então o âmbar caberia num único dia — frágil demais
# para um cenário. A documentação tem prazo de 10, e o âmbar cobre de 7 a 10.
echo "→ 2/8 perto do prazo: documentação há 8 dias úteis (prazo 10, âmbar a partir de 7)"
ID2=$(criar "${CNPJS[1]}" "BETA TRANSPORTES E LOGISTICA LTDA" "Tiago")
assumir "$ID2"; mover "$ID2" APROVADO; envelhecer "$ID2" 8

echo "→ 3/8 atrasado na análise: 6 dias úteis parada (prazo 2)"
ID3=$(criar "${CNPJS[2]}" "GAMA INDUSTRIA METALURGICA LTDA" "Clóvis")
assumir "$ID3"; envelhecer "$ID3" 6

echo "→ 4/8 documentação atrasada: 15 dias úteis (prazo 10), com checklist parcial"
ID4=$(criar "${CNPJS[3]}" "DELTA DISTRIBUIDORA DE BEBIDAS LTDA" "Gustavo")
assumir "$ID4"; mover "$ID4" APROVADO
# Valida alguns itens para o card mostrar progresso em vez de 0 de 12.
docs "$ID4" | python3 -c '
import sys,json
ids=[d["id"] for d in json.load(sys.stdin) if not d["informativo"]][:5]
print("\n".join(ids))' | while read -r d; do
  curl -sf -o /dev/null -X PATCH "$API_URL/prospeccao/documentos/$d" -H "$H" -H "$J" \
    -d '{"status":"VALIDADO","observacao":"conferido na validação dos cenários"}'
done
envelhecer "$ID4" 15

echo "→ 5/8 cliente em silêncio: cobrado há 40 dias e sem resposta"
ID5=$(criar "${CNPJS[4]}" "EPSILON CONFECCOES LTDA" "Andréia")
assumir "$ID5"; mover "$ID5" APROVADO
cobrar "$ID5" WHATSAPP "Primeira cobrança dos documentos"
cobrar "$ID5" EMAIL "Segunda cobrança, sem retorno"
echo "UPDATE prospeccao SET ultimo_contato_em = now() - INTERVAL '40 days', estagio_desde = DATE '$(dias_uteis_atras 30)' + TIME '09:00' WHERE id = '$ID5';" | sql >/dev/null

echo "→ 6/8 empresa de MG com dois sócios: certidão da JUCESP não se aplica, e um sócio sai do checklist"
{
  echo "INSERT INTO company_details (id, document_number, company_name, state) VALUES (gen_random_uuid(), '${CNPJS[5]}', 'ZETA MINEIRA COMERCIO LTDA', 'MG') ON CONFLICT DO NOTHING;"
  echo "INSERT INTO shareholders (id, document_type, document, document_mask, name, document_source) VALUES
          (gen_random_uuid(),'CPF','11111111111','***111111**','Maria Aparecida Souza','RECEITA_MASK'),
          (gen_random_uuid(),'CPF','22222222222','***222222**','Joao Batista Pereira','RECEITA_MASK'),
          (gen_random_uuid(),'CNPJ','${CNPJS[5]}','${CNPJS[5]}','ZETA HOLDING LTDA','RECEITA_MASK')
        ON CONFLICT DO NOTHING;"
  echo "INSERT INTO shareholder_companies (id, shareholder_id, cnpj_raiz, company_name, source)
          SELECT gen_random_uuid(), s.id, '${CNPJS[5]:0:8}', 'ZETA MINEIRA COMERCIO LTDA', 'RECEITA_MASK'
            FROM shareholders s WHERE s.document_mask IN ('***111111**','***222222**','${CNPJS[5]}')
          ON CONFLICT DO NOTHING;"
} | sql >/dev/null
ID6=$(criar "${CNPJS[5]}" "ZETA MINEIRA COMERCIO LTDA" "DIRETO")
assumir "$ID6"; mover "$ID6" APROVADO
curl -sf -o /dev/null -X PATCH "$API_URL/prospeccao/$ID6/socios" -H "$H" -H "$J" \
  -d '{"socioNome":"Joao Batista Pereira","ativo":false,"motivo":"Saiu da sociedade em 2025"}'

echo "→ 7/8 reprovado por quantidade de restrições"
ID7=$(criar "${CNPJS[6]}" "ETA VINICOLA LTDA" "Tiago")
assumir "$ID7"; mover "$ID7" REPROVADO '"motivoRecusa":"QUANTIDADE_DE_RESTRICOES"'

echo "→ 8/8 removido do radar depois de várias cobranças"
ID8=$(criar "${CNPJS[7]}" "TETA SUPLEMENTOS LTDA" "Clóvis")
assumir "$ID8"; mover "$ID8" APROVADO
cobrar "$ID8" LIGACAO "Ligado três vezes, sem retorno"
mover "$ID8" REMOVIDO_RADAR '"motivoRecusa":"DECISAO_DIRETORIA","observacao":"Sem resposta após diversas cobranças"'

echo
echo "Pronto. Abra /prospeccao e confira:"
cat <<'ROTEIRO'

  Colunas          ALFA em Triagem; GAMA em Em análise; BETA, DELTA, EPSILON e ZETA em
                   Documentos pendentes. ETA e TETA aparecem na seção recolhível
                   "Reprovados e removidos do radar", no pé da página.

  Semáforo         BETA em âmbar (8 de 10 dias úteis); GAMA, DELTA e EPSILON em vermelho;
                   ALFA e ZETA sem cor. A barra colorida fica na borda esquerda do card.

  Sem resposta     EPSILON traz a faixa vermelha "Sem resposta há mais de 30 dias".

  Contador         o badge vermelho ao lado de "Esteira de Prospecção", no menu, soma os
                   cards que precisam de atenção — e não conta duas vezes quem está
                   atrasado e calado ao mesmo tempo.

  Progresso        DELTA mostra "5/12 docs" e ZETA "1/17"; ALFA não mostra nada, porque o
                   checklist só nasce na aprovação.

  Arraste          arraste ALFA para Em análise: a coluna aceita. Tente arrastar para
                   Documentos completos: a coluna esmaece e não aceita, porque a
                   transição não existe. O botão "Mover" no card faz o mesmo por menu.

  Trava de docs    abra DELTA e tente "Documentos completos": aparece a mensagem com a
                   lista nominal do que falta, e o card não anda.

  ZETA             abra e veja, no checklist: "Certidão simplificada" já em "Não se aplica"
                   (empresa é de MG e a certidão é da JUCESP); bloco de Maria ativo; bloco
                   de João esmaecido com "fora do checklist — Saiu da sociedade em 2025",
                   e os itens dele não travam mais o fechamento. O sócio pessoa jurídica
                   (ZETA HOLDING) não gera bloco nenhum.

  Anexo            em qualquer item, "anexar" sobe um PDF. O status vira "Recebido, a
                   conferir" — não "Validado". Reenviar cria v2 sem apagar a v1.
                   Requer a pasta base configurada em Sistema.

  Histórico        na aba Histórico do EPSILON estão as duas cobranças, com canal e autor.
                   Registrar nova cobrança zera o contador de silêncio.

  Reabrir          abra ETA (reprovado) e use "Reabrir em triagem": volta para Triagem,
                   marca "1ª reanálise" e solta o analista anterior.

Para desfazer tudo:  scripts/prospeccao-cenarios.sh --limpar
ROTEIRO
