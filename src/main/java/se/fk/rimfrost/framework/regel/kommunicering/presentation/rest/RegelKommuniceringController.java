package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.rimfrost.framework.handlaggning.adapter.HandlaggningAdapter;
import se.fk.rimfrost.framework.handlaggning.exception.HandlaggningException;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.HandlaggningUpdate;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableRegelutfall;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableUppgift;
import se.fk.rimfrost.framework.regel.Utfall;
import se.fk.rimfrost.framework.regel.integration.config.RegelConfigProvider;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringService;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringDoneHandler;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.CorrelationDataReadException;
import se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.guard.RegelAccessGuard;
import se.fk.rimfrost.framework.regel.kommuniceringsutfall.jaxrsspec.controllers.generatedsource.KommuniceringsutfallControllerApi;
import se.fk.rimfrost.framework.regel.kommuniceringsutfall.jaxrsspec.controllers.generatedsource.model.Kommuniceringsutfall;
import se.fk.rimfrost.framework.regel.kommuniceringsutfall.jaxrsspec.controllers.generatedsource.model.PatchKommuniceringsutfallRequest;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.RegelOulControllerApi;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.GetUtokadUppgiftsbeskrivningResponse;
import se.fk.rimfrost.framework.regel.oul.logic.OulUppgiftService;
import se.fk.rimfrost.framework.regel.uppgiftskommentar.jaxrsspec.controllers.generatedsource.UppgiftskommentarControllerApi;
import se.fk.rimfrost.framework.regel.uppgiftskommentar.jaxrsspec.controllers.generatedsource.model.PatchUppgiftskommentarRequest;

import java.util.UUID;

/**
 * Abstract base controller for kommunicering endpoints. Extend with the regel's own
 * OpenAPI-generated data types:
 *
 * <pre>
 * {@code @Path("/api/rtf")}
 * public class RtfKommuniceringController
 *         extends RegelKommuniceringController&lt;RtfKommuniceringGetData, RtfKommuniceringPatchRequestData&gt; {}
 * </pre>
 *
 * @param <T> data type for the regel GET response
 * @param <Y> data type for the regel PATCH request body
 */
public abstract class RegelKommuniceringController<T, Y>
      implements RegelOulControllerApi, UppgiftskommentarControllerApi, KommuniceringsutfallControllerApi
{
   private static final Logger LOGGER = LoggerFactory.getLogger(RegelKommuniceringController.class);

   @Inject
   HandlaggningAdapter handlaggningAdapter;

   @Inject
   RegelConfigProvider regelConfigProvider;

   @Inject
   OulUppgiftService oulUppgiftService;

   @Inject
   RegelKommuniceringService<T, Y> regelKommuniceringService;

   @Inject
   RegelKommuniceringDoneHandler regelKommuniceringDoneHandler;

   @Inject
   RegelAccessGuard regelAccessGuard;

   @GET
   @Path("/{handlaggningId}")
   public Response getKommunicering(@PathParam("handlaggningId") UUID handlaggningId)
   {
      var handlaggning = fetchHandlaggning(handlaggningId);
      regelAccessGuard.verifyAccess(handlaggning);
      var response = regelKommuniceringService.read(handlaggning);
      return Response.ok(response).build();
   }

   @PATCH
   @Path("/{handlaggningId}")
   public void patchKommunicering(@PathParam("handlaggningId") UUID handlaggningId, @Valid @NotNull Y request)
   {
      var handlaggning = fetchHandlaggning(handlaggningId);
      regelAccessGuard.verifyAccess(handlaggning);
      var handlaggningUpdate = regelKommuniceringService.update(handlaggning, request);
      updateHandlaggning(handlaggningId, handlaggningUpdate);
   }

   @PATCH
   @Path("/uppgiftskommentar/{handlaggningId}")
   @Override
   public void patchUppgiftskommentar(@PathParam("handlaggningId") UUID handlaggningId,
         @Valid @NotNull PatchUppgiftskommentarRequest patchUppgiftskommentarRequest)
   {
      var handlaggning = fetchHandlaggning(handlaggningId);
      regelAccessGuard.verifyAccess(handlaggning);

      var oulCorrelationData = oulUppgiftService.getCorrelationData(handlaggningId);

      if (oulCorrelationData == null)
      {
         throw new CorrelationDataReadException("Correlation data not found");
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
      var handlaggning = fetchHandlaggning(handlaggningId);
      regelAccessGuard.verifyAccess(handlaggning);

      var oulCorrelationData = oulUppgiftService.getCorrelationData(handlaggningId);

      if (oulCorrelationData == null)
      {
         throw new CorrelationDataReadException("Correlation data not found");
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
      var handlaggning = fetchHandlaggning(handlaggningId);
      regelAccessGuard.verifyAccess(handlaggning);
      regelKommuniceringDoneHandler.handleKommuniceringDone(handlaggning);
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

   private static Response buildErrorResponse(int statusCode, String message)
   {
      return Response.status(statusCode, message).build();
   }
}
