USE lucroplus_db;

SELECT
    data_venda,
    SUM(valor_total) AS faturamento,
    COUNT(id) AS total_pedidos
FROM tb_venda
WHERE data_venda >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY data_venda
ORDER BY data_venda ASC;

SELECT
    p.id,
    p.nome,
    SUM(iv.quantidade) AS total_vendido,
    SUM(iv.quantidade * iv.preco_unitario) AS receita_total
FROM tb_item_venda iv
JOIN tb_produto p ON iv.produto_id = p.id
JOIN tb_venda v ON iv.venda_id = v.id
GROUP BY p.id, p.nome
ORDER BY total_vendido DESC
LIMIT 5;

SELECT
    p.id,
    p.nome,
    COALESCE(SUM(iv.quantidade), 0) AS total_vendido
FROM tb_produto p
LEFT JOIN tb_item_venda iv ON p.id = iv.produto_id
LEFT JOIN tb_venda v ON iv.venda_id = v.id AND v.data_venda >= DATE_SUB(CURDATE(), INTERVAL 15 DAY)
WHERE p.ativo = TRUE
GROUP BY p.id, p.nome
ORDER BY total_vendido ASC
LIMIT 10;
