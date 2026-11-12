# Roteiro do vídeo — parte do OS Service

O vídeo do grupo tem até 15 minutos e precisa mostrar quatro coisas (PDF da Fase 4): o fluxo completo
de uma OS pelos microsserviços, a Saga com tratamento de falhas, o deploy automatizado de pelo menos
um serviço com validação de testes, e o monitoramento dos fluxos distribuídos. Este roteiro cobre a
parte do OS Service em **cerca de 5 minutos**; os tempos são sugestões para o grupo encaixar com as
partes do Billing e da Execução.

## Preparação (antes de gravar)

- Cluster EKS com os três serviços no ar e o New Relic recebendo dados.
- Carga inicial aplicada (`admin@oficina.com`); Postman com o environment apontando para o Load
  Balancer do OS Service (ou o API Gateway).
- Abas abertas: Swagger do OS Service, painel do RabbitMQ (port-forward 15672), GitHub Actions do
  repositório, dashboard "Oficina - OS Service e Saga" e Distributed tracing no New Relic.
- Um PR pequeno já aberto (por exemplo, ajuste de texto no README) para mostrar o pipeline.
- Uma OS já paga e parada em `APROVADA` para a demonstração de falha, caso o Billing e a
  Execução não permitam forçar a recusa ao vivo.

## Roteiro

| Tempo | O que mostrar | O que falar |
| --- | --- | --- |
| 0:00–0:40 | Diagrama da arquitetura (README) | O OS Service é dono do ciclo de vida da OS e orquestrador da Saga. Banco PostgreSQL próprio; conversa com Billing e Execução só por mensagens no RabbitMQ. Por que orquestrada: o fluxo e as compensações ficam num lugar só (ADR 0001). |
| 0:40–1:40 | Postman / Swagger: abrir OS, iniciar diagnóstico, pedir orçamento | A OS segue em "Em diagnóstico" (a espera do orçamento é uma etapa da Saga, e a OS fica travada para alteração). O comando `SolicitarOrcamento` são gravados na mesma transação (outbox): nenhum evento se perde se o broker cair. No painel do RabbitMQ, a mensagem saindo em `os.eventos`. |
| 1:40–2:40 | `GET /ordens/{id}/historico` enquanto Billing e Execução respondem | O histórico mostra cada mudança com a origem: `API` para o atendente, nome do evento para o que veio dos outros serviços (OrcamentoGerado, PagamentoConfirmado, ExecucaoIniciada...). O cliente aprova pela API (`aprovar`) e o OS publica `CobrarPagamento`. A OS passa por "Aprovada", "Aguardando itens" (se faltar peça) e "Em execução". Termina em "Finalizada"; entrega pela API. |
| 2:40–3:40 | Falha: Execução falha (falta de estoque ou peça danificada) | A OS continua "Aprovada", a Saga entra em compensação e pede o estorno ao Billing. Só com `PagamentoEstornado` a OS vai para "Cancelada". Mostrar também: cancelar uma OS paga responde 202 e dispara o mesmo estorno. Mencionar o prazo: sem resposta, o comando é reenviado e depois compensado. |
| 3:40–4:30 | GitHub Actions: PR com os checks Qualidade, Imagem e Manifestos; execução do merge na main | Todo PR roda testes unitários, de integração e BDD, cobertura (95%), IQC e o quality gate do SonarCloud; a main é protegida. O merge publica a imagem no ECR e faz o deploy no EKS, com rollout sem queda e smoke test. Mostrar o resumo com o IQC e o link do Swagger publicado. |
| 4:30–5:15 | New Relic: dashboard e um trace | Dashboard: transições da Saga, desfechos, compensações, DLQ, outbox pendente. Abrir um trace com os três serviços: a requisição do atendente, o Billing, a transação `Mensagem/PagamentoConfirmado` no OS Service e a Execução, no mesmo trace (o contexto atravessa o outbox — ADR 0005). |

## Frases curtas para não esquecer

- "Uma requisição, três serviços, um trace."
- "O evento e a mudança de status são gravados juntos: ou os dois, ou nenhum."
- "Mensagem repetida não tem efeito: cada uma tem um id, e o serviço lembra o que já processou."
- "A OS só é cancelada depois que o dinheiro volta para o cliente."
