package org.sannet.activator;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.compiere.util.DB;
import java.io.InputStream;
import java.util.Scanner;
import java.sql.PreparedStatement;
import java.sql.ResultSet;


public class MyActivator implements BundleActivator {
    @Override
    public void start(BundleContext context) throws Exception {
        // Activación del Request Validator
        System.out.println("Request Validator activated.");

        // Verificar si se cumple la condición
        if (!checkCondition()) {
            // Ejecutar sentencias SQL estándar
            String sqlPath = "/META-INF/sql/install.sql";
            executeSQLFile(sqlPath);

            // Ejecutar comandos psql (DO $$ ... END $$;)
            String psqlPath = "/META-INF/sql/install_psql.sql";
            executePSQLFile(psqlPath);
        } else {
            System.out.println("Condition not met. SQL scripts will not be executed.");
        }
    }

    private boolean checkCondition() {
        String conditionQuery = 
            "SELECT 1 FROM ad_sysconfig WHERE ad_client_id = 0 AND ad_org_id = 0 AND \"name\" IN ('Ticket_request_type_ID', 'Ticket_support_user_ID')";
        try (PreparedStatement pstmt = DB.prepareStatement(conditionQuery, null);
            ResultSet rs = pstmt.executeQuery()) {
            return rs.next(); // Si hay resultados, la condición se cumple
        } catch (Exception e) {
            System.err.println("Error checking condition: " + e.getMessage());
            e.printStackTrace();
            return false; // Si ocurre un error, no ejecutamos los scripts
        }
    }

    // Método para ejecutar sentencias SQL estándar
    private void executeSQLFile(String path) {
        InputStream is = getClass().getResourceAsStream(path);
        if (is != null) {
            Scanner scanner = new Scanner(is).useDelimiter(";");
            while (scanner.hasNext()) {
                String sql = scanner.next().trim();
                if (!sql.isEmpty()) {
                    try {
                        DB.executeUpdate(sql, null);
                    } catch (Exception e) {
                        if (!"SELECT update_sequences();".equals(sql)) {
	                        System.out.println("Error executing SQL: " + sql);
	                        e.printStackTrace();
                    	}
                    }
                }
            }
            scanner.close();
        } else {
            System.err.println("File not found: " + path);
        }
    }

    private void executePSQLFile(String path) {
        InputStream is = getClass().getResourceAsStream(path);
        if (is != null) {
            Scanner scanner = new Scanner(is).useDelimiter("END \\$\\$;");
            while (scanner.hasNext()) {
                String psqlBlock = scanner.next().trim();
                if (!psqlBlock.isEmpty()) {
                    psqlBlock = psqlBlock + " END $$;"; // Volver a agregar el delimitador que se eliminó en el split
                    try {
                        DB.executeUpdateEx(psqlBlock, null);
                    } catch (Exception e) {
                        System.err.println("Error executing PSQL block: " + psqlBlock);
                        e.printStackTrace();
                    }
                }
            }
            scanner.close();
        } else {
            System.err.println("File not found: " + path);
        }
    }
    
    @Override
    public void stop(BundleContext context) throws Exception {
        // Aquí puedes agregar código para limpiar, si es necesario, cuando el plugin se detiene
    }
}
