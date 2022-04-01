package tech.coderonin.uploader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Pattern;

public class TemplateUploader {
	
    public static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]+");

	public static final String JDBC_URL = "jdbc.url";
	public static final String JDBC_USER = "jdbc.username";
	public static final String JDBC_PASSWORD = "jdbc.password";
	
    public static boolean verbose = System.getProperty("verbose")!=null;
    public static Properties jdbcProperties = new Properties();
    static {
    	try {
    		jdbcProperties.load(new FileInputStream(System.getenv("MCICO_PATH")+"/jdbc.properties"));
    	} catch( IOException ioEx ) {
    		System.err.println("Unable to load jdbc.properties from MCICO_PATH=" + System.getenv("MCICO_PATH") );
    	}
    }
    public static String url = System.getProperty( JDBC_URL, "jdbc:postgresql://localhost:5555/Mobile" );
    public static String username = System.getProperty( JDBC_USER, "postgres" );
    public static String password = System.getProperty( JDBC_PASSWORD, "ubi123" );
    
    public static String getURL() {
    	if( jdbcProperties != null ) {
    		String propertiesURL = jdbcProperties.getProperty(JDBC_URL);
    		if( propertiesURL != null && !"".equals(propertiesURL.trim()) ) {
    			return propertiesURL.trim();
    		}
    		
    	}
    	return url;
    }
    
    public static String getUserName() {
    	if( jdbcProperties != null ) {
    		String propertiesUserName = jdbcProperties.getProperty(JDBC_USER);
    		if(propertiesUserName != null && !"".equals(propertiesUserName.trim())) {
    			return propertiesUserName.trim();
    		}
    	}
    	return username;
    }
    
    public static String getPassword() {
    	if(jdbcProperties != null ) {
    		String propertiesPassword = jdbcProperties.getProperty(JDBC_PASSWORD);
    		if(propertiesPassword != null && !"".equals(propertiesPassword.trim())) {
    			return propertiesPassword.trim();
    		}
    	}
    	return password;
    }
    
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection( getURL(), getUserName(), getPassword() );
    }

    public static File getFile(String filename ){
        return new File(filename);
    }

    public static File createFile( String filename, boolean overrideExisting ){
        if( filename == null ) return null;

        File file = new File(filename);
        if( !overrideExisting && file.exists() ){
            String extension = filename.substring( filename.lastIndexOf(".") ).replaceAll( "\\.", "" );
            if( extension != null && extension.length() > 0 && NUMBER_PATTERN.matcher(extension).matches() ){
                Integer revision = Integer.valueOf(extension.trim());
                return createFile(filename.substring(0, filename.lastIndexOf(".") ) + "." + ++revision, overrideExisting );
            } else {
                return createFile(filename + "." + 1, overrideExisting);
            }
        }
        return file;
    }

    public static File writeToFile( File file, byte[] content ) throws IOException {
        if( file != null ){
            try( FileOutputStream fos = new FileOutputStream(file) ){
                fos.write(content);
            }
        }
        return file;
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

    public static File backupTemplate( String customerID, String type ) throws IOException, SQLException {
        System.out.println( "Backup existing survey..." );
        File backupFile = null;
        String query = new StringBuilder()
            .append( "SELECT \"FileName\", \"Content\" FROM \"DocumentStore\"" )
            .append( " WHERE " )
            .append( "\"CustomerID\" = ? ")
            .append( " AND " )
            .append( "\"ExternalType\" = ?")
        .toString();
        PreparedStatement prepared = connect().prepareStatement(query);
        prepared.setString(1, customerID.trim() );
        prepared.setString(2, type.trim() );
        ResultSet result = prepared.executeQuery();
        String filename = null;
        byte[] fileContent = null;
        while( result.next() ){
            filename = result.getString(1);
            fileContent = result.getBytes(2);
            break;
        }

        if( filename != null && fileContent != null ){
            backupFile = writeToFile( createFile( filename + ".backup" , false ), fileContent );
            if( backupFile != null ){
                System.out.println( "Existing template backed up to " + backupFile.getName() );
            }
        }
        return backupFile;
    }

    public static int updateTemplate( String customerID, String type, File template ) throws IOException, SQLException {
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
                "  WHERE \"ExternalType\" = ? " +
                "  AND \"CustomerID\" = ? " +
                ")";

            PreparedStatement prepared = connect().prepareStatement(query);
            prepared.setBytes( 1, fileContent );
            prepared.setString( 2, type.trim() );
            prepared.setString(3, customerID.trim() );
            updatedRows = prepared.executeUpdate();
        }
        return updatedRows;
    }

    public static void main( String[] args ) throws ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        System.out.println( "JDBC URL : " + getURL() );
        System.out.println( "JDBC UserName : " + getUserName() );
        if( args.length >= 3 ){
            String customerID = args[0];
            String type = args[1];
            String filepath = args[2];
            try {
                backupTemplate(customerID, type);
                System.out.println( "Updated " + updateTemplate( customerID, type, getFile(filepath) ) );
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
            System.out.println( "Usage : TemplateUploader <CustomerID> <TemplateType> <TemplateFilePath>" );
        }
    }
}
