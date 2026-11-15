# ADR 0003 — Pagamento antes da execução

- **Status:** aceita
- **Data:** 2026-11-06

## Contexto

No monólito da Fase 3, a OS aprovada ia para a execução e o pagamento era tratado à parte. Na Fase
4, o pagamento passa pelo Mercado Pago no Billing, e a Saga precisa de compensações reais.

## Decisão

O fluxo cobra antes de executar: o cliente aprova pela API, o OS publica `CobrarPagamento`, e
`PagamentoConfirmado → EnfileirarExecucao`. A OS só vira `APROVADA` e entra na fila da Execução depois
do pagamento confirmado.

## Por quê

- **Dá à Saga uma compensação de verdade:** se a Execução não puder atender (falta de estoque), o
  pagamento é estornado. Pagando na entrega, a Saga não teria nada financeiro a desfazer.
- **Evita trabalho não pago:** a oficina não reserva peças nem ocupa mecânico com OS sem pagamento.
- **Cancelamento claro:** antes do pagamento, cancelar é só mudar o status; depois, é estorno +
  devolução de estoque, tratado pela Saga (`PATCH /cancelar` responde 202).

## Consequências

- Os status são os 9 do monólito e do contrato da Fase 4 (`APROVADA` e `AGUARDANDO_ITENS`
  incluídos). A versão inicial do OS Service usava `AGUARDANDO_ORCAMENTO`, `AGUARDANDO_PAGAMENTO` e
  `AGUARDANDO_EXECUCAO`; foram removidos porque a espera por uma resposta é a etapa da Saga, não um
  status (migration V13 traduz os dados existentes).
- Enquanto a cobrança está em andamento a OS não pode ser cancelada: o pagamento poderia se
  confirmar depois e ficar sem estorno.
- O cancelamento de OS paga depende do Billing confirmar o estorno; se ele não confirmar dentro das
  tentativas, a Saga fica em `FALHA_COMPENSACAO` para verificação manual.
