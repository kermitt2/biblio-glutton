package com.scienceminer.glutton.web.module;

import com.scienceminer.glutton.exception.ServiceException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.HashMap;

@Provider
public class ServiceExceptionMapper implements ExceptionMapper<ServiceException> {

    @Override
    public Response toResponse(ServiceException exception) {

        final HashMap<String, String> responseBody = new HashMap<>();
        responseBody.put("message", exception.getMessage());
        responseBody.put("code", String.valueOf(exception.getStatusCode()));

        return Response.status(exception.getStatusCode())
                .entity(responseBody)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
