package se.fk.rimfrost.framework.regel.kommunicering;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import se.fk.rimfrost.adapter.permissions.adapter.PermissionsAdapter;
import se.fk.rimfrost.adapter.permissions.adapter.PermissionsException;
import se.fk.rimfrost.framework.handlaggning.adapter.HandlaggningAdapter;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.Uppgift;
import se.fk.rimfrost.framework.handlaggning.model.Yrkande;
import se.fk.rimfrost.adapter.identity.exception.IdentityException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringService;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;
import se.fk.rimfrost.framework.regel.oul.logic.entity.OulCorrelationData;
import se.fk.rimfrost.framework.sid.adapter.SidAdapter;
import se.fk.rimfrost.framework.sid.exception.SidException;
import se.fk.rimfrost.adapter.identity.model.ImmutableIdtyp;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@QuarkusTest
class RegelKommuniceringControllerSidCheckTest extends AbstractRegelKommuniceringTestBase
{
   private static final String NO_SID_PERMISSION_PATTERN = "(?i).*does not have permission to access this uppgift.*";
   private static final String UNRESOLVED_IDENTITY_PATTERN = "(?i).*could not resolve user identity.*";

   @InjectMock
   HandlaggningAdapter handlaggningAdapter;

   @InjectMock
   SidAdapter sidAdapter;

   @InjectMock
   PermissionsAdapter permissionsAdapter;

   @InjectMock
   RegelKommuniceringService<String, String> regelKommuniceringService;

   @Test
   @DisplayName("FRKOMM-FR-04.3: GET returnerar 403 när SID detekteras och handläggaren saknar SID-rättigheter")
   void get_should_return_403_when_sid_detected_and_handlaggare_lacks_permission() throws Exception
   {
      var handlaggning = createHandlaggning();
      Mockito.when(handlaggningAdapter.readHandlaggning(any())).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(any())).thenReturn(true);
      givenHandlaggareIdentity("PERSONNR", "19901010-1234");
      Mockito.when(permissionsAdapter.hasSidPermission(any(), any())).thenReturn(false);

