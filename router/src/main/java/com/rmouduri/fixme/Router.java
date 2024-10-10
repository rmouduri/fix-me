package com.rmouduri.fixme;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Router {
    private final static short BROKER_PORT = 5000;
    private final static short MARKET_PORT = 5001;
    private static Router instance;

    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String RED = "\u001B[31m";
    public static final String CYAN = "\u001B[36m";

    private final AtomicInteger id = new AtomicInteger(1);
    private final ExecutorService executorService;
    private final HashMap<String, Socket> routingTable = new HashMap<>();
    private final HashMap<String, List<FixMessage>> savedUnsentMessage = new HashMap<>();

    private Router() {
        this.executorService = Executors.newCachedThreadPool();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down Router...");
            executorService.shutdown();
        }));
    }

    public void start() {
        this.executorService.submit(() -> this.startPortListening(BROKER_PORT));
        this.executorService.submit(() -> this.startPortListening(MARKET_PORT));
    }

    /**
     * Start listening to port @param port
     */
    private void startPortListening(final int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.printf("Router started listening to port %d\n", port);

            while (true) {
                Socket clientSocket = serverSocket.accept();

                executorService.submit(() -> handleClient(clientSocket, port));
            }
        } catch (IOException e) {
            System.err.printf("Error while starting router on port %d%n", port);
            this.executorService.shutdown();
        }
    }

    private void handleClient(final Socket socket, final int port) {
        try (socket) {
            String id;
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String request = in.readLine();
            System.out.printf("%sNew Connection request received from Unknown %s: `%s'%s\n",
                    GREEN, port == BROKER_PORT ? "Broker" : "Market", request, RESET);

            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            if (request == null || request.isEmpty()) {
                id = String.format("%s%06d", port == BROKER_PORT ? "Broker" : "Market",
                        this.getNextId());
            } else if (this.isValidId(request, port == BROKER_PORT ? "Broker" : "Market")
                    && (!this.routingTable.containsKey(request)
                    || this.routingTable.get(request).isClosed())) {
                id = request;
            } else {
                id = String.format("%s%06d", port == BROKER_PORT ? "Broker" : "Market",
                        this.getNextId());
                System.err.printf("%sInvalid id `%s'%s, assigned id will be: %s%s%s\n",
                        RED, request, RESET, GREEN, id, RESET);
            }

            FixMessage identificationFixMessage = new FixMessage();
            identificationFixMessage.setMsgType(FixMessage.LOGON_MSG_TYPE);
            identificationFixMessage.setSenderId("Router");
            identificationFixMessage.setDestId(id);
            identificationFixMessage.setUserId(id);
            identificationFixMessage.buildMessage(new LinkedHashSet<>(Arrays.asList(
                    FixMessage.SENDER_IDENTIFIER, FixMessage.DEST_IDENTIFIER,
                    FixMessage.USERID_IDENTIFIER, FixMessage.MSG_TYPE_IDENTIFIER)));

            this.routingTable.put(id, socket);

            System.out.println(CYAN + "Sending: " + RESET + identificationFixMessage.getMessage());
            out.write(identificationFixMessage.getMessage() + '\n');
            out.flush();

            if (id.equals(request)) {
                this.sendUnsentMessages(id);
                this.savedUnsentMessage.remove(id);
            }

            while ((request = in.readLine()) != null) {
                final FixMessage fixMessage = new FixMessage(request);
                /* Validating Checksum */
                if (!fixMessage.validateChecksum()) {
                    final FixMessage errorFixMessage = new FixMessage();

                    errorFixMessage.setSenderId("Router");
                    errorFixMessage.setDestId(fixMessage.getSenderId());
                    errorFixMessage.setMsgType(FixMessage.REJECTED_MSG_TYPE);
                    errorFixMessage.setText("Invalid checksum");
                    errorFixMessage.buildMessage(new LinkedHashSet<>(Arrays.asList(
                            FixMessage.SENDER_IDENTIFIER, FixMessage.DEST_IDENTIFIER,
                            FixMessage.MSG_TYPE_IDENTIFIER, FixMessage.TEXT_IDENTIFIER)));

                    System.out.println(RED + "Sending" + RESET + " Invalid Checksum.");
                    this.sendMessage(errorFixMessage);
                    /* Checking if destination is of different type from source */
                } else if ((fixMessage.getSenderId().contains("Broker")
                        && fixMessage.getDestId().contains("Broker"))
                        || (fixMessage.getSenderId().contains("Market")
                        && fixMessage.getDestId().contains("Market"))) {
                    final FixMessage errorFixMessage = new FixMessage();

                    errorFixMessage.setSenderId("Router");
                    errorFixMessage.setDestId(fixMessage.getSenderId());
                    errorFixMessage.setMsgType(FixMessage.REJECTED_MSG_TYPE);
                    errorFixMessage.setText(String.format("Invalid target: %s instead of %s",
                            fixMessage.getSenderId().contains("Broker") ? "Broker" : "Market",
                            !fixMessage.getSenderId().contains("Broker") ? "Broker" : "Market"));
                    errorFixMessage.buildMessage(new LinkedHashSet<>(Arrays.asList(
                            FixMessage.SENDER_IDENTIFIER, FixMessage.DEST_IDENTIFIER,
                            FixMessage.MSG_TYPE_IDENTIFIER, FixMessage.TEXT_IDENTIFIER)));

                    System.out.println(RED + "Sending" + RESET + " Invalid Target.");
                    this.sendMessage(errorFixMessage);
                    /* Checking the message has been sent successfully */
                } else if (this.sendMessage(fixMessage) == -1) {
                    final FixMessage errorFixMessage = new FixMessage();

                    errorFixMessage.setSenderId("Router");
                    errorFixMessage.setDestId(fixMessage.getSenderId());
                    errorFixMessage.setMsgType(FixMessage.REJECTED_MSG_TYPE);
                    errorFixMessage.setText("Unknown destination");
                    errorFixMessage.buildMessage(new LinkedHashSet<>(Arrays.asList(
                            FixMessage.SENDER_IDENTIFIER, FixMessage.DEST_IDENTIFIER,
                            FixMessage.MSG_TYPE_IDENTIFIER, FixMessage.TEXT_IDENTIFIER)));

                    System.out.println(RED + "Sending" + RESET + " Unknown Destination.");
                    if (this.isValidId(fixMessage.getDestId(), port == BROKER_PORT ? "Market" : "Broker")) {
                        System.out.println("Saving message in case of destination reconnects.");
                        this.saveUnsentMessage(fixMessage);
                    }
                    this.sendMessage(errorFixMessage);
                }
            }

            System.err.printf(RED + "%s disconnected."+ RESET + "\n", id);
        } catch (IOException e) {
            System.err.printf("Error while listening to %s: %s\n",
                    port == BROKER_PORT ? "Broker" : "Market", e.getMessage());
            System.err.println("Error: " + e.getMessage());
        } catch (FixMessageException e) {
            System.err.println("Error in FixMessage: " + e.getMessage());
        } catch (RouterException e) {
            System.err.println("Error in Router: " + e.getMessage());
        }
    }

    private int sendMessage(final FixMessage fixMessage) throws RouterException {
        final Socket socket = this.routingTable.get(fixMessage.getDestId());

        if (socket == null) {
            return -1;
        } else if (socket.isClosed()) {
//            this.routingTable.remove(fixMessage.getDestId());
            return -1;
        }

        try {
            BufferedWriter destOut = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            System.out.printf(CYAN + "Sending" + RESET + " message to %s: %s\n",
                    fixMessage.getDestId(), fixMessage.getMessage());
            destOut.write(fixMessage.getMessage() + '\n');
            destOut.flush();
        } catch (IOException e) {
            throw new RouterException(String.format("Error while sending message to `%s': %s",
                    fixMessage.getDestId(), e.getMessage()));
        }

        return 0;
    }

    private boolean isValidId(final String id, final String type) {
        if (id.length() != 12 || !id.startsWith(type)) {
            return false;
        }

        final String idNumber = id.substring(6);

        for (char c : idNumber.toCharArray()) {
            if (c < '0' || c > '9') {
                return false;
            }
        }

        for (final String key : this.routingTable.keySet()) {
            if (key.contains(idNumber) && !this.routingTable.get(key).isClosed()) {
                return false;
            }
        }

        return true;
    }

    private boolean isIdUsed(final int id) {
        String idNumber = String.format("%06d", id);

        for (final String key : this.routingTable.keySet()) {
            if (key.contains(idNumber)) {
                return true;
            }
        }

        return false;
    }

    private int getNextId() {
        int id;

        do {
            id = this.id.getAndIncrement();
        } while (isIdUsed(id));

        return id;
    }

    private void saveUnsentMessage(final FixMessage fixMessage) {
        if (!this.savedUnsentMessage.containsKey(fixMessage.getDestId())) {
            this.savedUnsentMessage.put(fixMessage.getDestId(), new ArrayList<>());
        }

        this.savedUnsentMessage.get(fixMessage.getDestId()).add(fixMessage);
    }

    private void sendUnsentMessages(final String id) throws RouterException {
        if (!this.savedUnsentMessage.containsKey(id)) {
            return;
        }

        System.out.println(CYAN + "Sending" + RESET + " saved messages to " + id);
        for (final FixMessage fixMessage : this.savedUnsentMessage.get(id)) {
            this.sendMessage(fixMessage);
        }
    }

    /**
     * Singleton for the Router class
     * @return The unique instance of Router
     */
    public static Router getInstance() {
        if (instance == null) {
            instance = new Router();
        }
        return instance;
    }
}
