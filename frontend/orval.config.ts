import { defineConfig } from "orval";

export default defineConfig({
  api: {
    input: {
      target: "http://127.0.0.1:8080/v3/api-docs"
    },
    output: {
      target: "./src/api/generated.ts",
      schemas: "./src/api/model",
      client: "react-query",
      mode: "split",
      clean: true,
      headers: true,
      override: {
        mutator: {
          path: "./src/lib/http.ts",
          name: "customInstance"
        }
      }
    }
  }
});
