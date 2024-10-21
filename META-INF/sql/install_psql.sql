DO $$
DECLARE
    next_value numeric;
BEGIN
    -- Obtiene el siguiente valor de la secuencia
    SELECT currentnext INTO next_value FROM ad_sequence WHERE name = 'AD_SysConfig';

    -- Verifica si ambos 'Ticket_request_type_ID' y 'Ticket_support_user_ID' no existen
    IF (SELECT COUNT(*) FROM adempiere.ad_sysconfig WHERE ad_client_id = 0 AND ad_org_id = 0 AND "name" IN ('Ticket_request_type_ID', 'Ticket_support_user_ID')) = 0 THEN
        -- Inserta el nuevo registro para 'Ticket_request_type_ID' usando el valor de next_value
        INSERT INTO adempiere.ad_sysconfig (ad_sysconfig_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, "name", value, description)
        VALUES (next_value, 0, 0, 'Y', NOW(), 100, NOW(), 100, 'Ticket_request_type_ID', '101', 'ID de tipo de request para el plugin');
        
        -- Inserta el nuevo registro para 'Ticket_support_user_ID' usando el valor de next_value + 1
        INSERT INTO adempiere.ad_sysconfig (ad_sysconfig_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, "name", value, description)
        VALUES (next_value + 1, 0, 0, 'Y', NOW(), 100, NOW(), 100, 'Ticket_support_user_ID', '11', 'ID de usuario configurado para el plugin');
        
        -- Actualiza el valor de la secuencia solo si se realizaron las inserciones
        UPDATE ad_sequence SET currentnext = next_value + 2 WHERE name = 'AD_SysConfig';
    END IF;
END $$;
