DO $$
DECLARE
    next_value numeric;
BEGIN
    -- Obtiene el siguiente valor de la secuencia
    SELECT currentnext INTO next_value FROM ad_sequence WHERE name = 'AD_SysConfig';

    -- Verifica si ambos 'Ticket_request_type_ID' y 'Ticket_support_user_ID' no existen
    IF NOT EXISTS (SELECT 1 FROM ad_sysconfig WHERE ad_client_id = 0 AND ad_org_id = 0 AND "name" IN ('Ticket_request_type_ID', 'Ticket_support_user_ID')) THEN
        -- Inserta el nuevo registro para 'Ticket_request_type_ID' usando el valor de next_value
        --INSERT INTO ad_sysconfig (ad_sysconfig_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, "name", value, description)
        --VALUES (next_value, 0, 0, 'Y', NOW(), 100, NOW(), 100, 'Ticket_request_type_ID', '101', 'ID de tipo de request para el plugin');
        
        -- Inserta el nuevo registro para 'Ticket_support_user_ID' usando el valor de next_value + 1
        INSERT INTO ad_sysconfig (ad_sysconfig_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, "name", value, description)
        VALUES (next_value, 0, 0, 'Y', NOW(), 100, NOW(), 100, 'Ticket_support_user_ID', '11', 'ID de usuario configurado para el plugin');
        
        -- Actualiza el valor de la secuencia solo si se realizaron las inserciones
        UPDATE ad_sequence SET currentnext = next_value + 1 WHERE name = 'AD_SysConfig';
    END IF;
END $$;


DO $$ 
DECLARE
    next_value numeric;
BEGIN
    -- Obtiene el siguiente valor de la secuencia
    SELECT currentnext INTO next_value FROM ad_sequence WHERE name = 'AD_ModelValidator';

    -- Verifica si el 'modelvalidationclass' ya existe
    IF NOT EXISTS (SELECT 1 FROM ad_modelvalidator WHERE modelvalidationclass = 'org.sannet.RequestValidatorPackage.RequestValidator') THEN
        -- Inserta el nuevo registro usando el valor de currentnext
        INSERT INTO adempiere.ad_modelvalidator (ad_modelvalidator_id, ad_client_id, ad_org_id, created, createdby, updated, updatedby, isactive, name, description, entitytype, modelvalidationclass, seqno)
        VALUES (next_value, 0, 0, NOW(), 100, NOW(), 100, 'Y', 'Model Validator to Request', 'Request', 'U', 'org.sannet.RequestValidatorPackage.RequestValidator', 0);
        
        -- Actualiza el valor de la secuencia solo si se realiza la inserción
        UPDATE ad_sequence SET currentnext = next_value + 1 WHERE name = 'AD_ModelValidator';
    END IF;
END $$;


DO $$ 
DECLARE
    id_list numeric[]; -- Lista de todos los ad_client_id
    current_id numeric; -- ID actual en la iteración
    status_seq_value numeric; -- Valor actual de la secuencia para R_Status
    category_seq_value numeric; -- Valor de la secuencia de categoría
    requesttype_seq_value numeric; -- Valor de la secuencia para R_RequestType
