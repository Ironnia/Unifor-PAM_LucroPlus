SET time_zone='+00:00';
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @fifo_cenario=CONVERT(@fifo_cenario USING utf8mb4) COLLATE utf8mb4_unicode_ci;
DELIMITER $$
DROP PROCEDURE IF EXISTS fifo_carregar$$
CREATE PROCEDURE fifo_carregar()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF DATABASE() IS NULL OR LEFT(DATABASE(),15) <> 'fifo_lucroplus_' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FIFO exige banco isolado com prefixo correto';
 END IF;
 IF @fifo_cenario IS NULL OR @fifo_cenario NOT IN ('parcial','integral','perda','travessia','inicio_efetivo','inicio_futuro','historico') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Escolha um cenario FIFO documentado';
 END IF;
 CREATE TABLE IF NOT EXISTS fifo_fixture (id INT PRIMARY KEY, referencia DATE NOT NULL, cenario VARCHAR(30) NOT NULL);
 IF EXISTS(SELECT 1 FROM fifo_fixture WHERE referencia <> '2026-10-01' OR cenario <> @fifo_cenario) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Outra referencia/cenario: use banco novo';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM fifo_fixture) AND (
 EXISTS(SELECT 1 FROM tb_restaurante) OR EXISTS(SELECT 1 FROM tb_usuario) OR
 EXISTS(SELECT 1 FROM tb_lote) OR EXISTS(SELECT 1 FROM tb_produto) OR EXISTS(SELECT 1 FROM tb_ingrediente)
 OR EXISTS(SELECT 1 FROM tb_venda) OR EXISTS(SELECT 1 FROM tb_promocao)) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Primeira carga exige esquema FIFO vazio'; END IF;

 CREATE TABLE IF NOT EXISTS fifo_bcg_historico (restaurante_id BIGINT, produto_id BIGINT,
 mes DATE, categoria VARCHAR(30), ficticio BOOLEAN NOT NULL, PRIMARY KEY(restaurante_id,produto_id,mes));
 CREATE TABLE IF NOT EXISTS fifo_campanha_planejada (lote_id BIGINT PRIMARY KEY, ativada_em TIMESTAMP(6),
 inicio_efetivo_em TIMESTAMP(6), fim_exclusivo_em TIMESTAMP(6));
 START TRANSACTION;
INSERT INTO tb_restaurante (id,nome) VALUES
(1,'Loja A fictícia FIFO'),
(2,'Loja B fictícia FIFO')
ON DUPLICATE KEY UPDATE id=tb_restaurante.id;
INSERT INTO tb_usuario (id,nome,email,senha_hash,tipo,ativo,restaurante_id) VALUES
(1,'Gerente fictício 1','gerente.1@fifo.example','$2a$12$tHAF6hTUBZTwhobYs7.RQ.SkmmR327d/0l8t1WHLp/cpecKZb9sea','GERENTE',1,1),
(2,'Gerente fictício 2','gerente.2@fifo.example','$2a$12$tHAF6hTUBZTwhobYs7.RQ.SkmmR327d/0l8t1WHLp/cpecKZb9sea','GERENTE',1,2)
ON DUPLICATE KEY UPDATE id=tb_usuario.id;
INSERT INTO tb_ingrediente (id,restaurante_id,id_externo,nome,unidade,peso_por_unidade_g,estoque_minimo,data_cadastro) VALUES
(101,1,'1','Tomate','kg',1000,1000,'2026-04-01 11:00:00'),
(102,1,'2','Arroz','kg',1000,1000,'2026-04-01 11:00:00'),
(103,1,'3','Pão','un',80,1000,'2026-04-01 11:00:00'),
(104,1,'4','Óleo','L',900,1000,'2026-04-01 11:00:00'),
(201,2,'1','Tomate','kg',1000,1000,'2026-04-01 11:00:00'),
(202,2,'2','Arroz','kg',1000,1000,'2026-04-01 11:00:00'),
(203,2,'3','Pão','un',80,1000,'2026-04-01 11:00:00'),
(204,2,'4','Óleo','L',900,1000,'2026-04-01 11:00:00')
ON DUPLICATE KEY UPDATE id=tb_ingrediente.id;
INSERT INTO tb_produto (id,restaurante_id,id_externo,nome,descricao,preco,categoria,ativo,data_cadastro) VALUES
(101,1,'101','Prato 1','Receita fictícia completa FIFO',20,'Refeição',1,'2026-04-01 11:00:00'),
(102,1,'102','Prato 2','Receita fictícia completa FIFO',15,'Refeição',1,'2026-04-01 11:00:00'),
(201,2,'101','Prato 1','Receita fictícia completa FIFO',20,'Refeição',1,'2026-04-01 11:00:00'),
(202,2,'102','Prato 2','Receita fictícia completa FIFO',15,'Refeição',1,'2026-04-01 11:00:00')
ON DUPLICATE KEY UPDATE id=tb_produto.id;
INSERT INTO tb_ficha_tecnica (produto_id,ingrediente_id,quantidade_usada,unidade) VALUES
(101,101,200,'g'),
(101,102,100,'g'),
(101,103,80,'g'),
(101,104,9,'g'),
(102,101,100,'g'),
(102,102,50,'g'),
(102,103,80,'g'),
(102,104,9,'g'),
(201,201,200,'g'),
(201,202,100,'g'),
(201,203,80,'g'),
(201,204,9,'g'),
(202,201,100,'g'),
(202,202,50,'g'),
(202,203,80,'g'),
(202,204,9,'g')
ON DUPLICATE KEY UPDATE produto_id=tb_ficha_tecnica.produto_id;
INSERT INTO fifo_fixture VALUES(1,'2026-10-01',@fifo_cenario) ON DUPLICATE KEY UPDATE id=id;
COMMIT;
END$$
CALL fifo_carregar()$$
DROP PROCEDURE fifo_carregar$$
DELIMITER ;
