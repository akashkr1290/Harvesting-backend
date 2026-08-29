package com.harvestflow.api.service.image;

import java.nio.file.Path;

/**
 * The single seam through which every produce-image authenticity check
 * flows (SOW Phase 4, Part B — "if the architecture already supports an
 * external AI service, integrate through the existing service abstraction;
 * keep provider-specific code isolated").
 *
 * <p>This project had no prior AI-service abstraction to reuse, so this
 * interface <i>is</i> that abstraction going forward. {@link com.harvestflow.api.service.ProduceImageService}
 * (the caller) only ever depends on this interface, never on a concrete
 * implementation — swapping the default {@link HeuristicImageAuthenticityService}
 * for a real ML/vendor-backed implementation in a later phase means adding
 * one new {@code @Service} class and flipping
 * {@code harvestflow.image-verification.provider} in configuration; nothing
 * else in the codebase changes.
 *
 * <p>Implementations must never claim certainty — see {@link AuthenticityResult}.
 */
public interface ImageAuthenticityService {

    /**
     * Runs an authenticity/manipulation check against an already-stored image.
     *
     * @param imagePath   absolute path to the stored file on disk
     * @param contentType the declared MIME type (already validated by the caller)
     * @return a risk-based result; implementations should return
     *         {@link AuthenticityResult#failed} rather than throwing, so a
     *         checker outage degrades to "needs manual review" instead of
     *         a 500 on the upload endpoint.
     */
    AuthenticityResult analyze(Path imagePath, String contentType);

    /** Short identifier for this implementation, stored on every result it produces (e.g. "heuristic-v1"). */
    String verifierId();
}
