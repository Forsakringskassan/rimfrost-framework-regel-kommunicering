package se.fk.rimfrost.framework.regel.kommunicering;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import se.fk.rimfrost.framework.handlaggning.adapter.HandlaggningAdapter;
import se.fk.rimfrost.framework.handlaggning.exception.HandlaggningException;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.HandlaggningUpdate;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableRegelutfall;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableUppgift;
import se.fk.rimfrost.framework.handlaggning.model.Yrkande;
import se.fk.rimfrost.framework.regel.RegelKafkaConnector;
import se.fk.rimfrost.framework.regel.RegelTestData;
import se.fk.rimfrost.framework.regel.Utfall;
import se.fk.rimfrost.framework.regel.error.RegelFelkod;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringRequestHandler;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringService;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.CorrelationDataReadException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.EndOulUppgiftException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.HandlaggningReadException;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.UppgiftIncompleteException;
import se.fk.rimfrost.framework.regel.oul.logic.entity.ImmutableOulCorrelationData;
import se.fk.rimfrost.framework.regel.oul.logic.entity.OulUppgiftSpec;
import se.fk.rimfrost.framework.regel.oul.logic.exception.OulServiceException;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
class RegelKommuniceringRequestHandlerTest extends AbstractRegelKommuniceringTestBase
{
   @InjectMock
   HandlaggningAdapter handlaggningAdapter;

   @InjectMock
   RegelKommuniceringService<?, ?> regelKommuniceringService;

   @Inject
   RegelKommuniceringRequestHandler regelKommuniceringRequestHandler;

   private Handlaggning createHandlaggning()
   {
      var yrkande = Mockito.mock(Yrkande.class);
      var handlaggning = Mockito.mock(Handlaggning.class);

      Mockito.when(yrkande.ingangtypId()).thenReturn("b1cb6133-11af-4a30-9180-5ea8104a74c7");
      Mockito.when(handlaggning.yrkande()).thenReturn(yrkande);
      Mockito.when(handlaggning.id()).thenReturn(UUID.randomUUID());
      Mockito.when(handlaggning.version()).thenReturn(1);
      Mockito.when(handlaggning.skapadTS()).thenReturn(OffsetDateTime.now());
      Mockito.when(handlaggning.avslutadTS()).thenReturn(OffsetDateTime.now());
      Mockito.when(handlaggning.handlaggningspecifikationId()).thenReturn(UUID.randomUUID());

      return handlaggning;
   }

