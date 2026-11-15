-- Os 9 status do contrato da Fase 4: saem AGUARDANDO_ORCAMENTO, AGUARDANDO_PAGAMENTO e
-- AGUARDANDO_EXECUCAO; entram APROVADA e AGUARDANDO_ITENS. A espera por uma resposta passou a ser a
-- etapa da Saga (saga_ordem_servico.etapa), não um status da OS.
--
--   AGUARDANDO_ORCAMENTO -> EM_DIAGNOSTICO       (orçamento sendo gerado)
--   AGUARDANDO_PAGAMENTO -> AGUARDANDO_APROVACAO (cobrança em andamento)
--   AGUARDANDO_EXECUCAO  -> APROVADA             (pago, na fila da Execução)
--
-- O histórico recebe a mesma tradução, porque o código só conhece os status novos. Cada linha antiga
-- continua existindo; só o nome do status muda.
--
-- Atenção ao implantar: OS que estavam em AGUARDANDO_PAGAMENTO já tinham sido aprovadas pelo Billing
-- no fluxo anterior e o CobrarPagamento nunca foi enviado para elas. Drene a Saga (ou reaprove essas
-- OS pela API) antes de trocar a versão.

UPDATE ordem_servico SET status = 'EM_DIAGNOSTICO' WHERE status = 'AGUARDANDO_ORCAMENTO';
UPDATE ordem_servico SET status = 'AGUARDANDO_APROVACAO' WHERE status = 'AGUARDANDO_PAGAMENTO';
UPDATE ordem_servico SET status = 'APROVADA' WHERE status = 'AGUARDANDO_EXECUCAO';

UPDATE historico_ordem_servico SET status = 'EM_DIAGNOSTICO' WHERE status = 'AGUARDANDO_ORCAMENTO';
UPDATE historico_ordem_servico SET status = 'AGUARDANDO_APROVACAO' WHERE status = 'AGUARDANDO_PAGAMENTO';
UPDATE historico_ordem_servico SET status = 'APROVADA' WHERE status = 'AGUARDANDO_EXECUCAO';
