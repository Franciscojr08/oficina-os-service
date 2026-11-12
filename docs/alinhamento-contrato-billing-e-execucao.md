# Alinhamento do contrato: o que o OS Service implementou e o que Billing e Execução precisam ajustar

Documento para Rodrigo (Billing) e Anthony (Execução). O OS Service já está implementado e testado
(473 testes, com Billing e Execução simulados). Onde o código divergiu do PDF "Fase 4 — Divisão dos
três microsserviços", está marcado abaixo. O detalhe de cada mensagem, o envelope e as garantias estão
em [`mensageria.md`](mensageria.md).

## O fluxo, em uma olhada

```text
OS → Billing   SolicitarOrcamento        (só ids e quantidades, sem preço)
Billing → OS   OrcamentoGerado           OS: AGUARDANDO_APROVACAO
cliente aprova pela API do OS
OS → Billing   CobrarPagamento
Billing → OS   PagamentoConfirmado       OS: APROVADA        (ou PagamentoRecusado → OS cancelada)
OS → Execução  EnfileirarExecucao
Execução → OS  ExecucaoAguardandoItens   OS: AGUARDANDO_ITENS (opcional, se faltar peça)
Execução → OS  ExecucaoIniciada          OS: EM_EXECUCAO
Execução → OS  ServicoFinalizado         (um por serviço, informativo)
Execução → OS  ExecucaoFinalizada        OS: FINALIZADA

Compensação:
Execução → OS  ExecucaoFalhou            (na fila, esperando peça ou no meio do reparo)
OS → Billing   EstornarPagamento
Billing → OS   PagamentoEstornado        OS: CANCELADA
```

## Regras para os dois

1. **`sagaId`: devolva exatamente o que recebeu no comando.** O OS cria um `sagaId` novo a cada ciclo
   (um novo pedido de orçamento começa outro). Mensagem sem `sagaId`, ou com o `sagaId` de um ciclo que
   não é o atual da OS, vai para a DLQ sem efeito. Não gere `sagaId` próprio.
2. **`messageId` é opcional para vocês.** Se mandarem, o OS usa para deduplicar. Se não mandarem, a chave
   é `sagaId` + hash de `tipo` e `dados`; duas mensagens idênticas na mesma Saga contam como uma. O OS
   sempre manda `messageId` e vocês devem deduplicar por ele (a entrega é *pelo menos uma vez*).
3. **Envelope:** `sagaId` e `tipo` obrigatórios; `ordemServicoId` obrigatório; `codigoOrdemServico`,
   `correlationId` (repasse o recebido) e `ocorridoEm` recomendados; `dados` conforme o tipo.
4. **Cada evento só é aceito na etapa certa.** Fora de ordem, repetido depois do fim da Saga ou de outro
   ciclo: vai para a DLQ (`oficina-os-eventos-dlq`) e a OS não muda.
5. **Reenvio:** se o OS não receber `OrcamentoGerado` em 5 min, reenvia o mesmo `SolicitarOrcamento`
   (mesmo `messageId`), até 3 envios. Para `EstornarPagamento`, 10 min. Respondam de novo se já tinham
   processado, em vez de reprocessar.
6. **Dados pessoais** (CPF, e-mail) não devem ir em log. O OS não manda mais dados pessoais do cliente.

## Rodrigo (Billing)

| Direção | Mensagem | `dados` | Observação |
| --- | --- | --- | --- |
| OS → Billing | `SolicitarOrcamento` | `clienteId`, `veiculoId`, `servicos[{servicoId}]`, `itens[{itemId, quantidade}]` | **Sem preço, sem nome, sem CPF.** O Billing monta o orçamento com o catálogo dele |
| Billing → OS | `OrcamentoGerado` | `orcamentoId` (obrigatório), `valorServicos`, `valorItens`, `valorTotal` | O OS só usa o `orcamentoId`; o resto o OS não guarda |
| OS → Billing | `CobrarPagamento` | `orcamentoId` | Sai quando o **cliente aprova pela API do OS** |
| Billing → OS | `PagamentoConfirmado` | `pagamentoId` (obrigatório, numérico) | OS vira `APROVADA` e publica `EnfileirarExecucao` |
| Billing → OS | `PagamentoRecusado` | `motivo` | OS cancela a OS (nada a estornar) |
| OS → Billing | `EstornarPagamento` | `pagamentoId`, `motivo` | Compensação |
| Billing → OS | `PagamentoEstornado` | `pagamentoId` | OS cancela a OS e encerra a Saga |

O que **muda para o Billing**:

- **Não publique** `OrcamentoAprovado`, `OrcamentoReprovado` nem `OrcamentoFalhou`. A decisão do cliente é
  na API do OS (`PATCH /ordens/{id}/aprovar` e `/reprovar`). Reprovar só cancela a OS, sem avisar o
  Billing (o PDF não prevê comando para isso).
- **Não existe evento de falha de orçamento.** Se não conseguir orçar, o caminho hoje é não responder: o OS
  reenvia 3 vezes e depois libera a OS para pedir de novo. Se preferirem um evento explícito, precisamos
  combinar o nome e o efeito (ver "Decisões em aberto").
