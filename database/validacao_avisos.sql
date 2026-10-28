SET NAMES utf8mb4;
DELIMITER $$
CREATE PROCEDURE preparar_massa_avisos_avisos()
principal: BEGIN
    DECLARE referencia DATE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF DATABASE() NOT REGEXP '^homolog_avisos_avisos_[0-9]+$' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Massa permitida somente na base isolada de avisos';
    END IF;
    SET referencia = CAST(@avisos_data_referencia AS DATE);
    IF referencia IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informar data de referencia';
    END IF;
    START TRANSACTION;
    IF EXISTS (SELECT 1 FROM tb_produto) THEN
        IF (SELECT COUNT(*) FROM tb_produto) = 9
           AND (SELECT COUNT(*) FROM tb_produto WHERE id BETWEEN 9101 AND 9109 AND categoria = 'HOMOLOG_AVISOS_AVISOS') = 9
           AND EXISTS (SELECT 1 FROM tb_lote WHERE id = 9101 AND numero_lote = 'HOMOLOG-AVISOS-9101' AND data_entrada = referencia) THEN
            COMMIT;
            SELECT 'Massa ja existente; preservada sem recarga' AS resultado, referencia AS data_referencia;
            LEAVE principal;
        END IF;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Base ocupada ou data diferente; nao sobrescrever dados';
    END IF;
    IF EXISTS (SELECT 1 FROM tb_ingrediente) OR EXISTS (SELECT 1 FROM tb_lote)
       OR EXISTS (SELECT 1 FROM tb_venda) OR EXISTS (SELECT 1 FROM tb_promocao) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A massa requer esquema vazio';
    END IF;
    INSERT INTO tb_produto (id,nome,preco,categoria,ativo) VALUES
    (9101,'TESTE — Completo',10,'HOMOLOG_AVISOS_AVISOS',1),
    (9102,'TESTE — Sem vendas',10,'HOMOLOG_AVISOS_AVISOS',1),
    (9103,'TESTE — Sem custo',10,'HOMOLOG_AVISOS_AVISOS',1),
    (9104,'TESTE — Sem custo e vendas',10,'HOMOLOG_AVISOS_AVISOS',1),
    (9105,'TESTE — Dia incompatível',10,'HOMOLOG_AVISOS_AVISOS',1),
    (9106,'TESTE — Peso ausente',10,'HOMOLOG_AVISOS_AVISOS',1),
    (9107,'TESTE — Unidade inválida',10,'HOMOLOG_AVISOS_AVISOS',1),
    (9108,'TESTE — Quantidade inválida',10,'HOMOLOG_AVISOS_AVISOS',1),
    (9109,'TESTE — Preço inválido',0,'HOMOLOG_AVISOS_AVISOS',1);
    INSERT INTO tb_ingrediente (id,nome,unidade,peso_por_unidade_g,estoque_minimo)
    SELECT id,CONCAT('Insumo fictício ',id),'g',IF(id=9106,0,1000),0 FROM tb_produto;
    INSERT INTO tb_ingrediente (id,nome,unidade,peso_por_unidade_g,estoque_minimo)
    VALUES (9200,'Alface fictícia sem lote','kg',1000,0);

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='tb_lote' AND column_name='quantidade') THEN
        INSERT INTO tb_lote (id,ingrediente_id,quantidade,quantidade_g,custo_unitario,data_validade,data_entrada,numero_lote,observacao)
        SELECT id,id,1.000,1000,0.0100,IF(id=9105,referencia,DATE_ADD(referencia,INTERVAL 7 DAY)),referencia,
               CONCAT('HOMOLOG-AVISOS-',id),'Massa fictícia isolada de avisos' FROM tb_produto;
    ELSE
        INSERT INTO tb_lote (id,ingrediente_id,quantidade_g,custo_unitario,data_validade,data_entrada,numero_lote,observacao)
        SELECT id,id,1000,0.0100,IF(id=9105,referencia,DATE_ADD(referencia,INTERVAL 7 DAY)),referencia,
               CONCAT('HOMOLOG-AVISOS-',id),'Massa fictícia isolada de avisos' FROM tb_produto;
    END IF;
    INSERT INTO tb_ficha_tecnica (produto_id,ingrediente_id,quantidade_usada,unidade)
    SELECT id,id,CASE id WHEN 9106 THEN 1 WHEN 9108 THEN 0 ELSE 100 END,
           CASE id WHEN 9106 THEN 'un' WHEN 9107 THEN 'invalida' ELSE 'g' END FROM tb_produto;
    INSERT INTO tb_ficha_tecnica (produto_id,ingrediente_id,quantidade_usada,unidade)
    VALUES (9103,9200,0.03,'kg'),(9104,9200,0.03,'kg');
    INSERT INTO tb_alerta (id,lote_id,tipo,mensagem,data_alerta,visualizado)
    SELECT id,id,'VENCIMENTO','Cenário fictício de homologação',referencia,1 FROM tb_lote;
    INSERT INTO tb_acao_alerta (lote_id,alerta_id,acao,data_acao)
    SELECT id,id,'SALVAR_LOTE',referencia FROM tb_lote;
    INSERT INTO tb_venda (id,data_venda,valor_total,origem)
    VALUES (9100,DATE_SUB(referencia,INTERVAL 1 DAY),120,'manual');
    INSERT INTO tb_item_venda (id,venda_id,produto_id,quantidade,preco_unitario)
    SELECT id,9100,id,2,preco FROM tb_produto WHERE id NOT IN (9102,9104);
    COMMIT;
    SELECT 'Massa ficticia criada' AS resultado, referencia AS data_referencia;
END$$
DELIMITER ;
CALL preparar_massa_avisos_avisos();
DROP PROCEDURE preparar_massa_avisos_avisos;
