package com.rmouduri.fixme;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class Market {
    private final static short MARKET_PORT = 5001;
    private final static String LOCAL = "localhost";

    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String CYAN = "\u001B[36m";

    private String id;
    private Socket socket;
    private final HashMap<String, Instrument> instruments;
    private final Database database;
    private volatile boolean running;

    public Market() {
        this.id = "";
        this.socket = null;
        this.running = false;
        this.instruments = new HashMap<>();
        this.database = new Database();
        this.fillInstruments();
    }

    public Market(final String idParam) {
        this.id = idParam;
        this.socket = null;
        this.running = false;
        this.instruments = new HashMap<>();
        this.database = new Database();
        this.fillInstruments();
    }

    public void start() {
        try {
            this.socket = new Socket(LOCAL, MARKET_PORT);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

            out.write(String.format("%s\n", this.getId()));
            out.flush();

            final FixMessage identificationFixMessage = new FixMessage(in.readLine());
            if (!identificationFixMessage.getMsgType().equals("A")
                    || !identificationFixMessage.getSenderId().equals("Router")) {
                throw new MarketException(String.format("Invalid identification message from Router: `%s'.",
                        identificationFixMessage.getMessage()));
            }

            this.setId(identificationFixMessage.getUserId());
            System.out.printf("New Market Id: %s\n", this.getId());
            this.displayInstruments();

            this.running = true;
            Thread listeningThread = new Thread(() -> {
                while (this.running) {
                    try {
                        final String message = in.readLine();

                        if (message != null) {
                            FixMessage fixMessage = new FixMessage(message);

                            System.out.printf("Received %s order from %s of %d %s for %f$\n",
                                    fixMessage.getOrderType() == FixMessage.BUY_ORDER ? "Buy" : "Sell",
                                    fixMessage.getSenderId(), fixMessage.getQuantity(), fixMessage.getInstrument(),
                                    fixMessage.getPrice());

                            this.handleOrder(fixMessage);
                        } else {
                            break;
                        }

                    } catch (IOException | FixMessageException e) {
                        System.out.println(e.getMessage());
                        break;
                    }
                }
            });

            listeningThread.start();
        } catch (IOException | FixMessageException | MarketException e) {
            System.err.println("Error in market: " + e.getMessage());
        }
    }

    private void handleOrder(final FixMessage order) {
        if (!this.instruments.containsKey(order.getInstrument())) {
            this.rejectOrder(order, String.format("No such instrument `%s`", order.getInstrument()));
        } else if (order.getOrderType() == FixMessage.BUY_ORDER) {
            if (this.instruments.get(order.getInstrument()).getQuantity() < order.getQuantity()) {
                this.rejectOrder(order, String.format("Not enough instruments (%d in stock)",
                        this.instruments.get(order.getInstrument()).getQuantity()));
            } else if (this.instruments.get(order.getInstrument()).getPrice() > order.getPrice()) {
                this.rejectOrder(order, String.format("Price too low (%f$)",
                        this.instruments.get(order.getInstrument()).getPrice()));
            } else {
                final Instrument instrument = this.instruments.get(order.getInstrument());

                instrument.setQuantity(instrument.getQuantity() - order.getQuantity());
                this.acceptOrder(order);
            }
        } else if (order.getOrderType() == FixMessage.SELL_ORDER) {
            if (order.getPrice() > this.instruments.get(order.getInstrument()).getPrice()) {
                this.rejectOrder(order, String.format("Price too high (%f$)",
                        this.instruments.get(order.getInstrument()).getPrice()));
            } else {
                final Instrument instrument = this.instruments.get(order.getInstrument());

                instrument.setQuantity(instrument.getQuantity() + order.getQuantity());
                this.acceptOrder(order);
            }
        }
    }

    private void acceptOrder(final FixMessage order) {
        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
            FixMessage executedOrder = new FixMessage();

            executedOrder.setSenderId(this.getId());
            executedOrder.setDestId(order.getSenderId());
            executedOrder.setMsgType(FixMessage.EXECUTED_MSG_TYPE);
            executedOrder.setText(String.format("%s order of %d %s for %f$ executed",
                    order.getOrderType() == FixMessage.BUY_ORDER ? "Buy" : "Sell",
                    order.getQuantity(), order.getInstrument(), order.getPrice()));
            executedOrder.buildMessage(new LinkedHashSet<>(Arrays.asList(FixMessage.SENDER_IDENTIFIER,
                    FixMessage.DEST_IDENTIFIER, FixMessage.MSG_TYPE_IDENTIFIER, FixMessage.TEXT_IDENTIFIER)));

            System.out.printf("Sending %sExecuted%s order to %s: %s\n", GREEN, RESET, executedOrder.getDestId(),
                    executedOrder.getText());
            out.write(executedOrder.getMessage() + '\n');
            out.flush();
            this.database.insertTransaction(order);
        } catch (IOException | FixMessageException e) {
            System.err.println("Error while sending execute order: " + e.getMessage());
        }

        this.displayInstruments();
    }

    private void rejectOrder(final FixMessage order, final String reason) {
        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
            FixMessage rejectedOrder = new FixMessage();

            rejectedOrder.setSenderId(this.getId());
            rejectedOrder.setDestId(order.getSenderId());
            rejectedOrder.setMsgType(FixMessage.REJECTED_MSG_TYPE);
            rejectedOrder.setText(reason);
            rejectedOrder.buildMessage(new LinkedHashSet<>(Arrays.asList(FixMessage.SENDER_IDENTIFIER,
                    FixMessage.DEST_IDENTIFIER, FixMessage.MSG_TYPE_IDENTIFIER, FixMessage.TEXT_IDENTIFIER)));

            System.out.printf("Sending %sRejected%s order to %s: %s\n", RED, RESET, rejectedOrder.getDestId(),
                    rejectedOrder.getText());
            out.write(rejectedOrder.getMessage() + '\n');
            out.flush();
        } catch (IOException | FixMessageException e) {
            System.err.println("Error while sending reject order: " + e.getMessage());
        }
    }

    private void fillInstruments() {
        final List<String> instrumentNames = Arrays.asList("AAPL", "MSFT", "AMZN", "GOOGL", "TSLA", "META",
                "BRK.B", "NVDA", "JNJ", "V");
        Random random = new Random();

        for (final String instrument : instrumentNames) {
            this.instruments.put(instrument, new Instrument(instrument, random.nextInt(30, 5000),
                    random.nextDouble(20.0, 1000.0)));
        }
    }

    private void displayInstruments() {
        System.out.println(CYAN + this.getId() + RESET + ":");
        for (Map.Entry<String, Instrument> instrumentEntry : this.instruments.entrySet()) {
            System.out.println(instrumentEntry.getValue());
        }
    }

    public String getId() { return this.id; }

    public void setId(final String idParam) { this.id = idParam; }
}
