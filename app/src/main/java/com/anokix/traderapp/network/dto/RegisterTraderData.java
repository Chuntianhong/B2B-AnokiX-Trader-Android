package com.anokix.traderapp.network.dto;

/**
 * data block of POST /api/trader/register. Only the created trader's identity
 * is modelled – enough to confirm a successful public registration.
 */
public class RegisterTraderData {
    public Trader trader;
    public User user;

    public static class Trader {
        public String id;
        public String business_name;
        public String trading_name;
        public String registration_status;
    }

    public static class User {
        public String id;
        public String first_name;
        public String last_name;
        public String email;
    }
}
