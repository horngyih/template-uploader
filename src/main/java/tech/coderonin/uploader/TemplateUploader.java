package tech.coderonin.uploader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TemplateUploader {
    public static boolean verbose = System.getProperty("verbose")!=null;
    public static String url = "jdbc:postgresql://localhost:5555/Mobile";
    public static String username = "postgres";
    public static String password = "ubi123";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection( url, username, password );
    }

    public static File getFile(String filename ){
        return new File(filename);
    }

    public static byte[] getFileContent( File file ) throws IOException {
        if(file != null ){
            FileInputStream fsr = null;
            ByteArrayOutputStream bsr = null;
            try {
                fsr = new FileInputStream(file);
                bsr = new ByteArrayOutputStream();
                while (fsr.available() > 0) {
                    bsr.write(fsr.read());
                }
                fsr.close();
                bsr.flush();
                return bsr.toByteArray();
            } finally {
                if(fsr!=null)fsr.close();
                if(bsr!=null)bsr.close();
            }
        }
        return null;
    }

    public static int updateTemplate( String customerID, File template ) throws IOException, SQLException {
        int updatedRows = 0;
        byte[] fileContent = getFileContent(template);
        if( customerID != null && !"".equals(customerID.trim()) ){
            String query =
                "UPDATE \"DocumentStore\"" +
                "SET \"Content\" = ?, " +
                "\"UpdatedBy\" = 'UPLOADER', " +
                "\"UpdatedOn\" = NOW() " +
                "WHERE \"DocumentStoreID\" = " +
                "(" +
                "  SELECT \"DocumentStoreID\" FROM \"DocumentStore\" ds " +
                "  WHERE \"ExternalType\" = 'Survey' " +
                "  AND \"CustomerID\" = ? " +
                ")";

            PreparedStatement prepared = connect().prepareStatement(query);
            prepared.setBytes( 1, fileContent );
            prepared.setString(2, customerID.trim() );
            updatedRows = prepared.executeUpdate();
        }
        return updatedRows;
    }

    public static void main( String[] args ) throws ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        if( args.length >= 2 ){
            try {
                System.out.println( "Updated " + updateTemplate( args[0], getFile(args[1]) ) );
            } catch (IOException e) {
                if(verbose) {
                    e.printStackTrace();
                }
                System.out.println(e.getMessage());
            } catch (SQLException e) {
                if(verbose) {
                    e.printStackTrace();
                }
                System.out.println(e.getMessage());
            }
        } else {
            System.out.println( "Usage : TemplateUploader <CustomerID> <TemplateFilePath>" );
        }
    }
}
