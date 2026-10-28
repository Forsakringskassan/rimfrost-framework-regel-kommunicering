package se.fk.rimfrost.framework.regel.kommunicering;

import io.quarkus.arc.Arc;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.SkipInject;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.fk.rimfrost.adapter.permissions.adapter.PermissionsAdapter;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.RollIYrkande;
import se.fk.rimfrost.framework.handlaggning.model.Uppgift;
import se.fk.rimfrost.framework.handlaggning.model.Yrkande;
import se.fk.rimfrost.framework.regel.kommunicering.logic.entity.ImmutableIdtyp;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.CorrelationDataReadException;
import se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception.AccessDeniedException;
import se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception.UnauthorizedException;
import se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.guard.RegelAccessGuard;
import se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.identity.UserIdentityHolder;
import se.fk.rimfrost.framework.regel.oul.logic.OulUppgiftService;
import se.fk.rimfrost.framework.regel.oul.logic.entity.OulCorrelationData;
import se.fk.rimfrost.framework.sid.adapter.SidAdapter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest(
{
      RegelAccessGuard.class, UserIdentityHolder.class
})
class RegelKommuniceringAccessGuardTest
{
   private static final String ID_TYP = "PERSONNR";
   private static final String ID_VARDE = "19901010-1234";
   private static final String NOT_ASSIGNED_PATTERN = "(?i).*not assigned to uppgift.*";
   private static final String UNRESOLVED_IDENTITY_PATTERN = "(?i).*could not resolve user identity.*";

   @Inject
   RegelAccessGuard guard;

   @Inject
   UserIdentityHolder userIdentityHolder;

   @InjectMock
   OulUppgiftService oulUppgiftService;

   @InjectMock
   SidAdapter sidAdapter;

   @InjectMock
   PermissionsAdapter permissionsAdapter;

