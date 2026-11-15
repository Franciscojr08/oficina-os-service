# ADR 0004 — Fronteiras do OS Service

- **Status:** aceita
- **Data:** 2026-11-06

## Contexto

O monólito tinha num banco só: cliente, veículo, catálogo de serviços e itens, estoque, OS,
aprovação, execução e relatórios. O Tech Challenge pede pelo menos três serviços independentes, cada
um com banco próprio, e proíbe acesso direto ao banco de outro serviço.

## Decisão

| Fica no OS Service | Vai para outro serviço |
| --- | --- |
| Abertura, diagnóstico, status, histórico, cancelamento e entrega da OS | Orçamento, aprovação, pagamento e estorno → Billing |
| Cliente e veículo | Catálogo de serviços e itens, com preço → Billing |
| Login de funcionários, acompanhamento público | Estoque, fila e andamento do reparo → Execução |
| Itens e serviços da OS (só id do catálogo, nome e quantidade) | Relatórios de tempo médio, por OS e por serviço → Execução |

Referências a outros serviços são ids lógicos sem FK (`orcamento_id`, `pagamento_id`); o estoque da
Execução referencia o `itemId` do catálogo.

## Por quê

- **Cliente e veículo são do atendimento:** a OS é aberta e diagnosticada com eles; tirá-los do OS
  Service faria cada abertura de OS depender de chamadas síncronas a outro serviço.
- **O catálogo é do Billing, e o OS não o consulta:** o catálogo carrega preço, então foi para o Billing.
  Para abrir a OS sem chamada síncrona, o cliente da API envia o id e o nome do item ou serviço, e o OS
  guarda o id (sem FK) e uma cópia do nome. A cópia existe porque o `EnfileirarExecucao` precisa levar o
  nome do serviço para a tela do mecânico (contrato da Fase 4). Custo aceito: o OS confia no que o
  chamador mandou, e um id inexistente só é descoberto pelo Billing ao orçar (que, pelo contrato, não tem
  evento de falha de orçamento).
- **O OS Service não guarda preço:** a OS só liga ids de serviço e de item (com quantidade). O
  `SolicitarOrcamento` leva só ids, e o Billing, dono do catálogo e dos valores, monta o orçamento com os
  preços dele. Quando uma tela precisa do total, pergunta ao Billing. As colunas de valor da OS saíram
  na migration V14.
- **Estoque acompanha a execução:** quem reserva e baixa peças é quem executa o reparo; a reserva e a
  devolução viram passos da Saga com compensação.

## Consequências

- O OS Service usa PostgreSQL (banco relacional exigido); o banco NoSQL fica com o Billing ou a
  Execução.
- **Ponto em aberto:** a Lambda de autenticação por CPF da Fase 3 consulta a tabela `cliente`
  direto. Isso conflita com "nenhum serviço acessa o banco de outro"; as opções são a Lambda chamar
  um endpoint do OS Service ou o time registrar a exceção. O índice usado pela Lambda (migration V8)
  foi mantido para não quebrá-la até a decisão.
