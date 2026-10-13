package se.fk.rimfrost.framework.regel.kommunicering.logic;

import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.HandlaggningUpdate;

public interface RegelKommuniceringService<T, Y>
{
   /**
    * Returns the data the handläggare needs to register the sökande's svar.
    *
    * @param handlaggning the current handlaggning
    * @return response body for the kommunicering GET endpoint
    */
   T read(Handlaggning handlaggning);

   /**
    * Applies the svar to the handlaggning and returns an update ready to be persisted.
    *
    * @param handlaggning the current handlaggning
    * @param request      the handläggare's registered svar
    * @return the handlaggning update to persist
    */
   HandlaggningUpdate update(Handlaggning handlaggning, Y request);
}
