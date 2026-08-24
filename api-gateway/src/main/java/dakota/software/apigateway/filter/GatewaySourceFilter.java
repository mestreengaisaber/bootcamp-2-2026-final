package dakota.software.apigateway.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filtro global que añade el header X-Gateway-Source a todas las respuestas
 * que pasan por el Gateway, indicando que provienen de api-gateway.
 */
@Component
@Order(-1)
public class GatewaySourceFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader("X-Gateway-Source", "api-gateway");
        chain.doFilter(request, response);
    }
}
