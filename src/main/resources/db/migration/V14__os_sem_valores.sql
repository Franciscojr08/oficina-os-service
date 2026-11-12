-- O OS Service deixa de guardar valor em reais (contrato da Fase 4): o Billing é dono de tudo que tem
-- preço e monta o orçamento a partir do catálogo dele, recebendo do OS só ids e quantidades.
--
--   ordem_servico.valor_total_servicos e valor_total_itens  -> total do orçamento, no Billing
--   item_ordem_servico.valor_unitario                       -> orcamento_item.valor_unitario, no Billing
--   servico_ordem_servico.valor_unitario                    -> orcamento_servico.valor_unitario, no Billing
--
-- Atenção: é uma migration sem volta. Os preços gravados nas OS existentes são perdidos aqui; se for
-- preciso levá-los ao Billing (histórico de orçamentos), exporte as colunas antes de implantar.

ALTER TABLE ordem_servico DROP COLUMN valor_total_servicos;
ALTER TABLE ordem_servico DROP COLUMN valor_total_itens;
ALTER TABLE item_ordem_servico DROP COLUMN valor_unitario;
ALTER TABLE servico_ordem_servico DROP COLUMN valor_unitario;
