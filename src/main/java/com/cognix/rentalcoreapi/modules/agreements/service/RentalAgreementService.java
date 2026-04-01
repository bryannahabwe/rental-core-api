package com.cognix.rentalcoreapi.modules.agreements.service;

import com.cognix.rentalcoreapi.modules.agreements.dto.MoveOutRequest;
import com.cognix.rentalcoreapi.modules.agreements.dto.RentalAgreementRequest;
import com.cognix.rentalcoreapi.modules.agreements.dto.RentalAgreementResponse;
import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.agreements.repository.RentalAgreementRepository;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.tenants.repository.TenantRepository;
import com.cognix.rentalcoreapi.modules.units.repository.RentalUnitRepository;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RentalAgreementService {

    private final RentalAgreementRepository agreementRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RentalUnitRepository unitRepository;

    public PagedResponse<RentalAgreementResponse> getAllAgreements(
            Pageable pageable, AgreementStatus status) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Page<RentalAgreement> page;

        if (status != null) {
            page = agreementRepository.findAllByLandlordIdAndStatus(landlordId, status, pageable);
        } else {
            page = agreementRepository.findAllByLandlordId(landlordId, pageable);
        }

        return PagedResponse.from(page.map(RentalAgreementResponse::from));
    }

    public RentalAgreementResponse getAgreement(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        return agreementRepository.findByIdAndLandlordId(id, landlordId)
                .map(RentalAgreementResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Agreement not found"));
    }

    @Transactional
    public RentalAgreementResponse createAgreement(RentalAgreementRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        // ensure unit belongs to this landlord
        var unit = unitRepository.findByIdAndLandlordId(request.unitId(), landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Unit not found"));

        // ensure tenant belongs to this landlord
        var tenant = tenantRepository.findByIdAndLandlordId(request.tenantId(), landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        // ensure unit is not already occupied
        if (agreementRepository.existsByUnitIdAndStatus(request.unitId(), AgreementStatus.ACTIVE)) {
            throw new IllegalArgumentException(
                    "Unit " + unit.getRoomNumber() + " already has an active agreement");
        }

        RentalAgreement agreement = RentalAgreement.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .tenant(tenant)
                .unit(unit)
                .startDate(request.startDate())
                .rentAmount(request.rentAmount())
                .depositAmount(request.depositAmount())
                .status(AgreementStatus.ACTIVE)
                .build();

        // mark unit as occupied
        unit.setAvailable(false);
        unitRepository.save(unit);

        return RentalAgreementResponse.from(agreementRepository.save(agreement));
    }

    @Transactional
    public RentalAgreementResponse recordMoveOut(UUID id, MoveOutRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        RentalAgreement agreement = agreementRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Agreement not found"));

        if (agreement.getStatus() == AgreementStatus.TERMINATED) {
            throw new IllegalArgumentException("Agreement is already terminated");
        }

        if (request.moveOutDate().isBefore(agreement.getStartDate())) {
            throw new IllegalArgumentException(
                    "Move out date cannot be before the agreement start date");
        }

        agreement.setMoveOutDate(request.moveOutDate());
        agreement.setStatus(AgreementStatus.TERMINATED);

        // mark unit as available again
        var unit = agreement.getUnit();
        unit.setAvailable(true);
        unitRepository.save(unit);

        return RentalAgreementResponse.from(agreementRepository.save(agreement));
    }
}