   @Test
   @DisplayName("FRKOMM-FR-02.1 & FRKOMM-FR-05.1: OUL uppgift is created, without processvyUrl when the regel request has none")
   void should_create_oul_task() throws Exception
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);

      var captor = ArgumentCaptor.forClass(OulUppgiftSpec.class);
      Mockito.doNothing().when(oulUppgiftService).createOulUppgift(captor.capture());

      regelKafkaConnector.sendRegelRequest(handlaggningId.toString(), responseTopic);

      verify(handlaggningAdapter, timeout(5000)).readHandlaggning(handlaggningId);
      verify(oulUppgiftService, timeout(5000)).createOulUppgift(Mockito.any());
      var oulUppgiftSpec = captor.getValue();

      assertEquals(handlaggningId, oulUppgiftSpec.handlaggningId());
      assertNotNull(oulUppgiftSpec.aktivitetId());
      assertNotNull(oulUppgiftSpec.handlaggning());
      assertEquals(responseTopic, oulUppgiftSpec.replyTo());
      assertNotNull(oulUppgiftSpec.cloudEventData());
      assertNotNull(oulUppgiftSpec.cloudEventAttributes());
      assertEquals("TestUppgiftNamn", oulUppgiftSpec.regel());
      assertEquals("TestUppgiftBeskrivning", oulUppgiftSpec.beskrivning());
      assertEquals("C", oulUppgiftSpec.verksamhetslogik());
      assertEquals("ANSVARIG_HANDLAGGARE", oulUppgiftSpec.roll());
      assertEquals("/regel/kommunicering", oulUppgiftSpec.url());
      assertNotNull(oulUppgiftSpec.erbjudande());
      assertEquals(UUID.fromString("a42ffaed-2f20-47e8-8499-f2f79ae2f45f"), oulUppgiftSpec.uppgiftSpecifikationId());
      assertEquals(1, oulUppgiftSpec.uppgiftSpecifikationVersion());
      assertNull(oulUppgiftSpec.processvyUrl());
   }

   @Test
   @DisplayName("FRKOMM-FR-05.1: OUL uppgift is created with processvyUrl from the regel request")
   void should_create_oul_task_with_processvy_url() throws Exception
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);

      var captor = ArgumentCaptor.forClass(OulUppgiftSpec.class);
      Mockito.doNothing().when(oulUppgiftService).createOulUppgift(captor.capture());

      var payload = RegelTestData.newRegelRequestMessagePayload(handlaggningId.toString(), responseTopic);
      payload.getData().setProcessvyUrl("/process/vah");
      inMemoryConnector.source(RegelKafkaConnector.regelRequestsChannel).send(payload);

      verify(oulUppgiftService, timeout(5000)).createOulUppgift(Mockito.any());
      var oulUppgiftSpec = captor.getValue();

      assertEquals(handlaggningId, oulUppgiftSpec.handlaggningId());
      assertEquals(UUID.fromString(payload.getData().getAktivitetId()), oulUppgiftSpec.aktivitetId());
      assertEquals("/process/vah", oulUppgiftSpec.processvyUrl());
   }

   @Test
   @DisplayName("FRKOMM-FR-02.3: Kafka error is sent with error code RIMFROST_OTHER on runtime exception during handlaggning read")
   void should_send_error_response_on_handlaggning_read_failure_at_regel_request() throws Exception
   {
      var handlaggningId = UUID.randomUUID();

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenThrow(new RuntimeException());

      regelKafkaConnector.sendRegelRequest(handlaggningId.toString(), responseTopic);
      var response = regelKafkaConnector.waitForRegelResponse();

      assertEquals(Utfall.ERROR, response.getData().getUtfall());
      assertEquals(RegelFelkod.RIMFROST_OTHER, response.getData().getError().getFelkod());
      verify(handlaggningAdapter).readHandlaggning(handlaggningId);
      verifyNoInteractions(regelKommuniceringService);
      verifyNoInteractions(oulUppgiftService);
   }

   @Test
   @DisplayName("FRKOMM-FR-02.2: Kafka error is sent with error code RIMFROST_OTHER on OUL exception")
   void should_send_error_response_on_oul_uppgift_service_exception_at_regel_request() throws Exception
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);
      Mockito.doThrow(new RuntimeException()).when(oulUppgiftService).createOulUppgift(Mockito.any());

      regelKafkaConnector.sendRegelRequest(handlaggningId.toString(), responseTopic);
      var response = regelKafkaConnector.waitForRegelResponse();

      assertEquals(Utfall.ERROR, response.getData().getUtfall());
      assertEquals(RegelFelkod.RIMFROST_OTHER, response.getData().getError().getFelkod());
      verify(handlaggningAdapter).readHandlaggning(handlaggningId);
   }

   @Test
   @DisplayName("FRKOMM-FR-01.4: Kafka response with utfall from uppgift is sent on successful kommunicering")
   void should_send_response_on_successful_done_handling() throws Exception
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);
      Mockito.doNothing().when(oulUppgiftService).endOulUppgift(eq(DEFAULT_UPPGIFT_ID), Mockito.any());
      Mockito.doNothing().when(oulUppgiftService).cleanupCorrelation(handlaggningId);
      Mockito.when(handlaggningAdapter.updateHandlaggning(Mockito.any())).thenReturn(Mockito.mock(HandlaggningUpdate.class));

      regelKommuniceringRequestHandler.handleKommuniceringDone(handlaggningId);
      var response = regelKafkaConnector.waitForRegelResponse();

      assertEquals(Utfall.JA, response.getData().getUtfall());
      verify(handlaggningAdapter).readHandlaggning(handlaggningId);
      verify(oulUppgiftService).endOulUppgift(eq(DEFAULT_UPPGIFT_ID), Mockito.any());
      verify(oulUppgiftService).cleanupCorrelation(handlaggningId);
      verify(handlaggningAdapter).updateHandlaggning(Mockito.any());
   }

   @Test
   @DisplayName("FRKOMM-FR-03.5: UppgiftIncompleteException is thrown if regelutfall is not set")
   void should_throw_uppgift_incomplete_exception_on_regelutfall_not_set() throws Exception
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();

      var correlationData = createDefaultOulCorrelationData();

      var updatedUppgift = ImmutableUppgift.builder()
            .from(correlationData.uppgift())
            .regelutfall(null)
            .build();

      var updatedCorrelationData = ImmutableOulCorrelationData.builder()
            .from(correlationData)
            .uppgift(updatedUppgift)
            .build();

      Mockito.when(oulUppgiftService.getCorrelationData(Mockito.any())).thenReturn(updatedCorrelationData);
      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);

      assertThrows(UppgiftIncompleteException.class,
            () -> regelKommuniceringRequestHandler.handleKommuniceringDone(handlaggningId));
   }

   @Test
   @DisplayName("FRKOMM-FR-03.6: CorrelationDataReadException is thrown if correlation data is missing")
   void should_throw_correlation_data_read_exception_on_missing_data_at_done_handling() throws Exception
   {
      var handlaggningId = UUID.randomUUID();

      Mockito.when(oulUppgiftService.getCorrelationData(handlaggningId)).thenReturn(null);

      assertThrows(CorrelationDataReadException.class,
            () -> regelKommuniceringRequestHandler.handleKommuniceringDone(handlaggningId));
   }

   @Test
   @DisplayName("HandlaggningReadException is thrown on failure to read handlaggning")
   void should_throw_handlaggning_read_exception_on_handlaggning_read_failure_at_done_handling() throws Exception
   {
      var handlaggningId = UUID.randomUUID();

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId))
            .thenThrow(new HandlaggningException(HandlaggningException.ErrorType.UNEXPECTED_ERROR, ""));

      assertThrows(HandlaggningReadException.class,
            () -> regelKommuniceringRequestHandler.handleKommuniceringDone(handlaggningId));
   }

   @Test
   @DisplayName("EndOulUppgiftException is thrown on failure to end oul uppgift")
   void should_throw_end_oul_uppgift_exception_on_oul_uppgift_end_failure_at_done_handling() throws Exception
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);
      Mockito.doThrow(new OulServiceException(OulServiceException.ErrorType.UNEXPECTED_ERROR, "")).when(oulUppgiftService)
            .endOulUppgift(eq(DEFAULT_UPPGIFT_ID), Mockito.any());

      assertThrows(EndOulUppgiftException.class, () -> regelKommuniceringRequestHandler.handleKommuniceringDone(handlaggningId));
   }

   @Test
   @DisplayName("FRKOMM-FR-03.8: 404 end fault is treated as 200 at end oul uppgift")
   void should_not_throw_end_oul_uppgift_exception_on_oul_uppgift_end_failure_with_404_at_done_handling() throws Exception
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);
      Mockito.doThrow(new OulServiceException(OulServiceException.ErrorType.NOT_FOUND, "")).when(oulUppgiftService)
            .endOulUppgift(eq(DEFAULT_UPPGIFT_ID), Mockito.any());

      regelKommuniceringRequestHandler.handleKommuniceringDone(handlaggningId);

      var response = regelKafkaConnector.waitForRegelResponse();
      assertEquals(Utfall.JA, response.getData().getUtfall());
   }

   @Test
   @DisplayName("Handlaggning update failure does not throw exception")
   void should_not_throw_on_handlaggning_update_failure_at_done_handling() throws Exception
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();

      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);
      Mockito.doNothing().when(oulUppgiftService).endOulUppgift(eq(DEFAULT_UPPGIFT_ID), Mockito.any());
      Mockito.doNothing().when(oulUppgiftService).cleanupCorrelation(handlaggningId);
      Mockito.when(handlaggningAdapter.updateHandlaggning(Mockito.any())).thenThrow(new RuntimeException());

      regelKommuniceringRequestHandler.handleKommuniceringDone(handlaggningId);
      var response = regelKafkaConnector.waitForRegelResponse();

      assertEquals(Utfall.JA, response.getData().getUtfall());
      verify(handlaggningAdapter).readHandlaggning(handlaggningId);
      verify(oulUppgiftService).endOulUppgift(eq(DEFAULT_UPPGIFT_ID), Mockito.any());
      verify(oulUppgiftService).cleanupCorrelation(handlaggningId);
   }

   @Test
   @DisplayName("IllegalArgumentException is thrown if regelutfall is set to an unknown value")
   void should_throw_illegal_argument_exception_on_unknown_regelutfall_value() throws HandlaggningException
   {
      var handlaggningId = UUID.randomUUID();
      var handlaggning = createHandlaggning();

      var correlationData = createDefaultOulCorrelationData();

      var updatedUppgift = ImmutableUppgift.builder()
            .from(correlationData.uppgift())
            .regelutfall(ImmutableRegelutfall.builder().varde("test").build())
            .build();

      var updatedCorrelationData = ImmutableOulCorrelationData.builder()
            .from(correlationData)
            .uppgift(updatedUppgift)
            .build();

      Mockito.when(oulUppgiftService.getCorrelationData(Mockito.any())).thenReturn(updatedCorrelationData);
      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggningId)).thenReturn(handlaggning);

      assertThrows(IllegalArgumentException.class,
            () -> regelKommuniceringRequestHandler.handleKommuniceringDone(handlaggningId));
   }
}
