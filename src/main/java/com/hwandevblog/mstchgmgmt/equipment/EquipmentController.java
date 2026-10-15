package com.hwandevblog.mstchgmgmt.equipment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class EquipmentController {
    private final EquipmentRepository equipment;
    public EquipmentController(EquipmentRepository equipment) { this.equipment = equipment; }

    public record EquipmentResponse(String code, String name, String category,
                                    String dailyFeeKrw, boolean active, String version) { }

    @GetMapping("/api/equipment")
    public List<EquipmentResponse> list() {
        return equipment.findAllByOrderByCodeAsc().stream().map(row -> new EquipmentResponse(
                row.getCode(), row.getName(), row.getCategory(), Long.toString(row.getDailyFeeKrw()),
                row.isActive(), Long.toString(row.getVersion()))).toList();
    }
}
