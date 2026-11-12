package br.com.oficina.os.auth;

public record LoginResponse(
        String token,
        String tipo
) {
}
