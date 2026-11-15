package br.com.oficina.os.shared.exception;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Todos os erros da API saem em {@code application/problem+json}, com mensagem em português.
 * Herdar de {@link ResponseEntityExceptionHandler} cobre também os erros gerados pelo próprio
 * Spring MVC (rota inexistente, método não suportado, JSON malformado, tipo inválido na URL).
 */
@RestControllerAdvice
public class HandlerGeralExcecoes extends ResponseEntityExceptionHandler {

	private static final String TITULO_REQUISICAO_INVALIDA = "Requisicao invalida";

	@ExceptionHandler(RecursoNaoEncontradoException.class)
	public ProblemDetail handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problemDetail.setTitle("Recurso nao encontrado");
		return problemDetail;
	}

	@ExceptionHandler(AcessoNegadoException.class)
	public ProblemDetail handleAcessoNegado(AcessoNegadoException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
		problemDetail.setTitle("Acesso negado");
		return problemDetail;
	}

	@ExceptionHandler(RegraNegocioException.class)
	public ProblemDetail handleRegraNegocio(RegraNegocioException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
		problemDetail.setTitle("Regra de negocio violada");
		return problemDetail;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String mensagem = ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.findFirst()
				.map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
				.orElse("Dados invalidos");

		return responder(ex, HttpStatus.BAD_REQUEST, "Erro de validacao", mensagem, headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
			TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String parametro = ex instanceof MethodArgumentTypeMismatchException mismatch
				? mismatch.getName()
				: ex.getPropertyName();
		String mensagem = "Valor inválido para '%s': %s".formatted(parametro, ex.getValue());

		Class<?> tipo = ex.getRequiredType();
		if (tipo != null && tipo.isEnum()) {
			mensagem += ". Valores aceitos: " + Arrays.stream(tipo.getEnumConstants())
					.map(String::valueOf)
					.collect(Collectors.joining(", "));
		}

		return responder(ex, HttpStatus.BAD_REQUEST, TITULO_REQUISICAO_INVALIDA, mensagem, headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return responder(ex, HttpStatus.BAD_REQUEST, TITULO_REQUISICAO_INVALIDA,
				"Corpo da requisição ausente ou com JSON inválido", headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleNoResourceFoundException(
			NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return responder(ex, HttpStatus.NOT_FOUND, "Recurso nao encontrado",
				"Rota inexistente: /" + ex.getResourcePath(), headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
			HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return responder(ex, HttpStatus.METHOD_NOT_ALLOWED, "Metodo nao suportado",
				"Método %s não suportado nesta rota".formatted(ex.getMethod()), headers, request);
	}

	private ResponseEntity<Object> responder(
			Exception ex, HttpStatus status, String titulo, String detalhe, HttpHeaders headers, WebRequest request) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detalhe);
		problemDetail.setTitle(titulo);
		return handleExceptionInternal(ex, problemDetail, headers, status, request);
	}
}
