package se.fk.rimfrost.framework.regel.kommunicering;

import io.quarkus.test.InjectMock;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableRegelutfall;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableUppgift;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableUppgiftSpecifikation;
import se.fk.rimfrost.framework.regel.RegelTestBase;
import se.fk.rimfrost.framework.regel.Utfall;
import se.fk.rimfrost.framework.regel.logic.entity.ImmutableCloudEventData;
import se.fk.rimfrost.framework.regel.oul.logic.OulUppgiftService;
import se.fk.rimfrost.framework.regel.oul.logic.entity.ImmutableOulCorrelationData;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Base class for kommunicering regel tests.
 *
 * <p>This test base provides:
 * <ul>
 *   <li>Preconfigured Kafka in-memory test support</li>
 *   <li>A single {@link InjectMock} for {@link OulUppgiftService} with default stubs — the
 *       correct public API boundary between this framework and {@code rimfrost-framework-regel-oul}</li>
 *   <li>Common setup and cleanup logic for test isolation</li>
 * </ul>
 *
 * <p><b>Mock boundary:</b>
 * {@link OulUppgiftService} is mocked at the {@link InjectMock} level. This is the public
 * contract this framework depends on. The storage and persistence internals of
 * {@code rimfrost-framework-regel-oul} are tested in that module against a real PostgreSQL
 * devservices container — there is no value in re-testing them here.
 *
 * <p><b>Default stubs (configured before each test):</b>
 * <ul>
 *   <li>{@code createOulUppgift(any())} — returns a pre-built {@code OperativUppgift}</li>
 *   <li>{@code getCorrelationData(any())} — returns a pre-built {@code OulCorrelationData}
 *       with the configured response topic as {@code replyTopic}</li>
 *   <li>{@code endOulUppgift} and {@code cleanupCorrelation} — Mockito void defaults (do nothing)</li>
 * </ul>
 *
 * <p>Tests that need to simulate OUL failures override these defaults with
 * {@code thenThrow(OulException)} for the relevant method.
 *
 * <p><b>Test isolation:</b>
 * Each test resets:
 * <ul>
 *   <li>In-memory Kafka state</li>
 *   <li>All {@link OulUppgiftService} stubs</li>
 * </ul>
 */
public abstract class AbstractRegelKommuniceringTestBase extends RegelTestBase
{
   protected static final UUID DEFAULT_UPPGIFT_ID = UUID.randomUUID();

   protected static final String PLANERAD_STATUS = "PLANERAD";

   @ConfigProperty(name = "mp.messaging.outgoing.regel-responses.topic")
   protected String responseTopic;

   @InjectMock
   OulUppgiftService oulUppgiftService;

   /**
    * Resets external system state and reconfigures default stubs before each test.
    *
    * <p>This includes:
    * <ul>
    *   <li>Initializing the OUL Kafka connector if needed</li>
    *   <li>Clearing all in-memory Kafka messages to ensure test isolation</li>
    *   <li>Configuring default {@link OulUppgiftService} stubs</li>
    * </ul>
    *
    * <p><b>Important:</b> The {@link InMemoryConnector} may retain state across tests,
    * even when recreated, so explicit clearing is required.
    */
   @SuppressWarnings("JavadocReference")
   @BeforeEach
   void regelKommuniceringResetState() throws Exception
   {
      super.regelResetState();
      if (inMemoryConnector == null)
      {
         throw new IllegalStateException("inMemoryConnector not injected");
      }

      configureOulUppgiftServiceMocks();
   }

   /**
    * Configures default stubs on {@link OulUppgiftService} before each test.
    *
    * <p>Quarkus resets all {@link InjectMock} mocks before each test, so stubbing must be
    * re-established here. Subclasses that need to simulate OUL failures override individual
    * stubs with {@code thenThrow(OulException)} after calling {@code super.regelKommuniceringResetState()}.
    */
   private void configureOulUppgiftServiceMocks() throws Exception
   {
      Mockito.when(oulUppgiftService.getCorrelationData(Mockito.any()))
            .thenReturn(createDefaultOulCorrelationData());
   }

   protected se.fk.rimfrost.framework.regel.oul.logic.entity.OulCorrelationData createDefaultOulCorrelationData()
   {
      var cloudEventData = ImmutableCloudEventData.builder()
            .id(UUID.randomUUID())
            .kogitorootprociid(UUID.randomUUID())
            .kogitoparentprociid(UUID.randomUUID())
            .kogitoprocinstanceid(UUID.randomUUID())
            .kogitorootprocid("test-root-proc")
            .kogitoprocid("test-proc")
            .kogitoprocist("test-proc-ist")
            .kogitoprocversion("1.0")
            .type(responseTopic)
            .source("test-source")
            .build();
      var uppgiftSpecifikation = ImmutableUppgiftSpecifikation.builder()
            .id(DEFAULT_UPPGIFT_ID)
            .version(1)
            .build();
      var uppgift = ImmutableUppgift.builder()
            .id(DEFAULT_UPPGIFT_ID)
            .version(1)
            .skapadTS(OffsetDateTime.now())
            .aktivitetId(UUID.randomUUID())
            .fSSAinformation("")
            .uppgiftSpecifikation(uppgiftSpecifikation)
            .uppgiftStatus(PLANERAD_STATUS)
            .regelutfall(ImmutableRegelutfall.builder().varde(Utfall.JA.name()).build())
            .build();
      return ImmutableOulCorrelationData.builder()
            .oulUppgiftId(DEFAULT_UPPGIFT_ID)
            .uppgift(uppgift)
            .replyTopic(responseTopic)
            .cloudEventData(cloudEventData)
            .build();
   }
}
