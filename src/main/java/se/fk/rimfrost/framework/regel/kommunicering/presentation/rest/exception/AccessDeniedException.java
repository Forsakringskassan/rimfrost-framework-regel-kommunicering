package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception;

public class AccessDeniedException extends RuntimeException
{
   public AccessDeniedException(String message)
   {
      super(message);
   }
}
