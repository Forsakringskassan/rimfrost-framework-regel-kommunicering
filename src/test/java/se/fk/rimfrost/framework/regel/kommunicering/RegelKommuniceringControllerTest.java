package se.fk.rimfrost.framework.regel.kommunicering;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import se.fk.rimfrost.framework.handlaggning.adapter.HandlaggningAdapter;
import se.fk.rimfrost.framework.handlaggning.exception.HandlaggningException;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.HandlaggningUpdate;
import se.fk.rimfrost.framework.handlaggning.model.Yrkande;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringDoneHandler;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringService;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.CorrelationDataReadException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.EndOulUppgiftException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.HandlaggningNotFoundException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.HandlaggningReadException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.UppgiftIncompleteException;
import se.fk.rimfrost.framework.regel.kommuniceringsutfall.jaxrsspec.controllers.generatedsource.model.Kommuniceringsutfall;
import se.fk.rimfrost.framework.regel.kommuniceringsutfall.jaxrsspec.controllers.generatedsource.model.PatchKommuniceringsutfallRequest;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;
import se.fk.rimfrost.framework.regel.uppgiftskommentar.jaxrsspec.controllers.generatedsource.model.PatchUppgiftskommentarRequest;
import se.fk.rimfrost.framework.sid.adapter.SidAdapter;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@QuarkusTest
public class RegelKommuniceringControllerTest extends AbstractRegelKommuniceringTestBase
{
   @InjectMock
   HandlaggningAdapter handlaggningAdapter;

   @InjectMock
   SidAdapter sidAdapter;

   @InjectMock
   RegelKommuniceringService<String, String> regelKommuniceringService;

   @InjectMock
   RegelKommuniceringDoneHandler regelKommuniceringDoneHandler;

   private Handlaggning createHandlaggning()
   {
      var yrkande = Mockito.mock(Yrkande.class);
      Mockito.when(yrkande.rollerIYrkande()).thenReturn(List.of());
      var handlaggning = Mockito.mock(Handlaggning.class);
      Mockito.when(handlaggning.yrkande()).thenReturn(yrkande);
      return handlaggning;
   }

   @Test
   @DisplayName("FRKOMM-FR-03.5: kommuniceringDone returns 204 on success")
   void should_return_204_on_done_when_successful()
   {
      var handlaggningId = UUID.randomUUID();

      RestAssured.given()
            .post("/api/test/" + handlaggningId + "/done")
            .then()
            .statusCode(204);

      verify(regelKommuniceringDoneHandler).handleKommuniceringDone(handlaggningId);
   }

