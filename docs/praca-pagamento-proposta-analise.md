# Proposta de Analise - Praca de Pagamento

Data: 2026-06-02

## Objetivo

Estruturar uma analise assistida para os lancamentos de retorno bancario relacionados a praca de pagamento, especialmente nas secoes:

- Retorno Bancario - Auditoria Eletronica
- Retorno Bancario - Agencias Nao Localizadas

A proposta nao e identificar o pagador com certeza absoluta. O objetivo e organizar evidencias para ajudar o analista a decidir se o pagamento provavelmente partiu do sacado, do cedente ou se o caso deve permanecer inconclusivo.

## Contexto do problema

O relatorio bancario traz informacoes importantes, como titulo, sacado, municipio do cliente, municipio do sacado, banco/agencia e complemento da ocorrencia. Porem, com bancos digitais, cooperativas, correspondentes bancarios e canais eletronicos de pagamento, a agencia indicada nem sempre representa uma praca fisica confiavel.

No relatorio avaliado, uma parcela relevante das agencias/bancos apresentou incompatibilidade ou baixa confiabilidade geografica. Isso indica que a praca bancaria deve ser tratada como um sinal de analise, nao como verdade unica.

Tambem ha limites juridicos e operacionais. O banco pode ter informacoes internas sobre a liquidacao do titulo, mas esses dados podem estar protegidos por sigilo bancario e LGPD. Portanto, o sistema deve trabalhar com dados ja disponiveis no processo e fontes publicas/regulares de enriquecimento.

## Principio da solucao

A analise deve ser composta por tres camadas:

1. Dados extraidos do PDF/CNAB
2. Enriquecimento por fontes publicas ou contratadas
3. Pre-analise automatica com regras e apoio de IA

O resultado final deve sempre permitir revisao humana. A decisao oficial continua sendo do analista.

## Dados de entrada

Cada lancamento importado deve preservar os dados originais:

- Identificador do lancamento
- Codigo do cliente
- Numero do titulo
- Vencimento
- Valor do titulo
- Valor pago
- Ocorrencia
- Complemento da ocorrencia
- Documento do sacado
- Nome do sacado
- Municipio do cliente
- Municipio informado da agencia
- Municipio do sacado
- Banco/agencia
- Secao do relatorio

Esses campos formam a base auditavel da analise.

## Enriquecimento bancario

O sistema deve tentar enriquecer banco e agencia usando fontes como Bacen, IFData, lista de participantes Pix/SPI e, quando util, Google Maps/Geocoding.

Campos sugeridos:

- Codigo do banco
- Codigo da agencia
- Nome da instituicao
- ISPB
- CNPJ da instituicao
- Tipo regulatorio Bacen
- Categoria operacional JGM
- Endereco da agencia, quando localizado
- Municipio/UF da agencia, quando localizado
- Confiabilidade geografica
- Motivo da confiabilidade

## Classificacao da instituicao

O Bacen pode ajudar a identificar o tipo regulatorio da instituicao, como:

- Banco Multiplo
- Banco Comercial
- Cooperativa de Credito
- Instituicao de Pagamento
- Sociedade de Credito Direto
- Sociedade de Emprestimo entre Pessoas
- DTVM/CTVM
- Financeira

Porem, "banco digital" nem sempre aparece como classificacao oficial. Por isso, a proposta e manter duas classificacoes.

Classificacao oficial Bacen:

- Derivada de fonte publica ou regulatoria.
- Exemplo: Banco Multiplo, Cooperativa de Credito, Instituicao de Pagamento.

Classificacao operacional JGM:

- Derivada de regras internas.
- Exemplo: tradicional, digital, cooperativa, financeira, indeterminado.

Exemplo:

```json
{
  "codigo_banco": "748",
  "instituicao": "SICREDI",
  "tipo_bacen": "Cooperativa de Credito",
  "categoria_jgm": "cooperativa",
  "confiabilidade_geografica": "media",
  "motivo": "Cooperativa possui vinculo regional, mas a agencia pode nao representar o local real do pagamento."
}
```

## Confiabilidade geografica

A confiabilidade geografica indica o quanto a praca bancaria pode ser usada para inferir o local provavel do pagamento.

Sugestao de niveis:

- Alta: banco tradicional com agencia fisica localizada e municipio confiavel.
- Media: cooperativa ou instituicao com vinculo regional, mas com incerteza operacional.
- Baixa: banco digital, instituicao de pagamento, agencia generica, agencia nao localizada ou banco/agencia incompativel.
- Indeterminada: dados insuficientes ou codigo nao identificado.

Essa classificacao deve reduzir ou aumentar o peso da praca na analise.

## Regras objetivas

Antes de usar IA, o sistema deve calcular evidencias deterministicas:

- Agencia localizada no mesmo municipio do sacado.
- Agencia localizada no mesmo municipio do cliente/cedente.
- Agencia localizada distante de sacado e cedente.
- Agencia nao localizada.
- Banco/agencia incompativel.
- Instituicao com baixa confiabilidade geografica.
- Complemento indica pagamento fora da praca do sacado.
- Complemento indica pagamento na praca do cliente.
- Complemento indica pagamento na agencia do cliente.
- Sacado possui historico de pagamento semelhante.
- Cedente possui historico de pagamento semelhante.
- Banco/agencia recorrente para o mesmo sacado.
- Banco/agencia recorrente para o mesmo cedente.

Cada regra deve gerar uma evidencia positiva, negativa ou neutra.

## Score de pre-classificacao

O sistema pode calcular um score antes da analise humana:

- Provavel sacado
- Provavel cedente
- Inconclusivo
- Baixa confiabilidade da praca
- Exige revisao manual

Exemplo de fatores:

