package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.UppgiftIncompleteException;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

@Provider
public class UppgiftIncompleteExceptionMapper implements ExceptionMapper<UppgiftIncompleteException>
{
   @Override
   public Response toResponse(final UppgiftIncompleteException exception)
   {
      var statusCode = 422;

      ErrorResponse errorResponse = new ErrorResponse();
      errorResponse.setCode(statusCode);
      errorResponse.setMessage(exception.getMessage());

      return Response.status(statusCode).entity(errorResponse).build();
   }
}
