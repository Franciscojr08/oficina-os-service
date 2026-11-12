# Observabilidade do OS Service

O OS Service usa o New Relic, como a API principal da Fase 3. No EKS o agent Java é injetado pelo
k8s-agents-operator do cluster (`k8s/overlays/eks/newrelic-instrumentation.yaml`), sem mudança no
Dockerfile. Fora do cluster (testes, docker-compose) não há agent e toda a telemetria vira no-op.

## O que é enviado

### Tracing distribuído entre os serviços

| Trecho | Como o trace continua |
| --- | --- |
| Requisição HTTP → evento gravado no outbox | O contexto do trace (`traceparent`, `tracestate`, `newrelic`) é guardado junto com o evento (`outbox_evento.rastreamento`) |
| Outbox → RabbitMQ | O publicador roda depois, em outra transação, e coloca os cabeçalhos guardados na mensagem: o trace é o da requisição, não o do publicador |
| RabbitMQ → Billing / Execução | Quem consome aceita os cabeçalhos (o agent do New Relic faz isso nos consumidores instrumentados) e publica a resposta dentro da mesma transação |
| Billing / Execução → OS Service | O consumidor abre uma transação `Mensagem/<tipo>` e aceita o trace da mensagem; os comandos publicados em seguida (por exemplo, `EnfileirarExecucao`) levam o mesmo trace |

Resultado: uma OS aparece num único trace, da requisição do atendente até a Execução, passando pelo
Billing. O `correlationId` continua nos logs e nos envelopes, como antes.

### Eventos de negócio (consultáveis por NRQL)

| Evento | Quando | Atributos principais |
| --- | --- | --- |
| `SagaOrdemServicoTransicao` | Toda transição da Saga | `ordemServicoId`, `gatilho`, `etapa`, `situacao`, `encerrada`, `motivo`, `tentativas`, `duracaoMs` (no encerramento) |
| `SagaComandoReenviado` | Comando reenviado por falta de resposta no prazo | `ordemServicoId`, `etapa`, `tentativa` |
| `MensagemRejeitada` | Mensagem enviada para a DLQ | `tipo`, `messageId`, `motivo`, `ordemServicoId` |
| `OutboxFalhaPublicacao` | Broker recusou ou não confirmou a publicação | `tipo`, `messageId`, `tentativas`, `erro` |
| `OrdemServicoStatusAlterado` | Toda mudança de status da OS | `ordemServicoId`, `codigo`, `statusAnterior`, `statusNovo` |
| `OrdemServicoEtapaConcluida` | Fim de diagnóstico, execução e finalização | `etapa`, `duracaoMs` |
| `OrdemServicoTransicaoInvalida` | Ação recusada pela máquina de estados | `acao`, `motivo` |

### Métricas

| Métrica | O que mostra |
| --- | --- |
| `Custom/Mensageria/OutboxPendentes` | Eventos ainda não publicados; se só cresce, o broker está fora ou o publicador parou |
| `Custom/Mensageria/Processadas` | Mensagens recebidas e processadas |
| `Custom/Mensageria/Duplicadas` | Reentregas descartadas pela deduplicação |

Os logs continuam em JSON (ECS) com o `correlationId`; com
`NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED`, o agent os encaminha ligados ao trace.

## Dashboard

[`dashboard-saga.json`](dashboard-saga.json) tem quatro páginas: Saga, Mensageria, Tracing distribuído
e Ordem de serviço. Para importar:

```bash
sed 's/"accountIds": \[0\]/"accountIds": [<ID_DA_CONTA>]/' docs/observabilidade/dashboard-saga.json > /tmp/dashboard.json
```

No New Relic: **Dashboards → Import dashboard**, colar o conteúdo de `/tmp/dashboard.json`.

O `DashboardObservabilidadeTest` falha se o dashboard consultar um evento ou métrica que o código não
envia (ou o contrário), para o painel não ficar vazio depois de uma refatoração.

## Alertas sugeridos

| Condição (NRQL) | Limite | Por quê |
| --- | --- | --- |
| `FROM SagaOrdemServicoTransicao SELECT count(*) WHERE situacao = 'FALHA_COMPENSACAO'` | > 0 em 5 min | Estorno sem confirmação: pagamento precisa de verificação manual |
| `FROM MensagemRejeitada SELECT count(*)` | > 5 em 5 min | Mensagens indo para a DLQ: contrato quebrado ou eventos fora de ordem |
| `FROM Metric SELECT latest(newrelic.timeslice.value) WHERE metricTimesliceName = 'Custom/Mensageria/OutboxPendentes'` | > 50 por 10 min | Eventos não saem do outbox: broker fora |

## Como ver uma OS passando pelos três serviços

1. Com os três serviços instrumentados no mesmo cluster, abrir uma OS e pedir o orçamento.
2. No dashboard, página **Tracing distribuído**, widget "Traces que passaram pelos três serviços":
   os traces com `serviços = 3`.
3. Abrir o trace em **APM → Distributed tracing** (filtro `trace.id = <id>`): aparecem a requisição
   HTTP do OS Service, o consumo e a resposta no Billing, a transação `Mensagem/PagamentoConfirmado`
   no OS Service e o consumo da `EnfileirarExecucao` na Execução.

## O que os outros serviços precisam fazer

- Ter o agent do New Relic (ou OpenTelemetry exportando para o New Relic) com distributed tracing.
- Ao publicar a resposta a um comando, publicar **dentro da transação que consumiu o comando**, ou
  copiar os cabeçalhos `traceparent`, `tracestate` e `newrelic` da mensagem recebida para a
  publicada. Sem isso, o trace termina no Billing ou na Execução.
- Usar nomes de aplicação estáveis; o dashboard assume `oficina-billing` e `oficina-execucao`
  (ajustar as consultas da página Tracing distribuído se forem outros).