   @Test
   @DisplayName("FRKOMM-FR-05.5: UnauthorizedException kastas när ingen handläggaridentitet finns i UserIdentityHolder")
   void verifyAccess_should_throw_unauthorized_when_user_identity_missing()
   {
      var handlaggning = createHandlaggning(List.of());

      var ex = assertThrows(UnauthorizedException.class, () -> guard.verifyAccess(handlaggning));

      assertTrue(ex.getMessage().matches(UNRESOLVED_IDENTITY_PATTERN));
      verify(oulUppgiftService, never()).getCorrelationData(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-05.5: UnauthorizedException kastas när UserIdentityHolder inte är tillgänglig")
   void verifyAccess_should_throw_unauthorized_when_request_context_not_active()
   {
      var handlaggning = createHandlaggning(List.of());
      givenHandlaggareIdentity(ID_TYP, ID_VARDE);
      var requestContext = Arc.container().requestContext();
      requestContext.deactivate();
      try
      {
         var ex = assertThrows(UnauthorizedException.class, () -> guard.verifyAccess(handlaggning));

         assertTrue(ex.getMessage().matches(UNRESOLVED_IDENTITY_PATTERN));
         verify(oulUppgiftService, never()).getCorrelationData(any());
      }
      finally
      {
         requestContext.activate();
      }
   }

   @Test
   @DisplayName("FRKOMM-FR-03.6: CorrelationDataReadException kastas när korrelationsdata saknas")
   void verifyAccess_should_throw_correlation_data_read_exception_when_correlation_data_missing()
   {
      var handlaggning = createHandlaggning(List.of());
      givenHandlaggareIdentity(ID_TYP, ID_VARDE);
      when(oulUppgiftService.getCorrelationData(any())).thenReturn(null);

      assertThrows(CorrelationDataReadException.class, () -> guard.verifyAccess(handlaggning));

      verifyNoInteractions(sidAdapter, permissionsAdapter);
   }

   @Test
   @DisplayName("FRKOMM-FR-05.1/05.2: AccessDeniedException kastas när uppgiften inte är tilldelad någon handläggare")
   void verifyAccess_should_throw_access_denied_when_utforare_is_null()
   {
      var handlaggning = createHandlaggning(List.of());
      givenHandlaggareIdentity(ID_TYP, ID_VARDE);
      var uppgift = mock(Uppgift.class);
      when(uppgift.utforare()).thenReturn(null);
      givenCorrelationData(uppgift);

      var ex = assertThrows(AccessDeniedException.class, () -> guard.verifyAccess(handlaggning));

      assertTrue(ex.getMessage().matches(NOT_ASSIGNED_PATTERN));
   }

   @ParameterizedTest
   @CsvSource(
   {
         "PERSONNR, 19901010-9999",
         "SAMORDNINGSNR, 19901010-1234"
   })
   @DisplayName("FRKOMM-FR-05.1/05.2/05.4: AccessDeniedException kastas och SID kontrolleras inte när uppgiften är tilldelad en annan handläggare")
   void verifyAccess_should_throw_access_denied_without_sid_check_when_assigned_to_other_handlaggare(
         @SkipInject String utforareTypId, @SkipInject String utforareVarde) throws Exception
   {
      var handlaggning = createHandlaggning(List.of());
      givenHandlaggareIdentity(ID_TYP, ID_VARDE);
      givenCorrelationData(createTilldeladUppgift(utforareTypId, utforareVarde));

      var ex = assertThrows(AccessDeniedException.class, () -> guard.verifyAccess(handlaggning));

      assertTrue(ex.getMessage().matches(NOT_ASSIGNED_PATTERN));
      verify(sidAdapter, never()).containsSid(any());
   }

   @Test
   @DisplayName("FRKOMM-FR-04.2: Individer i yrkandet skickas till SID-tjänsten")
   void verifyAccess_should_pass_mapped_individer_to_sid_adapter() throws Exception
   {
      var handlaggning = createHandlaggning(List.of(
            createRollIYrkande("PERSONNR", "19800101-1111"),
            createRollIYrkande("SAMORDNINGSNR", "19800161-2222")));
      givenHandlaggareIdentity(ID_TYP, ID_VARDE);
      givenCorrelationData(createTilldeladUppgift(ID_TYP, ID_VARDE));

      guard.verifyAccess(handlaggning);

      verify(sidAdapter).containsSid(List.of(
            sidIdtyp("PERSONNR", "19800101-1111"),
            sidIdtyp("SAMORDNINGSNR", "19800161-2222")));
   }

   private Handlaggning createHandlaggning(List<RollIYrkande> roller)
   {
      var yrkande = mock(Yrkande.class);
      when(yrkande.rollerIYrkande()).thenReturn(roller);
      var handlaggning = mock(Handlaggning.class);
      when(handlaggning.id()).thenReturn(UUID.randomUUID());
      when(handlaggning.yrkande()).thenReturn(yrkande);
      return handlaggning;
   }

   private RollIYrkande createRollIYrkande(String typId, String varde)
   {
      var individ = mock(se.fk.rimfrost.framework.handlaggning.model.Idtyp.class);
      when(individ.typId()).thenReturn(typId);
      when(individ.varde()).thenReturn(varde);
      var roll = mock(RollIYrkande.class);
      when(roll.individ()).thenReturn(individ);
      return roll;
   }

   private Uppgift createTilldeladUppgift(String typId, String varde)
   {
      var utforare = mock(se.fk.rimfrost.framework.handlaggning.model.Idtyp.class);
      when(utforare.typId()).thenReturn(typId);
      when(utforare.varde()).thenReturn(varde);
      var uppgift = mock(Uppgift.class);
      when(uppgift.utforare()).thenReturn(utforare);
      return uppgift;
   }

   private void givenCorrelationData(Uppgift uppgift)
   {
      var correlationData = mock(OulCorrelationData.class);
      when(correlationData.oulUppgiftId()).thenReturn(UUID.randomUUID());
      when(correlationData.uppgift()).thenReturn(uppgift);
      when(oulUppgiftService.getCorrelationData(any())).thenReturn(correlationData);
   }

   private void givenHandlaggareIdentity(String typId, String varde)
   {
      userIdentityHolder.setIdentity(ImmutableIdtyp.builder().typId(typId).varde(varde).build());
   }

   private static se.fk.rimfrost.framework.sid.model.Idtyp sidIdtyp(String typId, String varde)
   {
      return se.fk.rimfrost.framework.sid.model.ImmutableIdtyp.builder().typId(typId).varde(varde).build();
   }
}