BEGIN
    -- Obtiene la lista de todos los ad_client_id mayores o iguales a 1000000
    SELECT ARRAY(SELECT ad_client_id FROM ad_client WHERE ad_client_id >= 1000000) INTO id_list;

    -- Itera sobre cada id en id_list
    FOREACH current_id IN ARRAY id_list LOOP
        -- Verifica si la tabla r_statuscategory tiene al menos un elemento con ese ad_client_id
        IF NOT EXISTS (SELECT 1 FROM r_statuscategory WHERE ad_client_id = current_id) THEN
            -- Inserta la categoría y obtiene el ID de la secuencia
            SELECT currentnext INTO category_seq_value FROM ad_sequence WHERE name = 'R_StatusCategory';
            
            INSERT INTO r_statuscategory (r_statuscategory_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, description, help, isdefault)
            VALUES (category_seq_value, current_id, 0, 'Y', NOW(), 100, NOW(), 100, 'Default', 'Default Request Status', NULL, 'Y');
            
            -- Obtiene el valor inicial de la secuencia para R_Status
            SELECT currentnext INTO status_seq_value FROM ad_sequence WHERE name = 'R_Status';

            -- Inserta el primer estado (Abierto)
            INSERT INTO r_status (r_status_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, description, help, isdefault, isopen, isclosed, value, next_status_id, update_status_id, timeoutdays, iswebcanupdate, isfinalclose, seqno, r_statuscategory_id)
            VALUES (status_seq_value, current_id, 0, 'Y', NOW(), 100, NOW(), 100, 'Abierto', NULL, NULL, 'Y', 'Y', 'N', 'Abierto', NULL, NULL, 0, 'Y', 'N', 1, category_seq_value);

            -- Incrementa la secuencia de estado
            status_seq_value := status_seq_value + 1;
            
            -- Inserta el segundo estado (Cerrado)
            INSERT INTO r_status (r_status_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, description, help, isdefault, isopen, isclosed, value, next_status_id, update_status_id, timeoutdays, iswebcanupdate, isfinalclose, seqno, r_statuscategory_id)
            VALUES (status_seq_value, current_id, 0, 'Y', NOW(), 100, NOW(), 100, 'Cerrado', NULL, NULL, 'N', 'Y', 'N', 'Cerrado', NULL, NULL, 0, 'N', 'N', 9, category_seq_value);

            -- Incrementa la secuencia de estado
            status_seq_value := status_seq_value + 1;

            -- Inserta el tercer estado (En espera de respuesta) con referencia al estado Cerrado
            INSERT INTO r_status (r_status_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, description, help, isdefault, isopen, isclosed, value, next_status_id, update_status_id, timeoutdays, iswebcanupdate, isfinalclose, seqno, r_statuscategory_id)
            VALUES (status_seq_value, current_id, 0, 'Y', NOW(), 100, NOW(), 100, 'En espera de respuesta', NULL, NULL, 'N', 'Y', 'N', 'En espera', status_seq_value - 1, status_seq_value - 2, 5, 'Y', 'N', 3, category_seq_value);

            -- Incrementa la secuencia de estado
            status_seq_value := status_seq_value + 1;

            -- Inserta el cuarto estado (En progreso) con referencia al estado Cerrado
            INSERT INTO r_status (r_status_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, description, help, isdefault, isopen, isclosed, value, next_status_id, update_status_id, timeoutdays, iswebcanupdate, isfinalclose, seqno, r_statuscategory_id)
            VALUES (status_seq_value, current_id, 0, 'Y', NOW(), 100, NOW(), 100, 'En progreso', NULL, NULL, 'N', 'Y', 'N', 'En progreso', status_seq_value - 2, status_seq_value - 3, 0, 'Y', 'N', 2, category_seq_value);

            -- Incrementa la secuencia de estado
            status_seq_value := status_seq_value + 1;

            -- Inserta en la tabla r_requesttype usando la secuencia R_RequestType
            SELECT currentnext INTO requesttype_seq_value FROM ad_sequence WHERE name = 'R_RequestType';
            
            INSERT INTO r_requesttype (r_requesttype_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, description, isdefault, isselfservice, duedatetolerance, isemailwhenoverdue, isemailwhendue, isinvoiced, autoduedatedays, confidentialtype, isautochangerequest, isconfidentialinfo, r_statuscategory_id, isindexed, headercolor, contentcolor)
            VALUES (requesttype_seq_value, current_id, 0, 'Y', NOW(), 100, NOW(), 100, 'Ticket de soporte', NULL, 'N', 'N', 7, 'N', 'N', 'N', NULL, 'C', 'N', 'N', category_seq_value, 'Y', NULL, NULL);

            -- Actualiza la secuencia de request type solo una vez después de todas las iteraciones
            UPDATE ad_sequence SET currentnext = requesttype_seq_value + 1 WHERE name = 'R_RequestType';

            -- Actualiza la secuencia de categoría solo una vez después de todas las iteraciones
            UPDATE ad_sequence SET currentnext = category_seq_value + 1 WHERE name = 'R_StatusCategory';

            -- Actualiza la secuencia solo una vez después de las inserciones
            UPDATE ad_sequence SET currentnext = status_seq_value WHERE name = 'R_Status';
        END IF;
    END LOOP;

