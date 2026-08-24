package dakota.software.flightservice.application.port.in;

public interface ReserveSeatsUseCase {
    void reserve(Long flightId, int seats);
}
