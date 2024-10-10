package com.rmouduri.fixme;

public class MarketMain {
    public static void main(String[] args) {
        Market market;

        if (args.length >= 1) {
            market = new Market(args[0]);
        } else {
            market = new Market();
        }

        market.start();
    }
}