END $$;


DO $$ 
DECLARE
    next_value INT;
BEGIN
    -- Verificar si ya existe el columnname con el entitytype
    IF NOT EXISTS (SELECT 1 FROM ad_element WHERE columnname = 'workedhours' AND entitytype = 'U') THEN
        -- Obtener el siguiente valor de la secuencia para AD_Element
        SELECT currentnext INTO next_value FROM ad_sequence WHERE name = 'AD_Element';

        -- Insertar en ad_element
        INSERT INTO ad_element (ad_element_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, columnname, entitytype, name, printname, description, help, po_name, po_printname, po_description, po_help, placeholder)
        VALUES
        (next_value, 0, 0, 'Y', NOW(), 100, NOW(), 100, 'workedhours', 'U', 'workedhours', 'workedhours', NULL, NULL, NULL, NULL, NULL, NULL, NULL);
    
        -- Insertar en ad_element_trl (no es necesaria verificación, solo traducción directa)
        INSERT INTO ad_element_trl (ad_element_id, ad_language, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, printname, description, help, po_name, po_printname, po_description, po_help, istranslated, placeholder)
        VALUES
        (next_value, 'es_CO', 0, 0, 'Y', NOW(), 100, NOW(), 100, 'workedhours', 'workedhours', NULL, NULL, NULL, NULL, NULL, NULL, 'N', NULL);

        -- Actualizar la secuencia de AD_Element
        UPDATE ad_sequence SET currentnext = next_value + 1 WHERE name = 'AD_Element';
    END IF;
END $$;


DO $$ 
DECLARE
    next_value INT;
    element_id INT;
BEGIN
    -- Obtener el ID del elemento basado en columnname y entitytype
    SELECT ad_element_id INTO element_id FROM ad_element WHERE columnname = 'workedhours' AND entitytype = 'U' LIMIT 1;

    -- Si no se encontró el ID del elemento, se lanza un error
    IF element_id IS NULL THEN
        RAISE EXCEPTION 'No se encontró el ad_element_id para columnname ''workedhours'' y entitytype ''U''';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ad_column WHERE name = 'workedhours' AND entitytype = 'U' AND columnname = 'workedhours' AND ad_table_id = 417) THEN
        -- Obtener el siguiente valor de la secuencia para AD_Column
        SELECT currentnext INTO next_value FROM ad_sequence WHERE name = 'AD_Column';

        -- Insertar o actualizar en ad_column
        INSERT INTO ad_column (ad_column_id, ad_client_id, ad_org_id, isactive, created, updated, createdby, updatedby, name, description, help, version, entitytype, columnname, ad_table_id, ad_reference_id, ad_reference_value_id, ad_val_rule_id, fieldlength, defaultvalue, iskey, isparent, ismandatory, isupdateable, readonlylogic, isidentifier, seqno, istranslated, isencrypted, callout, vformat, valuemin, valuemax, isselectioncolumn, ad_element_id, ad_process_id, issyncdatabase, isalwaysupdateable, columnsql, mandatorylogic, infofactoryclass, isautocomplete, isallowlogging, formatpattern, isallowcopy, seqnoselection, istoolbarbutton, issecure, ad_chart_id, fkconstraintname, fkconstrainttype, pa_dashboardcontent_id, placeholder, ishtml, ad_val_rule_lookup_id, ad_infowindow_id, alwaysupdatablelogic, fkconstraintmsg_id, partitioningmethod, ispartitionkey, seqnopartition, rangepartitioninterval)
        VALUES
        (next_value, 0, 0, 'Y', NOW(), NOW(), 100, 100, 'workedhours', NULL, NULL, 0.0, 'U', 'workedhours', 417, 22, NULL, NULL, 10, NULL, 'N', 'N', 'N', 'Y', NULL, 'N', NULL, 'N', 'N', NULL, NULL, NULL, NULL, 'N', element_id, NULL, 'N', 'N', NULL, NULL, NULL, 'N', 'Y', NULL, 'Y', NULL, 'N', 'N', NULL, NULL, 'N', NULL, NULL, 'N', NULL, NULL, NULL, NULL, NULL, 'N', NULL, NULL);

        -- Insertar o actualizar en ad_column_trl
        INSERT INTO ad_column_trl (ad_column_id, ad_language, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, istranslated, placeholder)
        VALUES
        (next_value, 'es_CO', 0, 0, 'Y', NOW(), 100, NOW(), 100, 'workedhours', 'N', NULL);
    END IF;
