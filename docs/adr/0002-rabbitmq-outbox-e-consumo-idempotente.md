# ADR 0002 — RabbitMQ com Transactional Outbox e consumo idempotente

- **Status:** aceita
- **Data:** 2026-11-06

## Contexto

A Saga depende de mensagens entre os serviços. Dois problemas clássicos aparecem:

1. **Escrita dupla:** gravar a mudança na OS e publicar o evento são duas operações em sistemas
   diferentes (PostgreSQL e broker). Se o processo cair entre elas, ou o evento se perde, ou um
   evento sai de uma mudança que foi desfeita.
2. **Entrega repetida:** brokers garantem "pelo menos uma vez". A mesma mensagem pode chegar duas
   vezes e, sem cuidado, pedir duas execuções ou dois estornos.

## Decisão

- **Broker:** RabbitMQ, com topic exchanges por serviço (`os.eventos`, `billing.eventos`,
  `execucao.eventos`), fila própria do OS Service (`oficina-os-eventos`) e dead-letter queue.
- **Transactional Outbox:** o evento é gravado em `outbox_evento` na mesma transação da mudança na
  OS; um publicador agendado envia com *publisher confirm* e só então marca como publicado. Várias
  réplicas publicam em paralelo com `FOR UPDATE SKIP LOCKED`; uma falha para a rodada para não
  inverter a ordem.
- **Consumo idempotente (inbox):** a chave de deduplicação é gravada em `evento_processado` na mesma
  transação do efeito, com `INSERT ... ON CONFLICT DO NOTHING`; repetição (inclusive simultânea) é
  descartada. A chave é o `messageId` quando a mensagem traz e, sem ele, `sagaId` + hash de `tipo` e
  `dados` (o contrato da Fase 4 não obriga Billing e Execução a mandar `messageId`).
- **Falhas:** mensagem inválida ou contraditória vai direto para a DLQ; falha transitória tem 3
  tentativas antes.

## Alternativas consideradas

- **Kafka:** retenção e reprocessamento melhores, mas mais pesado para operar no cluster do Learner
  Lab e sem ganho para o volume do projeto.
- **SQS/SNS gerenciados:** sem broker para manter, mas diferente entre docker-compose e AWS, e o
  painel do RabbitMQ é mais didático para mostrar o fluxo no vídeo.
- **Publicar direto do código de negócio (sem outbox):** mais simples, mas perde eventos se o broker
  estiver fora no momento da mudança.

## Consequências

- Nenhuma mudança de status fica sem evento, e nenhum evento sai de uma mudança desfeita.
- Quem consome mensagens do OS Service precisa deduplicar pelo `messageId` (contrato).
- Sem `messageId`, duas mensagens idênticas (mesmo `sagaId`, `tipo` e `dados`) contam como uma só.
- Há latência de até um intervalo do publicador (1 s) entre o commit e a publicação.
- Outbox e inbox crescem: uma limpeza diária remove publicados com mais de 7 dias e ids consumidos
  com mais de 30.
