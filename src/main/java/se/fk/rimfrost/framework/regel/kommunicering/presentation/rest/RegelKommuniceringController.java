package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest;

import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.rimfrost.adapter.identity.adapter.IdentityAdapter;
import se.fk.rimfrost.adapter.identity.exception.IdentityException;
import se.fk.rimfrost.adapter.permissions.adapter.PermissionsAdapter;
import se.fk.rimfrost.adapter.permissions.adapter.PermissionsException;
import se.fk.rimfrost.framework.handlaggning.adapter.HandlaggningAdapter;
import se.fk.rimfrost.framework.handlaggning.exception.HandlaggningException;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.HandlaggningUpdate;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableHandlaggning;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableHandlaggningUpdate;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableRegelutfall;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableUppgift;
import se.fk.rimfrost.framework.regel.Utfall;
import se.fk.rimfrost.framework.regel.integration.config.RegelConfigProvider;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringService;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringDoneHandler;
import se.fk.rimfrost.framework.regel.kommuniceringsutfall.jaxrsspec.controllers.generatedsource.KommuniceringsutfallControllerApi;
import se.fk.rimfrost.framework.regel.kommuniceringsutfall.jaxrsspec.controllers.generatedsource.model.Kommuniceringsutfall;
import se.fk.rimfrost.framework.regel.kommuniceringsutfall.jaxrsspec.controllers.generatedsource.model.PatchKommuniceringsutfallRequest;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.RegelOulControllerApi;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.GetUtokadUppgiftsbeskrivningResponse;
import se.fk.rimfrost.framework.regel.oul.logic.OulUppgiftService;
import se.fk.rimfrost.framework.regel.oul.storage.RegelCommonDataStorage;
import se.fk.rimfrost.framework.regel.oul.storage.entity.ImmutableRegelCommonData;
import se.fk.rimfrost.framework.regel.uppgiftskommentar.jaxrsspec.controllers.generatedsource.UppgiftskommentarControllerApi;
import se.fk.rimfrost.framework.regel.uppgiftskommentar.jaxrsspec.controllers.generatedsource.model.PatchUppgiftskommentarRequest;
import se.fk.rimfrost.framework.sid.adapter.SidAdapter;
import se.fk.rimfrost.framework.sid.exception.SidException;
import se.fk.rimfrost.framework.sid.model.Idtyp;

import java.util.List;
import java.util.UUID;

/**
 * Abstract base controller for kommunicering endpoints. Extend with the regel's own
 * OpenAPI-generated data types:
 *
 * <pre>
 * {@code @Path("/api/rtf")}
 * public class RtfKommuniceringController
 *         extends KommuniceringController<RtfKommuniceringGetData, RtfKommuniceringPatchRequestData> {}
 * </pre>
 *
 * @param <T> data type for the regel GET response
 * @param <Y> data type for the regel PATCH request body
 */
