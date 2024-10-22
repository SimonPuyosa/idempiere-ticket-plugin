package org.sannet.activator;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.compiere.util.DB;
import java.io.InputStream;
import java.util.Scanner;

public class MyActivator implements BundleActivator {
    @Override
    public void start(BundleContext context) throws Exception {
        // Activación del Request Validator
        System.out.println("Request Validator activated.");

        // Ejecutar sentencias SQL estándar
        String sqlPath = "/META-INF/sql/install.sql";
        executeSQLFile(sqlPath);

        // Ejecutar comandos psql (DO $$ ... END $$;)
        String psqlPath = "/META-INF/sql/install_psql.sql";
        executePSQLFile(psqlPath);
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
