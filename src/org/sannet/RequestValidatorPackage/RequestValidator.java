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
        String sql = "SELECT Name FROM R_Status WHERE R_Status_ID=?";
        return DB.getSQLValueString(null, sql, requestTypeID);
    }

    private String getDocumentNoFromDB() {
        String sql = "SELECT MAX(CAST(DocumentNo AS INT)) FROM R_Request";
        return DB.getSQLValueString(null, sql);
    }
    
    private String getRequestTypeName(int requestTypeID) {
        String sql = "SELECT Name FROM R_RequestType WHERE R_RequestType_ID = ?";
        return DB.getSQLValueString(null, sql, requestTypeID);
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
            String requestTypeName = getRequestTypeName(request.getR_RequestType_ID());

            // Obtener el userID desde AD_SysConfig
            int supportID = MSysConfig.getIntValue("Ticket_support_user_ID", 0, Env.getAD_Client_ID(Env.getCtx()));

            // Verifica si el tipo de request es 101
            if ("Ticket de soporte".equals(requestTypeName)) {
                int userModifyingID = Env.getAD_User_ID(Env.getCtx()); // Usuario que está modificando
                int requestCreatorID = request.getCreatedBy(); // Usuario que creó el request

                // Verifica si se está creando o editando el request
                if (type == ModelValidator.TYPE_BEFORE_NEW || type == ModelValidator.TYPE_BEFORE_CHANGE) {
                    log.info("Se está guardando o editando un request: " + request.getDocumentNo());

                    // Obtener el número del documento
                    String documentNo = request.getDocumentNo();
                    if (documentNo == null || documentNo.isEmpty()) {
                        documentNo = getDocumentNoFromDB(); // Obtener de la base de datos si está vacío
                    }
                    
                    // Verifica si el estatus ha cambiado
                    boolean statusChanged = request.is_ValueChanged("R_Status_ID");
                  
                    int currentStatusID = request.getR_Status_ID();
                    Integer oldStatusIDValue = statusChanged ? (Integer) request.get_ValueOld("R_Status_ID") : null;
                    int oldStatusID = (oldStatusIDValue != null) ? oldStatusIDValue : currentStatusID;
                    System.out.println("currentStatusID: " + currentStatusID);
                    // Verifica si el usuario que está modificando es el soporte
                    if (userModifyingID == supportID) {
                        // Si el estado NO es 102 (Close) ni 103 (Close)
                        if (currentStatusID != 1000001 && currentStatusID != 1000004) {
                            boolean isResultEmpty = request.getResult() == null || request.getResult().isEmpty();
                            // Cambiar el resumen del request a un espacio en blanco y el request no esta en blanco 
                            if (!isResultEmpty) {
                            	request.setSummary(" ");
                            }
                        }
                    }
                   
                    System.out.println("oldStatusID: " + oldStatusID);
                    // Validar las condiciones para modificar workedhours
                    if (oldStatusID == 1000003) { // Estado específico
                        boolean workedHoursChanged = request.is_ValueChanged("workedhours");
                        System.out.println("workedHoursChanged: " + workedHoursChanged);

                        if (!workedHoursChanged) { // Si no ha habido cambios en workedhours
                            // Obtener la fecha de última modificación anterior
                        	java.sql.Timestamp lastUpdatedPrevious = request.getUpdated();

                            // Guardar para actualizar 'Updated' en la base de datos
                            if (!request.save()) {
                                log.warning("Error al guardar la solicitud.");
                                System.out.println("Error al guardar la solicitud.");
                                return null;
                            }

                        	// Obtener el nuevo valor de lastUpdated
                        	java.sql.Timestamp lastUpdatedNew = request.getUpdated();

                            if (lastUpdatedPrevious == null || lastUpdatedNew == null) {
                                log.warning("No se pudo obtener la fecha de última modificación.");
                                return null;
                            }

                        	System.out.println("lastUpdatedPrevious (DB): " + lastUpdatedPrevious);
                        	System.out.println("lastUpdatedNew (Actual): " + lastUpdatedNew);

                            // Calcular la diferencia en horas y minutos
                            long differenceInMillis = lastUpdatedNew.getTime() - lastUpdatedPrevious.getTime();
                            long totalMinutes = differenceInMillis / (1000 * 60); // Total de minutos
                            long hours = totalMinutes / 60; // Horas completas
                            long remainingMinutes = totalMinutes % 60; // Minutos restantes

                            // Redondear minutos hacia arriba en intervalos de 30
                            double roundedMinutes = (remainingMinutes > 0 && remainingMinutes <= 30) ? 0.5 : (remainingMinutes > 30 ? 1.0 : 0.0);

                            // Calcular el nuevo valor de workedhours
                            double newWorkedHours = hours + roundedMinutes;
                            
                            System.out.println("newWorkedHours: " + newWorkedHours);

                            // Actualizar el valor en el request
                            request.set_ValueOfColumn("workedhours", newWorkedHours);

                            log.info("Worked hours actualizado a: " + newWorkedHours);
                        }
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
                            ? "Notificacion de creacion del Request #" + documentNo
                            : "Notificacion de cambios en el Request #" + documentNo;
                    StringBuilder body = new StringBuilder();

                    // Información del request
                    body.append("Estimado usuario,").append("\n\n");
                    String actionMessage = (request.getR_Request_ID() == 0)
                            ? "Se ha creado un nuevo Request #" + documentNo + "."
                            : "Se ha realizado una modificacion en el Request #" + documentNo + ".";
                    
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
