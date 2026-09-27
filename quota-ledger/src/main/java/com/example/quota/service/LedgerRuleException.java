package com.example.quota.service;

/** Business rule rejection (insufficient balance, wrong species/area/season, duplicate voucher, ...). */
public class LedgerRuleException extends RuntimeException {
    public LedgerRuleException(String message) {
        super(message);
    }
}
