# Contrato de mensageria do OS Service

Documento para os times do Billing e da Execução: o que o OS Service publica, o que ele consome e
as garantias de entrega. Broker: RabbitMQ.

## Topologia

```text
os.eventos (topic)        ← OS Service publica; routing key = tipo da mensagem
billing.eventos (topic)   ─┐
execucao.eventos (topic)  ─┴→ oficina-os-eventos (fila do OS Service)
                                 └─ rejeitada → oficina-os-eventos-dlx → oficina-os-eventos-dlq
```

- Cada serviço publica na **própria** topic exchange, com a routing key igual ao tipo da mensagem.
- Cada serviço consome da **própria** fila, ligada às exchanges dos outros só pelos tipos que lhe
  interessam. O OS Service declara a exchange do Billing e a da Execução (a declaração é idempotente,
  quem subir primeiro cria).
- Para receber o que o OS Service publica, ligue sua fila em `os.eventos` com as routing keys
  desejadas (por exemplo, o Billing liga `SolicitarOrcamento` e `EstornarPagamento`).
- Declare as filas como duráveis antes de o OS Service publicar: mensagem publicada em topic exchange
  sem fila ligada é descartada pelo broker.

## Envelope

Toda mensagem, nos dois sentidos, tem corpo JSON neste formato:

```json
{
  "sagaId": "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90",
  "messageId": "7b1f2c1e-3d4a-4f7e-9c2b-0a1b2c3d4e5f",
  "tipo": "SolicitarOrcamento",
  "ordemServicoId": 42,
  "codigoOrdemServico": "OS-2026-000042",
  "correlationId": "1f0e8d7c-...",
  "ocorridoEm": "2026-11-06T13:45:10.123Z",
  "versao": 1,
  "dados": { }
}
```

