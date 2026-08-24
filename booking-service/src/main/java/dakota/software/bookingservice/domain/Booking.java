package dakota.software.bookingservice.domain;

import java.math.BigDecimal;

public class Booking {

    private Long id;
    private final Passenger passenger;
    private final Long flightId;
    private final int seats;
    private final BigDecimal amount;
    private final PaymentMethod paymentMethod;
    private BookingStatus status;

    public Booking(Passenger passenger, Long flightId, int seats, BigDecimal amount, PaymentMethod paymentMethod) {
        validate(passenger, flightId, seats, amount, paymentMethod);
        this.passenger = passenger;
        this.flightId = flightId;
        this.seats = seats;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = BookingStatus.PENDING;
    }

    public Booking(Long id, Passenger passenger, Long flightId, int seats, BigDecimal amount,
                   PaymentMethod paymentMethod, BookingStatus status) {
        validate(passenger, flightId, seats, amount, paymentMethod);
        if (status == null) {
            throw new IllegalArgumentException("Status must not be null");
        }
        this.id = id;
        this.passenger = passenger;
        this.flightId = flightId;
        this.seats = seats;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
    }

    private static void validate(Passenger passenger, Long flightId, int seats, BigDecimal amount, PaymentMethod paymentMethod) {
        if (passenger == null) {
            throw new IllegalArgumentException("Passenger must not be null");
        }
        if (flightId == null) {
            throw new IllegalArgumentException("FlightId must not be null");
        }
        if (seats < 1) {
            throw new IllegalArgumentException("Seats must be greater than 0");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must not be null and must be >= 0");
        }
        // Con el enum la invariante es de tipo: solo existen STRIPE, PAYPAL y MOCK,
        // así que basta con el chequeo de nulidad.
        if (paymentMethod == null) {
            throw new IllegalArgumentException("PaymentMethod must not be null");
        }
    }

    //evitamos usar los sets en dominio  descontrolados y le indicamos solo un comportamiento controlado y desado .

    public void confirm() {
        ensurePending();
        this.status = BookingStatus.CONFIRMED;
    }

    public void cancel() {
        ensurePending();
        this.status = BookingStatus.CANCELLED;
    }

    public void fail() {
        ensurePending();
        this.status = BookingStatus.FAILED;
    }

    private void ensurePending() {
        if (this.status != BookingStatus.PENDING) {
            throw new IllegalStateException("Only PENDING bookings can change status, current: " + this.status);
        }
    }

    public Long getId() {
        return id;
    }

    public Passenger getPassenger() {
        return passenger;
    }

    public Long getFlightId() {
        return flightId;
    }

    public int getSeats() {
        return seats;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BookingStatus getStatus() {
        return status;
    }
}