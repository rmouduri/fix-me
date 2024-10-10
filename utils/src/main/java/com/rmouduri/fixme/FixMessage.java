package com.rmouduri.fixme;

import java.util.*;

public class FixMessage {
    private static final char DELIMITER = '|';
    private static final String EQUAL = "=";

    public static final int FIX_IDENTIFIER = 8;
    public static final int SENDER_IDENTIFIER = 49;
    public static final int DEST_IDENTIFIER = 56;
    public static final int DATETIME_IDENTIFIER = 52;
    public static final int INSTRUMENT_IDENTIFIER = 55;
    public static final int QUANTITY_IDENTIFIER = 38;
    public static final int PRICE_IDENTIFIER = 44;
    public static final int CHECKSUM_IDENTIFIER = 10;
    public static final int MSG_TYPE_IDENTIFIER = 35;
    public static final int USERID_IDENTIFIER = 553;
    public static final int ORDER_TYPE_IDENTIFIER = 54;
    public static final int TEXT_IDENTIFIER = 58;

    public static final String LOGON_MSG_TYPE = "A";
    public static final String LOGOUT_MSG_TYPE = "5";
    public static final String REJECTED_MSG_TYPE = "3";
    public static final String EXECUTED_MSG_TYPE = "8";

    public static final int BUY_ORDER = 1;
    public static final int SELL_ORDER = 2;

    private String fixVersion;
    private String message;
    private String senderId;
    private String destId;
    private String instrument;
    private String msgType;
    private String userId;
    private String text;

    private int quantity;
    private int orderType;
    private int checksum;

    private double price;

    /**
     * A Class with every component of a Fix Message
     * @param messageParam
     * @throws FixMessageException
     */
    public FixMessage(final String messageParam) throws FixMessageException {
        this.message = messageParam;

        final Set<Integer> identifiers = new LinkedHashSet<>();
        for (final String s : this.message.split("\\" + DELIMITER)) {
            identifiers.add(Integer.parseInt(s.split(EQUAL)[0]));
        }

        this.extract(identifiers);
    }

    /**
     * A Class with every component of a Fix Message
     */
    public FixMessage() {
        this.message = null;
        this.setFixIdentifier("FIX.4.2");
    }

    private void extract(final Set<Integer> identifiers) throws FixMessageException {
        for (int id : identifiers) {
            switch (id) {
                case FIX_IDENTIFIER:
                    this.setFixIdentifier(this.extractString(FIX_IDENTIFIER));
                    break;
                case SENDER_IDENTIFIER:
                    this.setSenderId(this.extractString(SENDER_IDENTIFIER));
                    break;
                case DEST_IDENTIFIER:
                    this.setDestId(this.extractString(DEST_IDENTIFIER));
                    break;
                case QUANTITY_IDENTIFIER:
                    this.setQuantity(this.extractInteger(QUANTITY_IDENTIFIER));
                    break;
                case CHECKSUM_IDENTIFIER:
                    this.setChecksum(this.extractInteger(CHECKSUM_IDENTIFIER));
                    break;
                case USERID_IDENTIFIER:
                    this.setUserId(this.extractString(USERID_IDENTIFIER));
                    break;
                case ORDER_TYPE_IDENTIFIER:
                    this.setOrderType(this.extractInteger(ORDER_TYPE_IDENTIFIER));
                    break;
                case INSTRUMENT_IDENTIFIER:
                    this.setInstrument(this.extractString(INSTRUMENT_IDENTIFIER));
                    break;
                case MSG_TYPE_IDENTIFIER:
                    this.setMsgType(this.extractString(MSG_TYPE_IDENTIFIER));
                    break;
                case PRICE_IDENTIFIER:
                    this.setPrice(this.extractDouble(PRICE_IDENTIFIER));
                    break;
                case TEXT_IDENTIFIER:
                    this.setText(this.extractString(TEXT_IDENTIFIER));
                    break;
                default:
                    throw new FixMessageException(String.format("Unknown identifier `%d'.", id));
            }
        }
    }

    private int extractInteger(final int identifier) throws FixMessageException {
        final String[] splitMessage = this.message.split("\\" + DELIMITER);
        int ret;

        for (String s : splitMessage) {
            if (Integer.parseInt(s.split(EQUAL)[0]) == identifier) {
                ret = Integer.parseInt(s.split(EQUAL)[1]);

                return ret;
            }
        }

        throw new FixMessageException(String.format("Integer of identifier `%d' not found.", identifier));
    }

    private double extractDouble(final int identifier) throws FixMessageException {
        final String[] splitMessage = this.message.split("\\" + DELIMITER);
        double ret;

        for (String s : splitMessage) {
            if (Integer.parseInt(s.split(EQUAL)[0]) == identifier) {
                ret = Double.parseDouble(s.split(EQUAL)[1]);

                return ret;
            }
        }

        throw new FixMessageException(String.format("Double of identifier `%d' not found.", identifier));
    }

