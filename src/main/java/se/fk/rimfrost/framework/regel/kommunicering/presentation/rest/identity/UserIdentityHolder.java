package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.identity;

import jakarta.enterprise.context.RequestScoped;
import se.fk.rimfrost.framework.regel.kommunicering.logic.entity.Idtyp;

/**
 * Container class holding a user identity.
 */
@RequestScoped
public class UserIdentityHolder
{
   private Idtyp identity = null;

   public Idtyp getIdentity()
   {
      return identity;
   }

   public void setIdentity(Idtyp identity)
   {
      this.identity = identity;
   }
}
