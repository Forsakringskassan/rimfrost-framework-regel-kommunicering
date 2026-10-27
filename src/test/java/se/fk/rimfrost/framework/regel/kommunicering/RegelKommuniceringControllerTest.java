package se.fk.rimfrost.framework.regel.kommunicering;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import se.fk.rimfrost.adapter.identity.exception.IdentityException;
import se.fk.rimfrost.adapter.identity.model.ImmutableIdtyp;
import se.fk.rimfrost.framework.handlaggning.adapter.HandlaggningAdapter;
import se.fk.rimfrost.framework.handlaggning.exception.HandlaggningException;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.HandlaggningUpdate;
import se.fk.rimfrost.framework.handlaggning.model.Yrkande;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringDoneHandler;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringService;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.CorrelationDataReadException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.EndOulUppgiftException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.UppgiftIncompleteException;
import se.fk.rimfrost.framework.regel.kommuniceringsutfall.jaxrsspec.controllers.generatedsource.model.Kommuniceringsutfall;
import se.fk.rimfrost.framework.regel.kommuniceringsutfall.jaxrsspec.controllers.generatedsource.model.PatchKommuniceringsutfallRequest;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;
import se.fk.rimfrost.framework.regel.uppgiftskommentar.jaxrsspec.controllers.generatedsource.model.PatchUppgiftskommentarRequest;
import se.fk.rimfrost.framework.sid.adapter.SidAdapter;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
public class RegelKommuniceringControllerTest extends AbstractRegelKommuniceringTestBase
{
   private static final String NOT_ASSIGNED_PATTERN = "(?i).*not assigned to uppgift.*";
   private static final String CORRELATION_DATA_READ_FAILED_PATTERN = "(?i).*failed to read correlation data.*";

   @InjectMock
   HandlaggningAdapter handlaggningAdapter;

   @InjectMock
   SidAdapter sidAdapter;

   @InjectMock
   RegelKommuniceringService<String, String> regelKommuniceringService;

   @InjectMock
   RegelKommuniceringDoneHandler regelKommuniceringDoneHandler;

   private Handlaggning createHandlaggning(UUID handlaggningId)
   {
      var yrkande = Mockito.mock(Yrkande.class);
      Mockito.when(yrkande.rollerIYrkande()).thenReturn(List.of());
      var handlaggning = Mockito.mock(Handlaggning.class);
      Mockito.when(handlaggning.id()).thenReturn(handlaggningId);
      Mockito.when(handlaggning.yrkande()).thenReturn(yrkande);
      return handlaggning;
   }

