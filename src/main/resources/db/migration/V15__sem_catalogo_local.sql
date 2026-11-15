-- Os catálogos de itens e serviços saem do OS Service e vão para o Billing (contrato da Fase 4: o
-- catálogo carrega preço, e o Billing é dono de tudo que tem valor). A OS passa a guardar só o id lógico
-- (sem FK) e uma cópia do nome, enviada pelo cliente da API, porque o EnfileirarExecucao precisa levar o
-- nome do serviço para a tela do mecânico sem consultar o catálogo de outro serviço.
--
--   item_ordem_servico.item_id        -> id do item no catálogo do Billing (sem FK)
--   item_ordem_servico.item_nome      -> cópia do nome (backfill a partir da tabela item)
--   servico_ordem_servico.servico_id  -> id do serviço no catálogo do Billing (sem FK)
--   servico_ordem_servico.servico_nome-> cópia do nome (backfill a partir da tabela servico)
--
-- As tabelas item e servico são removidas. É uma migration sem volta: exporte o catálogo para o Billing
-- antes de implantar (os ids são preservados, então o Billing deve manter os mesmos ids).

ALTER TABLE item_ordem_servico ADD COLUMN item_nome VARCHAR(120);
UPDATE item_ordem_servico ios SET item_nome = i.nome FROM item i WHERE i.id = ios.item_id;
ALTER TABLE item_ordem_servico ALTER COLUMN item_nome SET NOT NULL;
ALTER TABLE item_ordem_servico DROP CONSTRAINT fk_item_os;

ALTER TABLE servico_ordem_servico ADD COLUMN servico_nome VARCHAR(120);
UPDATE servico_ordem_servico sos SET servico_nome = s.nome FROM servico s WHERE s.id = sos.servico_id;
ALTER TABLE servico_ordem_servico ALTER COLUMN servico_nome SET NOT NULL;
ALTER TABLE servico_ordem_servico DROP CONSTRAINT fk_servico_os;

DROP TABLE item;
DROP TABLE servico;
