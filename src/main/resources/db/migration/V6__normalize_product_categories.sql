-- Limpia las categorias que quedaron con espacios al inicio o al final.
--
-- Un espacio invisible hacia que "Nuevo" y "Nuevo " se trataran como categorias distintas, y el
-- filtro del catalogo mostraba dos opciones que se veian identicas, cada una con solo una parte
-- de los productos. Desde ahora ProductService las normaliza al guardar; esto arregla las que ya
-- estaban en la base.

UPDATE products
SET category = TRIM(REGEXP_REPLACE(category, '\s+', ' '))
WHERE category IS NOT NULL
  AND category <> TRIM(REGEXP_REPLACE(category, '\s+', ' '));

COMMIT;
