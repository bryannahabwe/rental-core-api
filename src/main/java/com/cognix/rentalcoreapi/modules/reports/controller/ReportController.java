package com.cognix.rentalcoreapi.modules.reports.controller;

import com.cognix.rentalcoreapi.modules.reports.dto.OccupancyReportResponse;
import com.cognix.rentalcoreapi.modules.reports.dto.PaymentReportResponse;
import com.cognix.rentalcoreapi.modules.reports.dto.SummaryResponse;
import com.cognix.rentalcoreapi.modules.reports.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> getSummary() {
        return ResponseEntity.ok(reportService.getSummary());
    }

    @GetMapping("/payments")
    public ResponseEntity<PaymentReportResponse> getPaymentReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getPaymentReport(from, to));
    }

    @GetMapping("/occupancy")
    public ResponseEntity<OccupancyReportResponse> getOccupancyReport() {
        return ResponseEntity.ok(reportService.getOccupancyReport());
    }
}