   @BeforeEach
   void setupHandlaggning() throws HandlaggningException
   {
      Mockito.when(handlaggningAdapter.readHandlaggning(any()))
            .thenAnswer(invocation -> createHandlaggning(invocation.getArgument(0)));
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

      verify(regelKommuniceringDoneHandler).handleKommuniceringDone(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.6: kommuniceringDone returns 500 when no correlation data is in storage")
   void should_return_500_on_done_when_correlation_data_read_exception()
   {
      var handlaggningId = UUID.randomUUID();

      Mockito.doThrow(new CorrelationDataReadException("")).when(regelKommuniceringDoneHandler)
            .handleKommuniceringDone(any());

      var response = RestAssured.given()
            .post("/api/test/" + handlaggningId + "/done")
            .then()
            .statusCode(500).extract().as(ErrorResponse.class);
      assertEquals(500, response.getCode());
      verify(regelKommuniceringDoneHandler).handleKommuniceringDone(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.5: kommuniceringDone returns 422 when no utfall is found on uppgift")
   void should_return_422_on_done_when_kommunicering_uppgift_incomplete()
   {
      var handlaggningId = UUID.randomUUID();
      var exceptionMessage = "Regelutfall not set";

      Mockito.doThrow(new UppgiftIncompleteException(exceptionMessage)).when(regelKommuniceringDoneHandler)
            .handleKommuniceringDone(any());
      var response = RestAssured.given()
            .post("/api/test/" + handlaggningId + "/done")
            .then()
            .statusCode(422).extract().as(ErrorResponse.class);
      assertEquals(422, response.getCode());
      assertEquals(exceptionMessage, response.getMessage());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.7: kommuniceringDone returns 404 when handlaggning not found")
   void should_return_404_on_done_when_handlaggning_not_found() throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId))
            .thenThrow(new HandlaggningException(HandlaggningException.ErrorType.NOT_FOUND, "Handlaggning not found"));
      var response = RestAssured.given()
            .post("/api/test/" + handlaggningId + "/done")
            .then()
            .statusCode(404).extract().as(ErrorResponse.class);
      assertEquals(404, response.getCode());
      verifyNoInteractions(regelKommuniceringDoneHandler);
   }

   @Test
   @DisplayName("kommuniceringDone returns 500 when handlaggning read fails")
   void should_return_500_on_done_when_handlaggning_read_fails() throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();
      var exceptionMessage = "Unexpected error";

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId))
            .thenThrow(new HandlaggningException(HandlaggningException.ErrorType.UNEXPECTED_ERROR, exceptionMessage));
      var response = RestAssured.given()
            .post("/api/test/" + handlaggningId + "/done")
            .then()
            .statusCode(500).extract().as(ErrorResponse.class);
      assertEquals(500, response.getCode());
      assertEquals(exceptionMessage, response.getMessage());
      verifyNoInteractions(regelKommuniceringDoneHandler);
   }

   @Test
   @DisplayName("kommuniceringDone returns 500 when OUL end fails")
   void should_return_500_on_done_when_oul_end_fails()
   {
      var handlaggningId = UUID.randomUUID();

      Mockito.doThrow(new EndOulUppgiftException("", new Throwable())).when(regelKommuniceringDoneHandler)
            .handleKommuniceringDone(any());
      var response = RestAssured.given()
            .post("/api/test/" + handlaggningId + "/done")
            .then()
            .statusCode(500).extract().as(ErrorResponse.class);
      assertEquals(500, response.getCode());
      verify(regelKommuniceringDoneHandler).handleKommuniceringDone(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.1: GET data returns 200 on success")
   void should_return_200_on_get_data_success() throws Exception
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning(handlaggningId);

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
   @DisplayName("FRKOMM-FR-07.1: GET returns the rule's response in data together with informationsaccess")
   void should_return_rule_response_as_data_with_informationsaccess_on_get() throws Exception
   {
      var handlaggningId = givenReadableHandlaggning();

      RestAssured.given()
            .get("/api/test/" + handlaggningId)
            .then()
            .statusCode(200)
            .body("data", equalTo("response"))
            .body("informationsaccess", notNullValue());
   }

   @Test
   @DisplayName("FRKOMM-FR-07.2: informationsaccess.uppgiftId is the OUL uppgift id from the correlation data")
   void should_return_oul_uppgift_id_in_informationsaccess_on_get() throws Exception
   {
      var handlaggningId = givenReadableHandlaggning();

      RestAssured.given()
            .get("/api/test/" + handlaggningId)
            .then()
            .statusCode(200)
            .body("informationsaccess.uppgiftId", equalTo(DEFAULT_UPPGIFT_ID.toString()));
   }

   @Test
   @DisplayName("FRKOMM-FR-07.2: GET returns 500 and does not read data when no correlation data is stored")
   void should_return_500_without_correlation_data_on_get() throws Exception
   {
      var handlaggningId = givenReadableHandlaggning();
      Mockito.when(oulUppgiftService.getCorrelationData(Mockito.any())).thenReturn(null);

      RestAssured.given()
            .get("/api/test/" + handlaggningId)
            .then()
            .statusCode(500);

      verify(regelKommuniceringService, never()).read(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-07.3: informationsaccess.regelId and regelVersion come from the rule's config.yaml")
   void should_return_regel_id_and_version_from_config_on_get() throws Exception
   {
      var handlaggningId = givenReadableHandlaggning();

      // Values from src/test/resources/config-test.yaml
      RestAssured.given()
            .get("/api/test/" + handlaggningId)
            .then()
            .statusCode(200)
            .body("informationsaccess.regelId", equalTo("a11f3429-3a6b-4389-ba1c-c2747b0fb45b"))
            .body("informationsaccess.regelVersion", equalTo("1"));
   }

   @Test
   @DisplayName("FRKOMM-FR-07.5: informationsaccess.anvandare is the inloggad handläggare's identity")
   void should_return_handlaggare_identity_in_informationsaccess_on_get() throws Exception
   {
      var handlaggningId = givenReadableHandlaggning();

      RestAssured.given()
            .get("/api/test/" + handlaggningId)
            .then()
            .statusCode(200)
            .body("informationsaccess.anvandare.typId", equalTo(ID_TYP))
            .body("informationsaccess.anvandare.varde", equalTo(ID_VARDE));
   }

   private UUID givenReadableHandlaggning() throws Exception
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning(handlaggningId);
      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(Mockito.anyList())).thenReturn(false);
      Mockito.when(regelKommuniceringService.read(any())).thenReturn("response");
      return handlaggningId;
   }

   @Test
   @DisplayName("FRKOMM-FR-03.2: PATCH data returns 204 on success")
   void should_return_204_on_patch_data_success() throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning(handlaggningId);
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
      var handlaggning = createHandlaggning(handlaggningId);
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

      verify(oulUppgiftService, atLeastOnce()).getCorrelationData(handlaggningId);
      verify(oulUppgiftService).updateUppgift(eq(handlaggningId), any());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.6: patchUppgiftskommentar returns 500 when correlation data not found")
   void should_return_500_on_correlation_data_not_found_failure_during_patch_uppgift_kommentar()
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchUppgiftskommentarRequest();
      requestBody.setKommentar("");

      Mockito.when(oulUppgiftService.getCorrelationData(handlaggningId)).thenReturn(null);

      var response = RestAssured.given()
            .body(requestBody)
            .patch("/api/test/uppgiftskommentar/" + handlaggningId)
            .then()
            .statusCode(500).extract().as(ErrorResponse.class);

      assertEquals(500, response.getCode());
      assertTrue(response.getMessage().matches(CORRELATION_DATA_READ_FAILED_PATTERN));

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

      verify(oulUppgiftService, atLeastOnce()).getCorrelationData(handlaggningId);
      verify(oulUppgiftService).updateUppgift(eq(handlaggningId), any());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.6: patchKommuniceringsutfall returns 500 when correlation data not found")
   void should_return_500_on_correlation_data_not_found_failure_during_patch_kommuniceringsutfall()
   {
      var handlaggningId = UUID.randomUUID();
      var requestBody = new PatchKommuniceringsutfallRequest();
      requestBody.setUtfall(Kommuniceringsutfall.KUND_ACCEPTERAT);

      Mockito.when(oulUppgiftService.getCorrelationData(handlaggningId)).thenReturn(null);

      var response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/kommuniceringsutfall/" + handlaggningId)
            .then()
            .statusCode(500).extract().as(ErrorResponse.class);

      assertEquals(500, response.getCode());
      assertTrue(response.getMessage().matches(CORRELATION_DATA_READ_FAILED_PATTERN));

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

   @Test
   @DisplayName("FRKOMM-FR-05.1: GET data returns 403 and does not read data when handlaggare is not assigned to uppgift")
   void should_return_403_on_get_data_when_not_assigned_to_uppgift() throws Exception
   {
      givenUnassignedHandlaggare();

      var response = RestAssured.given()
            .get("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(403).extract().as(ErrorResponse.class);

      assertEquals(403, response.getCode());
      assertTrue(response.getMessage().matches(NOT_ASSIGNED_PATTERN));
      verify(regelKommuniceringService, never()).read(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-05.1: PATCH data returns 403 and does not update handlaggning when handlaggare is not assigned to uppgift")
   void should_return_403_on_patch_data_when_not_assigned_to_uppgift() throws Exception
   {
      givenUnassignedHandlaggare();

      var response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("")
            .patch("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(403).extract().as(ErrorResponse.class);

      assertEquals(403, response.getCode());
      assertTrue(response.getMessage().matches(NOT_ASSIGNED_PATTERN));
      verify(regelKommuniceringService, never()).update(any(), any());
      verify(handlaggningAdapter, never()).updateHandlaggning(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-05.1: PATCH uppgift kommentar returns 403 and does not update uppgift when handlaggare is not assigned to uppgift")
   void should_return_403_on_patch_uppgift_kommentar_when_not_assigned_to_uppgift() throws Exception
   {
      givenUnassignedHandlaggare();
      var requestBody = new PatchUppgiftskommentarRequest();
      requestBody.setKommentar("Kommentar");

      var response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/uppgiftskommentar/" + UUID.randomUUID())
            .then()
            .statusCode(403).extract().as(ErrorResponse.class);

      assertEquals(403, response.getCode());
      assertTrue(response.getMessage().matches(NOT_ASSIGNED_PATTERN));
      verify(oulUppgiftService, never()).updateUppgift(any(), any());
   }

   @Test
   @DisplayName("FRKOMM-FR-05.1: PATCH kommuniceringsutfall returns 403 and does not update uppgift when handlaggare is not assigned to uppgift")
   void should_return_403_on_patch_kommuniceringsutfall_when_not_assigned_to_uppgift() throws Exception
   {
      givenUnassignedHandlaggare();
      var requestBody = new PatchKommuniceringsutfallRequest();
      requestBody.setUtfall(Kommuniceringsutfall.KUND_ACCEPTERAT);

      var response = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .patch("/api/test/kommuniceringsutfall/" + UUID.randomUUID())
            .then()
            .statusCode(403).extract().as(ErrorResponse.class);

      assertEquals(403, response.getCode());
      assertTrue(response.getMessage().matches(NOT_ASSIGNED_PATTERN));
      verify(oulUppgiftService, never()).updateUppgift(any(), any());
   }

   @Test
   @DisplayName("FRKOMM-FR-05.1: kommuniceringDone returns 403 when handlaggare is not assigned to uppgift")
   void should_return_403_on_done_when_not_assigned_to_uppgift() throws Exception
   {
      givenUnassignedHandlaggare();

      var response = RestAssured.given()
            .post("/api/test/" + UUID.randomUUID() + "/done")
            .then()
            .statusCode(403).extract().as(ErrorResponse.class);

      assertEquals(403, response.getCode());
      assertTrue(response.getMessage().matches(NOT_ASSIGNED_PATTERN));
      verifyNoInteractions(regelKommuniceringDoneHandler);
   }

   private void givenUnassignedHandlaggare() throws IdentityException
   {
      Mockito.when(identityAdapter.getIdentity(any()))
            .thenReturn(ImmutableIdtyp.builder().typId(ID_TYP).varde("19700101-0000").build());
   }
}
