package br.com.oficina.os.shared.exception;

public final class ExceptionMessage {

    private ExceptionMessage() {
    }

    public static final String CUSTOMER_NOT_FOUND = "Cliente não encontrado";
    public static final String VEHICLE_NOT_FOUND = "Veículo não encontrado";
    public static final String SERVICE_ORDER_NOT_FOUND = "Ordem de serviço não encontrada";
    public static final String EXISTING_CUSTOMER = "Já existe cliente cadastrado com este CPF/CNPJ";
    public static final String DUPLICATED_VEHICLE = "Já existe veículo cadastrado com esta placa";
    public static final String NOT_ACTIVE_CUSTOMER = "O cliente informado não está ativo";
    public static final String NOT_ACTIVE_VEHICLE = "O Veículo informado não está ativo";
    public static final String EXISTING_USER_SAME_EMAIL = "Já existe usuário cadastrado com este email";
    public static final String INVALID_PERSON_DOCUMENT = "CPF inválido";
    public static final String INVALID_COMPANY_DOCUMENT = "CNPJ inválido";
    public static final String INVALID_VEHICLE_PLATE = "Placa inválida, não segue o padrão.";
	public static final String VEHICLE_DOES_NOT_BELONG_TO_CUSTOMER = "O veículo informado não pertence ao cliente informado.";
	public static final String CANNOT_ADD_ITEM_WITH_ORDER_OUTSIDE_DIAGNOSIS = "Não é possível adicionar o item, pois a ordem de serviço não está %s";
	public static final String CANNOT_ADD_SERVICE_WITH_ORDER_OUTSIDE_DIAGNOSIS = "Não é possível adicionar o serviço, pois a ordem de serviço não está %s";
	public static final String INVALID_ORDER_STATUS_FOR_ACTION = "Não é possível %s, pois a ordem de serviço está %s";
	public static final String FORBIDDEN_OTHER_CUSTOMER_ORDERS = "Acesso negado: as ordens de serviço pertencem a outro cliente";
	public static final String QUOTE_IN_PROGRESS = "Não é possível alterar a ordem de serviço enquanto o orçamento está sendo gerado";
	public static final String PAYMENT_IN_PROGRESS = "Não é possível cancelar a ordem de serviço enquanto o pagamento está sendo processado";
	public static final String CANNOT_CANCEL_PAID_SERVICE_ORDER = "Não é possível cancelar a ordem de serviço, pois o pagamento já foi registrado e o estorno depende do Billing";
	public static final String SERVICE_ORDER_MUST_HAVE_ITEM_AND_SERVICE_TO_REQUEST_APPROVAL = "A OS deve possuir ao menos uma peça e um serviço para solicitar o orçamento";
	public static final String CANNOT_INACTIVATE_CUSTOMER_WITH_ACTIVE_SERVICE_ORDERS = "Não é possível inativar o cliente, pois ele possui ordens de serviço ativas.";
	public static final String CANNOT_INACTIVATE_VEHICLE_WITH_ACTIVE_SERVICE_ORDERS = "Não é possível inativar o veículo, pois ele possui ordens de serviço ativas.";
}
