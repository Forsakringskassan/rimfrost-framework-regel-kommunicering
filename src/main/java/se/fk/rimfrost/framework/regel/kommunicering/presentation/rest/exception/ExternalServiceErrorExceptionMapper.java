package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

/**
 * Maps {@link ExternalServiceErrorException} to HTTP status indicated by exception.
 */
@Provider
public class ExternalServiceErrorExceptionMapper implements ExceptionMapper<ExternalServiceErrorException>
{
   /** {@inheritDoc} */
   @Override
   public Response toResponse(ExternalServiceErrorException exception)
   {
      var errorResponse = new ErrorResponse();
      errorResponse.setCode(exception.getStatus().getStatusCode());
      errorResponse.setMessage(exception.getMessage());

      return Response.status(exception.getStatus()).entity(errorResponse).build();
   }
}