- Agencia proxima ao sacado aumenta score de "provavel sacado".
- Agencia proxima ao cedente aumenta score de "provavel cedente".
- Banco digital reduz peso geografico.
- Agencia nao localizada reduz peso geografico.
- Historico recorrente aumenta confianca.
- Divergencia entre PDF e fonte externa reduz confianca.

O score deve ser explicavel, nao uma caixa-preta.

## Uso do Gemini

O Gemini deve atuar como assistente de analise, nao como decisor final.

Entrada sugerida para IA:

- Dados originais do lancamento
- Dados enriquecidos da instituicao/agencia
- Distancias calculadas
- Regras acionadas
- Historico relevante do sacado/cedente
- Resultado preliminar do score

Saida esperada:

- Classificacao sugerida
- Nivel de confianca
- Justificativa curta
- Fatores a favor
- Fatores contra
- Recomendacao para o analista

Exemplo:

```json
{
  "classificacao_sugerida": "provavel_sacado",
  "confianca": "media",
  "resumo": "A praca bancaria tem aderencia maior ao municipio do sacado do que ao municipio do cliente.",
  "fatores_a_favor": [
    "Municipio da agencia proximo ao municipio do sacado",
    "Municipio do cliente distante da agencia",
    "Banco tradicional com agencia localizada"
  ],
  "fatores_contra": [
    "Ocorrencia indica pagamento irregular",
    "Nao ha historico suficiente para confirmar recorrencia"
  ],
  "recomendacao": "Revisar manualmente antes de concluir."
}
```

## Tela operacional

A tela de Praca de Pagamento deve permitir:

- Importar arquivo diario.
- Visualizar lotes importados.
- Filtrar por secao, banco, tipo de instituicao, confiabilidade, status e decisao.
- Ver dados originais do lancamento.
- Ver enriquecimento bancario.
- Ver mapa ou distancias entre cliente, sacado e agencia.
- Ver sugestao automatica.
- Ver justificativa da IA.
- Registrar decisao do analista: sacado, cedente ou inconclusivo.
- Registrar observacao manual.

## Resultado na pagina da empresa

Apos revisao, a decisao deve aparecer na pagina da empresa/sacado ou no historico do cliente, conforme o modelo de negocio definido.

Informacoes sugeridas:

- Titulo
- Data de pagamento
- Banco/agencia
- Classificacao final
- Analista responsavel
- Justificativa
- Evidencias principais
- Confianca automatica original
- Data da decisao

## Modelo de dados sugerido

Tabelas ou entidades principais:

- Lote de importacao
- Lancamento de praca de pagamento
- Enriquecimento bancario
- Regra acionada
- Analise automatica
- Decisao humana
- Historico de revisoes

Campos importantes:

- `tipo_bacen`
- `categoria_jgm`
- `confiabilidade_geografica`
- `motivo_confiabilidade`
- `score_sacado`
- `score_cedente`
- `score_confianca`
- `classificacao_sugerida`
- `classificacao_final`
- `justificativa_ia`
- `justificativa_analista`

## Cuidados juridicos e de governanca

O sistema deve evitar inferir ou buscar dados pessoais bancarios que nao estejam disponiveis de forma legitima.

Boas praticas:

- Usar somente dados necessarios para a finalidade.
- Registrar a fonte dos dados enriquecidos.
- Manter trilha de auditoria.
- Separar sugestao automatica de decisao humana.
- Evitar linguagem conclusiva quando a confianca for baixa.
- Permitir revisao e correcao pelo analista.
- Documentar a finalidade da analise: prevencao de risco, auditoria e apoio operacional.

## Fontes externas candidatas

- IFData / Dados Abertos do Banco Central
- Listas publicas de participantes Pix/SPI
- Dados de agencias e instituicoes financeiras
- Google Geocoding / Maps, quando necessario para endereco e coordenadas
- Historico interno de decisoes dos analistas

## Fases de implementacao

Fase 1 - Base operacional:

- Importar PDF diario.
- Persistir lancamentos.
- Exibir tela de revisao.
- Permitir decisao manual.

Fase 2 - Enriquecimento bancario:

- Identificar instituicao pelo banco/ISPB.
- Classificar tipo Bacen.
- Classificar categoria JGM.
- Definir confiabilidade geografica.

Fase 3 - Distancias e evidencias:

- Geocodificar municipios e agencias.
- Calcular distancias.
- Gerar regras acionadas.
- Exibir fatores de analise.

Fase 4 - Pre-analise automatica:

- Criar score deterministico.
- Integrar Gemini para justificativa.
- Exibir sugestao com confianca.

Fase 5 - Aprendizado operacional:

- Comparar sugestao automatica com decisao humana.
- Medir taxa de acerto por regra.
- Ajustar pesos.
- Criar indicadores por banco, agencia, cedente e sacado.

## Indicadores de acompanhamento

- Total de lancamentos importados.
- Percentual de agencias localizadas.
- Percentual de bancos/agencias incompativeis.
- Percentual por tipo de instituicao.
- Percentual de baixa confiabilidade geografica.
- Decisoes como sacado.
- Decisoes como cedente.
- Casos inconclusivos.
- Concordancia entre sugestao automatica e analista.
- Bancos/agencias com maior recorrencia de divergencia.

## Conclusao

A praca de pagamento deve ser tratada como uma evidencia qualificada, nao como prova definitiva. A melhor implementacao combina regras objetivas, enriquecimento de instituicoes, distancias geograficas, historico interno e apoio do Gemini para gerar uma explicacao clara.

Essa abordagem melhora a produtividade da equipe, preserva a decisao humana e cria uma base de aprendizado para evoluir a analise ao longo do tempo.