@SuppressWarnings("unused")
public abstract class RegelKommuniceringController<T, Y>
      implements RegelOulControllerApi, UppgiftskommentarControllerApi, KommuniceringsutfallControllerApi
{
   private static final Logger LOGGER = LoggerFactory.getLogger(RegelKommuniceringController.class);

   @Inject
   HandlaggningAdapter handlaggningAdapter;

   @Inject
   SidAdapter sidAdapter;

   @Inject
   PermissionsAdapter permissionsAdapter;

   @Inject
   IdentityAdapter identityAdapter;

   @Inject
   RoutingContext routingContext;

   @Inject
   RegelConfigProvider regelConfigProvider;

   @Inject
   OulUppgiftService oulUppgiftService;

   @Inject
   RegelKommuniceringService<T, Y> regelKommuniceringService;

   @Inject
   RegelKommuniceringDoneHandler regelKommuniceringDoneHandler;

   @Inject
   RegelCommonDataStorage regelCommonDataStorage;

   @GET
   @Path("/{handlaggningId}")
   public Response getKommunicering(@PathParam("handlaggningId") UUID handlaggningId)
   {
      var handlaggning = fetchHandlaggning(handlaggningId);
      if (checkSid(handlaggning) && !checkHandlaggareHasSidPermission())
      {
         unassignUppgift(handlaggningId);
         return Response.status(Response.Status.FORBIDDEN.getStatusCode(), "Skyddad identitet").build();
      }

      var response = regelKommuniceringService.read(handlaggning);
      return Response.ok(response).build();
   }

   @PATCH
   @Path("/{handlaggningId}")
   public void patchKommunicering(@PathParam("handlaggningId") UUID handlaggningId, @Valid @NotNull Y request)
   {
      var handlaggning = fetchHandlaggning(handlaggningId);
      var handlaggningUpdate = regelKommuniceringService.update(handlaggning, request);
      updateHandlaggning(handlaggningId, handlaggningUpdate);
   }

   @PATCH
   @Path("/uppgiftskommentar/{handlaggningId}")
   @Override
   public void patchUppgiftskommentar(@PathParam("handlaggningId") UUID handlaggningId,
         @Valid @NotNull PatchUppgiftskommentarRequest patchUppgiftskommentarRequest)
   {
      var oulCorrelationData = oulUppgiftService.getCorrelationData(handlaggningId);

      if (oulCorrelationData == null)
      {
         throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
      }

      var updatedUppgift = ImmutableUppgift.builder()
            .from(oulCorrelationData.uppgift())
            .version(oulCorrelationData.uppgift().version() + 1)
            .kommentar(patchUppgiftskommentarRequest.getKommentar())
            .build();

      try
      {
         oulUppgiftService.updateUppgift(handlaggningId, updatedUppgift);
      }
      catch (HandlaggningException e)
      {
         handleHandlaggningWriteException(e, handlaggningId);
      }
   }

   @PATCH
   @Path("/kommuniceringsutfall/{handlaggningId}")
   @Override
   public void patchKommuniceringsutfall(@PathParam("handlaggningId") UUID handlaggningId,
         @Valid @NotNull PatchKommuniceringsutfallRequest patchKommuniceringsutfallRequest)
   {
      var oulCorrelationData = oulUppgiftService.getCorrelationData(handlaggningId);

      if (oulCorrelationData == null)
      {
         throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
      }

      var utfall = toUtfall(patchKommuniceringsutfallRequest.getUtfall());

      var updatedUppgift = ImmutableUppgift.builder()
            .from(oulCorrelationData.uppgift())
            .version(oulCorrelationData.uppgift().version() + 1)
            .regelutfall(ImmutableRegelutfall.builder().varde(utfall.name()).build())
            .build();

      try
      {
         oulUppgiftService.updateUppgift(handlaggningId, updatedUppgift);
      }
      catch (HandlaggningException e)
      {
         handleHandlaggningWriteException(e, handlaggningId);
      }
   }

   /**
    * Returns the extended description of the task the handläggare should perform
    *
    * @return 200 with the extended description
    */
   @GET
   @Path("/utokadUppgiftsbeskrivning")
   @Override
   public GetUtokadUppgiftsbeskrivningResponse getUtokadUppgiftsbeskrivning()
   {
      var regelConfig = regelConfigProvider.getConfig();

      GetUtokadUppgiftsbeskrivningResponse response = new GetUtokadUppgiftsbeskrivningResponse();
      response.setBeskrivning(regelConfig.getUtokadUppgiftsbeskrivning().getBeskrivning());

      return response;
   }

   @POST
   @Path("/{handlaggningId}/done")
   public void markDone(@PathParam("handlaggningId") UUID handlaggningId)
   {
      regelKommuniceringDoneHandler.handleKommuniceringDone(handlaggningId);
   }

   private Handlaggning fetchHandlaggning(UUID handlaggningId)
   {
      try
      {
         return handlaggningAdapter.readHandlaggning(handlaggningId);
      }
      catch (HandlaggningException e)
      {
         throw new WebApplicationException(buildErrorResponse(toHttpStatus(e).getStatusCode(), e.getMessage()));
      }
   }

   private void updateHandlaggning(UUID handlaggningId, HandlaggningUpdate update)
   {
      try
      {
         handlaggningAdapter.updateHandlaggning(update);
      }
      catch (HandlaggningException e)
      {
         handleHandlaggningWriteException(e, handlaggningId);
      }
   }

   /**
    * Checks whether any individ on the handläggning has a protected identity (SID).
    * Returns {@code true} if SID is detected, {@code false} otherwise.
    * Maps {@link SidException} to an appropriate HTTP status on service errors.
    */
   private boolean checkSid(Handlaggning handlaggning)
   {
      try
      {
         return sidAdapter.containsSid(extractIndivider(handlaggning));
      }
      catch (SidException e)
      {
         LOGGER.error("Error checking SID for handlaggningsId: {}", handlaggning.id(), e);
         throw new WebApplicationException(buildErrorResponse(toHttpStatus(e).getStatusCode(), e.getMessage()));
      }
   }

   /**
    * Checks whether the inloggad handläggare has SID-behörighet (FRKOMP-FR-05.9).
    * Identity is resolved via {@link IdentityAdapter#getIdentity(String)}.
    * Returns {@code true} if the handläggare has permission, {@code false} otherwise.
    * Maps {@link PermissionsException} to an appropriate HTTP status on service errors.
    */
   private boolean checkHandlaggareHasSidPermission()
   {
      var identity = resolveHandlaggareIdentity();
      if (identity == null)
      {
         LOGGER.warn("Handläggare identity could not be resolved, denying SID permission");
         return false;
      }
      try
      {
         return permissionsAdapter.hasSidPermission(identity.typId(), identity.varde());
      }
      catch (PermissionsException e)
      {
         LOGGER.error("Error checking SID permission for idTyp: {}", identity.typId(), e);
         throw new WebApplicationException(buildErrorResponse(toHttpStatus(e).getStatusCode(), e.getMessage()));
      }
   }

   /**
    * Resolves the inloggad handläggare's identity via {@link IdentityAdapter}.
    * Returns {@code null} and logs a warning if the identity cannot be resolved due to an
    * {@link IdentityException.ErrorType#UNAUTHORIZED} error; throws {@link WebApplicationException}
    * for other identity service errors.
    */
   private se.fk.rimfrost.adapter.identity.model.Idtyp resolveHandlaggareIdentity()
   {
      var authHeader = routingContext.request().headers().get(HttpHeaders.AUTHORIZATION);
      try
      {
         return identityAdapter.getIdentity(authHeader);
      }
      catch (IdentityException e)
      {
         if (e.getErrorType() == IdentityException.ErrorType.UNAUTHORIZED)
         {
            LOGGER.warn("Could not resolve handläggare identity from request, treating as no SID permission");
            return null;
         }
         LOGGER.error("Error fetching handläggare identity from request", e);
         throw new WebApplicationException(buildErrorResponse(toHttpStatus(e).getStatusCode(), e.getMessage()));
      }
   }

   /**
    * Unassigns the OUL uppgift for the given handläggning so it returns to an unassigned state.
    * Delegates to {@link OulUppgiftService#tryUnassignOulUppgift}, which logs and swallows any
    * failure so it never affects the HTTP response (FRKOMM-FR-08.8).
    */
   private void unassignUppgift(UUID handlaggningId)
   {
      var correlationData = oulUppgiftService.getCorrelationData(handlaggningId);
      if (correlationData == null)
      {
         LOGGER.warn("No correlation data found for handlaggningId: {}, skipping unassign", handlaggningId);
         return;
      }
      var oulUppgiftId = correlationData.oulUppgiftId();
      if (oulUppgiftId == null)
      {
         LOGGER.warn("No oulUppgiftId found for handlaggningId: {}, skipping unassign", handlaggningId);
         return;
      }
      oulUppgiftService.tryUnassignOulUppgift(oulUppgiftId);
   }

   /**
    * Extracts individer from the handläggning's yrkande and maps them to the SID adapter's
    * {@link se.fk.rimfrost.framework.sid.model.Idtyp} type.
    */
   private List<Idtyp> extractIndivider(Handlaggning handlaggning)
   {
      return handlaggning.yrkande().rollerIYrkande()
            .stream().<se.fk.rimfrost.framework.sid.model.Idtyp> map(
                  roll -> se.fk.rimfrost.framework.sid.model.ImmutableIdtyp.builder()
                        .typId(roll.individ().typId())
                        .varde(roll.individ().varde())
                        .build())
            .toList();
   }

   private void handleHandlaggningWriteException(HandlaggningException e, UUID handlaggningId)
   {
      if (e.getErrorType() == HandlaggningException.ErrorType.CONFLICT)
      {
         LOGGER.error(
               "Version conflict while attempting to update handlaggning with id: {}. Programming fault?",
               handlaggningId, e);
         throw new WebApplicationException(buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
               "Version conflict while attempting to update handlaggning with id: " + handlaggningId
                     + ". Programming fault?"));
      }

      LOGGER.error("Failed to update handlaggning. handlaggningId: {}", handlaggningId, e);
      throw new WebApplicationException(buildErrorResponse(toHttpStatus(e).getStatusCode(), e.getMessage()));
   }

   private Utfall toUtfall(Kommuniceringsutfall kommuniceringsutfall)
    {
        return switch (kommuniceringsutfall) {
            case NEKAT_BESTRIDANDE, KUND_ACCEPTERAT -> Utfall.JA;
            case ACCEPTERAT_BESTRIDANDE -> Utfall.NEJ;
        };
    }

   private Response.Status toHttpStatus(HandlaggningException e)
    {
        return switch (e.getErrorType())
        {
            case NOT_FOUND -> Response.Status.NOT_FOUND;
            case BAD_REQUEST -> Response.Status.BAD_REQUEST;
            case SERVICE_UNAVAILABLE -> Response.Status.SERVICE_UNAVAILABLE;
            default -> Response.Status.INTERNAL_SERVER_ERROR;
        };
    }

   private static Response.Status toHttpStatus(SidException e)
   {
      return externalServiceErrorToHttpStatus(e.getErrorType().name());
   }

   private static Response.Status toHttpStatus(PermissionsException e)
   {
      return externalServiceErrorToHttpStatus(e.getErrorType().name());
   }

   private static Response.Status toHttpStatus(IdentityException e)
   {
      return externalServiceErrorToHttpStatus(e.getErrorType().name());
   }

   private static Response.Status externalServiceErrorToHttpStatus(String errorType)
   {
      return switch(errorType){case"NOT_FOUND"->Response.Status.NOT_FOUND;case"BAD_REQUEST"->Response.Status.BAD_REQUEST;case"SERVICE_UNAVAILABLE"->Response.Status.SERVICE_UNAVAILABLE;default->Response.Status.INTERNAL_SERVER_ERROR;};
   }

   private static Response buildErrorResponse(int statusCode, String message)
   {
      return Response.status(statusCode, message).build();
   }
}