    private String extractString(final int identifier) throws FixMessageException {
        final String[] splitMessage = this.message.split("\\" + DELIMITER);
        String ret;

        for (String s : splitMessage) {
            if (Integer.parseInt(s.split(EQUAL)[0]) == identifier) {
                ret = s.split(EQUAL)[1];

                return ret;
            }
        }

        throw new FixMessageException(String.format("String of identifier `%d' not found.", identifier));
    }

    /**
     * Build the message with the identifiers given in @param identifiers
     * @param identifiers
     */
    public String buildMessage(final LinkedHashSet<Integer> identifiers) throws FixMessageException {
        final StringBuilder message = new StringBuilder();

        identifiers.remove(FIX_IDENTIFIER);
        identifiers.remove(CHECKSUM_IDENTIFIER);

        message.append(String.format("%d=%s%c", FIX_IDENTIFIER, this.fixVersion, DELIMITER));

        for (int id : identifiers) {
            switch (id) {
                case SENDER_IDENTIFIER:
                    message.append(String.format("%d=%s%c", SENDER_IDENTIFIER, this.getSenderId(), DELIMITER));
                    break;
                case DEST_IDENTIFIER:
                    message.append(String.format("%d=%s%c", DEST_IDENTIFIER, this.getDestId(), DELIMITER));
                    break;
                case USERID_IDENTIFIER:
                    message.append(String.format("%d=%s%c", USERID_IDENTIFIER, this.getUserId(), DELIMITER));
                    break;
                case INSTRUMENT_IDENTIFIER:
                    message.append(String.format("%d=%s%c", INSTRUMENT_IDENTIFIER, this.getInstrument(), DELIMITER));
                    break;
                case MSG_TYPE_IDENTIFIER:
                    message.append(String.format("%d=%s%c", MSG_TYPE_IDENTIFIER, this.getMsgType(), DELIMITER));
                    break;
                case TEXT_IDENTIFIER:
                    message.append(String.format("%d=%s%c", TEXT_IDENTIFIER, this.getText(), DELIMITER));
                    break;
                case QUANTITY_IDENTIFIER:
                    message.append(String.format("%d=%d%c", QUANTITY_IDENTIFIER, this.getQuantity(), DELIMITER));
                    break;
                case ORDER_TYPE_IDENTIFIER:
                    message.append(String.format("%d=%d%c", ORDER_TYPE_IDENTIFIER, this.getOrderType(), DELIMITER));
                    break;
                case PRICE_IDENTIFIER:
                    message.append(String.format("%d=%f%c", PRICE_IDENTIFIER, this.getPrice(), DELIMITER));
                    break;
                default:
                    throw new FixMessageException(String.format("Unknown identifier `%d' when building message.", id));
            }
        }

        int checksum = 0;
        for (char c : message.toString().toCharArray()) {
            checksum += c;
        }

        checksum %= 256;
        this.setChecksum(checksum);
        message.append(String.format("%d=%d", CHECKSUM_IDENTIFIER, this.getChecksum()));

        this.setMessage(message.toString());
        return this.getMessage();
    }

    public boolean validateChecksum() {
        int checksum = 0;
        final String message = this.message.split("10=")[0];

        for (char c : message.toCharArray()) {
            checksum += c;
        }

        checksum %= 256;

        return this.checksum == checksum;
    }

    // Getters
    public String getMessage() { return this.message; }

    public String getSenderId() { return this.senderId; }

    public String getDestId() { return this.destId; }

    public String getInstrument() { return this.instrument; }

    public int getQuantity() { return this.quantity; }

    public double getPrice() { return this.price; }

    public int getChecksum() { return this.checksum; }

    public String getMsgType() { return this.msgType; }

    public String getUserId() { return this.userId; }

    // Setters
    private void setMessage(final String messageParam) { this.message = messageParam; }

    public void setSenderId(final String senderIdParam) { this.senderId = senderIdParam; }

    public void setDestId(final String destIdParam) { this.destId = destIdParam; }

    public void setInstrument(final String instrumentParam) { this.instrument = instrumentParam; }

    public void setQuantity(final int quantityParam) { this.quantity = quantityParam; }

    public void setPrice(final double priceParam) { this.price = priceParam; }

    private void setChecksum(final int checksumParam) { this.checksum = checksumParam; }

    public void setMsgType(final String msgTypeParam) { this.msgType = msgTypeParam; }

    public void setUserId(final String userIdParam) { this.userId = userIdParam; }


    public int getOrderType() {
        return orderType;
    }

    public void setOrderType(int orderType) {
        this.orderType = orderType;
    }

    public String getFixIdentifier() {
        return fixVersion;
    }

    public void setFixIdentifier(String fixVersion) {
        this.fixVersion = fixVersion;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}