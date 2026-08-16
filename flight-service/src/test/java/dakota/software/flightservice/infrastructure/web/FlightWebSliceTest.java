package dakota.software.flightservice.infrastructure.web;

import dakota.software.flightservice.application.port.out.FlightRepositoryPort;
import dakota.software.flightservice.application.service.FlightService;
import dakota.software.flightservice.domain.Airport;
import dakota.software.flightservice.domain.Flight;
import dakota.software.flightservice.domain.SeatInventory;
import dakota.software.flightservice.testsupport.WebSliceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {
        FlightController.class,
        FlightService.class,
        WebSliceAutoConfiguration.class
})
@AutoConfigureMockMvc
class FlightWebSliceTest {

    private static final Flight FLIGHT = new Flight(
            1L,
            "IB1234",
            new Airport(1L, "MAD", "Adolfo Suarez", "Madrid", "Spain"),
            new Airport(2L, "BCN", "El Prat", "Barcelona", "Spain"),
            LocalDateTime.of(2026, 8, 20, 10, 0),
            LocalDateTime.of(2026, 8, 20, 11, 30),
            new BigDecimal("199.90"),
            new SeatInventory(180, 150));

    @MockitoBean
    private FlightRepositoryPort flightRepository;

    @Autowired
    private MockMvcTester mvc;

    @Test
    void searchByOriginAndDestinationReturnsFlights() {
        when(flightRepository.findByOriginAndDestination(eq("MAD"), eq("BCN"), any()))
                .thenReturn(List.of(FLIGHT));

        assertThat(mvc.get().uri("/api/v1/flights?origin=MAD&destination=BCN").accept(MediaType.APPLICATION_JSON))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].flightNumber").isEqualTo("IB1234");
    }

    @Test
    void searchWithoutParametersReturnsEmptyList() {
        assertThat(mvc.get().uri("/api/v1/flights").accept(MediaType.APPLICATION_JSON))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Test
    void searchWithInvalidIataCodeIsRejected() {
        assertThat(mvc.get().uri("/api/v1/flights?origin=ma&destination=BCN").accept(MediaType.APPLICATION_JSON))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void searchWithMalformedDateIsRejected() {
        assertThat(mvc.get().uri("/api/v1/flights?origin=MAD&destination=BCN&departureDate=20/08/2026")
                .accept(MediaType.APPLICATION_JSON))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