| Campo | Obrigatório | Descrição |
| --- | --- | --- |
| `sagaId` | sim | UUID da Saga. Nasce no OS Service quando a OS entra na Saga (um novo pedido de orçamento começa outra Saga, com outro id) e acompanha todos os comandos e eventos daquele ciclo. Quem responde devolve o mesmo `sagaId` que recebeu. Mensagem recebida sem `sagaId`, ou com o `sagaId` de um ciclo que não é o atual da OS (resposta atrasada de um pedido anterior), vai para a DLQ. Só o evento informativo `OrdemServicoStatusAlterado` pode sair sem ele, quando não há Saga (a abertura da OS, por exemplo) |
| `messageId` | OS: sempre manda; Billing e Execução: opcional | UUID único da mensagem. O OS Service manda em tudo o que publica e quem consome deve deduplicar por ele. Ao receber, o OS Service o usa como chave de deduplicação quando vem; quando não vem, ver [Consumo idempotente](#garantias) |
| `tipo` | sim | Nome do comando ou evento (igual à routing key) |
| `ordemServicoId` | sim | Id da OS no OS Service |
| `codigoOrdemServico` | recomendado | Código legível da OS (`OS-AAAA-NNNNNN`), para log e tela |
| `correlationId` | recomendado | Id da requisição original; repasse o que recebeu nas mensagens que publicar em consequência |
| `ocorridoEm` | recomendado | Instante do fato (ISO-8601, UTC) |
| `versao` | não | Versão do envelope; ausente vale 1 |
| `dados` | conforme o tipo | Conteúdo específico |

Propriedades AMQP preenchidas pelo OS Service (recomendadas para todos): `message_id` = `messageId`,
`type` = `tipo`, `content_type` = `application/json`, `delivery_mode` = persistente e os headers
`x-correlation-id` e `x-ordem-servico-id`.

Tracing distribuído: as mensagens levam os headers `traceparent` e `tracestate` (W3C Trace Context) e
`newrelic`, com o trace da requisição que originou o comando. Ao responder, publique dentro da
transação que consumiu o comando (o agent do New Relic propaga sozinho) ou copie esses três headers
para a resposta. Detalhes em [observabilidade/README.md](observabilidade/README.md).

## O que o OS Service publica (`os.eventos`)

| Tipo | Para | Quando | `dados` |
| --- | --- | --- | --- |
| `SolicitarOrcamento` | Billing | Diagnóstico concluído (`PATCH /ordens/{id}/solicitar-aprovacao`) | `clienteId`, `veiculoId`, `servicos` (`servicoId`) e `itens` (`itemId`, `quantidade`): só ids, sem preço (ver abaixo) |
| `CobrarPagamento` | Billing | O cliente aprovou o orçamento (`PATCH /ordens/{id}/aprovar`) | `orcamentoId` |
| `OrdemServicoStatusAlterado` | quem quiser | Toda mudança de status, inclusive a abertura | `ordemServicoId`, `codigo`, `statusAnterior` (nulo na abertura), `statusAtual`, `origem`, `alteradoEm` |
| `EnfileirarExecucao` | Execução | `PagamentoConfirmado` recebido (OS `APROVADA`) | `servicos` (`servicoId`, `nome`) e `itens` (`itemId`, `quantidade`) |
| `EstornarPagamento` | Billing | Compensação: `ExecucaoFalhou` ou cancelamento de OS paga | `pagamentoId`, `motivo` |
| `CancelarExecucao` | Execução | Compensação: cancelamento de OS paga, ainda na fila | `motivo` |

`dados` de `SolicitarOrcamento`:

```json
{
  "clienteId": 7,
  "veiculoId": 3,
  "servicos": [ { "servicoId": 30 } ],
  "itens": [ { "itemId": 20, "quantidade": 2 } ]
}
```

O OS Service não guarda nem envia preço: o Billing é dono do catálogo e monta o orçamento com os preços
dele (o `OrcamentoGerado` devolve `valorServicos`, `valorItens` e `valorTotal`). O payload também não leva
nome, CPF nem e-mail do cliente, só o `clienteId`.

## O que o OS Service consome (`oficina-os-eventos`)

| Tipo | Exchange | Efeito na OS | `dados` esperados |
| --- | --- | --- | --- |
| `OrcamentoGerado` | `billing.eventos` | `AGUARDANDO_APROVACAO`, guarda `orcamentoId` | `orcamentoId` |
| `PagamentoConfirmado` | `billing.eventos` | Guarda `pagamentoId`, `APROVADA`, publica `EnfileirarExecucao` | `pagamentoId` |
| `PagamentoRecusado` | `billing.eventos` | `CANCELADA` | `motivo` |
| `PagamentoEstornado` | `billing.eventos` | `CANCELADA`, Saga encerrada com compensação | `pagamentoId` |
| `ExecucaoAguardandoItens` | `execucao.eventos` | `AGUARDANDO_ITENS` (falta peça); repetido, não muda mais nada | `itensEmFalta` (informativo) |
| `ExecucaoIniciada` | `execucao.eventos` | `EM_EXECUCAO` (da `APROVADA` ou de `AGUARDANDO_ITENS`) | — |
| `ServicoFinalizado` | `execucao.eventos` | Informativo, só com a OS em `EM_EXECUCAO`: nenhum status muda | `servicoId`, `duracaoMinutos` (informativos) |
| `ExecucaoFalhou` | `execucao.eventos` | Publica `EstornarPagamento`, Saga em compensação. Vale na fila, esperando peça ou no meio do reparo | `motivo`, `etapa` (informativo) |
| `ExecucaoFinalizada` | `execucao.eventos` | `FINALIZADA` | — |

Todos levam `sagaId` no envelope (ver acima). Cada evento só é aceito na etapa da Saga que o espera (por exemplo, `PagamentoConfirmado` só depois
do `CobrarPagamento`, que por sua vez só sai depois de `OrcamentoGerado` e da aprovação do cliente). Fora de ordem, ou depois do fim da Saga, vai para a DLQ sem mexer na OS.
Campos obrigatórios em `dados`: `orcamentoId` em `OrcamentoGerado` e `pagamentoId` em
`PagamentoConfirmado`; `motivo` é opcional nos eventos de falha.

## Decisão do cliente e status da OS

Quem aprova ou reprova o orçamento é o cliente, pela API do OS Service (`PATCH /ordens/{id}/aprovar` e
`/reprovar`). O Billing **não** publica `OrcamentoAprovado` nem `OrcamentoReprovado`: aprovar faz o OS
publicar `CobrarPagamento`; reprovar só cancela a OS. O Billing também não tem um evento de falha de
orçamento (`OrcamentoFalhou` saiu do contrato): se ele não conseguir orçar, o caminho é não responder, e
o OS reenvia o pedido e depois deixa a OS pedir de novo (ver abaixo).

Os status da OS são os 9 do contrato: `RECEBIDA`, `EM_DIAGNOSTICO`, `AGUARDANDO_APROVACAO`, `APROVADA`,
`AGUARDANDO_ITENS`, `EM_EXECUCAO`, `FINALIZADA`, `ENTREGUE` e `CANCELADA`. A espera por uma resposta é a
etapa da Saga, não um status: com o orçamento sendo gerado a OS segue em `EM_DIAGNOSTICO`, e com a
cobrança em andamento, em `AGUARDANDO_APROVACAO`.

## Prazos e reenvio

O OS Service espera resposta automática em duas etapas: `SolicitarOrcamento` (5 min) e
`EstornarPagamento` (10 min). Sem resposta no prazo, ele **reenvia o mesmo comando, com o mesmo
`messageId`**, até 3 envios no total. Por isso o Billing precisa descartar repetições pelo
`messageId` e responder de novo se já tinha processado. Esgotadas as tentativas, o orçamento volta
ao diagnóstico; o estorno fica marcado para verificação manual.

## Garantias

- **Publicação (Transactional Outbox).** O evento é gravado na tabela `outbox_evento`, na mesma
  transação da mudança na OS, e um publicador agendado envia com *publisher confirm*. Se o broker
  estiver fora, o evento fica pendente e é reenviado, na ordem, quando ele voltar. Nenhuma mudança
  de status fica sem evento e nenhum evento sai de uma mudança desfeita.
- **Entrega pelo menos uma vez.** Uma mensagem pode chegar repetida (por exemplo, se o OS Service
  cair entre a confirmação do broker e o commit). Quem consome precisa descartar repetições pelo
  `messageId`.
- **Consumo idempotente no OS Service.** A chave de deduplicação é gravada em `evento_processado` na
  mesma transação do efeito. Mensagem repetida é ignorada, inclusive duas entregas simultâneas. A chave
  é:
  - o `messageId`, quando a mensagem traz;
  - sem `messageId`, `sagaId` + SHA-256 de `tipo` e `dados`. Assim a mesma mensagem entregue duas vezes é
    descartada, mas dois `ServicoFinalizado` de serviços diferentes (`dados` diferentes) na mesma Saga
    continuam distintos. **Limite:** duas mensagens legítimas com o mesmo `tipo` e os mesmos `dados` na
    mesma Saga contam como uma só; quem precisar repetir um fato idêntico deve mandar `messageId`.
- **Falhas no consumo do OS Service.**
  - Mensagem inválida (JSON quebrado, sem `sagaId` ou `tipo`, tipo sem manipulador) ou que
    contradiz o estado da OS: vai direto para a DLQ, sem nova tentativa.
  - Falha transitória (banco fora, por exemplo): 3 tentativas com espera crescente (500 ms, 1 s);
    esgotadas, vai para a DLQ.
  - Mensagem na DLQ nunca volta sozinha para a fila. Para reprocessar, mova pelo painel do RabbitMQ
    (*Move messages* ou shovel) depois de corrigir a causa.
- **Retenção.** Eventos publicados ficam 7 dias no outbox; ids de mensagens consumidas ficam 30 dias
  (janela de deduplicação). Ambos configuráveis.
