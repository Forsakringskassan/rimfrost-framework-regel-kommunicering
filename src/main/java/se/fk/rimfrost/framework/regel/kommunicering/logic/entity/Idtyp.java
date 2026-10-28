package se.fk.rimfrost.framework.regel.kommunicering.logic.entity;

import org.immutables.value.Value;

@Value.Immutable
public interface Idtyp
{
   String typId();

   String varde();
}
