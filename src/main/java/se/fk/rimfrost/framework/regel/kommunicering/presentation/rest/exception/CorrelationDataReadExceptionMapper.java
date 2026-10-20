package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.CorrelationDataReadException;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

/**
 * Maps {@link CorrelationDataReadException} to HTTP 500 Internal Server Error.
 */
@Provider
public class CorrelationDataReadExceptionMapper implements ExceptionMapper<CorrelationDataReadException>
{
   /** {@inheritDoc} */
   @Override
   public Response toResponse(final CorrelationDataReadException exception)
   {
      var responseStatus = Response.Status.INTERNAL_SERVER_ERROR;

      ErrorResponse errorResponse = new ErrorResponse();
      errorResponse.setCode(responseStatus.getStatusCode());
      errorResponse.setMessage("Failed to read correlation data");

      return Response.status(responseStatus).entity(errorResponse).build();
   }
}
