package com.cognix.rentalcoreapi.modules.settings.dto;

public record LandlordSettingsRequest(
        String companyName,
        String address,
        String receiptPrefix,
        Integer nextReceiptNo,
        String receiptNumbering,
        String receiptFooter,
        String receiptStyle
) {
}