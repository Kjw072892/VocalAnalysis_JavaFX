package com.kass.vocalanalysistool.model;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sqlite.SQLiteDataSource;


public class UserFormantDatabase {

    /**
     * The data source object.
     */
    private SQLiteDataSource myDs;

    /**
     * The jdbc URL.
     */
    private static final String DB_URL = "jdbc:sqlite:Vocal_Analysis.db";

    /**
     * Logger used for debugging.
     */
    private static final Logger MY_LOGGER = Logger.getLogger("User Formant Database");

    /**
     * Constructor for the sql database.
     *
     * @param theDebugger Sets the debugger status flag.
     */
    public UserFormantDatabase(final boolean theDebugger) {
        setDebugger(theDebugger);
        initializeDatabase();
    }

    /**
     * Turns the debugger on or off.
     *
     * @param theDubberStatus The debugger status flag.
     */
    private void setDebugger(final boolean theDubberStatus) {
        if (!theDubberStatus) {
            MY_LOGGER.setLevel(Level.OFF);
        }
    }

    /**
     * Initializes the database connections.
     */
    private void initializeDatabase() {
        try {
            myDs = new SQLiteDataSource();
            myDs.setUrl(DB_URL);
            MY_LOGGER.info("Database connection established successfully\n");
        } catch (final Exception theException) {
            MY_LOGGER.severe(theException.getMessage() + "\n");
            throw new RuntimeException("Failed to initialize database: ", theException);
        }
    }

    /**
     * Deletes all records from the 'user_formants' table.
     */
    public final void clearDatabase() {
        final String deleteSQL = "DELETE FROM user_formants";
        try (final Connection conn = myDs.getConnection();
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(deleteSQL);
        } catch (final SQLException theEvent) {
            MY_LOGGER.log(Level.SEVERE, "Error clearing database: " + theEvent.getMessage() +
                    "\n");
        }
    }

    /**
     * Retrieves the formant data from the sqlite database
     *
     * @return Returns a matrix where the rows are the formants and the columns are the time
     */
    public final double[][] getFormants() {
        final double[][] results = new double[5][];
        String f0_str = "";
        String f1_str = "";
        String f2_str = "";
        String f3_str = "";
        String f4_str = "";
        final double[] f0;
        final double[] f1;
        final double[] f2;
        final double[] f3;
        final double[] f4;

        final String query = """ 
                SELECT f0_json, f1_json, f2_json, f3_json, f4_json
                FROM user_formants
                """;
        try (final Connection conn = myDs.getConnection();
             final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                f0_str = rs.getString("f0_json");
                f1_str = rs.getString("f1_json");
                f2_str = rs.getString("f2_json");
                f3_str = rs.getString("f3_json");
                f4_str = rs.getString("f4_json");
            }
            if (!f0_str.isEmpty() && !f1_str.isEmpty() && !f2_str.isEmpty()
                    && !f3_str.isEmpty() && !f4_str.isEmpty()) {
                f0 = new Gson().fromJson(f0_str, double[].class);
                f1 = new Gson().fromJson(f1_str, double[].class);
                f2 = new Gson().fromJson(f2_str, double[].class);
                f3 = new Gson().fromJson(f3_str, double[].class);
                f4 = new Gson().fromJson(f4_str, double[].class);
                results[0] = f0;
                results[1] = f1;
                results[2] = f2;
                results[3] = f3;
                results[4] = f4;
            } else {
                MY_LOGGER.severe("Formants are empty. Unable to retrieve them!");
                throw new RuntimeException("Formants are empty.");
            }
        } catch (final SQLException theException) {
            MY_LOGGER.severe("Unable to execute SQL query!");
            throw new RuntimeException(theException.getMessage());
        }
        return results;
    }

    /**
     * Gets the average formant data.
     *
     * @return Returns an array of average formans from f0-f4
     */
    public final double[] getAverage() {
        final double[] result;
        String f_Avg = "";
        final String query = """
                SELECT formant_avg_json
                FROM user_formants
                """;
        try (final Connection conn = myDs.getConnection();
             final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                f_Avg = rs.getString("formant_avg_json");
            }
            result = new Gson().fromJson(f_Avg, double[].class);
        } catch (final SQLException theEvent) {
            MY_LOGGER.severe("Unable to retrieve the average formant data: " + theEvent.getMessage());
            throw new RuntimeException("Unable to retrieve the average formant data: " + theEvent.getMessage());
        }
        return result;
    }

    /**
     * Gets the scatter plot image from the database.
     *
     * @return Returns a binary byte array of the image.
     */
    public final byte[] getScatterPlot() {

        final String query = """
                SELECT scatter_plot, timestamp, id
                FROM user_formants
                ORDER BY timestamp DESC, id DESC
                LIMIT 1
                """;
        try (Connection conn = myDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getBytes("scatter_plot");
            } else {
                throw new RuntimeException("There are no images present!");
            }


        } catch (final SQLException theEvent) {
            MY_LOGGER.severe("Unable to retrieve the image: " + theEvent.getMessage());
            throw new RuntimeException("Unable to get the image: " + theEvent.getMessage());
        }
    }

}
