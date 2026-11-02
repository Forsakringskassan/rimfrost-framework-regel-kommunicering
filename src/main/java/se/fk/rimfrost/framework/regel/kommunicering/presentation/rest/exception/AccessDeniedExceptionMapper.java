package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

/**
 * Maps {@link AccessDeniedException} to HTTP 403 Forbidden.
 */
@Provider
public class AccessDeniedExceptionMapper implements ExceptionMapper<AccessDeniedException>
{
   /** {@inheritDoc} */
   @Override
   public Response toResponse(AccessDeniedException exception)
   {
      var responseStatus = Response.Status.FORBIDDEN;

      var errorResponse = new ErrorResponse();
      errorResponse.setCode(responseStatus.getStatusCode());
      errorResponse.setMessage(exception.getMessage());

      return Response.status(responseStatus).entity(errorResponse).build();
   }
}
