package com.rmouduri.fixme;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/fixme";
    private static final String USER = "user";
    private static final String PASSWORD = "password";

    /**
     * SQL script to create transaction's table
     */
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS transaction (
            id INT PRIMARY KEY AUTO_INCREMENT,
            order_type INT,
            sender TEXT NOT NULL,
            destination TEXT NOT NULL,
            instrument TEXT NOT NULL,
            quantity INT,
            price DECIMAL(20, 6),
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        """;

    /**
     * SQL Script to insert a transaction in transaction table
     */
    private static final String INSERT_TRANSACTION_SQL = """
            INSERT INTO transaction (order_type, sender, destination, instrument, quantity, price)
            VALUES (%d, '%s', '%s', '%s', %d, %f);
            """;

    private Connection connection;

    public Database() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
            System.out.println("Connection to the database established successfully.");

            if (connection != null) {
                try (Statement stmt = this.getConnection().createStatement()) {
                    stmt.execute(String.format(Database.CREATE_TABLE_SQL));
                } catch (SQLException e) {
                    System.out.println("Database error: " + e.getMessage());
                }
            }
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL Driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("Connection to the database closed.");
            } catch (SQLException e) {
                System.err.println("Failed to close connection: " + e.getMessage());
            }
        }
    }

    public void insertTransaction(final FixMessage fixMessage) {
        if (connection != null) {
            try (Statement stmt = this.getConnection().createStatement()) {

                stmt.execute(
                        String.format(Database.INSERT_TRANSACTION_SQL,
                                fixMessage.getOrderType(), fixMessage.getSenderId(), fixMessage.getDestId(),
                                fixMessage.getInstrument(), fixMessage.getQuantity(), fixMessage.getPrice()));
                System.out.println("Transaction stored in database");
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
            }
        } else {
            System.err.println("Can't store transaction to database.");
        }
    }
}