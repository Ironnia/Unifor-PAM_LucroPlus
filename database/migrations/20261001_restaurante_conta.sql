DELIMITER $$
DROP PROCEDURE IF EXISTS migrar_restaurante_conta_restaurante$$
CREATE PROCEDURE migrar_restaurante_conta_restaurante()
BEGIN
    IF DATABASE() IS NULL OR LEFT(DATABASE(),12) <> 'restaurante_' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='E1A autorizado somente em banco isolado restaurante_';
    END IF;
    CREATE TABLE IF NOT EXISTS tb_restaurante (
        id BIGINT NOT NULL AUTO_INCREMENT,
        nome VARCHAR(150) NOT NULL,
        PRIMARY KEY (id)
    );
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='tb_usuario' AND column_name='restaurante_id') THEN
        ALTER TABLE tb_usuario ADD COLUMN restaurante_id BIGINT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage
        WHERE table_schema=DATABASE() AND table_name='tb_usuario' AND column_name='restaurante_id'
            AND referenced_table_name='tb_restaurante' AND referenced_column_name='id') THEN
        ALTER TABLE tb_usuario ADD CONSTRAINT fk_usuario_restaurante
            FOREIGN KEY (restaurante_id) REFERENCES tb_restaurante(id);
    END IF;
END$$
CALL migrar_restaurante_conta_restaurante()$$
DROP PROCEDURE migrar_restaurante_conta_restaurante$$
DELIMITER ;
