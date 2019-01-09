package com.scienceminer.lookup.web.module;

import com.scienceminer.lookup.exception.ServiceOverloadedException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.HashMap;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Provider
public class ServiceOverloadedExceptionMapper implements ExceptionMapper<ServiceOverloadedException> {

    @Override
    public Response toResponse(ServiceOverloadedException exception) {
        String message = "The service is overloaded. Please reduce the request rate or increase the servers resources. ";

        if(isNotBlank(exception.getMessage())) {
            message = exception.getMessage();
        }
        
        final HashMap<String, String> responseBody = new HashMap<>();
        responseBody.put("message", message);
        
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(responseBody)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
