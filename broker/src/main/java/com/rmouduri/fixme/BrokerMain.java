package com.rmouduri.fixme;

public class BrokerMain {
    public static void main(String[] args) {
        Broker broker;

        if (args.length >= 1) {
            broker = new Broker(args[0]);
        } else {
            broker = new Broker();
        }

        broker.start();
    }
}
