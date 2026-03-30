package com.gurch.sandbox.esign;

import java.io.InputStream;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Internal command representing a multipart e-sign upload request. */
@Value
@Builder
public class EsignCreateEnvelopeCommand {
  String subject;
  String message;
  EsignDeliveryMode deliveryMode;
  boolean remindersEnabled;
  Integer reminderIntervalHours;
  String originalFilename;
  Long contentSize;
  InputStream contentStream;
  List<EsignCreateEnvelopeRequest.SignerInput> signers;
}
