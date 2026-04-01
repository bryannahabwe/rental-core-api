package com.cognix.rentalcoreapi.modules.units.service;

import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.units.dto.RentalUnitRequest;
import com.cognix.rentalcoreapi.modules.units.dto.RentalUnitResponse;
import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import com.cognix.rentalcoreapi.modules.units.repository.RentalUnitRepository;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RentalUnitService {

    private final RentalUnitRepository rentalUnitRepository;
    private final UserRepository userRepository;

    public PagedResponse<RentalUnitResponse> getAllUnits(Pageable pageable) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        Page<RentalUnitResponse> page = rentalUnitRepository
                .findAllByLandlordId(landlordId, pageable)
                .map(RentalUnitResponse::from);
        return PagedResponse.from(page);
    }

    public RentalUnitResponse getUnit(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        return rentalUnitRepository.findByIdAndLandlordId(id, landlordId)
                .map(RentalUnitResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Rental unit not found"));
    }

    public RentalUnitResponse createUnit(RentalUnitRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        if (rentalUnitRepository.existsByRoomNumberAndLandlordId(
                request.roomNumber(), landlordId)) {
            throw new IllegalArgumentException(
                    "Room number already exists: " + request.roomNumber());
        }

        var landlord = userRepository.getReferenceById(landlordId);

        RentalUnit unit = RentalUnit.builder()
                .landlord(landlord)
                .roomNumber(request.roomNumber())
                .description(request.description())
                .rentAmount(request.rentAmount())
                .isAvailable(request.isAvailable())
                .build();

        return RentalUnitResponse.from(rentalUnitRepository.save(unit));
    }

    public RentalUnitResponse updateUnit(UUID id, RentalUnitRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        RentalUnit unit = rentalUnitRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Rental unit not found"));

        unit.setRoomNumber(request.roomNumber());
        unit.setDescription(request.description());
        unit.setRentAmount(request.rentAmount());
        unit.setAvailable(request.isAvailable());

        return RentalUnitResponse.from(rentalUnitRepository.save(unit));
    }

    public void deleteUnit(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        RentalUnit unit = rentalUnitRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Rental unit not found"));

        rentalUnitRepository.delete(unit);
    }
}