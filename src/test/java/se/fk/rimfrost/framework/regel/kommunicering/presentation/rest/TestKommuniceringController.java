package se.fk.rimfrost.framework.regel.kommunicering.presentation.rest;

import io.quarkus.arc.DefaultBean;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Minimal concrete subclass registered as a JAX-RS resource. */
@Path("/api/test")
@DefaultBean
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TestKommuniceringController extends RegelKommuniceringController<String, String>
{
}
