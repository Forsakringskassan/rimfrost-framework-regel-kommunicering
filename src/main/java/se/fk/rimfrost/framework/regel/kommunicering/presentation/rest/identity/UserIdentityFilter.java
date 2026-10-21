package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.identity;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception.ExternalServiceErrorException;
import se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception.UnauthorizedException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.entity.ImmutableIdtyp;
import se.fk.rimfrost.adapter.identity.adapter.IdentityAdapter;
import se.fk.rimfrost.adapter.identity.exception.IdentityException;

import java.io.IOException;

/**
 * Request filter for ensuring that only authenticated users can access resources.
 * The filter stores the resolved user identity in {@link UserIdentityHolder} to
 * facilitate easy access to the user identity.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class UserIdentityFilter implements ContainerRequestFilter
{
   private static final Logger LOGGER = LoggerFactory.getLogger(UserIdentityFilter.class);

   @Inject
   IdentityAdapter identityAdapter;

   @Inject
   UserIdentityHolder userIdentityHolder;

   /**
    * Resolves the inloggad handläggare's identity via {@link IdentityAdapter}.
    * throws {@link UnauthorizedException} at failure to resolve user identity
    * throws {@link ExternalServiceErrorException} at any other external service error
    */
   @Override
   public void filter(ContainerRequestContext requestContext) throws IOException
   {
      try
      {
         var identity = identityAdapter.getIdentity(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION));

         userIdentityHolder.setIdentity(ImmutableIdtyp.builder().typId(identity.typId()).varde(identity.varde()).build());
      }
      catch (IdentityException e)
      {
         if (e.getErrorType() == IdentityException.ErrorType.UNAUTHORIZED)
         {
            LOGGER.info("Could not resolve user identity from request: {}", e.getMessage());
            throw new UnauthorizedException("Could not resolve user identity", e);
         }
         LOGGER.error("Error fetching user identity from request", e);
         throw new ExternalServiceErrorException(toHttpStatus(e), e.getMessage(), e);
      }
   }

   private static Response.Status toHttpStatus(IdentityException e)
   {
      return switch (e.getErrorType()) {
         case NOT_FOUND -> Response.Status.NOT_FOUND;
         case BAD_REQUEST -> Response.Status.BAD_REQUEST;
         case SERVICE_UNAVAILABLE -> Response.Status.SERVICE_UNAVAILABLE;
         default -> Response.Status.INTERNAL_SERVER_ERROR;
      };
   }
}
