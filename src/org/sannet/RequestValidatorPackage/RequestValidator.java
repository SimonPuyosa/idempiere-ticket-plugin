package org.sannet.RequestValidatorPackage;

import org.compiere.model.MClient;
import org.compiere.model.MRequest;
import org.compiere.model.MSysConfig;
import org.compiere.model.MUser;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.PO;
import org.compiere.model.ModelValidator;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.EMail;
import org.compiere.util.Env;
//import java.io.*;

public class RequestValidator implements ModelValidator {
    private static CLogger log = CLogger.getCLogger(RequestValidator.class);
    
    @Override
    public void initialize(ModelValidationEngine engine, MClient client) {
        // Registra el validator para el modelo MRequest
        engine.addModelChange(MRequest.Table_Name, this);
        System.out.println("Request Validator initialized.");
    }
    
    @Override
    public int getAD_Client_ID() {
        return -1; // Aplicar a todos los clientes
    }
    
    private String getUserEmail(int userID) {
        MUser user = new MUser(Env.getCtx(), userID, null);
        return user.getEMail();
    }

    private String getUserName(int userID) {
        MUser user = new MUser(Env.getCtx(), userID, null);
        return user.getName();
    }
    
    private String getStatusNameFromRequestStatus(int requestTypeID) {
        String sql = "SELECT Name FROM R_Status WHERE R_RequestStatus_ID=?";
        return DB.getSQLValueString(null, sql, requestTypeID);
    }

    private String getDocumentNoFromDB() {
        String sql = "SELECT MAX(CAST(DocumentNo AS INT)) + 1 FROM R_Request";
        return DB.getSQLValueString(null, sql);
    }

    private void sendEmail(String recipientEmail, String subject, String body) {
        MClient client = MClient.get(Env.getCtx());
        String senderEmail = client.getRequestEMail(); // Correo del SMTP configurado

        if (senderEmail != null && !senderEmail.isEmpty()) {
            EMail email = new EMail(client, senderEmail, recipientEmail, subject, body);
            String status = email.send();
            if (EMail.SENT_OK.equals(status)) {
                log.info("Correo enviado exitosamente a: " + recipientEmail);
            } else {
                log.warning("No se pudo enviar el correo: " + status);
            }
        } else {
            log.warning("El correo del servidor SMTP no está configurado.");
        }
    }

    @Override
    public String modelChange(PO po, int type) throws Exception {
        if (po instanceof MRequest) {
            MRequest request = (MRequest) po;
            // Obtener el R_RequestType_ID desde AD_SysConfig
            int requestTypeID = MSysConfig.getIntValue("Ticket_request_type_ID", 0, Env.getAD_Client_ID(Env.getCtx()));

            // Obtener el userID desde AD_SysConfig
            int supportID = MSysConfig.getIntValue("Ticket_support_user_ID", 0, Env.getAD_Client_ID(Env.getCtx()));

            // Verifica si el tipo de request es 101
            if (request.getR_RequestType_ID() == requestTypeID) {
                int userModifyingID = Env.getAD_User_ID(Env.getCtx()); // Usuario que está modificando
                int requestCreatorID = request.getCreatedBy(); // Usuario que creó el request

                // Verifica si se está creando o editando el request
                if (type == ModelValidator.TYPE_BEFORE_NEW || type == ModelValidator.TYPE_BEFORE_CHANGE) {
                    log.info("Se está guardando o editando un request: " + request.getDocumentNo());

                    // Verifica si el estatus ha cambiado
                    boolean statusChanged = request.is_ValueChanged("R_Status_ID");

                    // Obtener el número del documento
                    String documentNo = request.getDocumentNo();
                    if (documentNo == null || documentNo.isEmpty()) {
                        documentNo = getDocumentNoFromDB(); // Obtener de la base de datos si está vacío
                    }
                    
                    // Definir el destinatario del correo
                    int recipientID;
                    if (userModifyingID != supportID) {
                        recipientID = supportID;
                    } else {
                        recipientID = requestCreatorID;
                    }

                    // Obtener el correo del destinatario
                    String recipientEmail = getUserEmail(recipientID);
                    if (recipientEmail == null || recipientEmail.isEmpty()) {
                        log.warning("No se pudo encontrar un correo válido para el usuario con ID: " + recipientID);
                        return null; // Si no hay correo válido, no se envía el correo
                    }
                    
                    // Obtener el nombre del estado actual
                    String statusName = getStatusNameFromRequestStatus(request.getR_Status_ID());

                    // Preparar el contenido del correo
                    String subject = (request.getR_Request_ID() == 0)
                            ? "Notificación de creación del Request #" + documentNo
                            : "Notificación de cambios en el Request #" + documentNo;
                    StringBuilder body = new StringBuilder();

                    // Información del request
                    body.append("Estimado usuario,").append("\n\n");
                    String actionMessage = (request.getR_Request_ID() == 0)
                            ? "Se ha creado un nuevo Request #" + documentNo + "."
                            : "Se ha realizado una modificación en el Request #" + documentNo + ".";
                    
                    body.append(actionMessage).append("\n\n").append("Detalles del request:").append("\n\n");

                    // Mostrar el ID del Request solo si no es 0
                    if (request.getR_Request_ID() != 0) {
                        body.append("ID del Request: ").append(request.getR_Request_ID()).append("\n");
                    }
                    
                    body.append("Usuario que creo el request: ").append(getUserName(requestCreatorID)).append("\n");
                    body.append("Usuario que esta modificando el request: ").append(getUserName(userModifyingID)).append("\n");
                    body.append("Fecha de creacion del request: ").append(request.getCreated()).append("\n");
                    body.append("Resumen del request: ").append(request.getSummary()).append("\n");

                    // Verificar si el campo Result no está vacío
                    if (request.getResult() != null && !request.getResult().isEmpty()) {
                        body.append("Resultado del request: ").append(request.getResult()).append("\n");
                    }
                    
                    if (statusChanged) {
                        body.append("El estado del request ha cambiado a: ").append(statusName).append("\n");
                    } else {
                        body.append("El estado del request no ha cambiado.\n");
                    }

                    // Enviar el correo
                    sendEmail(recipientEmail, subject, body.toString());
                }
            }
        }
        return null;
    }
    
    @Override
    public String docValidate(PO po, int timing) {
    	System.out.println("El documento se cambio su estado. PO: " + po + ", timing: " + timing);
        return null;
    }

	@Override
	public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) {
		// TODO Auto-generated method stub
		return null;
	}
}
