package com.scienceminer.lookup.web.module;

import com.scienceminer.lookup.exception.NotFoundException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.HashMap;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Override
    public Response toResponse(NotFoundException exception) {
        String message = "The resource was not found.";

        if(isNotBlank(exception.getMessage())) {
            message = exception.getMessage();
        }
//        ObjectMapper mapper = new ObjectMapper();
//        ObjectNode root = mapper.createObjectNode();
//        root.put("message", message);
        final HashMap<String, String> responseBody = new HashMap<>();
        responseBody.put("message", message);
        
        return Response.status(Response.Status.NOT_FOUND)
                .entity(responseBody)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
