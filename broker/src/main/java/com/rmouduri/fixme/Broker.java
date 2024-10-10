package com.rmouduri.fixme;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class Broker {
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String CYAN = "\u001B[36m";

    private final static short BROKER_PORT = 5000;
    private final static String LOCAL = "localhost";

    private String id;
    private Socket socket;
    private volatile boolean running;

    public Broker() {
        this.id = "";
        this.socket = null;
        this.running = false;
    }

    public Broker(final String idParam) {
        this.id = idParam;
        this.socket = null;
        this.running = false;
    }

    public void start() {
        try {
            this.socket = new Socket(LOCAL, BROKER_PORT);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

            out.write(String.format("%s\n", this.getId()));
            out.flush();

            final FixMessage identificationFixMessage = new FixMessage(in.readLine());
            if (!identificationFixMessage.getMsgType().equals("A")
                    || !identificationFixMessage.getSenderId().equals("Router")) {
                throw new BrokerException(String.format("Invalid identification message from Router: `%s'.",
                        identificationFixMessage.getMessage()));
            }

            this.setId(identificationFixMessage.getUserId());
            System.out.printf("New Broker Id: %s\n", this.getId());

            Scanner scanner = new Scanner(System.in);
            this.running = true;
            Thread listeningThread = new Thread(() -> {
                while (this.running) {
                    try {
                        final String message = in.readLine();

                        if (message != null) {
                            FixMessage fixMessage = new FixMessage(message);

                            System.out.printf("Received %s message from %s: `%s'\n",
                                    fixMessage.getMsgType().equals(FixMessage.REJECTED_MSG_TYPE) ?
                                            RED + "Rejected" + RESET: GREEN + "Executed" + RESET,
                                    fixMessage.getSenderId(), fixMessage.getText());
                        } else {
                            scanner.close();
                            break;
                        }

                    } catch (IOException | FixMessageException e) {
                        System.out.println(e.getMessage());
                        break;
                    }
                }
            });

            listeningThread.start();
            this.handleOrders(scanner);
            this.socket.close();
        } catch (IOException | FixMessageException | BrokerException e) {
            System.err.println("Error in broker: " + e.getMessage());
        }
    }

    /**
     * Infinite loop in which user can make orders with current Broker
     */
    private void handleOrders(final Scanner scanner) {
        String input;

        try {
            while (this.running) {
                System.out.printf(CYAN + "%s> " + RESET, this.getId());
                input = scanner.nextLine();

                String[] parsedInput = input.split(" ");

                if (parsedInput.length >= 1 && parsedInput[0].equalsIgnoreCase("exit")) {
                    this.running = false;
                    break;
                } else if (parsedInput.length < 5) {
                    System.err.println("Usage:\n  [buy/sell] MARKET_ID INSTRUMENT QUANTITY PRICE\nOr\n  exit");
                    continue;
                }

                String order = parsedInput[0];
                try {
                    switch (order.toLowerCase()) {
                        case "buy":
                            this.sendOrder(FixMessage.BUY_ORDER, parsedInput[1], parsedInput[2],
                                    Integer.parseInt(parsedInput[3]), Double.parseDouble(parsedInput[4]));
                            break;
                        case "sell":
                            this.sendOrder(FixMessage.SELL_ORDER, parsedInput[1], parsedInput[2],
                                    Integer.parseInt(parsedInput[3]), Double.parseDouble(parsedInput[4]));
                            break;
                        default:
                            System.err.println("Usage:\n  [buy/sell] MARKET_ID INSTRUMENT QUANTITY PRICE\nOr\n  exit");
                            break;
                    }
                } catch (IllegalArgumentException e) {
                    System.err.println("Illegal argument: " + e.getMessage());
                }

                Thread.sleep(100);
            }
        } catch (NoSuchElementException | IllegalStateException | InterruptedException e) {
            System.err.println("Error in Broker: " + e.getMessage());
        }

        this.running = false;
        scanner.close();
    }

    private void sendOrder(final int orderType, final String destId, final String instrument,
            final int quantity, final double price) {
        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
            FixMessage fixOrder = new FixMessage();

            fixOrder.setOrderType(orderType);
            fixOrder.setSenderId(this.getId());
            fixOrder.setDestId(destId);
            fixOrder.setInstrument(instrument);
            fixOrder.setQuantity(quantity);
            fixOrder.setPrice(price);

            final String message = fixOrder.buildMessage(new LinkedHashSet<>(Arrays.asList(FixMessage.SENDER_IDENTIFIER,
                    FixMessage.ORDER_TYPE_IDENTIFIER, FixMessage.DEST_IDENTIFIER, FixMessage.INSTRUMENT_IDENTIFIER,
                    FixMessage.QUANTITY_IDENTIFIER, FixMessage.PRICE_IDENTIFIER)));

            System.out.println("Order sent to Router: " + message);
            out.write(message + '\n');
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (FixMessageException e) {
            System.err.println("err");
            throw new RuntimeException(e);
        }
    }

    public String getId() { return this.id; }

    public void setId(final String idParam) { this.id = idParam; }


}
