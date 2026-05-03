package com.cognix.rentalcoreapi.modules.settings.dto;

import com.cognix.rentalcoreapi.modules.settings.model.LandlordSettings;

import java.util.UUID;

public record LandlordSettingsResponse(
        UUID id,
        String companyName,
        String address,
        String logoUrl,
        String receiptPrefix,
        Integer nextReceiptNo,
        String receiptNumbering,
        String receiptFooter,
        String receiptStyle
) {
    public static LandlordSettingsResponse from(LandlordSettings s) {
        return new LandlordSettingsResponse(
                s.getId(),
                s.getCompanyName(),
                s.getAddress(),
                s.getLogoUrl(),
                s.getReceiptPrefix(),
                s.getNextReceiptNo(),
                s.getReceiptNumbering(),
                s.getReceiptFooter(),
                s.getReceiptStyle()
        );
    }
}