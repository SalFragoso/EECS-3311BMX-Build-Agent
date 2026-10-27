package com.salvador.bmxbuilder.domain;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@Embeddable
public class Money {

    protected Money(){}

    public Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    private BigDecimal amount;
    private String currency;

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Money add(Money other){
        if (!currency.equals(other.currency)){
            throw new IllegalArgumentException("Can't add " + other.currency + " to " + currency);
        }

        return new Money(amount.add(other.amount), currency);
    }
}
