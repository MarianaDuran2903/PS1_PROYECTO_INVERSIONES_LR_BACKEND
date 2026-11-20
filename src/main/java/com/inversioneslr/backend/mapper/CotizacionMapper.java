package com.inversioneslr.backend.mapper;

import com.inversioneslr.backend.dto.response.CotizacionResponse;
import com.inversioneslr.backend.model.CotizacionEstancia;
import com.inversioneslr.backend.model.DesglosePrecio;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class CotizacionMapper {

    public CotizacionResponse toResponse(Long idPublicacion, LocalDate llegada, LocalDate salida,
                                         CotizacionEstancia cotizacion) {
        DesglosePrecio desglose = cotizacion.desglose();
        List<CotizacionResponse.ServicioAdicionalResponse> servicios = cotizacion.serviciosAdicionales().stream()
                .map(servicio -> new CotizacionResponse.ServicioAdicionalResponse(servicio.nombre(),
                        servicio.tipoCobro(), servicio.precio(), servicio.valor()))
                .toList();
        return new CotizacionResponse(idPublicacion, llegada, salida, cotizacion.noches(),
                new CotizacionResponse.PrecioCotizacionResponse(
                        cotizacion.precioNoche(),
                        desglose.precioBase(),
                        desglose.porcentajeDescuento(),
                        desglose.valorDescuento(),
                        desglose.tarifaLimpieza(),
                        servicios,
                        cotizacion.totalServiciosAdicionales(),
                        desglose.porcentajeTarifaServicio(),
                        desglose.tarifaServicio(),
                        cotizacion.total()));
    }
}
