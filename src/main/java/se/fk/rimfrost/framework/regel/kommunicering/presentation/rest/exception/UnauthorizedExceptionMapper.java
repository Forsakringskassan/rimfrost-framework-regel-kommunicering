package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

/**
 * Maps {@link UnauthorizedException} to HTTP 401 Unauthorized.
 */
@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException>
{
   /** {@inheritDoc} */
   @Override
   public Response toResponse(UnauthorizedException exception)
   {
      var responseStatus = Response.Status.UNAUTHORIZED;

      var errorResponse = new ErrorResponse();
      errorResponse.setCode(responseStatus.getStatusCode());
      errorResponse.setMessage(exception.getMessage());

      return Response.status(responseStatus).entity(errorResponse).build();
   }
}