- **Os ids de `itemId` e `servicoId` são os do catálogo do Billing.** O catálogo saiu do OS (migration
  V15). As OS já existentes apontam para os ids antigos, então o catálogo do Billing precisa manter os
  mesmos ids.
- **Recebem `CobrarPagamento`** com o `orcamentoId` que o próprio Billing devolveu.

## Anthony (Execução)

| Direção | Mensagem | `dados` | Observação |
| --- | --- | --- | --- |
| OS → Execução | `EnfileirarExecucao` | `servicos[{servicoId, nome}]`, `itens[{itemId, quantidade}]` | Formato do PDF: o serviço leva o nome, o item só id e quantidade |
| Execução → OS | `ExecucaoAguardandoItens` | `itensEmFalta[{itemId, quantidadeNecessaria, quantidadeDisponivel}]` | Opcional. OS vira `AGUARDANDO_ITENS`. Repetir é aceito e não muda mais nada |
| Execução → OS | `ExecucaoIniciada` | `execucaoId`, `mecanico`, `iniciadoEm` | Aceito a partir de `APROVADA` ou `AGUARDANDO_ITENS`. OS vira `EM_EXECUCAO` |
| Execução → OS | `ServicoFinalizado` | `execucaoId`, `servicoId`, `duracaoMinutos` | Um por serviço. Informativo: só é aceito com a OS em `EM_EXECUCAO` e não muda status |
| Execução → OS | `ExecucaoFinalizada` | `execucaoId`, `duracaoTotalMinutos`, `servicosExecutados[]` | OS vira `FINALIZADA` |
| Execução → OS | `ExecucaoFalhou` | `motivo`, `etapa` | Vale na fila, esperando peça ou no meio do reparo. **Sempre leva a estorno**. O OS ignora `etapa` por enquanto |
| OS → Execução | `CancelarExecucao` | `motivo` | O cliente cancelou uma OS paga antes de a execução começar: devolva o estoque e tire a OS da fila |

O que **muda para a Execução**:

- **Publique `ExecucaoAguardandoItens` e `ServicoFinalizado`.** O OS agora consome os dois.
- **`ExecucaoFalhou` vale também no meio do reparo.** Nesse caso a OS está em `EM_EXECUCAO` e é cancelada
  quando o estorno se confirmar.
- **Corrida possível:** se o cliente cancelar a OS paga e a Execução ainda não tiver recebido o
  `CancelarExecucao`, um `ExecucaoIniciada` que ela publicar vai para a DLQ (a Saga já está estornando).
  Ao receber `CancelarExecucao`, pare e não publique mais nada daquela Saga.
- **Relatórios de tempo médio** (por OS e por serviço) saíram do OS e ficam com a Execução, como no PDF.
  As datas de início e fim da execução continuam na OS, atualizadas pelos eventos.

## Onde o OS diverge do PDF

| Assunto | PDF | OS Service |
| --- | --- | --- |
| `messageId` no envelope | não existe | opcional para quem responde; o OS sempre manda |
| `dados` dos comandos | `CobrarPagamento`, `EnfileirarExecucao`, `EstornarPagamento`, `CancelarExecucao` | **igual ao PDF**, sem campos extras (o `ordemServicoId` e o `codigo` já vão no envelope) |
| `OrcamentoFalhou` | não existe | removido (o OS tinha, saiu para seguir o PDF) |
| Aprovação do orçamento | só diz "o cliente aprovou" | pela API do OS; o OS publica `CobrarPagamento` |
| Status da OS | 9 status | os mesmos 9 (`APROVADA`, `AGUARDANDO_ITENS` incluídos) |
| Mensageria | SNS + SQS preferida, RabbitMQ como plano B | **RabbitMQ** (ver abaixo) |

## Decisões em aberto (precisam de resposta dos três)

1. **Broker: RabbitMQ ou SNS+SQS?** O OS foi feito em RabbitMQ (ADR 0002), por causa do plano B do PDF.
   Se Billing e Execução forem em SQS, ou trocamos o adaptador do OS (as portas `EventoPublisherPort` e
   `ComandoListener` já isolam o broker, é um adaptador novo) ou os dois usam RabbitMQ. Hoje o OS publica na
   topic exchange `os.eventos` (routing key = `tipo`) e consome da fila `oficina-os-eventos`, ligada a
   `billing.eventos` e `execucao.eventos`. As filas de vocês precisam estar declaradas e ligadas **antes**
   de o OS publicar, senão o broker descarta.
2. **Falha de orçamento:** o Billing quer um evento explícito (por exemplo `OrcamentoFalhou`), ou segue sem
   resposta e deixa o prazo esgotar (5 min × 3 envios)?
3. **`PagamentoRecusado`:** hoje cancela a OS. Alternativa: manter em `AGUARDANDO_APROVACAO` para o cliente
   tentar pagar de novo. O PDF não define.
4. **Ids do catálogo:** o Billing confirma que vai manter os ids de item e serviço do OS antigo.
5. **Atualizar o PDF.** Ele diz que é a referência; vale um PR nele com o que for decidido.
