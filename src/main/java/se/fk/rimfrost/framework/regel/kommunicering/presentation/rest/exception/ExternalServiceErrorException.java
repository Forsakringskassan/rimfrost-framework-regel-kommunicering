package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception;

import jakarta.ws.rs.core.Response;

public class ExternalServiceErrorException extends RuntimeException
{
   private final Response.Status status;

   public ExternalServiceErrorException(Response.Status status, String message, Throwable cause)
   {
      super(message, cause);

      this.status = status;
   }

   public Response.Status getStatus()
   {
      return status;
   }
}
