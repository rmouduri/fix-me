package com.rmouduri.fixme;

public class Instrument {
    private final String instrumentName;
    private int quantity;
    private double price;

    public Instrument(final String instrumentNameParam, final int quantityParam, final double priceParam) {
        this.instrumentName = instrumentNameParam;
        this.quantity = quantityParam;
        this.price = priceParam;
    }

    public String getInstrumentName() { return this.instrumentName; }

    public int getQuantity() { return this.quantity; }

    public double getPrice() { return this.price; }

    public void setQuantity(final int quantityParam) { this.quantity = quantityParam; }

    public void setPrice(final double priceParam) { this.price = priceParam; }

    @Override
    public String toString() {
        return String.format("%5s:  qty: %6d | price: %10f $", this.getInstrumentName(), this.getQuantity(), this.getPrice());
    }
}
