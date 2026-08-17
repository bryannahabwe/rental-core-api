package com.cognix.rentalcoreapi.modules.income.model;

/**
 * How a non-rent income payment was received. Broader than {@code PaymentMethod}
 * (rent is CASH-only today) because other income — deposit settlements,
 * move-out charges — often comes through mobile money or a bank transfer.
 */
public enum IncomeMethod {
    CASH,
    MOBILE_MONEY,
    BANK_TRANSFER,
    CHEQUE
}
