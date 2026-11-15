# language: pt
Funcionalidade: Ordem de serviço conduzida pela Saga
  Como oficina, quero que a ordem de serviço avance sozinha conforme o orçamento, o pagamento e o
  reparo acontecem no Billing e na Execução, e que um passo que falhe seja desfeito, para que o
  cliente acompanhe a OS sem atualização manual e nunca pague por um reparo que não foi feito.

  Contexto:
    Dado uma ordem de serviço em diagnóstico com 2 unidades de "Filtro de óleo" e o serviço "Troca de óleo"
    Quando o atendente conclui o diagnóstico e pede o orçamento
    Então o Billing recebe o pedido de orçamento com 1 serviço e 2 unidades do item, sem preço
    E a ordem de serviço continua "Em diagnóstico" enquanto o orçamento é gerado

  Cenário: Caminho feliz, do orçamento à entrega do veículo
    Quando o Billing gera o orçamento 501
    Então a ordem de serviço fica "Aguardando aprovação"
    Quando o cliente aprova o orçamento
    Então o Billing recebe o pedido de cobrança do orçamento 501
    E a ordem de serviço continua "Aguardando aprovação" enquanto o pagamento não é confirmado
    Quando o Billing confirma o pagamento 901
    Então a Execução recebe o pedido de reparo com 2 unidades do item
    E a ordem de serviço fica "Aprovada"
    Quando a Execução avisa que falta peça no estoque
    Então a ordem de serviço fica "Aguardando itens"
    Quando a Execução inicia o reparo
    Então a ordem de serviço fica "Em execução"
    Quando a Execução finaliza o serviço
    E a Execução finaliza o reparo
    Então a ordem de serviço fica "Finalizada"
    E a Saga termina como "concluída"
    Quando o atendente entrega o veículo
    Então a ordem de serviço fica "Entregue"
    E o histórico da ordem de serviço é:
      | status               | origem                  |
      | Recebida             | API                     |
      | Em diagnóstico       | API                     |
      | Aguardando aprovação | OrcamentoGerado         |
      | Aprovada             | PagamentoConfirmado     |
      | Aguardando itens     | ExecucaoAguardandoItens |
      | Em execução          | ExecucaoIniciada        |
      | Finalizada           | ExecucaoFinalizada      |
      | Entregue             | API                     |

  Cenário: Cliente reprova o orçamento e nada é cobrado
    Dado que o Billing gerou o orçamento 502
    Quando o cliente reprova o orçamento
    Então a ordem de serviço fica "Cancelada"
    E a Saga termina como "cancelada"
    E nenhum pedido de cobrança é enviado ao Billing
    E nenhum pedido de reparo é enviado à Execução
    E nenhum pedido de estorno é enviado ao Billing

  Cenário: Execução falha na fila e o pagamento é estornado
    Dado que o cliente aprovou e pagou o orçamento com o pagamento 903
    Quando a Execução falha por "Sem estoque de Filtro de óleo"
    Então o Billing recebe o pedido de estorno do pagamento 903 com o motivo "Sem estoque de Filtro de óleo"
    E a ordem de serviço continua "Aprovada" enquanto o estorno não é confirmado
    Quando o Billing confirma o estorno do pagamento 903
    Então a ordem de serviço fica "Cancelada"
    E a Saga termina como "compensada"

  Cenário: Execução falha no meio do reparo e o pagamento é estornado
    Dado que o cliente aprovou e pagou o orçamento com o pagamento 904
    Quando a Execução inicia o reparo
    Então a ordem de serviço fica "Em execução"
    Quando a Execução falha por "Peça danificada na montagem"
    Então o Billing recebe o pedido de estorno do pagamento 904 com o motivo "Peça danificada na montagem"
    E a ordem de serviço continua "Em execução" enquanto o estorno não é confirmado
    Quando o Billing confirma o estorno do pagamento 904
    Então a ordem de serviço fica "Cancelada"
    E a Saga termina como "compensada"
