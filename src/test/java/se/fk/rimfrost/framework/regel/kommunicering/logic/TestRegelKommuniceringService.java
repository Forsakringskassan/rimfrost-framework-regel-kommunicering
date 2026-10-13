package se.fk.rimfrost.framework.regel.kommunicering.logic;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.HandlaggningUpdate;

/** Stub CDI bean satisfying the RegelKommuniceringService injection point; replaced by @InjectMock at test time. */
@ApplicationScoped
@DefaultBean
public class TestRegelKommuniceringService implements RegelKommuniceringService<String, String>
{

   @Override
   public String read(Handlaggning handlaggning)
   {
      throw new UnsupportedOperationException();
   }

   @Override
   public HandlaggningUpdate update(Handlaggning handlaggning, String data)
   {
      throw new UnsupportedOperationException();
   }
}
