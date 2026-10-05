package com.salt.field.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salt.field.domain.Equipment;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findByEquipStatus(String equipStatus);
}
