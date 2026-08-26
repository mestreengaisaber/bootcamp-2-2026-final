package dakota.software.flightservice.application.port.in;

public interface ReleaseSeatsUseCase {
    void release(Long flightId, int seats);

}
