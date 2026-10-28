DELIMITER $$
DROP PROCEDURE IF EXISTS preparar_fixture_restaurante$$
CREATE PROCEDURE preparar_fixture_restaurante()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    IF DATABASE() IS NULL OR LEFT(DATABASE(),12) <> 'restaurante_' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fixture exige banco isolado restaurante_';
    END IF;
    START TRANSACTION;
    IF EXISTS (SELECT 1 FROM tb_restaurante WHERE id IN (91001,91002)
        AND nome <> IF(id=91001,'Loja A fictícia','Loja B fictícia'))
        OR EXISTS (SELECT 1 FROM tb_usuario WHERE
            (id IN (91001,91002) AND email <> IF(id=91001,'gerente.a@e1a.example','gerente.b@e1a.example'))
            OR (email IN ('gerente.a@e1a.example','gerente.b@e1a.example') AND
                id <> IF(email='gerente.a@e1a.example',91001,91002))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Colisao com IDs ou emails reservados do fixture';
    END IF;
    INSERT INTO tb_restaurante (id,nome) VALUES (91001,'Loja A fictícia'),(91002,'Loja B fictícia')
        ON DUPLICATE KEY UPDATE id=VALUES(id);
    INSERT INTO tb_usuario (id,nome,email,senha_hash,tipo,ativo,restaurante_id) VALUES
        (91001,'Gerente A','gerente.a@e1a.example','$2a$12$tHAF6hTUBZTwhobYs7.RQ.SkmmR327d/0l8t1WHLp/cpecKZb9sea','GERENTE',TRUE,91001),
        (91002,'Gerente B','gerente.b@e1a.example','$2a$12$tHAF6hTUBZTwhobYs7.RQ.SkmmR327d/0l8t1WHLp/cpecKZb9sea','GERENTE',TRUE,91002)
        ON DUPLICATE KEY UPDATE id=VALUES(id);
    COMMIT;
END$$
CALL preparar_fixture_restaurante()$$
DROP PROCEDURE preparar_fixture_restaurante$$
DELIMITER ;