      var response = RestAssured.given()
            .get("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(403).extract().as(ErrorResponse.class);

      assertEquals(403, response.getCode());
      assertTrue(response.getMessage().matches(NO_SID_PERMISSION_PATTERN));

      verify(regelKommuniceringService, never()).read(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-07.4: GET returnerar inte informationsaccess när SID-kontrollen inte passerar")
   void get_should_not_return_informationsaccess_when_sid_check_fails() throws Exception
   {
      var handlaggning = createHandlaggning();
      Mockito.when(handlaggningAdapter.readHandlaggning(any())).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(any())).thenReturn(true);
      givenHandlaggareIdentity("PERSONNR", "19901010-1234");
      Mockito.when(permissionsAdapter.hasSidPermission(any(), any())).thenReturn(false);

      RestAssured.given()
            .get("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(403)
            .body(not(containsString("informationsaccess")));

      verify(regelKommuniceringService, never()).read(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-04.6: OUL-uppgiften unassignas innan 403 returneras när SID detekteras och handläggaren saknar rättigheter")
   void get_should_unassign_uppgift_before_403_when_sid_detected_and_no_permission() throws Exception
   {
      var handlaggning = createHandlaggning();
      Mockito.when(handlaggningAdapter.readHandlaggning(any())).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(any())).thenReturn(true);
      givenHandlaggareIdentity("PERSONNR", "19901010-1234");
      Mockito.when(permissionsAdapter.hasSidPermission(any(), any())).thenReturn(false);

      var response = RestAssured.given()
            .get("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(403).extract().as(ErrorResponse.class);

      assertEquals(403, response.getCode());
      assertTrue(response.getMessage().matches(NO_SID_PERMISSION_PATTERN));

      verify(oulUppgiftService).tryUnassignOulUppgift(eq(DEFAULT_UPPGIFT_ID));
   }

   @Test
   @DisplayName("FRKOMM-FR-04.9/04.10: read() anropas normalt när SID detekteras men handläggaren har SID-rättigheter")
   void get_should_proceed_when_sid_detected_but_handlaggare_has_permission() throws Exception
   {
      var handlaggning = createHandlaggning();
      Mockito.when(handlaggningAdapter.readHandlaggning(any())).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(any())).thenReturn(true);
      givenHandlaggareIdentity("PERSONNR", "19901010-1234");
      Mockito.when(permissionsAdapter.hasSidPermission(any(), any())).thenReturn(true);
      Mockito.when(regelKommuniceringService.read(any())).thenReturn("response");

      RestAssured.given()
            .get("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(200);

      verify(regelKommuniceringService).read(any());
      verify(oulUppgiftService, never()).tryUnassignOulUppgift(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-04.9: hasSidPermission anropas med handläggarens identitet från UserIdentityHolder när SID detekteras")
   void get_should_check_permission_with_handlaggare_identity_when_sid_detected() throws Exception
   {
      var handlaggning = createHandlaggning();
      Mockito.when(handlaggningAdapter.readHandlaggning(any())).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(any())).thenReturn(true);
      givenHandlaggareIdentity("PERSONNR", "19901010-1234");
      Mockito.when(permissionsAdapter.hasSidPermission(any(), any())).thenReturn(false);

      var response = RestAssured.given()
            .get("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(403).extract().as(ErrorResponse.class);

      assertEquals(403, response.getCode());
      assertTrue(response.getMessage().matches(NO_SID_PERMISSION_PATTERN));

      verify(permissionsAdapter).hasSidPermission(eq("PERSONNR"), eq("19901010-1234"));
   }

   @Test
   @DisplayName("FRKOMM-FR-04.7: Unassign hoppas över utan fel när inget uppgifts-ID finns lagrat")
   void get_should_skip_unassign_when_no_oul_uppgift_id_stored() throws Exception
   {
      var handlaggning = createHandlaggning();
      Mockito.when(handlaggningAdapter.readHandlaggning(any())).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(any())).thenReturn(true);
      givenHandlaggareIdentity("PERSONNR", "19901010-1234");
      Mockito.when(permissionsAdapter.hasSidPermission(any(), any())).thenReturn(false);
      givenStoredOulUppgiftId(null);

      var response = RestAssured.given()
            .get("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(403).extract().as(ErrorResponse.class);

      assertEquals(403, response.getCode());
      assertTrue(response.getMessage().matches(NO_SID_PERMISSION_PATTERN));

      verify(oulUppgiftService, never()).tryUnassignOulUppgift(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-04.8: Fel vid unassign loggas men påverkar inte HTTP 403-svaret")
   void get_should_still_return_403_when_unassign_fails() throws Exception
   {
      var handlaggning = createHandlaggning();
      Mockito.when(handlaggningAdapter.readHandlaggning(any())).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(any())).thenReturn(true);
      givenHandlaggareIdentity("PERSONNR", "19901010-1234");
      Mockito.when(permissionsAdapter.hasSidPermission(any(), any())).thenReturn(false);
      Mockito.doNothing().when(oulUppgiftService).tryUnassignOulUppgift(any());

      var response = RestAssured.given()
            .get("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(403).extract().as(ErrorResponse.class);

      assertEquals(403, response.getCode());
      assertTrue(response.getMessage().matches(NO_SID_PERMISSION_PATTERN));
   }

   @ParameterizedTest
   @EnumSource(PermissionsException.ErrorType.class)
   @DisplayName("FRKOMM-FR-03.12-15: Fel från behörighetstjänsten mappas till väldefinierade HTTP-statuskoder")
   void get_should_return_mapped_status_when_permissions_throws(PermissionsException.ErrorType errorType)
         throws Exception
   {
      var handlaggning = createHandlaggning();
      var exceptionMessage = "permissions error";
      Mockito.when(handlaggningAdapter.readHandlaggning(any())).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(any())).thenReturn(true);
      givenHandlaggareIdentity("PERSONNR", "19901010-1234");
      Mockito.doThrow(new PermissionsException(errorType, exceptionMessage))
            .when(permissionsAdapter).hasSidPermission(any(), any());

      var response = RestAssured.given()
            .get("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(expectedHttpStatus(errorType.name())).extract().as(ErrorResponse.class);

      assertEquals(expectedHttpStatus(errorType.name()), response.getCode());
      assertEquals(exceptionMessage, response.getMessage());
   }

   @ParameterizedTest
   @EnumSource(IdentityException.ErrorType.class)
   @DisplayName("FRKOMM-FR-06.1, FRKOMM-FR-03.12-15: Fel från identity-tjänsten mappas till väldefinierade HTTP-statuskoder")
   void get_should_return_mapped_status_when_identity_throws(IdentityException.ErrorType errorType) throws Exception
   {
      var handlaggning = createHandlaggning();
      var exceptionMessage = "identity error";
      Mockito.when(handlaggningAdapter.readHandlaggning(any())).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(any())).thenReturn(true);
      Mockito.doThrow(new IdentityException(errorType, exceptionMessage)).when(identityAdapter).getIdentity(any());

      var response = RestAssured.given()
            .get("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(expectedHttpStatus(errorType.name())).extract().as(ErrorResponse.class);

      assertEquals(expectedHttpStatus(errorType.name()), response.getCode());
      if (errorType == IdentityException.ErrorType.UNAUTHORIZED)
      {
         assertTrue(response.getMessage().matches(UNRESOLVED_IDENTITY_PATTERN));
      }
      else
      {
         assertEquals(exceptionMessage, response.getMessage());
      }
   }

   @ParameterizedTest
   @EnumSource(SidException.ErrorType.class)
   @DisplayName("FRKOMM-FR-03.12-15: Fel från SID-tjänsten mappas till väldefinierade HTTP-statuskoder")
   void get_should_return_mapped_status_when_sid_throws(SidException.ErrorType errorType) throws Exception
   {
      var handlaggning = createHandlaggning();
      var exceptionMessage = "sid error";
      Mockito.when(handlaggningAdapter.readHandlaggning(any())).thenReturn(handlaggning);
      Mockito.doThrow(new SidException(errorType, exceptionMessage)).when(sidAdapter).containsSid(any());

      var response = RestAssured.given()
            .get("/api/test/" + UUID.randomUUID())
            .then()
            .statusCode(expectedHttpStatus(errorType.name())).extract().as(ErrorResponse.class);

      assertEquals(expectedHttpStatus(errorType.name()), response.getCode());
      assertEquals(exceptionMessage, response.getMessage());
   }

   private void givenHandlaggareIdentity(String typId, String varde) throws IdentityException
   {
      Mockito.when(identityAdapter.getIdentity(any()))
            .thenReturn(ImmutableIdtyp.builder().typId(typId).varde(varde).build());
   }

   private void givenStoredOulUppgiftId(UUID oulUppgiftId)
   {
      var utforare = se.fk.rimfrost.framework.handlaggning.model.ImmutableIdtyp.builder().typId(ID_TYP).varde(ID_VARDE)
            .build();
      var uppgift = Mockito.mock(Uppgift.class);
      Mockito.when(uppgift.utforare()).thenReturn(utforare);
      var correlationData = Mockito.mock(OulCorrelationData.class);
      Mockito.when(correlationData.oulUppgiftId()).thenReturn(oulUppgiftId);
      Mockito.when(correlationData.uppgift()).thenReturn(uppgift);
      Mockito.when(oulUppgiftService.getCorrelationData(any())).thenReturn(correlationData);
   }

   private Handlaggning createHandlaggning()
   {
      var yrkande = Mockito.mock(Yrkande.class);
      Mockito.when(yrkande.rollerIYrkande()).thenReturn(List.of());
      var handlaggning = Mockito.mock(Handlaggning.class);
      Mockito.when(handlaggning.yrkande()).thenReturn(yrkande);
      return handlaggning;
   }

   private static int expectedHttpStatus(String errorType)
   {
      return switch(errorType){case"NOT_FOUND"->404;case"BAD_REQUEST"->400;case"SERVICE_UNAVAILABLE"->503;case"UNAUTHORIZED"->401;default->500;};
   }
}