   @Test
   @DisplayName("FRKOMM-FR-3.6: kommuniceringDone returns 409 when no correlation data is in storage")
   void should_return_409_on_done_when_correlation_data_read_exception()
   {
      var handlaggningId = UUID.randomUUID();

      Mockito.doThrow(new CorrelationDataReadException("")).when(regelKommuniceringDoneHandler)
            .handleKommuniceringDone(handlaggningId);

      var response = RestAssured.given()
            .post("/api/test/" + handlaggningId + "/done")
            .then()
            .statusCode(409).extract().as(ErrorResponse.class);
      assertEquals(409, response.getCode());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.5: kommuniceringDone returns 422 when no utfall is found on uppgift")
   void should_return_422_on_done_when_kommunicering_uppgift_incomplete()
   {
      var handlaggningId = UUID.randomUUID();
      var exceptionMessage = "Regelutfall not set";

      Mockito.doThrow(new UppgiftIncompleteException(exceptionMessage)).when(regelKommuniceringDoneHandler)
            .handleKommuniceringDone(handlaggningId);
      var response = RestAssured.given()
            .post("/api/test/" + handlaggningId + "/done")
            .then()
            .statusCode(422).extract().as(ErrorResponse.class);
      assertEquals(422, response.getCode());
      assertEquals(exceptionMessage, response.getMessage());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.7: kommuniceringDone returns 404 when handlaggning not found")
   void should_return_404_on_done_when_handlaggning_not_found()
   {
      var handlaggningId = UUID.randomUUID();

      Mockito.doThrow(new HandlaggningNotFoundException("", new Throwable())).when(regelKommuniceringDoneHandler)
            .handleKommuniceringDone(handlaggningId);
      var response = RestAssured.given()
            .post("/api/test/" + handlaggningId + "/done")
            .then()
            .statusCode(404).extract().as(ErrorResponse.class);
      assertEquals(404, response.getCode());
   }

   @Test
   @DisplayName("kommuniceringDone returns 500 when handlaggning read fails")
   void should_return_500_on_done_when_handlaggning_read_fails()
   {
      var handlaggningId = UUID.randomUUID();

      Mockito.doThrow(new HandlaggningReadException("", new Throwable())).when(regelKommuniceringDoneHandler)
            .handleKommuniceringDone(handlaggningId);
      var response = RestAssured.given()
            .post("/api/test/" + handlaggningId + "/done")
            .then()
            .statusCode(500).extract().as(ErrorResponse.class);
      assertEquals(500, response.getCode());
   }

   @Test
   @DisplayName("kommuniceringDone returns 500 when OUL end fails")
   void should_return_500_on_done_when_oul_end_fails()
   {
      var handlaggningId = UUID.randomUUID();

      Mockito.doThrow(new EndOulUppgiftException("", new Throwable())).when(regelKommuniceringDoneHandler)
            .handleKommuniceringDone(handlaggningId);
      var response = RestAssured.given()
            .post("/api/test/" + handlaggningId + "/done")
            .then()
            .statusCode(500).extract().as(ErrorResponse.class);
      assertEquals(500, response.getCode());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.1: GET data returns 200 on success")
   void should_return_200_on_get_data_success() throws Exception
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(Mockito.anyList())).thenReturn(false);
      Mockito.when(regelKommuniceringService.read(any())).thenReturn("response");
      RestAssured.given()
            .get("/api/test/" + handlaggningId)
            .then()
            .statusCode(200);

      verify(handlaggningAdapter).readHandlaggning(handlaggningId);
      verify(sidAdapter).containsSid(Mockito.anyList());
      verify(regelKommuniceringService).read(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.2: PATCH data returns 204 on success")
   void should_return_204_on_patch_data_success() throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();
      var requestBody = "";
      var handlaggningUpdate = Mockito.mock(HandlaggningUpdate.class);

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);
      Mockito.when(regelKommuniceringService.update(eq(handlaggning), eq(requestBody)))
            .thenReturn(handlaggningUpdate);

      RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/" + handlaggningId)
            .then()
            .statusCode(204);

      verify(handlaggningAdapter).readHandlaggning(handlaggningId);
      verify(regelKommuniceringService).update(eq(handlaggning), eq(requestBody));
      verify(handlaggningAdapter).updateHandlaggning(handlaggningUpdate);
   }

   @Test
   @DisplayName("FRKOMM-FR-03.9: PATCH data returns 500 on conflict error during handlaggning update")
   void should_return_500_on_handlaggning_update_conflict_during_patch_data() throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();
      var requestBody = "";

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);
      Mockito.when(regelKommuniceringService.update(any(), eq(requestBody)))
            .thenReturn(Mockito.mock(HandlaggningUpdate.class));
      Mockito.when(handlaggningAdapter.updateHandlaggning(any()))
            .thenThrow(new HandlaggningException(HandlaggningException.ErrorType.CONFLICT, "Version conflict detected"));

      var response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/" + handlaggningId)
            .then()
            .statusCode(500).extract().as(ErrorResponse.class);

