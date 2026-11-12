# ADR 0005 — Trace distribuído atravessando o outbox

- **Status:** aceita
- **Data:** 2026-11-06

## Contexto

O Tech Challenge pede monitoramento e rastreamento dos fluxos distribuídos, com as ferramentas da
Fase 3 (New Relic). O agent do New Relic propaga o trace sozinho em chamadas HTTP e em mensagens
publicadas dentro de uma transação. Com o outbox (ADR 0002), porém, quem publica não é a transação
da requisição: o evento é gravado no banco e um publicador agendado envia depois. Sem tratamento, o
trace da requisição terminaria no banco.

## Decisão

- O `OutboxService` captura os cabeçalhos do trace atual (`traceparent`, `tracestate`, `newrelic`) e
  os guarda com o evento (`outbox_evento.rastreamento`).
- O `OutboxPublicador` coloca esses cabeçalhos na mensagem: ela sai no trace da requisição.
- O consumidor abre uma transação `Mensagem/<tipo>` e aceita o trace da mensagem; os comandos que ele
  publica em seguida (via outbox) levam o mesmo trace.
- O código de negócio fala com a interface `Telemetria`, não com o New Relic: a propagação é testada
  com uma implementação em memória (`RastreamentoDistribuidoIntegrationTest`).

## Consequências

- Uma OS aparece num trace único: requisição do atendente → Billing → OS Service → Execução.
- Billing e Execução precisam publicar a resposta dentro da transação que consumiu o comando (ou
  copiar os cabeçalhos), senão o trace termina neles.
- A propagação real só é observável com o agent conectado a uma conta New Relic; sem o agent
  (testes, docker-compose), a telemetria é no-op.
