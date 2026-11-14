package bo.edu.uagrm.inf513.grupo09sa.email;

import java.io.IOException;

/** Approval applies to these exact bytes, never to a sender-supplied filesystem path. */
@FunctionalInterface
public interface InspectorPdf {
    boolean aprobado(AdjuntoPdf archivo) throws IOException, InterruptedException;
}
