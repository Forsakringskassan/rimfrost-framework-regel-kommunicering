package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.HandlaggningReadException;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

/**
 * Maps {@link HandlaggningReadException} to HTTP 500 Internal Server Error.
 */
@Provider
public class HandlaggningReadExceptionMapper implements ExceptionMapper<HandlaggningReadException>
{
   /** {@inheritDoc} */
   @Override
   public Response toResponse(final HandlaggningReadException exception)
   {
      ErrorResponse errorResponse = new ErrorResponse();
      errorResponse.setCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
      errorResponse.setMessage(exception.getMessage());

      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
   }
}