      assertEquals(500, response.getCode());
      assertTrue(response.getMessage().matches("(?i).*conflict.*"));
      assertTrue(response.getMessage().matches("(?i).*programming fault.*"));
   }

   @Test
   @DisplayName("FRKOMM-FR-03.3: PATCH uppgift kommentar returns 204 on success")
   void should_return_204_on_patch_uppgift_kommentar_success() throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchUppgiftskommentarRequest();
      requestBody.setKommentar("Kommentar");

      RestAssured.given()
            .body(requestBody)
            .patch("/api/test/uppgiftskommentar/" + handlaggningId)
            .then()
            .statusCode(204);

      verify(oulUppgiftService).getCorrelationData(handlaggningId);
      verify(oulUppgiftService).updateUppgift(eq(handlaggningId), any());
   }

   @Test
   @DisplayName("patchUppgiftskommentar returns 500 when correlation data not found")
   void should_return_500_on_correlation_data_not_found_failure_during_patch_uppgift_kommentar()
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchUppgiftskommentarRequest();
      requestBody.setKommentar("");

      Mockito.when(oulUppgiftService.getCorrelationData(handlaggningId)).thenReturn(null);

      RestAssured.given()
            .body(requestBody)
            .patch("/api/test/uppgiftskommentar/" + handlaggningId)
            .then()
            .statusCode(500);

      verify(oulUppgiftService).getCorrelationData(handlaggningId);
   }

   @Test
   @DisplayName("patchUppgiftskommentar returns 500 when exception occurs during correlation data read")
   void should_return_500_on_correlation_data_read_failure_during_patch_uppgift_kommentar()
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchUppgiftskommentarRequest();
      requestBody.setKommentar("");

      Mockito.when(oulUppgiftService.getCorrelationData(handlaggningId)).thenThrow(new RuntimeException());

      RestAssured.given()
            .body(requestBody)
            .patch("/api/test/uppgiftskommentar/" + handlaggningId)
            .then()
            .statusCode(500);

      verify(oulUppgiftService).getCorrelationData(handlaggningId);
   }

   @Test
   @DisplayName("patchUppgiftskommentar returns 400 when request body is missing")
   void should_return_400_on_null_request_body_during_patch_uppgift_kommentar()
   {
      RestAssured.given()
            .contentType(ContentType.JSON)
            .patch("/api/test/uppgiftskommentar/" + UUID.randomUUID())
            .then()
            .statusCode(400);
   }

   @Test
   @DisplayName("patchUppgiftskommentar should return expected error status when handlaggning update fails")
   void should_forward_error_response_status_on_handlaggning_update_failure_during_patch_uppgift_kommentar()
         throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchUppgiftskommentarRequest();
      requestBody.setKommentar("");

      Mockito.doThrow(new HandlaggningException(HandlaggningException.ErrorType.SERVICE_UNAVAILABLE, "Unexpected Error"))
            .when(oulUppgiftService).updateUppgift(eq(handlaggningId), any());

      RestAssured.given()
            .body(requestBody)
            .patch("/api/test/uppgiftskommentar/" + handlaggningId)
            .then()
            .statusCode(503);

      verify(oulUppgiftService).updateUppgift(eq(handlaggningId), any());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.10: PATCH uppgift kommentar returns 500 on conflict error during handlaggning update")
   void should_return_500_on_handlaggning_update_conflict_during_patch_uppgift_kommentar() throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchUppgiftskommentarRequest();
      requestBody.setKommentar("");

      Mockito.doThrow(new HandlaggningException(HandlaggningException.ErrorType.CONFLICT, "Conflict")).when(oulUppgiftService)
            .updateUppgift(eq(handlaggningId), any());

      var response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/uppgiftskommentar/" + handlaggningId)
            .then()
            .statusCode(500).extract().as(ErrorResponse.class);

      assertEquals(500, response.getCode());
      assertTrue(response.getMessage().matches("(?i).*conflict.*"));
      assertTrue(response.getMessage().matches("(?i).*programming fault.*"));
   }

   @Test
   @DisplayName("FRKOMM-FR-03.4: PATCH kommuniceringsutfall returns 204 on success")
   void should_return_204_on_patch_kommuniceringsutfall_success() throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchKommuniceringsutfallRequest();
      requestBody.setUtfall(Kommuniceringsutfall.KUND_ACCEPTERAT);

      RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/kommuniceringsutfall/" + handlaggningId)
            .then()
            .statusCode(204);

      verify(oulUppgiftService).getCorrelationData(handlaggningId);
      verify(oulUppgiftService).updateUppgift(eq(handlaggningId), any());
   }

   @Test
   @DisplayName("patchKommuniceringsutfall returns 500 when correlation data not found")
   void should_return_500_on_correlation_data_not_found_failure_during_patch_kommuniceringsutfall()
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchKommuniceringsutfallRequest();
      requestBody.setUtfall(Kommuniceringsutfall.KUND_ACCEPTERAT);

      Mockito.when(oulUppgiftService.getCorrelationData(handlaggningId)).thenReturn(null);

      RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/kommuniceringsutfall/" + handlaggningId)
            .then()
            .statusCode(500);

      verify(oulUppgiftService).getCorrelationData(handlaggningId);
   }

   @Test
   @DisplayName("patchKommuniceringsutfall returns 500 when exception occurs during correlation data read")
   void should_return_500_on_correlation_data_read_failure_during_patch_kommuniceringsutfall()
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchKommuniceringsutfallRequest();
      requestBody.setUtfall(Kommuniceringsutfall.KUND_ACCEPTERAT);

      Mockito.when(oulUppgiftService.getCorrelationData(handlaggningId)).thenThrow(new RuntimeException());

      RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/kommuniceringsutfall/" + handlaggningId)
            .then()
            .statusCode(500);

      verify(oulUppgiftService).getCorrelationData(handlaggningId);
   }

   @Test
   @DisplayName("patchKommuniceringsutfall returns 400 when request body is missing")
   void should_return_400_on_null_request_body_during_patch_kommuniceringsutfall()
   {
      RestAssured.given()
            .contentType(ContentType.JSON)
            .patch("/api/test/kommuniceringsutfall/" + UUID.randomUUID())
            .then()
            .statusCode(400);
   }

   @Test
   @DisplayName("patchKommuniceringsutfall returns 400 when utfall is missing from request body")
   void should_return_400_on_null_utfall_during_patch_kommuniceringsutfall()
   {
      var requestBody = new PatchKommuniceringsutfallRequest();
      requestBody.setUtfall(null);

      RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/kommuniceringsutfall/" + UUID.randomUUID())
            .then()
            .statusCode(400);
   }

   @Test
   @DisplayName("patchKommuniceringsutfall should return expected error status when handlaggning update fails")
   void should_forward_error_response_status_on_handlaggning_update_failure_during_patch_kommuniceringsutfall()
         throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchKommuniceringsutfallRequest();
      requestBody.setUtfall(Kommuniceringsutfall.KUND_ACCEPTERAT);

      Mockito.doThrow(new HandlaggningException(HandlaggningException.ErrorType.SERVICE_UNAVAILABLE, "Unexpected Error"))
            .when(oulUppgiftService).updateUppgift(eq(handlaggningId), any());

      RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/kommuniceringsutfall/" + handlaggningId)
            .then()
            .statusCode(503);

      verify(oulUppgiftService).updateUppgift(eq(handlaggningId), any());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.11: PATCH uppgift kommuniceringsutfall returns 500 on conflict error during handlaggning update")
   void should_return_500_on_handlaggning_update_conflict_during_patch_patch_kommuniceringsutfall() throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchKommuniceringsutfallRequest();
      requestBody.setUtfall(Kommuniceringsutfall.KUND_ACCEPTERAT);

      Mockito.doThrow(new HandlaggningException(HandlaggningException.ErrorType.CONFLICT, "Conflict")).when(oulUppgiftService)
            .updateUppgift(any(), any());

      var response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/kommuniceringsutfall/" + handlaggningId)
            .then()
            .statusCode(500).extract().as(ErrorResponse.class);

      assertEquals(500, response.getCode());
      assertTrue(response.getMessage().matches("(?i).*conflict.*"));
      assertTrue(response.getMessage().matches("(?i).*programming fault.*"));
   }
}
