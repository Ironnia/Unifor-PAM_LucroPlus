SET NAMES utf8mb4;
USE lucroplus_db;

START TRANSACTION;

UPDATE tb_ingrediente AS ingrediente
JOIN tb_lote AS lote ON lote.ingrediente_id = ingrediente.id
SET ingrediente.nome = 'Pão de Hambúrguer',
    ingrediente.unidade = 'un',
    ingrediente.peso_por_unidade_g = 80
WHERE lote.numero_lote IN ('LOT-2026-001', 'LOT-2026-002')
  AND ingrediente.id = 1;

UPDATE tb_lote AS lote
JOIN tb_ingrediente AS ingrediente ON ingrediente.id = lote.ingrediente_id
SET lote.quantidade_g = ROUND(
        lote.quantidade *
        CASE
            WHEN LOWER(ingrediente.unidade) = 'un' THEN ingrediente.peso_por_unidade_g
            WHEN LOWER(ingrediente.unidade) IN ('kg', 'l') THEN 1000
            ELSE 1
        END
    ),
    lote.custo_unitario = ROUND(
        lote.custo_unitario /
        CASE
            WHEN LOWER(ingrediente.unidade) = 'un' THEN ingrediente.peso_por_unidade_g
            WHEN LOWER(ingrediente.unidade) IN ('kg', 'l') THEN 1000
            ELSE 1
        END,
        4
    )
WHERE lote.quantidade_g = 0
  AND lote.quantidade > 0;

UPDATE tb_lote
SET data_entrada = CURDATE(),
    data_validade = CASE numero_lote
        WHEN 'LOT-2026-001' THEN DATE_ADD(CURDATE(), INTERVAL 4 DAY)
        WHEN 'LOT-2026-002' THEN DATE_ADD(CURDATE(), INTERVAL 10 DAY)
        WHEN 'LOT-2026-003' THEN DATE_ADD(CURDATE(), INTERVAL 2 DAY)
        WHEN 'LOT-2026-004' THEN DATE_ADD(CURDATE(), INTERVAL 12 DAY)
        WHEN 'LOT-2026-005' THEN DATE_ADD(CURDATE(), INTERVAL 3 DAY)
        WHEN 'LOT-2026-006' THEN DATE_ADD(CURDATE(), INTERVAL 14 DAY)
        WHEN 'LOT-2026-007' THEN DATE_ADD(CURDATE(), INTERVAL 1 DAY)
        WHEN 'LOT-2026-008' THEN DATE_ADD(CURDATE(), INTERVAL 5 DAY)
        WHEN 'LOT-2026-009' THEN DATE_ADD(CURDATE(), INTERVAL 7 DAY)
        WHEN 'LOT-2026-010' THEN DATE_ADD(CURDATE(), INTERVAL 20 DAY)
        WHEN 'LOT-COST-001' THEN DATE_ADD(CURDATE(), INTERVAL 2 DAY)
        WHEN 'LOT-PROV-002' THEN DATE_ADD(CURDATE(), INTERVAL 4 DAY)
        WHEN 'LOT-MARAC-003' THEN DATE_ADD(CURDATE(), INTERVAL 9 DAY)
        WHEN 'LOT-CHOC-004' THEN DATE_ADD(CURDATE(), INTERVAL 15 DAY)
        ELSE data_validade
    END
WHERE numero_lote IN (
    'LOT-2026-001', 'LOT-2026-002', 'LOT-2026-003', 'LOT-2026-004',
    'LOT-2026-005', 'LOT-2026-006', 'LOT-2026-007', 'LOT-2026-008',
    'LOT-2026-009', 'LOT-2026-010', 'LOT-COST-001', 'LOT-PROV-002',
    'LOT-MARAC-003', 'LOT-CHOC-004'
);

UPDATE tb_lote
SET observacao = CASE numero_lote
        WHEN 'LOT-2026-003' THEN 'Vencimento próximo'
        WHEN 'LOT-2026-005' THEN 'Lote em atenção'
        WHEN 'LOT-2026-007' THEN 'Vencimento crítico'
        ELSE observacao
    END
WHERE numero_lote IN ('LOT-2026-003', 'LOT-2026-005', 'LOT-2026-007');

COMMIT;

SELECT COUNT(*) AS total_lotes,
       SUM(quantidade_g = 0) AS lotes_com_quantidade_zerada,
       SUM(data_validade <= DATE_ADD(CURDATE(), INTERVAL 2 DAY)) AS lotes_criticos,
       SUM(data_validade BETWEEN DATE_ADD(CURDATE(), INTERVAL 3 DAY)
                            AND DATE_ADD(CURDATE(), INTERVAL 5 DAY)) AS lotes_atencao,
       SUM(data_validade > DATE_ADD(CURDATE(), INTERVAL 5 DAY)) AS lotes_seguros
FROM tb_lote;

SELECT id, nome, unidade, peso_por_unidade_g
FROM tb_ingrediente
WHERE id = 1;
