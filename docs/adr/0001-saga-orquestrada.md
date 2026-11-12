# ADR 0001 — Saga orquestrada, com o OS Service como orquestrador

- **Status:** aceita
- **Data:** 2026-11-06

## Contexto

A abertura de uma OS até a entrega envolve três serviços com bancos próprios: o OS Service (dono da
OS), o Billing (orçamento e pagamento, integrado ao Mercado Pago) e a Execução (estoque e reparo).
Não existe transação distribuída entre os bancos, e o Tech Challenge exige o Saga Pattern com
rollback e compensação em caso de falha em qualquer etapa.

O fluxo tem passos que dependem uns dos outros (só se executa o que foi pago) e falhas que exigem
desfazer passos já confirmados em outro serviço (estornar um pagamento quando a Execução não
consegue atender).

## Decisão

Saga **orquestrada**, com o **OS Service como orquestrador** (`OrquestradorSaga`).

- O OS Service envia comandos (`SolicitarOrcamento`, `CobrarPagamento`, `EnfileirarExecucao`,
  `EstornarPagamento`, `CancelarExecucao`) e reage aos eventos de resposta do Billing e da Execução.
  A decisão do cliente sobre o orçamento (aprovar ou reprovar) entra pela API do OS Service, não por
  evento do Billing.
- O estado de cada Saga fica em `saga_ordem_servico`: etapa, situação, prazo, tentativas e o comando
  que espera resposta.
- Cada etapa só aceita os eventos que espera; evento fora de ordem vai para a DLQ sem mexer na OS.
- Etapas automáticas têm prazo: sem resposta, o mesmo comando é reenviado (mesmo `messageId`) e,
  esgotadas as tentativas, a compensação é disparada.

| Falha | Compensação |
| --- | --- |
| Billing não responde ao pedido de orçamento (prazo esgotado) | OS segue em `EM_DIAGNOSTICO` e pode pedir de novo |
| Cliente reprova o orçamento / pagamento recusado | OS cancelada (nada a desfazer) |
| Execução falha (na fila, esperando peça ou no meio do reparo) | Estorno do pagamento; OS cancelada quando o estorno é confirmado |
| Cliente cancela a OS paga | Devolução do estoque + estorno; OS cancelada quando o estorno é confirmado |
| Billing não confirma o estorno | Saga em `FALHA_COMPENSACAO`, alerta para intervenção manual |

## Por que orquestrada e por que no OS Service

- **O OS Service já é o dono do ciclo de vida da OS.** O status da OS é, na prática, o estado da
  Saga; colocar a orquestração em outro lugar duplicaria esse estado.
- **O fluxo fica num lugar só.** Quem quer entender "o que acontece quando a Execução falha" lê um
  método (`OrquestradorSaga.execucaoFalhou`), não a soma das reações de três serviços. Isso também
  facilita testar (um teste de integração cobre o fluxo inteiro, `SagaIntegrationTest`) e rastrear
  (eventos `SagaOrdemServicoTransicao` com etapa e situação).
- **Billing e Execução não se conhecem.** Cada um só recebe comandos do OS Service e responde; a
  Execução nunca precisa saber que existe estorno, e o Billing nunca precisa saber de estoque.
- **Compensação e prazo precisam de memória.** Saber que um estorno foi pedido e ainda não voltou,
  ou que o Billing não respondeu em 5 minutos, exige estado; num modelo coreografado esse estado
  ficaria espalhado ou implícito.

## Alternativas consideradas

- **Saga coreografada (cada serviço reage aos eventos dos outros):** menos acoplamento a um
  orquestrador, mas o fluxo fica implícito, as compensações cruzadas (Execução falha → Billing
  estorna) exigiriam que os serviços conhecessem os eventos uns dos outros, e prazos ficariam sem
  dono. Para um fluxo com várias saídas de compensação, a visibilidade da orquestração compensa.
- **Orquestrador como serviço separado:** mais um serviço, banco e pipeline para manter, e o estado
  da OS ficaria duplicado entre ele e o OS Service.

## Consequências

- O OS Service concentra a lógica do fluxo e precisa ser altamente disponível; por isso roda com
  HPA e várias réplicas são seguras (outbox com `SKIP LOCKED`, consumo idempotente, Saga travada
  por OS no banco).
- Billing e Execução dependem do contrato de mensagens (`docs/mensageria.md`), não do código do OS
  Service.
- Novas saídas do fluxo entram no `OrquestradorSaga`, com teste no `SagaIntegrationTest` e cenário
  BDD quando forem visíveis para o negócio.
