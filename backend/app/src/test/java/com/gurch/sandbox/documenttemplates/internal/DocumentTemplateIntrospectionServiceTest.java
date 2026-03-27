package com.gurch.sandbox.documenttemplates.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gurch.sandbox.documenttemplates.DocumentTemplateFormField;
import com.gurch.sandbox.documenttemplates.DocumentTemplateFormFieldType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentTemplateIntrospectionServiceTest {

  private final DocumentTemplateIntrospectionService service =
      new DocumentTemplateIntrospectionService();

  @Test
  void shouldSkipPushButtonsAndExtractRadioValuesFromOnStates() throws IOException {
    byte[] payload = loadTestPdf();

    TemplateIntrospectionResult result = service.introspect("application/pdf", payload);
    List<DocumentTemplateFormField> fields = result.formMap().fields();

    assertThat(fields).noneMatch(field -> field.key().equals("Print"));
    assertThat(fields).noneMatch(field -> field.key().equals("Reset"));

    DocumentTemplateFormField accountTypeField = findField(fields, "1-AcctType");
    assertThat(accountTypeField).isNotNull();
    assertThat(accountTypeField.type()).isEqualTo(DocumentTemplateFormFieldType.RADIO);
    assertThat(accountTypeField.possibleValues()).contains("I-RSP", "PRIF", "LIRA", "RLIF");

    DocumentTemplateFormField electronicDeliveryField = findField(fields, "9-Elect");
    assertThat(electronicDeliveryField).isNotNull();
    assertThat(electronicDeliveryField.type()).isEqualTo(DocumentTemplateFormFieldType.CHECKBOX);
    assertThat(electronicDeliveryField.possibleValues()).containsExactly("false", "true");
  }

  @Test
  void shouldMarkDocxWithEsignAnchorsAsEsignable() throws IOException {
    TemplateIntrospectionResult result = service.introspect(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", loadTestDocx());

    assertThat(result.esignable()).isTrue();
  }

  private byte[] loadTestPdf() throws IOException {
    try (InputStream inputStream =
        getClass().getResourceAsStream("/documenttemplates/introspect.pdf")) {
      assertThat(inputStream).isNotNull();
      return inputStream.readAllBytes();
    }
  }

  private byte[] loadTestDocx() throws IOException {
    try (InputStream inputStream =
        getClass().getResourceAsStream("/documenttemplates/introspect.docx")) {
      assertThat(inputStream).isNotNull();
      return inputStream.readAllBytes();
    }
  }

  private static DocumentTemplateFormField findField(
      List<DocumentTemplateFormField> fields, String key) {
    for (DocumentTemplateFormField field : fields) {
      if (field.key().equals(key)) {
        return field;
      }
    }
    return null;
  }
}