END $$;


DO $$ 
DECLARE
    next_value INT;
    column_id INT;
BEGIN
    -- Obtener el ID del elemento basado en columnname y entitytype
    SELECT ad_column_id INTO column_id FROM ad_column WHERE name = 'workedhours' AND entitytype = 'U' AND columnname = 'workedhours' AND ad_table_id = 417 LIMIT 1;

    -- Si no se encontró el ID del elemento, se lanza un error
    IF column_id IS NULL THEN
        RAISE EXCEPTION 'No se encontró el ad_column_id para name ''workedhours'', entitytype ''U'', columname ''workedhours'' y ad_table_id ''417''';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ad_field WHERE name = 'Horas trabajadas' AND entitytype = 'U' AND ad_column_id = column_id AND ad_tab_id = 344) THEN
        -- Obtener el siguiente valor de la secuencia para AD_Field
        SELECT currentnext INTO next_value FROM ad_sequence WHERE name = 'AD_Field';

        -- Insertar en ad_field
        INSERT INTO ad_field (ad_field_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, description, help, iscentrallymaintained, ad_tab_id, ad_column_id, ad_fieldgroup_id, isdisplayed, displaylogic, displaylength, isreadonly, seqno, sortno, issameline, isheading, isfieldonly, isencrypted, entitytype, obscuretype, ad_reference_id, ismandatory, included_tab_id, defaultvalue, ad_reference_value_id, ad_val_rule_id, infofactoryclass, isallowcopy, seqnogrid, isdisplayedgrid, xposition, numlines, columnspan, isquickentry, isupdateable, isalwaysupdateable, mandatorylogic, readonlylogic, istoolbarbutton, isadvancedfield, isdefaultfocus, vformat, ad_labelstyle_id, ad_fieldstyle_id, placeholder, isquickform, isselectioncolumn, ad_val_rule_lookup_id, columnsql, ad_chart_id, alwaysupdatablelogic)
        VALUES
        (next_value, 0, 0, 'Y', NOW(), 100, NOW(), 100, 'Horas trabajadas', NULL, NULL, 'Y', 344, column_id, NULL, 'Y', NULL, 0, 'N', 340, 0, 'N', 'N', 'N', 'N', 'U', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 600, 'Y', 1, 1, 1, 'N', NULL, NULL, NULL, NULL, NULL, 'N', 'N', NULL, NULL, NULL, NULL, 'N', NULL, NULL, NULL, NULL, NULL),
        (next_value + 1, 0, 0, 'Y', NOW(), 100, NOW(), 100, 'Horas trabajadas', NULL, NULL, 'Y', 402, column_id, NULL, 'Y', NULL, 0, 'N', 580, 0, 'N', 'N', 'N', 'N', 'U', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 610, 'Y', 1, 1, 1, 'N', NULL, NULL, '1=1', NULL, NULL, 'N', 'N', NULL, NULL, NULL, NULL, 'N', NULL, NULL, NULL, NULL, NULL);
     
        -- Insertar en ad_field_trl
        INSERT INTO ad_field_trl (ad_field_id, ad_language, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, description, help, istranslated, placeholder)
        VALUES
        (next_value, 'es_CO', 0, 0, 'Y', NOW(), 100, NOW(), 100, 'workedhours', NULL, NULL, 'N', NULL),
        (next_value + 1, 'es_CO', 0, 0, 'Y', NOW(), 100, NOW(), 100, 'workedhours', NULL, NULL, 'N', NULL);

        -- Actualizar la secuencia de AD_Field
        UPDATE ad_sequence SET currentnext = next_value + 2 WHERE name = 'AD_Field';
    END IF;
END $$;

