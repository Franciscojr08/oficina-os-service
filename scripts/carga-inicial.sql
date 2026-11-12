-- Carga inicial para desenvolvimento local e demonstração (não é migration; rode uma vez à mão).
--
--   docker compose exec -T postgres psql -U postgres -d os_service < scripts/carga-inicial.sql
--
-- Adaptado do script do monólito, sem estoque (Execução) nem catálogo de itens e serviços (Billing).
-- Usuário administrador: admin@oficina.com / admin (senha em BCrypt; troque fora do ambiente local).

INSERT INTO cliente (nome, cpf_cnpj, telefone, email, cep, logradouro, bairro, cidade, uf, data_nascimento, ativo, data_criacao)
VALUES
    ('Mariana Oliveira Silva','12345678901','79999881122','mariana.silva@outlook.com','49032490','Avenida Beira Mar','13 de Julho','Aracaju','SE','1988-03-15',true,current_timestamp),
    ('Carlos Eduardo Pontes','98765432100','11987654321','cadu.pontes@gmail.com','01310100','Avenida Paulista','Bela Vista','São Paulo','SP','1995-07-22',true,current_timestamp);

INSERT INTO veiculo (cliente_id, placa, marca, modelo, ano, cor, observacao, ativo, data_criacao)
VALUES
    ( 1, 'NTA2L19', 'Honda', 'Civic', 2026, 'Cinza', 'Veículo de fábrica', true, current_timestamp),
    ( 1, 'QPT1Y98', 'Fiat', 'Pálio', 2025, 'Branco', 'Veículo semi-novo', true, current_timestamp),
    ( 2, 'PKG3A48', 'Volkswagen', 'Nivus', 2026, 'Azul', 'Veículo de fábrica', true, current_timestamp),
    ( 2, 'HGT9I16', 'Chevrolet', 'Onix', 2025, 'Preto', 'Veículo semi-novo', true, current_timestamp);

INSERT INTO usuario (nome, email, senha, role, ativo)
VALUES (
           'Administrador',
           'admin@oficina.com',
           '$2a$12$AhsRJmiIJ3pjmnixSVyT4.BuBxZJZSZq4hdS5W9PVqaoa5DK27ggS',
           'ADMIN',
           TRUE
       );