package se.fk.rimfrost.framework.regel.kommunicering.logic.exception;

public class HandlaggningNotFoundException extends RuntimeException
{
   public HandlaggningNotFoundException(String message, Throwable cause)
   {
      super(message, cause);
   }
}
