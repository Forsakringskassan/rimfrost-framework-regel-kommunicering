package se.fk.rimfrost.framework.regel.kommunicering.logic.dto;

import java.util.UUID;

/**
 * Data the framework adds to {@code GET /{handlaggningId}} so that a BFF can publish an
 * informationsaccess event without each rule exposing it in its own contract (FRKOMM-FR-07).
 *
 * @param uppgiftId the OUL uppgift the handläggning is read through
 * @param regelId the rule's id from {@code config.yaml}
 * @param regelVersion the rule's version from {@code config.yaml}
 * @param anvandare the inloggad handläggare's identity
 */
public record Informationsaccess(UUID uppgiftId,UUID regelId,String regelVersion,Anvandare anvandare){}
