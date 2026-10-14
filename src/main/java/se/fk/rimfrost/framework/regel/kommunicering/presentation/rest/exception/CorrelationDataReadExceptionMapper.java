package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.CorrelationDataReadException;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

/**
 * Maps {@link CorrelationDataReadException} to HTTP 409 Conflict.
 */
@Provider
public class CorrelationDataReadExceptionMapper implements ExceptionMapper<CorrelationDataReadException>
{
   /** {@inheritDoc} */
   @Override
   public Response toResponse(final CorrelationDataReadException exception)
   {
      ErrorResponse errorResponse = new ErrorResponse();
      errorResponse.setCode(Response.Status.CONFLICT.getStatusCode());
      errorResponse.setMessage("Failed to read correlation data");

      return Response.status(Response.Status.CONFLICT).entity(errorResponse).build();
   }
}
