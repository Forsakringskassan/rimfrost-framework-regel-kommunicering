package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import se.fk.rimfrost.framework.handlaggning.exception.HandlaggningException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.HandlaggningNotFoundException;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

/**
 * Maps {@link HandlaggningException} to HTTP 404 Not Found.
 */
@Provider
public class HandlaggningNotFoundExceptionMapper implements ExceptionMapper<HandlaggningNotFoundException>
{
   /** {@inheritDoc} */
   @Override
   public Response toResponse(final HandlaggningNotFoundException exception)
   {
      ErrorResponse errorResponse = new ErrorResponse();
      errorResponse.setCode(Response.Status.NOT_FOUND.getStatusCode());
      errorResponse.setMessage(exception.getMessage());

      return Response.status(Response.Status.NOT_FOUND).entity(errorResponse).build();
   }
}
