import { Navigate, Route, Router, createRootRouteWithContext } from "@tanstack/react-router";
import type { QueryClient } from "@tanstack/react-query";
import App from "./App";
import {
  DocumentTemplateGeneratePage,
  parseTemplateIdParams,
  stringifyTemplateIdParams,
} from "./routes/document-templates/$templateId/generate";
import {
  DocumentTemplateCreatePage,
} from "./routes/document-templates/new";
import {
  DocumentTemplateSearchPage,
  validateDocumentTemplateSearch,
} from "./routes/document-templates/search";
import { EsignPage, validateEsignSearch } from "./routes/esign";
import { EsignReturnPage, validateEsignReturnSearch } from "./routes/esign-return";

type RouterContext = {
  queryClient: QueryClient;
};

const rootRoute = createRootRouteWithContext<RouterContext>()({
  component: App,
});

const indexRoute = new Route({
  getParentRoute: () => rootRoute,
  path: "/",
  component: () => <Navigate to="/document-templates" search={{ page: 0, size: 12 }} />,
});

const documentTemplateSearchRoute = new Route({
  getParentRoute: () => rootRoute,
  path: "/document-templates",
  validateSearch: validateDocumentTemplateSearch,
  component: DocumentTemplateSearchPage,
});

const documentTemplateCreateRoute = new Route({
  getParentRoute: () => rootRoute,
  path: "/document-templates/new",
  component: DocumentTemplateCreatePage,
});

const documentTemplateGenerateRoute = new Route({
  getParentRoute: () => rootRoute,
  path: "/document-templates/$templateId/generate",
  parseParams: parseTemplateIdParams,
  stringifyParams: stringifyTemplateIdParams,
  component: DocumentTemplateGeneratePage,
});

const esignRoute = new Route({
  getParentRoute: () => rootRoute,
  path: "/esign",
  validateSearch: validateEsignSearch,
  component: EsignPage,
});

const esignReturnRoute = new Route({
  getParentRoute: () => rootRoute,
  path: "/esign/return",
  validateSearch: validateEsignReturnSearch,
  component: EsignReturnPage,
});

const routeTree = rootRoute.addChildren([
  indexRoute,
  documentTemplateSearchRoute,
  documentTemplateCreateRoute,
  documentTemplateGenerateRoute,
  esignRoute,
  esignReturnRoute,
]);

export function createAppRouter(queryClient: QueryClient) {
  return new Router({
    routeTree,
    defaultPreload: "intent",
    context: {
      queryClient,
    },
  });
}

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof createAppRouter>;
  }
}
