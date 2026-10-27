package se.fk.rimfrost.framework.regel.kommunicering.logic.dto;

/**
 * Wraps the rule-specific response from {@code GET /{handlaggningId}} together with the data
 * a BFF needs to publish an informationsaccess event (FRKOMM-FR-07).
 *
 * @param data the rule-specific response returned by {@code read()}
 * @param informationsaccess the informationsaccess data for the read
 * @param <T> the rule-specific response type
 */
public record RegelKommuniceringReadResponse<T>(T data, Informationsaccess informationsaccess)
{
}
