package com.gurch.sandbox.esign;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Summary response for an ad hoc e-sign envelope reconciliation run.
 *
 * @param activeEnvelopeCount number of active local envelopes considered for reconciliation
 * @param providerEnvelopeCount number of envelopes returned by the provider for the requested batch
 * @param reconciledEnvelopeCount number of local envelopes updated during reconciliation
 * @param reconciledEnvelopeIds internal ids of envelopes whose local state changed
 */
@Schema(description = "Summary response for an ad hoc e-sign envelope reconciliation run")
public record EsignReconcileResponse(
    @Schema(
            description = "Number of active local envelopes considered for reconciliation",
            example = "3")
        int activeEnvelopeCount,
    @Schema(
            description = "Number of envelopes returned by the provider for the requested batch",
            example = "3")
        int providerEnvelopeCount,
    @Schema(description = "Number of local envelopes updated during reconciliation", example = "2")
        int reconciledEnvelopeCount,
    @Schema(
            description = "Internal ids of envelopes whose local state changed",
            example = "[42, 43]")
        List<Long> reconciledEnvelopeIds) {

  /**
   * Creates a response with an immutable copy of the reconciled envelope ids.
   *
   * @param activeEnvelopeCount number of active local envelopes considered for reconciliation
   * @param providerEnvelopeCount number of envelopes returned by the provider for the requested
   *     batch
   * @param reconciledEnvelopeCount number of local envelopes updated during reconciliation
   * @param reconciledEnvelopeIds internal ids of envelopes whose local state changed
   */
  public EsignReconcileResponse {
    reconciledEnvelopeIds =
        reconciledEnvelopeIds == null ? List.of() : List.copyOf(reconciledEnvelopeIds);
  }
}
