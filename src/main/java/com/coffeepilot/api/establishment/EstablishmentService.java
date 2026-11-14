package com.coffeepilot.api.establishment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class EstablishmentService {

    private final EstablishmentRepository establishmentRepository;

    public EstablishmentService(EstablishmentRepository establishmentRepository) {
        this.establishmentRepository = establishmentRepository;
    }

    @Transactional
    public Establishment create(String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("O nome do estabelecimento é obrigatório.");
        }
        return establishmentRepository.save(new Establishment(normalizedName));
    }

    @Transactional(readOnly = true)
    public Optional<Establishment> findById(Long id) {
        return establishmentRepository.findById(id);
    }
}
