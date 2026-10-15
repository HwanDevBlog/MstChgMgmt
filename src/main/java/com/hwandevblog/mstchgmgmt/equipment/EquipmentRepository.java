package com.hwandevblog.mstchgmgmt.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, String> {
    List<Equipment> findAllByOrderByCodeAsc();
}
