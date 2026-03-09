package com.gurch.sandbox.esign.internal;

enum AnchorToken {
  S1("s1"),
  S2("s2"),
  D1("d1"),
  D2("d2");

  private final String token;

  AnchorToken(String token) {
    this.token = token;
  }

  String token() {
    return token;
  }
}
