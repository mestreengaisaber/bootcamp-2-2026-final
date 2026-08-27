package dakota.software.checkinservice.domain;

import java.time.LocalDateTime;

public record BoardingPass(
        Long id,
        Long checkInId,
        String seatNumber,// ej: "12A"
        String gate,// ej: "A12"
        LocalDateTime boardingTime  // ej: departure - 45min
) {
    // Constructor compacto para validaciones basicas
    public BoardingPass {
        // validaciones simples: seatNumber no null, gate no null, boardingTime no null

        // Validaciones fail-fast
        if (seatNumber == null || seatNumber.isBlank()) {
            throw new IllegalArgumentException("seatNumber obligatorio");
        }
        if (gate == null || gate.isBlank()) {
            throw new IllegalArgumentException("gate obligatorio");
        }
        if (boardingTime == null) {
            throw new IllegalArgumentException("boardingTime obligatorio");
        }

    }

}
