DELIMITER $$
DROP PROCEDURE IF EXISTS preparar_fixture_isolamento$$
CREATE PROCEDURE preparar_fixture_isolamento()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    IF DATABASE() IS NULL OR LEFT(DATABASE(),11) <> 'isolamento_' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fixture exige banco isolado isolamento_';
    END IF;
    START TRANSACTION;
    IF EXISTS (SELECT 1 FROM tb_restaurante WHERE id IN (91001,91002)
        AND nome <> IF(id=91001,'Loja A fictícia','Loja B fictícia'))
        OR EXISTS (SELECT 1 FROM tb_usuario WHERE id IN (91001,91002,91003)
            AND email <> CASE id WHEN 91001 THEN 'gerente.a@e1b.example' WHEN 91002 THEN 'gerente.b@e1b.example' ELSE 'admin@e1b.example' END)
        OR EXISTS (SELECT 1 FROM tb_usuario WHERE email IN ('gerente.a@e1b.example','gerente.b@e1b.example','admin@e1b.example')
            AND id NOT IN (91001,91002,91003))
        OR EXISTS (SELECT 1 FROM tb_produto WHERE id IN (92001,92002) AND (restaurante_id IS NULL OR restaurante_id <> id-1000))
        OR EXISTS (SELECT 1 FROM tb_ingrediente WHERE id IN (92001,92002) AND (restaurante_id IS NULL OR restaurante_id <> id-1000))
        OR EXISTS (SELECT 1 FROM tb_lote WHERE id IN (92001,92002) AND (restaurante_id IS NULL OR restaurante_id <> id-1000))
        OR EXISTS (SELECT 1 FROM tb_venda WHERE id IN (92001,92002) AND (restaurante_id IS NULL OR restaurante_id <> id-1000))
        OR EXISTS (SELECT 1 FROM tb_item_venda WHERE id IN (92001,92002) AND venda_id <> id)
        OR EXISTS (SELECT 1 FROM tb_alerta WHERE id IN (92001,92002) AND lote_id <> id)
        OR EXISTS (SELECT 1 FROM tb_promocao WHERE id=92002 AND (restaurante_id IS NULL OR restaurante_id<>91002)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Colisao com IDs reservados do fixture E1B';
    END IF;
    INSERT INTO tb_restaurante(id,nome) VALUES (91001,'Loja A fictícia'),(91002,'Loja B fictícia') ON DUPLICATE KEY UPDATE id=VALUES(id);
    INSERT INTO tb_usuario(id,nome,email,senha_hash,tipo,ativo,restaurante_id) VALUES
        (91001,'Gerente A','gerente.a@e1b.example','$2a$12$tHAF6hTUBZTwhobYs7.RQ.SkmmR327d/0l8t1WHLp/cpecKZb9sea','GERENTE',TRUE,91001),
        (91002,'Gerente B','gerente.b@e1b.example','$2a$12$tHAF6hTUBZTwhobYs7.RQ.SkmmR327d/0l8t1WHLp/cpecKZb9sea','GERENTE',TRUE,91002),
        (91003,'Admin E1B','admin@e1b.example','$2a$12$tHAF6hTUBZTwhobYs7.RQ.SkmmR327d/0l8t1WHLp/cpecKZb9sea','ADMIN',TRUE,NULL)
        ON DUPLICATE KEY UPDATE id=VALUES(id);
    INSERT INTO tb_produto(id,restaurante_id,id_externo,nome,descricao,preco,categoria,ativo) VALUES
        (92001,91001,'100','Prato comum','Loja A',20,'Teste',TRUE),
        (92002,91002,'100','Prato comum','Loja B',30,'Teste',TRUE) ON DUPLICATE KEY UPDATE id=VALUES(id);
    INSERT INTO tb_ingrediente(id,restaurante_id,id_externo,nome,unidade,peso_por_unidade_g,estoque_minimo) VALUES
        (92001,91001,'100','Tomate','g',1,0),(92002,91002,'100','Tomate','g',1,0) ON DUPLICATE KEY UPDATE id=VALUES(id);
    INSERT INTO tb_lote(id,restaurante_id,id_externo,ingrediente_id,quantidade_g,custo_unitario,data_validade,data_entrada,numero_lote) VALUES
        (92001,91001,'100',92001,1000,0.01,CURRENT_DATE+INTERVAL 7 DAY,CURRENT_DATE-INTERVAL 2 DAY,'LOJA-A'),
        (92002,91002,'100',92002,2000,0.02,CURRENT_DATE+INTERVAL 7 DAY,CURRENT_DATE-INTERVAL 2 DAY,'LOJA-B') ON DUPLICATE KEY UPDATE id=VALUES(id);
    INSERT INTO tb_ficha_tecnica(produto_id,ingrediente_id,quantidade_usada,unidade) VALUES
        (92001,92001,100,'g'),(92002,92002,100,'g') ON DUPLICATE KEY UPDATE produto_id=VALUES(produto_id);
    INSERT INTO tb_venda(id,restaurante_id,id_externo,data_venda,valor_total,origem) VALUES
        (92001,91001,'100',CURRENT_DATE-INTERVAL 1 DAY,20,'fixture_isolamento'),
        (92002,91002,'100',CURRENT_DATE-INTERVAL 1 DAY,30,'fixture_isolamento') ON DUPLICATE KEY UPDATE id=VALUES(id);
    INSERT INTO tb_item_venda(id,venda_id,produto_id,quantidade,preco_unitario) VALUES
        (92001,92001,92001,1,20),(92002,92002,92002,1,30) ON DUPLICATE KEY UPDATE id=VALUES(id);
    INSERT INTO tb_alerta(id,lote_id,tipo,mensagem,data_alerta,visualizado) VALUES
        (92001,92001,'VENCIMENTO','Fixture A',CURRENT_DATE,FALSE),
        (92002,92002,'VENCIMENTO','Fixture B',CURRENT_DATE,TRUE) ON DUPLICATE KEY UPDATE id=VALUES(id);
    INSERT INTO tb_acao_alerta(lote_id,alerta_id,acao,data_acao) VALUES
        (92002,92002,'SALVAR_LOTE',CURRENT_DATE-INTERVAL 1 DAY) ON DUPLICATE KEY UPDATE lote_id=VALUES(lote_id);

    INSERT INTO tb_promocao(id,restaurante_id,lote_id,produto_id,desconto_pct,motivo,status,data_sugestao,data_ativacao,data_inicio,data_fim,quantidade_referencia_g,valor_em_risco,valor_salvo) VALUES
        (92002,91002,92002,92002,10,'Campanha fictícia E1B','EM_ANDAMENTO',CURRENT_DATE-INTERVAL 1 DAY,CURRENT_DATE-INTERVAL 1 DAY,CURRENT_DATE-INTERVAL 1 DAY,CURRENT_DATE-INTERVAL 1 DAY,2000,40,0)
        ON DUPLICATE KEY UPDATE id=VALUES(id);
    COMMIT;
END$$
CALL preparar_fixture_isolamento()$$
DROP PROCEDURE preparar_fixture_isolamento$$
DELIMITER ;
