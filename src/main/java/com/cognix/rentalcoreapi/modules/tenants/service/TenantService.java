package com.cognix.rentalcoreapi.modules.tenants.service;

import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.tenants.dto.TenantRequest;
import com.cognix.rentalcoreapi.modules.tenants.dto.TenantResponse;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.modules.tenants.repository.TenantRepository;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public PagedResponse<TenantResponse> getAllTenants(Pageable pageable, String search) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        return PagedResponse.from(
                tenantRepository.findAllByLandlordIdWithSearch(landlordId, search, pageable)
                        .map(TenantResponse::from)
        );
    }

    public TenantResponse getTenant(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        return tenantRepository.findByIdAndLandlordId(id, landlordId)
                .map(TenantResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }

    public TenantResponse createTenant(TenantRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        if (tenantRepository.existsByPhoneAndLandlordId(request.phone(), landlordId)) {
            throw new IllegalArgumentException(
                    "Tenant with phone number already exists: " + request.phone());
        }

        if (request.email() != null &&
                tenantRepository.existsByEmailAndLandlordId(request.email(), landlordId)) {
            throw new IllegalArgumentException(
                    "Tenant with email already exists: " + request.email());
        }

        Tenant tenant = Tenant.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .name(request.name())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .build();

        return TenantResponse.from(tenantRepository.save(tenant));
    }

    public TenantResponse updateTenant(UUID id, TenantRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Tenant tenant = tenantRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        if (!tenant.getPhone().equals(request.phone()) &&
                tenantRepository.existsByPhoneAndLandlordId(request.phone(), landlordId)) {
            throw new IllegalArgumentException(
                    "Tenant with phone number already exists: " + request.phone());
        }

        if (request.email() != null &&
                !request.email().equals(tenant.getEmail()) &&
                tenantRepository.existsByEmailAndLandlordId(request.email(), landlordId)) {
            throw new IllegalArgumentException(
                    "Tenant with email already exists: " + request.email());
        }

        tenant.setName(request.name());
        tenant.setPhone(request.phone());
        tenant.setEmail(request.email());
        tenant.setAddress(request.address());

        return TenantResponse.from(tenantRepository.save(tenant));
    }

    public void deleteTenant(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Tenant tenant = tenantRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        tenantRepository.delete(tenant);
    }
}