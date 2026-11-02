package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;

@Provider
public class CatchAllExceptionMapper implements ExceptionMapper<Exception>
{
   Logger logger = LoggerFactory.getLogger(CatchAllExceptionMapper.class);

   @ConfigProperty(name = "rimfrost.framework.regel.kommunicering.rest.exception.catchall.response.message", defaultValue = "Internal Server Error")
   String errorResponseMessage;

   @Override
   public Response toResponse(final Exception exception)
   {
      logger.error("Request terminated due to unexpected exception", exception);

      var errorResponse = new ErrorResponse();
      errorResponse.setCode(500);
      errorResponse.setMessage(errorResponseMessage);

      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
   }
}
