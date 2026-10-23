package com.coffeepilot.api.coffeeshop;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CoffeeShopService {

    private final CoffeeShopRepository coffeeShopRepository;

    public CoffeeShopService(CoffeeShopRepository coffeeShopRepository) {
        this.coffeeShopRepository = coffeeShopRepository;
    }

    @Transactional
    public CoffeeShop create(String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("O nome da cafeteria é obrigatório.");
        }
        return coffeeShopRepository.save(new CoffeeShop(normalizedName));
    }

    @Transactional(readOnly = true)
    public Optional<CoffeeShop> findById(Long id) {
        return coffeeShopRepository.findById(id);
    }
}
